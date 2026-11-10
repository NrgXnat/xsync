package org.nrg.xsync.globus.upload;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.nrg.xapi.exceptions.NotFoundException;
import org.nrg.xdat.XDAT;
import org.nrg.xdat.security.helpers.Users;
import org.nrg.xft.security.UserI;
import org.nrg.xnat.archive.DicomInboxImporter;
import org.nrg.xnat.archive.GradualDicomImporter;
import org.nrg.xnat.services.XnatAppInfo;
import org.nrg.xsync.globus.GlobusAuthService;
import org.nrg.xsync.globus.GlobusClient;
import org.nrg.xsync.globus.entities.GlobusNodeConfig;
import org.nrg.xsync.globus.services.GlobusNodeConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Receive-side watcher for the Globus upload service (spike): detects user
 * DICOM uploads that have finished transferring into this node's inbox
 * collection and hands each completed archive to XNAT's DICOM inbox importer.
 *
 * <p>Detection uses Globus's endpoint-manager (activity-monitor) API rather
 * than the filesystem: each poll asks Globus for transfers that have reached
 * {@code SUCCEEDED} with this node's inbox collection as destination, then
 * reads the files each such task delivered. A {@code SUCCEEDED} task is a
 * definitive completion signal, so no filesystem size/stability heuristic is
 * needed (that is the documented fallback, not implemented here). The user
 * only runs a Globus transfer; XNAT never exposes an upload API.</p>
 *
 * <p>Expected inbox layout is {@code <inboxPath>/<PROJECT_ID>/<user>/<archive>}:
 * the first path segment is the destination project and the second is the
 * uploading user. The import runs as that XNAT user, so XNAT's
 * normal project-edit permissions apply. Mapping the Globus upload identity to
 * an XNAT username (here: same name) is a real-build design point.</p>
 *
 * <p>External interactions are isolated behind {@code protected} seams so the
 * detection, path-resolution, and idempotency logic can be unit-tested without
 * Globus or XNAT.</p>
 *
 * @author XSync
 */
@Slf4j
@Component
public class GlobusUploadInboxMonitor implements Runnable {

    private final GlobusNodeConfigService _nodeConfigService;
    private final GlobusAuthService _authService;
    private final GlobusClient _client;
    private final XnatAppInfo _appInfo;

    /**
     * Task ids already handled this process lifetime (at-most-once per task).
     * In-memory for the spike; a real build persists this and adds retry/backoff.
     */
    private final Set<String> _handledTaskIds = ConcurrentHashMap.newKeySet();

    /**
     * Lower bound for the completion-time filter: tasks that completed before
     * the monitor started are ignored. Fixed for the spike (the window is
     * {@code [startup, now]}); a real build advances a persisted cursor.
     */
    private final Instant _since = Instant.now();

    @Autowired
    public GlobusUploadInboxMonitor(final GlobusNodeConfigService nodeConfigService,
                                    final GlobusAuthService authService, final GlobusClient client,
                                    final XnatAppInfo appInfo) {
        _nodeConfigService = nodeConfigService;
        _authService = authService;
        _client = client;
        _appInfo = appInfo;
    }

    /** A resolved upload: the destination project, uploading user, and absolute archive path. */
    record ResolvedUpload(String projectId, String username, String absolutePath) {
    }

    @Override
    public void run() {
        poll();
    }

    /**
     * Run one detection pass: find newly-completed uploads into the inbox
     * collection and import each. Safe to call repeatedly; already-handled
     * tasks are skipped. Runs only on the primary node, and only when the node
     * has a Globus configuration with an inbox collection.
     */
    public void poll() {
        if (!isPrimaryNode()) {
            return;
        }
        final Optional<String> inboxCollectionId = inboxCollectionId();
        if (inboxCollectionId.isEmpty()) {
            log.debug("No Globus inbox collection configured for this node; upload monitor idle.");
            return;
        }
        final String inboxPath = inboxPath();
        if (StringUtils.isBlank(inboxPath)) {
            log.warn("XNAT inbox path is not configured; cannot import Globus uploads.");
            return;
        }

        final String token;
        try {
            token = token();
        } catch (NotFoundException e) {
            log.warn("Globus is not configured for this node ({}); upload monitor idle.", e.getMessage());
            return;
        } catch (Exception e) {
            log.warn("Could not obtain a Globus token for the upload monitor: {}", e.getMessage());
            return;
        }

        final List<GlobusClient.CompletedTask> tasks;
        try {
            tasks = completedTasks(token, inboxCollectionId.get(), _since);
        } catch (Exception e) {
            log.warn("Globus endpoint-manager task list failed; upload monitor pass aborted: {}", e.getMessage());
            return;
        }

        for (final GlobusClient.CompletedTask task : tasks) {
            if (!_handledTaskIds.add(task.taskId())) {
                continue; // already handled this task id
            }
            handleTask(token, inboxPath, task);
        }
    }

