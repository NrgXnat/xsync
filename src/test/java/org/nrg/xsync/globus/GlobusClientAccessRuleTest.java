package org.nrg.xsync.globus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.nrg.xsync.globus.GlobusClient.AccessRule;
import org.nrg.xsync.globus.GlobusClient.Permission;
import org.nrg.xsync.globus.GlobusClient.PrincipalType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

/**
 * Unit tests for the Globus guest-collection ACL operations on
 * {@link GlobusClient} ({@code listAccessRules}, {@code createAccessRule},
 * {@code deleteAccessRule}) and the strongly-typed {@link AccessRule} value,
 * exercising URL/body construction and parsing via the overridable HTTP seams
 * (no network).
 */
class GlobusClientAccessRuleTest {

    private static final UUID ALICE = UUID.fromString("11111111-1111-1111-1111-111111111111");

    /** Test double for the GET/POST/DELETE seams used by the ACL methods. */
    private static final class AclStub extends GlobusClient {
        private String getResult = "{}";
        private String postResult = "{}";
        private String deleteResult = "{}";
        private RuntimeException getError;
        private String lastGetUrl;
        private String lastPostUrl;
        private String lastPostBody;
        private String lastDeleteUrl;

        AclStub() {
            super(new ObjectMapper());
        }

        @Override
        protected String getForBody(final String url, final String accessToken) {
            lastGetUrl = url;
            if (getError != null) {
                throw getError;
            }
            return getResult;
        }

        @Override
        protected String postForBody(final String url, final String accessToken, final String jsonBody) {
            lastPostUrl = url;
            lastPostBody = jsonBody;
            return postResult;
        }

        @Override
        protected String deleteForBody(final String url, final String accessToken) {
            lastDeleteUrl = url;
            return deleteResult;
        }
    }

    // --- AccessRule value type --------------------------------------------

    @Test
    void principalTypeFromWireMapsKnownAndRejectsUnknown() {
        assertEquals(PrincipalType.IDENTITY, PrincipalType.fromWire("identity"));
        assertEquals(PrincipalType.ANONYMOUS, PrincipalType.fromWire("anonymous"));
        assertThrows(IllegalArgumentException.class, () -> PrincipalType.fromWire("some_future_type"));
        assertThrows(IllegalArgumentException.class, () -> PrincipalType.fromWire(null));
    }

    @Test
    void permissionFromWireMapsKnownAndRejectsUnknown() {
        assertEquals(Permission.READ, Permission.fromWire("r"));
        assertEquals(Permission.READ_WRITE, Permission.fromWire("rw"));
        assertThrows(IllegalArgumentException.class, () -> Permission.fromWire("x"));
        assertThrows(IllegalArgumentException.class, () -> Permission.fromWire(null));
    }

    @Test
    void accessRuleEnforcesNonNullInvariants() {
        assertThrows(NullPointerException.class,
                () -> new AccessRule("1", null, ALICE.toString(), "/p/u/", Permission.READ_WRITE));
        assertThrows(NullPointerException.class,
                () -> new AccessRule("1", PrincipalType.IDENTITY, ALICE.toString(), "/p/u/", null));
    }

    @Test
    void identityIdIsPresentOnlyForIdentityRulesWithAUuid() {
        assertEquals(ALICE, new AccessRule("1", PrincipalType.IDENTITY, ALICE.toString(), "/p/u/",
                Permission.READ_WRITE).identityId().orElseThrow());
        assertTrue(new AccessRule("2", PrincipalType.ANONYMOUS, null, "/p/", Permission.READ)
                .identityId().isEmpty(), "anonymous rules have no identity UUID");
        assertTrue(new AccessRule("3", PrincipalType.IDENTITY, "not-a-uuid", "/p/u/", Permission.READ_WRITE)
                .identityId().isEmpty(), "a non-UUID principal yields no identity UUID");
    }

    // --- listAccessRules ---------------------------------------------------

