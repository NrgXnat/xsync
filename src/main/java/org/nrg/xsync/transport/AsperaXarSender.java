package org.nrg.xsync.transport;

import java.io.File;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.nrg.xsync.aspera.AsperaClient;
import org.nrg.xsync.aspera.AsperaProjectPrefs;
import org.nrg.xsync.connection.RemoteConnection;
import org.nrg.xsync.connection.RemoteConnectionManager;
import org.nrg.xsync.connection.RemoteConnectionResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Sends a XAR via IBM Aspera: uploads the file with {@code ascp} to the
 * destination host, then triggers a server-side import-by-path. On any Aspera
 * failure it falls back to {@link HttpsXarSender}.
 *
 * @author XSync
 */
@Slf4j
@Component
public class AsperaXarSender implements XarSender {

    private static final int ASPERA_RETRY = 1;

    private final AsperaClient _aspera;
    private final AsperaProjectPrefs _asperaProjectPrefs;
    private final RemoteConnectionManager _manager;
    private final HttpsXarSender _httpsFallback;

    @Autowired
    public AsperaXarSender(final AsperaClient aspera, final AsperaProjectPrefs asperaProjectPrefs,
                           final RemoteConnectionManager manager, final HttpsXarSender httpsFallback) {
        _aspera = aspera;
        _asperaProjectPrefs = asperaProjectPrefs;
        _manager = manager;
        _httpsFallback = httpsFallback;
    }

    /**
     * Aspera supports a project only when it is enabled and fully configured
     * (node URL and user present); otherwise the project uses HTTPS.
     *
     * @param projectId the local (source) project id
     * @return {@code true} if Aspera is enabled and configured for the project
     */
    @Override
    public boolean supports(final String projectId) {
        if (!Boolean.TRUE.equals(_asperaProjectPrefs.getAsperaEnabled(projectId))) {
            log.info("Using HTTPS for the data transfer method.");
            return false;
        }
        final String node = _asperaProjectPrefs.getAsperaNodeUrl(projectId);
        final String aUser = _asperaProjectPrefs.getAsperaNodeUser(projectId);
        if (node == null || node.isEmpty() || aUser == null || aUser.isEmpty()) {
            log.error("Aspera is enabled but not properly configured.  Using HTTPS transfers instead.");
            return false;
        }
        log.info("Using Aspera for the data transfer method.");
        return true;
    }

    @Override
    public RemoteConnectionResponse send(final String projectId, final RemoteConnection connection, final File xar) throws Exception {
        int retryCount = 0;
        boolean uploadSuccess = false;
        boolean exceptionOnHttpSend = false;
        try {
            while (!uploadSuccess && retryCount <= ASPERA_RETRY) {
                uploadSuccess = _aspera.upload(projectId, xar);
                retryCount += 1;
            }
            if (uploadSuccess) {
                final String xarPath = _asperaProjectPrefs.getDestinationDirectory(projectId) +
                        File.separator + xar.getName();
                return _manager.importXar(connection, xarPath);
            } else {
                log.warn("Aspera upload  and retries failed.  Failing over to standard http send.");
                exceptionOnHttpSend = true;
                return _httpsFallback.send(projectId, connection, xar);
            }
        } catch (Exception e) {
            log.debug(ExceptionUtils.getStackTrace(e));
            if (!exceptionOnHttpSend) {
                log.warn("Aspera upload failed with an exception.  Failing over to standard http send.");
                return _httpsFallback.send(projectId, connection, xar);
            } else {
                throw e;
            }
        }
    }
}
