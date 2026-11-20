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
 */
@Component
public class HttpsXarSender implements XarSender {

    private final RemoteConnectionManager _manager;

    @Autowired
    public HttpsXarSender(final RemoteConnectionManager manager) {
        _manager = manager;
    }

    @Override
    public String transportId() {
        return "https";
    }

    /** The guaranteed fallback: lowest possible priority, so every other sender is tried first. */
    @Override
    public int priority() {
        return Integer.MIN_VALUE;
    }

    /**
     * HTTPS is always available, so it supports every destination. It is the
     * resolver's default and every other sender's fallback.
     *
     * @param projectId  the local project id (unused)
     * @param connection the destination connection (unused)
     * @return {@code true}
     */
    @Override
    public boolean supports(final String projectId, final RemoteConnection connection) {
        return true;
    }

    @Override
    public RemoteConnectionResponse send(final String projectId, final RemoteConnection connection, final File xar) throws Exception {
        return _manager.importXar(connection, xar);
    }
}
