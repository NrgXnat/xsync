package org.nrg.xsync.globus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.nrg.xapi.exceptions.DataFormatException;
import org.nrg.xapi.exceptions.NotFoundException;
import org.nrg.xsync.globus.GlobusClient.AccessRule;
import org.nrg.xsync.globus.GlobusClient.Permission;
import org.nrg.xsync.globus.GlobusClient.PrincipalType;
import org.nrg.xsync.globus.GlobusIdentityService.GlobusIdentity;
import org.nrg.xsync.globus.entities.GlobusNodeConfig;
import org.nrg.xsync.globus.entities.GlobusUploadGrant;
import org.nrg.xsync.globus.entities.GlobusUploadGrantState;
import org.nrg.xsync.globus.services.GlobusNodeConfigService;
import org.nrg.xsync.globus.services.GlobusUploadGrantService;
import org.nrg.xsync.globus.upload.GlobusUploadPermissionManager;
import org.nrg.xsync.pojo.GlobusUploadGrantPojo;

/**
 * Unit tests for {@link GlobusUploadPermissionManager}, using hand-rolled fakes
 * for its collaborators (the project has no mocking framework). Cover the add /
 * remove / list-reconcile behaviors and the error cases.
 */
class GlobusUploadPermissionManagerTest {

    private static final String PROJECT = "PRJ";
    private static final String UPLOAD_COLL = "upload-coll";
    private static final String UUID_A = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String UUID_B = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
    private static final String UUID_X = "cccccccc-cccc-cccc-cccc-cccccccccccc";

    // --- fakes -------------------------------------------------------------

    private static final class FakeGrants implements GlobusUploadGrantService {
        private final List<GlobusUploadGrant> store = new ArrayList<>();

        @Override
        public List<GlobusUploadGrant> getByProject(final String projectId) {
            return store.stream().filter(g -> projectId.equals(g.getProjectId())).toList();
        }

        @Override
        public Optional<GlobusUploadGrant> getByProjectAndIdentity(final String projectId, final String identityId) {
            return store.stream()
                    .filter(g -> projectId.equals(g.getProjectId()) && identityId.equals(g.getGlobusIdentityId()))
                    .findFirst();
        }

        @Override
        public GlobusUploadGrant save(final GlobusUploadGrant grant) {
            store.add(grant);
            return grant;
        }

        @Override
        public void delete(final GlobusUploadGrant grant) {
            store.removeIf(g -> g.getProjectId().equals(grant.getProjectId())
                    && g.getGlobusIdentityId().equals(grant.getGlobusIdentityId()));
        }
    }

    private static final class FakeNodeConfig implements GlobusNodeConfigService {
        private final String uploadCollectionId;

        FakeNodeConfig(final String uploadCollectionId) {
            this.uploadCollectionId = uploadCollectionId;
        }

        @Override
        public Optional<GlobusNodeConfig> getConfig() {
            return Optional.of(new GlobusNodeConfig("cid", "sec", "outbox-coll", "/outbox",
                    "inbox-coll", uploadCollectionId));
        }

        @Override
        public GlobusNodeConfig save(final GlobusNodeConfig config) {
            return config;
        }

        @Override
        public GlobusCredentials getServiceCredentials() {
            return new GlobusCredentials("cid", "sec");
        }
    }

    private static final class FakeAuth extends GlobusAuthService {
        FakeAuth() {
            super("http://unused", new ObjectMapper());
        }

        @Override
        public String getTransferToken(final GlobusCredentials credentials) {
            return "tok";
        }
    }

    private static final class FakeClient extends GlobusClient {
        private List<AccessRule> rules = new ArrayList<>();
        private RuntimeException listError;
        private int created;
        private String lastCreatePath;
        private String lastCreatePrincipal;
        private final List<String> deleted = new ArrayList<>();

        FakeClient() {
            super(new ObjectMapper());
        }

        @Override
        public List<AccessRule> listAccessRules(final String token, final String collectionId) {
            if (listError != null) {
                throw listError;
            }
            return rules;
        }

        @Override
        public String createAccessRule(final String token, final String collectionId, final UUID identityId,
                                       final String path, final Permission permissions) {
            lastCreatePath = path;
            lastCreatePrincipal = identityId.toString();
            return "acl-" + (++created);
        }

        @Override
        public void deleteAccessRule(final String token, final String collectionId, final String ruleId) {
            deleted.add(ruleId);
        }
    }

    private static final class FakeIdentity extends GlobusIdentityService {
        private final Map<String, GlobusIdentity> byUsername;
        private final Map<String, GlobusIdentity> byId;