    @Test
    void listAccessRulesParsesRulesAndBuildsUrl() {
        final AclStub client = new AclStub();
        client.getResult = "{\"DATA_TYPE\":\"access_list\",\"DATA\":["
                + "{\"DATA_TYPE\":\"access\",\"id\":\"1\",\"principal_type\":\"identity\","
                + "\"principal\":\"" + ALICE + "\",\"path\":\"/PRJ/alice/\",\"permissions\":\"rw\"},"
                + "{\"DATA_TYPE\":\"access\",\"id\":\"2\",\"principal_type\":\"anonymous\","
                + "\"principal\":\"\",\"path\":\"/PRJ/\",\"permissions\":\"r\"}]}";

        final List<AccessRule> rules = client.listAccessRules("token", "coll-1");

        assertEquals(2, rules.size());
        assertEquals(PrincipalType.IDENTITY, rules.get(0).principalType());
        assertEquals(ALICE.toString(), rules.get(0).principal());
        assertEquals("/PRJ/alice/", rules.get(0).path());
        assertEquals(Permission.READ_WRITE, rules.get(0).permissions());
        assertEquals(PrincipalType.ANONYMOUS, rules.get(1).principalType());
        assertEquals(Permission.READ, rules.get(1).permissions());
        assertTrue(client.lastGetUrl.endsWith("/endpoint/coll-1/access_list"), client.lastGetUrl);
    }

    @Test
    void listAccessRulesWrapsUnrecognizedPermission() {
        final AclStub client = new AclStub();
        client.getResult = "{\"DATA\":[{\"principal_type\":\"identity\",\"principal\":\"" + ALICE
                + "\",\"path\":\"/p/u/\",\"permissions\":\"wx\"}]}";
        assertThrows(GlobusTransferException.class, () -> client.listAccessRules("token", "coll-1"));
    }

    @Test
    void listAccessRulesWrapsUnrecognizedPrincipalType() {
        final AclStub client = new AclStub();
        client.getResult = "{\"DATA\":[{\"principal_type\":\"future_type\",\"principal\":\"x\","
                + "\"path\":\"/p/\",\"permissions\":\"rw\"}]}";
        assertThrows(GlobusTransferException.class, () -> client.listAccessRules("token", "coll-1"));
    }

    @Test
    void listAccessRulesSurfacesErrorBody() {
        final AclStub client = new AclStub();
        client.getError = HttpClientErrorException.create(HttpStatus.FORBIDDEN, "Forbidden",
                HttpHeaders.EMPTY, "{\"code\":\"PermissionDenied\"}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);
        final GlobusTransferException ex =
                assertThrows(GlobusTransferException.class, () -> client.listAccessRules("token", "coll-1"));
        assertTrue(ex.getMessage().contains("PermissionDenied"), ex.getMessage());
    }

    // --- createAccessRule / deleteAccessRule -------------------------------

    @Test
    void createAccessRuleBuildsDocumentAndReturnsId() throws Exception {
        final AclStub client = new AclStub();
        client.postResult = "{\"DATA_TYPE\":\"access_create_result\",\"code\":\"Created\",\"access_id\":\"42\"}";

        final String id = client.createAccessRule("token", "coll-1", ALICE, "/PRJ/alice/", Permission.READ_WRITE);

        assertEquals("42", id);
        assertTrue(client.lastPostUrl.endsWith("/endpoint/coll-1/access"), client.lastPostUrl);
        final JsonNode doc = new ObjectMapper().readTree(client.lastPostBody);
        assertEquals("access", doc.get("DATA_TYPE").asText());
        assertEquals("identity", doc.get("principal_type").asText());
        assertEquals(ALICE.toString(), doc.get("principal").asText());
        assertEquals("/PRJ/alice/", doc.get("path").asText());
        assertEquals("rw", doc.get("permissions").asText());
    }

    @Test
    void createAccessRuleMissingIdThrows() {
        final AclStub client = new AclStub();
        client.postResult = "{\"DATA_TYPE\":\"access_create_result\",\"code\":\"Created\"}";
        assertThrows(GlobusTransferException.class,
                () -> client.createAccessRule("token", "coll-1", ALICE, "/PRJ/alice/", Permission.READ_WRITE));
    }

    @Test
    void deleteAccessRuleBuildsUrl() {
        final AclStub client = new AclStub();
        client.deleteResult = "{\"DATA_TYPE\":\"result\",\"code\":\"Deleted\"}";
        client.deleteAccessRule("token", "coll-1", "42");
        assertTrue(client.lastDeleteUrl.endsWith("/endpoint/coll-1/access/42"), client.lastDeleteUrl);
    }
}
