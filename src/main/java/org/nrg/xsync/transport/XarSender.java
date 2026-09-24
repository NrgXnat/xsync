package org.nrg.xsync.transport;

import java.io.File;

import org.nrg.xsync.connection.RemoteConnection;
import org.nrg.xsync.connection.RemoteConnectionResponse;

/**
 * A strategy for delivering a XAR file to a destination XNAT.
 *
 * <p>Each transport method (HTTPS, Aspera, and&mdash;in future&mdash;Globus) is a
 * separate implementation. {@link XarSenderResolver} selects one per project
 * based on {@link #supports(String)}. HTTPS is the default and every other
 * sender's fallback.</p>
 *
 * @author XSync
 */
public interface XarSender {

    /**
     * Whether this sender is configured and enabled for the given project.
     *
     * @param projectId the local (source) project id
     * @return {@code true} if this sender should be used for the project
     */
    boolean supports(String projectId);

    /**
     * Deliver a XAR to the destination identified by the connection.
     *
     * @param projectId  the local (source) project id
     * @param connection the remote connection to the destination XNAT
     * @param xar        the XAR file to send
     * @return the destination's response
     * @throws Exception if the transfer or the subsequent import fails
     */
    RemoteConnectionResponse send(String projectId, RemoteConnection connection, File xar) throws Exception;
}