        FakeIdentity(final Map<String, GlobusIdentity> byUsername, final Map<String, GlobusIdentity> byId) {
            super(new ObjectMapper());
            this.byUsername = byUsername;
            this.byId = byId;
        }

        @Override
        public Optional<GlobusIdentity> resolveByUsername(final GlobusCredentials c, final String username) {
            return Optional.ofNullable(byUsername.get(username));
        }

        @Override
        public Optional<GlobusIdentity> resolveById(final GlobusCredentials c, final String id) {
            return Optional.ofNullable(byId.get(id));
        }
    }

    private static GlobusUploadGrant grant(final String identityId, final String xnatUser, final String aclId) {
        final GlobusUploadGrant g = new GlobusUploadGrant();
        g.setProjectId(PROJECT);
        g.setGlobusIdentityId(identityId);
        g.setXnatUsername(xnatUser);
        g.setGlobusAclId(aclId);
        g.setState(GlobusUploadGrantState.ACTIVE);
        return g;
    }

    // --- add ---------------------------------------------------------------

    @Test
    void addResolvesIdentityCreatesAclAndPersistsActive() throws Exception {
        final FakeGrants grants = new FakeGrants();
        final FakeClient client = new FakeClient();
        final FakeIdentity identity = new FakeIdentity(
                Map.of("alice@inst.edu", new GlobusIdentity(UUID_A, "alice@inst.edu")), Map.of());
        final GlobusUploadPermissionManager mgr = new GlobusUploadPermissionManager(
                grants, client, new FakeAuth(), identity, new FakeNodeConfig(UPLOAD_COLL));

        final GlobusUploadGrantPojo pojo = mgr.add(PROJECT, "alice@inst.edu", "alice");

        assertEquals(UUID_A, pojo.getGlobusIdentityId());
        assertEquals("alice", pojo.getXnatUsername());
        assertEquals(GlobusUploadGrantState.ACTIVE.name(), pojo.getState());
        assertEquals("acl-1", pojo.getGlobusAclId());
        assertEquals("/PRJ/alice/", client.lastCreatePath, "ACL must be on the per-user subpath");
        assertEquals(UUID_A, client.lastCreatePrincipal);
        assertEquals(1, grants.getByProject(PROJECT).size());
    }

    @Test
    void addRejectsUnresolvableIdentity() {
        final GlobusUploadPermissionManager mgr = new GlobusUploadPermissionManager(
                new FakeGrants(), new FakeClient(), new FakeAuth(),
                new FakeIdentity(Map.of(), Map.of()), new FakeNodeConfig(UPLOAD_COLL));
        assertThrows(DataFormatException.class, () -> mgr.add(PROJECT, "ghost@inst.edu", "ghost"));
    }

    @Test
    void addRejectsNonUuidIdentityId() {
        final FakeIdentity identity = new FakeIdentity(
                Map.of("alice@inst.edu", new GlobusIdentity("not-a-uuid", "alice@inst.edu")), Map.of());
        final GlobusUploadPermissionManager mgr = new GlobusUploadPermissionManager(
                new FakeGrants(), new FakeClient(), new FakeAuth(), identity, new FakeNodeConfig(UPLOAD_COLL));
        assertThrows(DataFormatException.class, () -> mgr.add(PROJECT, "alice@inst.edu", "alice"));
    }

    @Test
    void addRejectsDuplicateGrant() {
        final FakeGrants grants = new FakeGrants();
        grants.save(grant(UUID_A, "alice", "acl-0"));
        final GlobusUploadPermissionManager mgr = new GlobusUploadPermissionManager(
                grants, new FakeClient(), new FakeAuth(),
                new FakeIdentity(Map.of("alice@inst.edu", new GlobusIdentity(UUID_A, "alice@inst.edu")), Map.of()),
                new FakeNodeConfig(UPLOAD_COLL));
        assertThrows(DataFormatException.class, () -> mgr.add(PROJECT, "alice@inst.edu", "alice"));
    }

    @Test
    void addRejectsBlankInput() {
        final GlobusUploadPermissionManager mgr = new GlobusUploadPermissionManager(
                new FakeGrants(), new FakeClient(), new FakeAuth(),
                new FakeIdentity(Map.of(), Map.of()), new FakeNodeConfig(UPLOAD_COLL));
        assertThrows(DataFormatException.class, () -> mgr.add(PROJECT, "  ", "alice"));
    }

