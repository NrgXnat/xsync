package org.nrg.xsync.pojo;

import org.nrg.xsync.transport.SiteTransportPolicy;
import org.nrg.xsync.transport.XarSender;

/**
 * A registered XAR transport and its site-wide enablement, for the admin UI that
 * presents a per-transport enable control.
 *
 * @param id          the transport's stable id (see {@code XarSender.transportId()})
 * @param displayName human-readable transport name
 * @param description optional help text (may be empty)
 * @param enabled     whether the transport is currently enabled site-wide
 * @param baseline    whether this is the always-on baseline transport (HTTPS),
 *                    which is always enabled and cannot be toggled off
 */
public record TransportInfo(String id, String displayName, String description, boolean enabled, boolean baseline) {

    /**
     * Builds an info record from a registered sender and its site-wide
     * enablement, flagging the baseline transport.
     *
     * @param sender  the registered transport
     * @param enabled whether the transport is currently enabled site-wide
     */
    public TransportInfo(final XarSender sender, final boolean enabled) {
        this(sender.transportId(), sender.displayName(), sender.description(), enabled,
                SiteTransportPolicy.BASELINE_TRANSPORT_ID.equals(sender.transportId()));
    }
}
