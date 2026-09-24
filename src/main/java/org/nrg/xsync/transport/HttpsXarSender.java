package org.nrg.xsync.transport;

import java.io.File;

import org.nrg.xsync.connection.RemoteConnection;
import org.nrg.xsync.connection.RemoteConnectionManager;
import org.nrg.xsync.connection.RemoteConnectionResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Sends a XAR over HTTPS by streaming it to the destination XNAT's import
 * service. This is the default transport and the fallback used by the other
 * senders when their preferred transport fails.
 *
 * @author XSync
 */
@Component
public class HttpsXarSender implements XarSender {

    private final RemoteConnectionManager _manager;

    @Autowired
    public HttpsXarSender(final RemoteConnectionManager manager) {
        _manager = manager;
    }

    /**
     * HTTPS is always available, so it supports every project. It is the
     * resolver's default and every other sender's fallback.
     *
     * @param projectId the local project id (unused)
     * @return {@code true}
     */
    @Override
    public boolean supports(final String projectId) {
        return true;
    }

    @Override
    public RemoteConnectionResponse send(final String projectId, final RemoteConnection connection, final File xar) throws Exception {
        return _manager.importXar(connection, xar);
    }
}
