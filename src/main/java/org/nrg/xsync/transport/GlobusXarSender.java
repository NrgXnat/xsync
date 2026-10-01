package org.nrg.xsync.transport;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.nrg.xapi.exceptions.NotFoundException;
import org.nrg.xsync.connection.RemoteConnection;
import org.nrg.xsync.connection.RemoteConnectionManager;
import org.nrg.xsync.connection.RemoteConnectionResponse;
import org.nrg.xsync.globus.GlobusAuthService;
import org.nrg.xsync.globus.GlobusClient;
import org.nrg.xsync.globus.GlobusCredentials;
import org.nrg.xsync.globus.GlobusProjectPrefs;
import org.nrg.xsync.globus.entities.GlobusEndpoint;
import org.nrg.xsync.globus.services.GlobusEndpointService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Sends a XAR over Globus: stages it into the local outbox guest collection,
 * submits a Globus transfer to the destination inbox guest collection, waits
 * for the task to complete, then triggers a server-side import-by-path at the
 * destination. On any Globus failure it falls back to {@link HttpsXarSender}.
 *
 * <p>Transport parameters come from {@link GlobusProjectPrefs} (per project)
 * plus the named {@link GlobusEndpoint} (client credentials + inbox/outbox
 * guest-collection UUIDs). The external interactions are isolated behind
 * {@code protected} seams so the orchestration can be unit-tested without
 * network or Globus access.</p>
 *
 * @author XSync
 */
@Slf4j
@Component
public class GlobusXarSender implements XarSender {

    /** Poll a submitted transfer this often. */
    static final long POLL_INTERVAL_MILLIS = 30_000L;
    /** Give up on a transfer after this long (matches the HTTPS import poll cap). */
    static final long TIMEOUT_MILLIS = 2L * 60L * 60L * 1000L;

    private final GlobusProjectPrefs _prefs;
    private final GlobusEndpointService _endpointService;
    private final GlobusAuthService _authService;
    private final GlobusClient _client;
    private final RemoteConnectionManager _manager;
    private final HttpsXarSender _httpsFallback;

    @Autowired
    public GlobusXarSender(final GlobusProjectPrefs prefs, final GlobusEndpointService endpointService,
                           final GlobusAuthService authService, final GlobusClient client,
                           final RemoteConnectionManager manager, final HttpsXarSender httpsFallback) {
        _prefs = prefs;
        _endpointService = endpointService;
        _authService = authService;
        _client = client;
        _manager = manager;
        _httpsFallback = httpsFallback;
    }

    /** The per-project Globus route configuration, resolved from preferences. */
    record RouteConfig(boolean enabled, String endpointName, String outboxDirectory,
                       String remoteInboxPath, String remoteInboxServerDirectory) {
        boolean pathsComplete() {
            return StringUtils.isNoneBlank(endpointName, outboxDirectory, remoteInboxPath, remoteInboxServerDirectory);
        }
    }

    /**
     * Globus supports a project only when it is enabled, fully configured, and
     * its named endpoint is registered; otherwise the project uses HTTPS.
     *
     * @param projectId the local (source) project id
     * @return {@code true} if Globus is usable for the project
     */
    @Override
    public boolean supports(final String projectId) {
        final RouteConfig config = config(projectId);
        if (!config.enabled()) {
            return false;
        }
        if (!config.pathsComplete()) {
            log.error("Globus is enabled for project {} but not fully configured. Using HTTPS instead.", projectId);
            return false;
        }
        try {
            lookupEndpoint(config.endpointName());
        } catch (NotFoundException e) {
            log.error("Globus endpoint '{}' for project {} is not registered. Using HTTPS instead.",
                    config.endpointName(), projectId);
            return false;
        }
        log.info("Using Globus for the data transfer method.");
        return true;
    }

