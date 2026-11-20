package org.nrg.xsync.transport;

import java.util.Comparator;
import java.util.List;

import org.nrg.xsync.connection.RemoteConnection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Registry that selects the {@link XarSender} for a given destination.
 *
 * <p>Rather than hard-coding the known transports, this injects every
 * {@link XarSender} bean in the context and orders them by
 * {@link XarSender#priority()} (highest first). {@link #resolve} returns the
 * first whose {@link XarSender#supports(String, RemoteConnection)} is true;
 * {@link HttpsXarSender} uses the lowest priority and always supports, so it is
 * the guaranteed fallback.</p>
 *
 * <p>This is the transport SPI: a transport contributed by <em>another plugin</em>
 * (e.g. a Globus plugin) registers its {@code XarSender} into the shared XNAT
 * Spring context and is discovered here automatically, with no change to this
 * class. Ordering is by explicit priority, not plugin load order (which XNAT
 * leaves unspecified).</p>
 *
 * <p>Selection is also gated by site-level governance: a transport is
 * considered only when {@link SiteTransportPolicy#isTransportEnabled} allows it,
 * so an admin can disable a transport (e.g. Globus) site-wide. HTTPS is the
 * baseline transport and is expected to remain enabled; it is the fallback.</p>
 */
@Component
public class XarSenderResolver {

    private final List<XarSender> _senders;
    private final SiteTransportPolicy _transportPolicy;

    @Autowired
    public XarSenderResolver(final List<XarSender> senders, final SiteTransportPolicy transportPolicy) {
        // Highest priority first; stable for equal priorities.
        _senders = senders.stream()
                .sorted(Comparator.comparingInt(XarSender::priority).reversed())
                .toList();
        _transportPolicy = transportPolicy;
    }

    /**
     * Resolve the sender for a destination: the highest-priority registered
     * sender that is enabled site-wide and whose {@code supports} is true for
     * this project and destination. Globus (if present, enabled, and configured
     * for the destination) outranks Aspera, which outranks HTTPS; HTTPS is the
     * enabled baseline that always supports, so a sender is always found.
     *
     * @param projectId  the local (source) project id
     * @param connection the remote connection to the destination XNAT
     * @return the sender to use for this destination
     */
    public XarSender resolve(final String projectId, final RemoteConnection connection) {
        return _senders.stream()
                .filter(sender -> _transportPolicy.isTransportEnabled(sender.transportId()))
                .filter(sender -> sender.supports(projectId, connection))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No enabled XarSender supports project " + projectId + " for destination "
                                + (connection == null ? "(null)" : connection.getUrl())
                                + "; the baseline HTTPS transport must be registered and enabled."));
    }
}
