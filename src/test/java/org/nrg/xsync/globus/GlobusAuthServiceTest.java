package org.nrg.xsync.globus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link GlobusAuthService}: token-response parsing (including
 * the {@code other_tokens} multi-resource-server case and the expiry safety
 * margin) and the caching / force-refresh behavior, exercised through the
 * overridable {@link GlobusAuthService#postTokenRequest} seam (no network).
 */
class GlobusAuthServiceTest {

    private static final String TOKEN_ENDPOINT = "https://auth.example/token";

    private static GlobusAuthService service() {
        return new GlobusAuthService(TOKEN_ENDPOINT, new ObjectMapper());
    }

    private static String transferToken(final String accessToken, final long expiresIn) {
        return "{\"resource_server\":\"transfer.api.globus.org\",\"access_token\":\"" + accessToken
                + "\",\"expires_in\":" + expiresIn + "}";
    }

    // --- parseTransferToken ------------------------------------------------

    @Test
    void parsesTopLevelTransferToken() {
        final GlobusAuthService.CachedToken token = service().parseTransferToken(transferToken("AT", 3600));
        assertEquals("AT", token.accessToken());
        assertFalse(token.isExpired());
    }

    @Test
    void parsesTransferTokenFromOtherTokens() {
        final String json = "{\"resource_server\":\"auth.globus.org\",\"access_token\":\"authtok\","
                + "\"expires_in\":3600,\"other_tokens\":[" + transferToken("AT2", 3600) + "]}";
        assertEquals("AT2", service().parseTransferToken(json).accessToken());
    }

    @Test
    void expiryHonorsSafetyMargin() {
        // expires_in below the 5-minute margin means the token is already treated as expired.
        assertTrue(service().parseTransferToken(transferToken("AT", 60)).isExpired(),
                "a token expiring within the safety margin should be considered expired");
    }

    @Test
    void missingTransferTokenThrows() {
        final String json = "{\"resource_server\":\"auth.globus.org\",\"access_token\":\"x\","
                + "\"expires_in\":3600,\"other_tokens\":[]}";
        assertThrows(GlobusAuthException.class, () -> service().parseTransferToken(json));
    }

    @Test
    void emptyResponseThrows() {
        assertThrows(GlobusAuthException.class, () -> service().parseTransferToken(""));
    }

    @Test
    void unparseableResponseThrows() {
        assertThrows(GlobusAuthException.class, () -> service().parseTransferToken("not json"));
    }

    // --- caching / force refresh ------------------------------------------

    /** Counts token requests and returns a fresh, long-lived transfer token each time. */
    private static final class CountingService extends GlobusAuthService {
        private int calls;

        CountingService() {
            super(TOKEN_ENDPOINT, new ObjectMapper());
        }

        @Override
        protected String postTokenRequest(final GlobusCredentials credentials, final String scope) {
            calls++;
            return transferToken("token-" + calls, 3600);
        }
    }

    @Test
    void secondCallForSameClientIsServedFromCache() {
        final CountingService svc = new CountingService();
        final GlobusCredentials creds = new GlobusCredentials("client", "secret");

        final String first = svc.getTransferToken(creds);
        final String second = svc.getTransferToken(creds);

        assertEquals(first, second, "cached token should be reused");
        assertEquals(1, svc.calls, "the token endpoint should be hit only once");
    }

    @Test
    void forceRefreshBypassesCache() {
        final CountingService svc = new CountingService();
        final GlobusCredentials creds = new GlobusCredentials("client", "secret");

        svc.getTransferToken(creds);
        svc.getTransferToken(creds, true);

        assertEquals(2, svc.calls, "force refresh should mint a new token");
    }

    @Test
    void differentClientsGetSeparateCacheEntries() {
        final CountingService svc = new CountingService();

        svc.getTransferToken(new GlobusCredentials("client-a", "secret"));
        svc.getTransferToken(new GlobusCredentials("client-b", "secret"));

        assertEquals(2, svc.calls, "distinct clients must not share a cached token");
    }
}