    @Override
    public RemoteConnectionResponse send(final String projectId, final RemoteConnection connection, final File xar)
            throws Exception {
        final RouteConfig config = config(projectId);
        final GlobusEndpoint endpoint;
        try {
            endpoint = lookupEndpoint(config.endpointName());
        } catch (NotFoundException e) {
            log.warn("Globus endpoint '{}' not found; failing over to HTTPS.", config.endpointName());
            return fallback(projectId, connection, xar);
        }

        final String opaqueName = opaqueName();
        File staged = null;
        try {
            staged = stage(xar, config.outboxDirectory(), opaqueName);
            log.debug("Staged XAR for project {} in the Globus outbox as {}", projectId, opaqueName);

            final String sourcePath = "/" + opaqueName;
            final String destinationPath = joinPath(config.remoteInboxPath(), opaqueName);
            final String token = obtainToken(endpoint);
            final GlobusClient.TransferRequest request = new GlobusClient.TransferRequest(
                    endpoint.getOutboxCollectionId(), sourcePath,
                    endpoint.getInboxCollectionId(), destinationPath,
                    "XSync " + projectId, true);

            final String taskId = submit(token, request);
            log.info("Submitted Globus transfer task {} for project {}.", taskId, projectId);
            final GlobusClient.TaskStatus status = await(token, taskId);
            if (!status.isSuccessful()) {
                log.warn("Globus task {} did not succeed (status {}, {}); failing over to HTTPS.",
                        taskId, status.status(), status.fatalError());
                return fallback(projectId, connection, xar);
            }
            log.info("Globus task {} succeeded; importing the transferred XAR at the destination.", taskId);

            final String serverPath = joinPath(config.remoteInboxServerDirectory(), opaqueName);
            return importByPath(connection, serverPath);
        } catch (Exception e) {
            log.warn("Globus transfer for project {} failed ({}); failing over to HTTPS.", projectId, e.getMessage());
            log.debug("Globus transfer failure detail", e);
            return fallback(projectId, connection, xar);
        } finally {
            cleanup(staged);
        }
    }

    /**
     * Join a base path and a name with a single {@code /} separator.
     *
     * @param base the base path (may be blank)
     * @param name the name to append
     * @return the joined path
     */
    static String joinPath(final String base, final String name) {
        if (StringUtils.isBlank(base)) {
            return "/" + name;
        }
        return base.endsWith("/") ? base + name : base + "/" + name;
    }

    // --- seams (overridable so the orchestration is unit-testable) ---------

    /** @return the per-project route configuration read from preferences. */
    protected RouteConfig config(final String projectId) {
        return new RouteConfig(Boolean.TRUE.equals(_prefs.getGlobusEnabled(projectId)),
                _prefs.getGlobusEndpointName(projectId),
                _prefs.getGlobusOutboxDirectory(projectId),
                _prefs.getGlobusRemoteInboxPath(projectId),
                _prefs.getGlobusRemoteInboxServerDirectory(projectId));
    }

    /** @return the registered endpoint, or throws if it is not registered. */
    protected GlobusEndpoint lookupEndpoint(final String endpointName) throws NotFoundException {
        return _endpointService.getByName(endpointName);
    }

    /** @return an opaque (non-identifying) file name for the staged XAR. */
    protected String opaqueName() {
        return UUID.randomUUID().toString().replace("-", "") + ".xar";
    }

    /** Stage the XAR into the outbox directory under the opaque name; returns the staged file. */
    protected File stage(final File xar, final String outboxDirectory, final String opaqueName) throws IOException {
        final Path outbox = new File(outboxDirectory).toPath();
        Files.createDirectories(outbox);
        final Path target = outbox.resolve(opaqueName);
        Files.copy(xar.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
        return target.toFile();
    }

    /** @return a Transfer token for the endpoint's client credentials. */
    protected String obtainToken(final GlobusEndpoint endpoint) {
        return _authService.getTransferToken(new GlobusCredentials(endpoint.getClientId(), endpoint.getClientSecret()));
    }

    /** @return the submitted task id. */
    protected String submit(final String token, final GlobusClient.TransferRequest request) {
        return _client.submitTransfer(token, request);
    }

    /** @return the terminal task status. */
    protected GlobusClient.TaskStatus await(final String token, final String taskId) {
        return _client.waitForTask(token, taskId, TIMEOUT_MILLIS, POLL_INTERVAL_MILLIS);
    }

    /** Trigger the destination's import-by-path of the transferred file. */
    protected RemoteConnectionResponse importByPath(final RemoteConnection connection, final String serverPath)
            throws Exception {
        return _manager.importXar(connection, serverPath);
    }

    /** Fall back to the HTTPS sender (streams the original XAR to the destination). */
    protected RemoteConnectionResponse fallback(final String projectId, final RemoteConnection connection,
                                                final File xar) throws Exception {
        return _httpsFallback.send(projectId, connection, xar);
    }

    /** Delete the staged outbox file; best-effort. */
    protected void cleanup(final File staged) {
        if (staged != null && staged.exists() && !staged.delete()) {
            log.warn("Could not delete staged Globus outbox file {}", staged.getAbsolutePath());
        }
    }
}
