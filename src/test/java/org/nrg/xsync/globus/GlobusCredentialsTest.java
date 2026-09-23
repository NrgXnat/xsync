package org.nrg.xsync.globus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link GlobusCredentials}, focused on the security-relevant
 * contract that the client secret is never exposed in {@link Object#toString()}.
 */
class GlobusCredentialsTest {

    @Test
    void toStringRedactsSecretButKeepsClientId() {
        final GlobusCredentials credentials = new GlobusCredentials("client-abc", "super-secret-value");
        final String rendered = credentials.toString();

        assertFalse(rendered.contains("super-secret-value"),
                "toString() must not contain the client secret");
        assertTrue(rendered.contains("client-abc"),
                "toString() should still identify the client by id");
        assertTrue(rendered.contains("***"),
                "toString() should mark the secret as redacted");
    }

    @Test
    void accessorsReturnConstructorValues() {
        final GlobusCredentials credentials = new GlobusCredentials("client-abc", "the-secret");

        assertEquals("client-abc", credentials.clientId());
        assertEquals("the-secret", credentials.clientSecret(),
                "the secret is still readable via the accessor (only toString redacts it)");
    }
}
