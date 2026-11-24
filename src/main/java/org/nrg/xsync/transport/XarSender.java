package org.nrg.xsync.transport;

import java.io.File;

import org.nrg.xsync.connection.RemoteConnection;
import org.nrg.xsync.connection.RemoteConnectionResponse;

/**
 * A strategy for delivering a XAR file to a destination XNAT.
 *
 * <p>Each transport method is a separate implementation (HTTPS and Aspera here;
 * others may be contributed by plugins via this SPI). {@link XarSenderResolver}
 * selects one per destination based on
 * {@link #supports(String, RemoteConnection)}. HTTPS is the default and every
 * other sender's fallback.</p>
 */
public interface XarSender {

    /**
     * Whether this sender is configured and enabled for the given project and
     * destination. The {@code connection} identifies the specific destination
     * (its url and remote project id), so a project with several destinations
     * may use a different transport for each.
     *
     * @param projectId  the local (source) project id
     * @param connection the remote connection to the destination XNAT
     * @return {@code true} if this sender should be used for this destination
     */
    boolean supports(String projectId, RemoteConnection connection);

    /**
     * A stable identifier for this transport (e.g. {@code "https"},
     * {@code "aspera"}), used by {@link XarSenderResolver} to consult site-level
     * enablement ({@link SiteTransportPolicy}). Must be unique across registered
     * transports and stable across releases (it keys the enablement setting).
     *
     * @return the transport id
     */
    String transportId();

    /**
     * Selection priority: {@link XarSenderResolver} consults registered senders
     * highest-priority first and uses the first whose
     * {@link #supports(String, RemoteConnection)} is true. The default HTTPS
     * transport uses the lowest priority so it is always the final fallback.
     * Ordering is explicit (not plugin load order), so a transport contributed
     * by another plugin slots in deterministically.
     *
     * @return the selection priority; higher wins
     */
    default int priority() {
        return 0;
    }

    /**
     * A human-readable name for this transport, used to label its site-wide
     * enable control in the admin UI (e.g. {@code "Aspera"}). Defaults to the
     * {@link #transportId()} so a transport that does not supply one still
     * renders.
     *
     * @return the display name
     */
    default String displayName() {
        return transportId();
    }

    /**
     * Optional help text shown beside this transport's enable control in the
     * admin UI. Defaults to empty.
     *
     * @return the description, or an empty string
     */
    default String description() {
        return "";
    }

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
