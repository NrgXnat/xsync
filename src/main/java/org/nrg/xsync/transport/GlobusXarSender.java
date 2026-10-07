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
import org.nrg.xsync.components.XsyncSitePreferencesBean;
import org.nrg.xsync.connection.RemoteConnection;
import org.nrg.xsync.connection.RemoteConnectionManager;
import org.nrg.xsync.connection.RemoteConnectionResponse;
import org.nrg.xsync.globus.GlobusAuthService;
import org.nrg.xsync.globus.GlobusClient;
import org.nrg.xsync.globus.GlobusProjectPrefs;
import org.nrg.xsync.globus.entities.GlobusEndpoint;
import org.nrg.xsync.globus.entities.GlobusNodeConfig;
import org.nrg.xsync.globus.services.GlobusEndpointService;
import org.nrg.xsync.globus.services.GlobusNodeConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Sends a XAR over Globus: stages it into this node's outbox, submits a Globus
 * transfer to the destination's inbox guest collection, waits for the task to
 * complete, then triggers a server-side import-by-path at the destination. On
 * any Globus failure it falls back to {@link HttpsXarSender}.
 *
 * <p>Parameters come from three places: the node's {@link GlobusNodeConfig}
 * (one service account + one outbox), the selected {@link GlobusEndpoint}
 * destination (remote inbox + paths), and {@link GlobusProjectPrefs} (whether
 * Globus is enabled and which destination).
 * External interactions are isolated behind {@code protected} seams so the
 * orchestration can be unit-tested without network or Globus access.</p>
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
    private final XsyncSitePreferencesBean _sitePrefs;
    private final GlobusNodeConfigService _nodeConfigService;
    private final GlobusEndpointService _endpointService;
    private final GlobusAuthService _authService;
    private final GlobusClient _client;
    private final RemoteConnectionManager _manager;
    private final HttpsXarSender _httpsFallback;

    @Autowired
    public GlobusXarSender(final GlobusProjectPrefs prefs, final XsyncSitePreferencesBean sitePrefs,
                           final GlobusNodeConfigService nodeConfigService, final GlobusEndpointService endpointService,
                           final GlobusAuthService authService, final GlobusClient client,
                           final RemoteConnectionManager manager, final HttpsXarSender httpsFallback) {
        _prefs = prefs;
        _sitePrefs = sitePrefs;
        _nodeConfigService = nodeConfigService;
        _endpointService = endpointService;
        _authService = authService;
        _client = client;
        _manager = manager;
        _httpsFallback = httpsFallback;
    }

    /** The per-project Globus selection: whether enabled, and which destination. */
    record RouteConfig(boolean enabled, String endpointName) {
    }

    /**
     * Globus supports a project only when it is enabled (site + project), a
     * destination is selected and registered, and this node's Globus
     * configuration exists; otherwise the project uses HTTPS.
     *
     * @param projectId the local (source) project id
     * @return {@code true} if Globus is usable for the project
     */
    @Override
    public boolean supports(final String projectId) {
        if (!siteGlobusEnabled()) {
            log.debug("Globus is disabled site-wide; project {} will use another transport.", projectId);
            return false;
        }
        final RouteConfig config = config(projectId);
        if (!config.enabled()) {
            return false;
        }
        if (StringUtils.isBlank(config.endpointName())) {
            log.error("Globus is enabled for project {} but no destination is selected. Using HTTPS instead.", projectId);
            return false;
        }
        try {
            lookupEndpoint(config.endpointName());
            nodeConfig();
        } catch (NotFoundException e) {
            log.error("Globus route for project {} is not usable ({}). Using HTTPS instead.", projectId, e.getMessage());
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
        final GlobusNodeConfig nodeConfig;
        try {
            endpoint = lookupEndpoint(config.endpointName());
            nodeConfig = nodeConfig();
        } catch (NotFoundException e) {
            log.warn("Globus route for project {} not fully configured ({}); failing over to HTTPS.",
                    projectId, e.getMessage());
            return fallback(projectId, connection, xar);
        }

        final String opaqueName = opaqueName();
        File staged = null;
        try {
            staged = stage(xar, nodeConfig.getOutboxDirectory(), opaqueName);
            log.debug("Staged XAR for project {} in the Globus outbox as {}", projectId, opaqueName);

            final String sourcePath = "/" + opaqueName;
            final String destinationPath = joinPath(endpoint.getRemoteInboxPath(), opaqueName);
            final String token = obtainToken();
            final GlobusClient.TransferRequest request = new GlobusClient.TransferRequest(
                    nodeConfig.getOutboxCollectionId(), sourcePath,
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

            final String serverPath = joinPath(endpoint.getRemoteInboxServerDirectory(), opaqueName);
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

    /** @return whether Globus is enabled at the site level (governance gate). */
    protected boolean siteGlobusEnabled() {
        return _sitePrefs.getGlobusEnabled();
    }

    /** @return the per-project Globus selection (enabled + destination name). */
    protected RouteConfig config(final String projectId) {
        return new RouteConfig(Boolean.TRUE.equals(_prefs.getGlobusEnabled(projectId)),
                _prefs.getGlobusEndpointName(projectId));
    }

    /** @return the registered destination, or throws if it is not registered. */
    protected GlobusEndpoint lookupEndpoint(final String endpointName) throws NotFoundException {
        return _endpointService.getByName(endpointName);
    }

    /** @return this node's Globus configuration, or throws if it is not set. */
    protected GlobusNodeConfig nodeConfig() throws NotFoundException {
        return _nodeConfigService.getConfig()
                .orElseThrow(() -> new NotFoundException("Globus is not configured for this node."));
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

    /** @return a Transfer token for this node's service account (the single credential-resolution seam). */
    protected String obtainToken() throws NotFoundException {
        return _authService.getTransferToken(_nodeConfigService.getServiceCredentials());
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