    /** Import every archive a completed task delivered; failures are logged, not retried (spike). */
    private void handleTask(final String token, final String inboxPath, final GlobusClient.CompletedTask task) {
        final List<String> paths;
        try {
            paths = destinationPaths(token, task.taskId());
        } catch (Exception e) {
            log.warn("Could not read delivered files for Globus task {}: {}", task.taskId(), e.getMessage());
            return;
        }
        for (final String relativePath : paths) {
            final Optional<ResolvedUpload> resolved = resolveUpload(inboxPath, relativePath);
            if (resolved.isEmpty()) {
                log.warn("Skipping Globus upload with unexpected inbox path '{}' (task {}); "
                        + "expected <PROJECT_ID>/<user>/<archive>.", relativePath, task.taskId());
                continue;
            }
            final ResolvedUpload upload = resolved.get();
            try {
                importArchive(upload);
                log.info("Imported Globus upload {} for project {} (user {}, task {}).",
                        upload.absolutePath(), upload.projectId(), upload.username(), task.taskId());
            } catch (Exception e) {
                log.warn("Failed to import Globus upload {} (project {}, user {}, task {}): {}",
                        upload.absolutePath(), upload.projectId(), upload.username(), task.taskId(), e.getMessage());
            }
        }
    }

    /**
     * Resolve a collection-relative destination path to the import target,
     * requiring the {@code <PROJECT_ID>/<user>/<archive>} layout and confining
     * the result under the inbox path (reject traversal).
     *
     * @param inboxPath    XNAT's site-configured inbox path (collection root)
     * @param relativePath the collection-relative path Globus delivered to
     * @return the resolved upload, or empty if the path is malformed or escapes
     */
    static Optional<ResolvedUpload> resolveUpload(final String inboxPath, final String relativePath) {
        if (StringUtils.isBlank(relativePath)) {
            return Optional.empty();
        }
        final String[] segments = Arrays.stream(relativePath.split("/"))
                .filter(StringUtils::isNotBlank)
                .toArray(String[]::new);
        // Need at least project, user, and an archive filename.
        if (segments.length < 3) {
            return Optional.empty();
        }
        final Path base = Paths.get(inboxPath).normalize();
        final Path resolved = base.resolve(String.join("/", segments)).normalize();
        if (!resolved.startsWith(base)) {
            return Optional.empty(); // traversal guard
        }
        return Optional.of(new ResolvedUpload(segments[0], segments[1], resolved.toString()));
    }

    // --- seams (overridable so the orchestration is unit-testable) ---------

    /** @return whether this is the primary node (scheduled work runs there only). */
    protected boolean isPrimaryNode() {
        return _appInfo.isPrimaryNode();
    }

    /** @return this node's upload inbox guest-collection UUID, if a Globus configuration with one is set. */
    protected Optional<String> inboxCollectionId() {
        return _nodeConfigService.getConfig()
                .map(GlobusNodeConfig::getUploadInboxCollectionId)
                .filter(StringUtils::isNotBlank);
    }

    /** @return XNAT's site-configured DICOM inbox path (the collection root on disk). */
    protected String inboxPath() {
        return XDAT.getSiteConfigPreferences().getInboxPath();
    }

    /** @return a Transfer token for this node's service account. */
    protected String token() throws NotFoundException {
        return _authService.getTransferToken(_nodeConfigService.getServiceCredentials());
    }

    /** @return completed transfers into the inbox collection since the given time. */
    protected List<GlobusClient.CompletedTask> completedTasks(final String token, final String collectionId,
                                                              final Instant since) {
        return _client.listCompletedTransfersTo(token, collectionId, since);
    }

    /** @return the collection-relative paths a completed task delivered. */
    protected List<String> destinationPaths(final String token, final String taskId) {
        return _client.successfulDestinationPaths(token, taskId);
    }

    /**
     * Hand a completed upload to XNAT's DICOM inbox importer, acting as the
     * uploading user so XNAT's project-edit permissions apply.
     *
     * <p>The import is set to <strong>direct-archive</strong>
     * ({@link GradualDicomImporter#DIRECT_ARCHIVE_PARAM}): the session goes
     * straight to the project archive, bypassing the prearchive — the inbox
     * listener skips the prearchive rebuild for direct-archive sessions and
     * archives each once it has been idle. Routing and anonymization still run;
     * only the prearchive review/staging step is skipped, which is what the
     * upload service wants (no manual prearchive gate).</p>
     *
     * @param upload the resolved upload (project, user, absolute archive path)
     * @throws Exception if the user cannot be resolved or the import is rejected
     */
    protected void importArchive(final ResolvedUpload upload) throws Exception {
        final UserI user = Users.getUser(upload.username());
        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("path", upload.absolutePath());
        parameters.put("PROJECT_ID", upload.projectId());
        parameters.put("cleanupAfterImport", "true");
        parameters.put(GradualDicomImporter.DIRECT_ARCHIVE_PARAM, "true");
        new DicomInboxImporter(null, user, null, parameters).call();
    }
}
