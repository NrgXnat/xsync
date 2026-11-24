package org.nrg.xsync.transport;

/**
 * Site-level governance over which transports may be used. The
 * {@link XarSenderResolver} consults this before selecting a non-default
 * transport, so an admin can enable or disable a transport site-wide
 * independently of any per-project configuration.
 */
public interface SiteTransportPolicy {

    /**
     * Whether a transport is permitted site-wide. Enablement is opt-in: an
     * optional transport is disabled until an admin enables it. The baseline
     * HTTPS transport is always enabled.
     *
     * @param transportId the transport's {@link XarSender#transportId()}
     * @return {@code true} if the transport may be used
     */
    boolean isTransportEnabled(String transportId);
}
