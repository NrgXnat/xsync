package org.nrg.xsync.globus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link GlobusIdentityService}, exercising URL construction
 * (including {@code provision=false}) and response parsing via the overridable
 * {@link GlobusIdentityService#get} seam (no network). The HTTP-error-to-
 * {@link GlobusAuthException} wrapping lives inside {@code get} itself, so it is
 * not reachable through the seam and is not unit-tested here.
 */
class GlobusIdentityServiceTest {

    private static final GlobusCredentials CREDS = new GlobusCredentials("client-id", "secret");

    /** Test double capturing the URL and returning a canned body (or throwing). */
    private static final class Stub extends GlobusIdentityService {
        private String result = "{\"identities\":[]}";
        private RuntimeException error;
        private String lastUrl;

        Stub() {
            super(new ObjectMapper());
        }

        @Override
        protected String get(final String url, final GlobusCredentials credentials) {
            lastUrl = url;
            if (error != null) {
                throw error;
            }
            return result;
        }
    }

    @Test
    void resolveByUsernameBuildsUrlWithProvisionFalseAndParsesIdentity() {
        final Stub svc = new Stub();
        svc.result = "{\"identities\":[{\"id\":\"uuid-a\",\"username\":\"alice@inst.edu\",\"status\":\"used\"}]}";

        final Optional<GlobusIdentityService.GlobusIdentity> id = svc.resolveByUsername(CREDS, "alice@inst.edu");

        assertTrue(id.isPresent());
        assertEquals("uuid-a", id.get().id());
        assertEquals("alice@inst.edu", id.get().username());
        assertTrue(svc.lastUrl.contains("usernames=alice@inst.edu")
                || svc.lastUrl.contains("usernames=alice%40inst.edu"), svc.lastUrl);
        assertTrue(svc.lastUrl.contains("provision=false"),
                "username lookups must pass provision=false: " + svc.lastUrl);
    }

    @Test
    void resolveByUsernameEmptyWhenNoIdentity() {
        final Stub svc = new Stub();
        svc.result = "{\"identities\":[]}";
        assertFalse(svc.resolveByUsername(CREDS, "nobody@inst.edu").isPresent());
    }

    @Test
    void resolveByIdBuildsIdsUrlAndParses() {
        final Stub svc = new Stub();
        svc.result = "{\"identities\":[{\"id\":\"uuid-b\",\"username\":\"bob@inst.edu\"}]}";

        final Optional<GlobusIdentityService.GlobusIdentity> id = svc.resolveById(CREDS, "uuid-b");

        assertTrue(id.isPresent());
        assertEquals("bob@inst.edu", id.get().username());
        assertTrue(svc.lastUrl.contains("ids=uuid-b"), svc.lastUrl);
        assertFalse(svc.lastUrl.contains("provision"), "id lookups do not provision: " + svc.lastUrl);
    }

    @Test
    void firstIdentityWithNonBlankIdIsChosen() {
        final Stub svc = new Stub();
        svc.result = "{\"identities\":[{\"username\":\"no-id@inst.edu\"},"
                + "{\"id\":\"uuid-c\",\"username\":\"carol@inst.edu\"}]}";
        final Optional<GlobusIdentityService.GlobusIdentity> id = svc.resolveByUsername(CREDS, "x");
        assertTrue(id.isPresent());
        assertEquals("uuid-c", id.get().id());
    }
}