    @Test
    void addFailsWhenNoUploadCollectionConfigured() {
        final GlobusUploadPermissionManager mgr = new GlobusUploadPermissionManager(
                new FakeGrants(), new FakeClient(), new FakeAuth(),
                new FakeIdentity(Map.of("alice@inst.edu", new GlobusIdentity(UUID_A, "alice@inst.edu")), Map.of()),
                new FakeNodeConfig("  "));
        assertThrows(DataFormatException.class, () -> mgr.add(PROJECT, "alice@inst.edu", "alice"));
    }

    // --- remove ------------------------------------------------------------

    @Test
    void removeDeletesAclAndRecord() throws Exception {
        final FakeGrants grants = new FakeGrants();
        grants.save(grant(UUID_A, "alice", "acl-7"));
        final FakeClient client = new FakeClient();
        final GlobusUploadPermissionManager mgr = new GlobusUploadPermissionManager(
                grants, client, new FakeAuth(), new FakeIdentity(Map.of(), Map.of()),
                new FakeNodeConfig(UPLOAD_COLL));

        mgr.remove(PROJECT, UUID_A);

        assertEquals(List.of("acl-7"), client.deleted);
        assertTrue(grants.getByProject(PROJECT).isEmpty());
    }

    @Test
    void removeMissingGrantThrowsNotFound() {
        final GlobusUploadPermissionManager mgr = new GlobusUploadPermissionManager(
                new FakeGrants(), new FakeClient(), new FakeAuth(), new FakeIdentity(Map.of(), Map.of()),
                new FakeNodeConfig(UPLOAD_COLL));
        assertThrows(NotFoundException.class, () -> mgr.remove(PROJECT, UUID_X));
    }

    // --- list / reconcile --------------------------------------------------

    @Test
    void listReconcilesActivePendingAndOrphan() {
        final FakeGrants grants = new FakeGrants();
        grants.save(grant(UUID_A, "alice", "acl-a")); // has a matching ACL -> ACTIVE
        grants.save(grant(UUID_B, "bob", "acl-b"));    // no matching ACL   -> PENDING

        final FakeClient client = new FakeClient();
        client.rules = List.of(
                new AccessRule("acl-a", PrincipalType.IDENTITY, UUID_A, "/PRJ/alice/", Permission.READ_WRITE),
                new AccessRule("acl-x", PrincipalType.IDENTITY, UUID_X, "/PRJ/carol/", Permission.READ_WRITE));
        final FakeIdentity identity = new FakeIdentity(Map.of(),
                Map.of(UUID_X, new GlobusIdentity(UUID_X, "carol@inst.edu")));

        final GlobusUploadPermissionManager mgr = new GlobusUploadPermissionManager(
                grants, client, new FakeAuth(), identity, new FakeNodeConfig(UPLOAD_COLL));

        final List<GlobusUploadGrantPojo> list = mgr.list(PROJECT);

        assertEquals(3, list.size());
        assertEquals(GlobusUploadGrantState.ACTIVE.name(), stateOf(list, UUID_A));
        assertEquals(GlobusUploadGrantState.PENDING.name(), stateOf(list, UUID_B));
        assertEquals(GlobusUploadGrantState.ORPHANED.name(), stateOf(list, UUID_X));
        final GlobusUploadGrantPojo orphan = find(list, UUID_X);
        assertEquals("carol", orphan.getXnatUsername(), "orphan xnat user is derived from the ACL path");
        assertEquals("carol@inst.edu", orphan.getGlobusUsername(), "orphan username resolved from the UUID");
    }

    @Test
    void listFallsBackToStoredRecordsWhenGlobusUnreachable() {
        final FakeGrants grants = new FakeGrants();
        grants.save(grant(UUID_A, "alice", "acl-a"));
        final FakeClient client = new FakeClient();
        client.listError = new GlobusTransferException("boom");

        final GlobusUploadPermissionManager mgr = new GlobusUploadPermissionManager(
                grants, client, new FakeAuth(), new FakeIdentity(Map.of(), Map.of()),
                new FakeNodeConfig(UPLOAD_COLL));

        final List<GlobusUploadGrantPojo> list = mgr.list(PROJECT);

        assertEquals(1, list.size());
        assertEquals(GlobusUploadGrantState.ACTIVE.name(), list.get(0).getState(), "stored state is returned");
        assertFalse(client.deleted.contains("acl-a"));
    }

    private static String stateOf(final List<GlobusUploadGrantPojo> list, final String identityId) {
        return find(list, identityId).getState();
    }

    private static GlobusUploadGrantPojo find(final List<GlobusUploadGrantPojo> list, final String identityId) {
        return list.stream().filter(p -> identityId.equals(p.getGlobusIdentityId())).findFirst().orElseThrow();
    }
}
