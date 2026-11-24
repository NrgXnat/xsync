package org.nrg.xsync.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.nrg.xsync.connection.RemoteConnection;
import org.nrg.xsync.connection.RemoteConnectionResponse;

/**
 * Unit tests for {@link XarSenderResolver} as a priority-ordered registry over
 * injected {@link XarSender} beans (the transport SPI). Verifies that selection
 * follows {@link XarSender#priority()} regardless of registration order, that
 * {@code supports} and site-level {@link SiteTransportPolicy} enablement gate
 * the choice, and that the lowest-priority always-on sender is the fallback.
 *
 * <p>{@code fast} is a stand-in for a high-priority, plugin-contributed transport
 * (it outranks Aspera and HTTPS).</p>
 */
class XarSenderResolverTest {

    /** Policy that permits every transport (the opt-out default). */
    private static final SiteTransportPolicy ALL_ENABLED = transportId -> true;

    /** A fake transport with a fixed id, priority, and supports() answer. */
    private static final class FakeSender implements XarSender {
        private final String name;
        private final int priority;
        private final boolean supports;

        FakeSender(final String name, final int priority, final boolean supports) {
            this.name = name;
            this.priority = priority;
            this.supports = supports;
        }

        @Override public String transportId() { return name; }

        @Override public int priority() { return priority; }

        @Override public boolean supports(final String projectId, final RemoteConnection connection) {
            return supports;
        }

        @Override public RemoteConnectionResponse send(final String projectId, final RemoteConnection connection,
                                                       final File xar) {
            return null;
        }

        @Override public String toString() { return name; }
    }

    private static final FakeSender HTTPS = new FakeSender("https", Integer.MIN_VALUE, true);

    @Test
    void picksHighestPriorityThatSupports() {
        final FakeSender fast = new FakeSender("fast", 200, true);
        final FakeSender aspera = new FakeSender("aspera", 100, true);
        // Deliberately shuffled registration order; priority must decide.
        final XarSenderResolver resolver = new XarSenderResolver(List.of(aspera, HTTPS, fast), ALL_ENABLED);

        assertSame(fast, resolver.resolve("proj", null), "highest-priority supporting sender wins");
    }

    @Test
    void skipsHigherPriorityThatDoesNotSupport() {
        final FakeSender fast = new FakeSender("fast", 200, false); // not configured for this destination
        final FakeSender aspera = new FakeSender("aspera", 100, true);
        final XarSenderResolver resolver = new XarSenderResolver(List.of(fast, aspera, HTTPS), ALL_ENABLED);

        assertSame(aspera, resolver.resolve("proj", null),
                "a higher-priority sender that does not support is skipped");
    }

    @Test
    void skipsHigherPriorityThatIsDisabledSiteWide() {
        final FakeSender fast = new FakeSender("fast", 200, true);  // supports, but...
        final FakeSender aspera = new FakeSender("aspera", 100, true);
        // Admin has disabled the 'fast' transport site-wide; even though it supports and outranks, it is skipped.
        final SiteTransportPolicy fastDisabled = transportId -> !transportId.equals("fast");
        final XarSenderResolver resolver = new XarSenderResolver(List.of(fast, aspera, HTTPS), fastDisabled);

        assertSame(aspera, resolver.resolve("proj", null),
                "a site-disabled transport is skipped even when it supports and has higher priority");
    }

    @Test
    void fallsBackToHttpsWhenNoneOfTheOthersSupport() {
        final FakeSender fast = new FakeSender("fast", 200, false);
        final FakeSender aspera = new FakeSender("aspera", 100, false);
        final XarSenderResolver resolver = new XarSenderResolver(List.of(fast, aspera, HTTPS), ALL_ENABLED);

        assertSame(HTTPS, resolver.resolve("proj", null), "HTTPS is the guaranteed fallback");
    }

    @Test
    void ordersAreIndependentOfRegistrationOrder() {
        final FakeSender fast = new FakeSender("fast", 200, true);
        final FakeSender aspera = new FakeSender("aspera", 100, true);
        // Same senders, several registration orders: all must resolve to fast.
        assertSame(fast, new XarSenderResolver(List.of(fast, aspera, HTTPS), ALL_ENABLED).resolve("p", null));
        assertSame(fast, new XarSenderResolver(List.of(HTTPS, aspera, fast), ALL_ENABLED).resolve("p", null));
        assertSame(fast, new XarSenderResolver(List.of(aspera, fast, HTTPS), ALL_ENABLED).resolve("p", null));
    }

    @Test
    void isRegisteredMatchesOnlyRegisteredTransportIds() {
        final XarSenderResolver resolver = new XarSenderResolver(
                List.of(new FakeSender("aspera", 100, true), HTTPS), ALL_ENABLED);

        assertTrue(resolver.isRegistered("aspera"));
        assertTrue(resolver.isRegistered("https"));
        assertFalse(resolver.isRegistered("asprea"), "an unknown (e.g. misspelled) id is not registered");
        assertFalse(resolver.isRegistered(null));
    }

    @Test
    void throwsWhenNoSenderIsEligible() {
        // Nothing supports and no baseline: the invariant (an enabled default must be registered) is violated.
        final XarSenderResolver resolver = new XarSenderResolver(List.of(new FakeSender("x", 0, false)), ALL_ENABLED);
        assertThrows(IllegalStateException.class, () -> resolver.resolve("proj", null));
    }

    @Test
    void interfaceDefaultPriorityIsZeroAndHttpsIsStrictlyLast() {
        // A sender that does not override priority() uses the interface default.
        final XarSender usingDefault = new XarSender() {
            @Override public String transportId() { return "default"; }
            @Override public boolean supports(final String projectId, final RemoteConnection connection) {
                return false;
            }
            @Override public RemoteConnectionResponse send(final String projectId, final RemoteConnection connection,
                                                           final File xar) {
                return null;
            }
        };
        assertEquals(0, usingDefault.priority(), "the SPI default priority is 0");
        assertEquals(Integer.MIN_VALUE, HTTPS.priority(), "HTTPS overrides to the minimum so it is strictly last");
    }
}
