package org.nrg.xsync.transport;

/**
 * Site-level governance over which transports may be used. The
 * {@link XarSenderResolver} consults this before selecting a non-default
 * transport, so an admin can disable a transport site-wide (e.g. turn off
 * Globus) independently of any per-project configuration.
 */
public interface SiteTransportPolicy {

    /**
     * Whether a transport is permitted site-wide. Enablement is opt-out: a
     * transport is enabled unless an admin has explicitly disabled it.
     *
     * @param transportId the transport's {@link XarSender#transportId()}
     * @return {@code true} if the transport may be used
     */
    boolean isTransportEnabled(String transportId);
}
