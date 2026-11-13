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
import org.nrg.xsync.globus.entities.GlobusGrantState;
import org.nrg.xsync.globus.entities.GlobusNodeConfig;
import org.nrg.xsync.globus.entities.GlobusSyncPeerGrant;
import org.nrg.xsync.globus.services.GlobusNodeConfigService;
import org.nrg.xsync.globus.services.GlobusSyncPeerGrantService;
import org.nrg.xsync.globus.sync.GlobusSyncPeerGrantManager;
import org.nrg.xsync.pojo.GlobusSyncPeerGrantPojo;

/**
 * Unit tests for {@link GlobusSyncPeerGrantManager}, using hand-rolled fakes for
 * its collaborators (the project has no mocking framework). Cover add / remove /
 * list-reconcile and the error cases. No subclass is needed — the manager has no
 * XNAT-security or filesystem seams, only injectable Globus collaborators.
 */
class GlobusSyncPeerGrantManagerTest {

    private static final String SYNC_COLL = "sync-inbox-coll";
    private static final String SUFFIX = "@clients.auth.globus.org";
    private static final String PEER_A = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String PEER_B = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
    private static final String PEER_X = "cccccccc-cccc-cccc-cccc-cccccccccccc";

    // --- fakes -------------------------------------------------------------

    private static final class FakeGrants implements GlobusSyncPeerGrantService {
        private final List<GlobusSyncPeerGrant> store = new ArrayList<>();

        @Override
        public List<GlobusSyncPeerGrant> getAll() {
            return new ArrayList<>(store);
        }

        @Override
        public Optional<GlobusSyncPeerGrant> getByPeerIdentityId(final String peerIdentityId) {
            return store.stream().filter(g -> peerIdentityId.equals(g.getPeerIdentityId())).findFirst();
        }

        @Override
        public GlobusSyncPeerGrant save(final GlobusSyncPeerGrant grant) {
            store.add(grant);
            return grant;
        }

        @Override
        public void delete(final GlobusSyncPeerGrant grant) {
            store.removeIf(g -> g.getPeerIdentityId().equals(grant.getPeerIdentityId()));
        }
    }

    private static final class FakeNodeConfig implements GlobusNodeConfigService {
        private final String inboxCollectionId;

        FakeNodeConfig(final String inboxCollectionId) {
            this.inboxCollectionId = inboxCollectionId;
        }

        @Override
        public Optional<GlobusNodeConfig> getConfig() {
            return Optional.of(new GlobusNodeConfig("cid", "sec", "outbox-coll", "/outbox",
                    inboxCollectionId, "upload-coll"));
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

        FakeIdentity(final Map<String, GlobusIdentity> byUsername) {
            super(new ObjectMapper());
            this.byUsername = byUsername;
        }

        @Override
        public Optional<GlobusIdentity> resolveByUsername(final GlobusCredentials c, final String username) {
            return Optional.ofNullable(byUsername.get(username));
        }
    }

    private static FakeIdentity identityFor(final String... clientIds) {
        final Map<String, GlobusIdentity> map = new java.util.HashMap<>();
        for (final String id : clientIds) {
            map.put(id + SUFFIX, new GlobusIdentity(id, id + SUFFIX));
        }
        return new FakeIdentity(map);
    }

    private static GlobusSyncPeerGrant grant(final String label, final String identityId, final String aclId) {
        final GlobusSyncPeerGrant g = new GlobusSyncPeerGrant();
        g.setPeerLabel(label);
        g.setPeerIdentityId(identityId);
        g.setSubpath("/" + label + "/");
        g.setGlobusAclId(aclId);
        g.setState(GlobusGrantState.ACTIVE);
        return g;
    }

    // --- add ---------------------------------------------------------------

    @Test
    void addValidatesClientCreatesAclAndPersistsActive() throws Exception {
        final FakeGrants grants = new FakeGrants();
        final FakeClient client = new FakeClient();
        final GlobusSyncPeerGrantManager mgr = new GlobusSyncPeerGrantManager(
                grants, client, new FakeAuth(), identityFor(PEER_A), new FakeNodeConfig(SYNC_COLL));

        final GlobusSyncPeerGrantPojo pojo = mgr.add("site1", PEER_A);

        assertEquals("site1", pojo.getPeerLabel());
        assertEquals(PEER_A, pojo.getPeerClientId());
        assertEquals(GlobusGrantState.ACTIVE.name(), pojo.getState());
        assertEquals("acl-1", pojo.getGlobusAclId());
        assertEquals("/site1/", client.lastCreatePath, "ACL is on the peer's subpath");
        assertEquals(PEER_A, client.lastCreatePrincipal);
        assertEquals(1, grants.getAll().size());
    }

    @Test
    void addRejectsNonUuidClientId() {
        final GlobusSyncPeerGrantManager mgr = new GlobusSyncPeerGrantManager(
                new FakeGrants(), new FakeClient(), new FakeAuth(), identityFor(), new FakeNodeConfig(SYNC_COLL));
        assertThrows(DataFormatException.class, () -> mgr.add("site1", "not-a-uuid"));
    }

    @Test
    void addRejectsUnresolvableClientIdentity() {
        final GlobusSyncPeerGrantManager mgr = new GlobusSyncPeerGrantManager(
                new FakeGrants(), new FakeClient(), new FakeAuth(), identityFor(/* none */), new FakeNodeConfig(SYNC_COLL));
        assertThrows(DataFormatException.class, () -> mgr.add("site1", PEER_A));
    }

    @Test
    void addRejectsDuplicatePeer() {
        final FakeGrants grants = new FakeGrants();
        grants.save(grant("site1", PEER_A, "acl-0"));
        final GlobusSyncPeerGrantManager mgr = new GlobusSyncPeerGrantManager(
                grants, new FakeClient(), new FakeAuth(), identityFor(PEER_A), new FakeNodeConfig(SYNC_COLL));
        assertThrows(DataFormatException.class, () -> mgr.add("site1", PEER_A));
    }

    @Test
    void addRejectsBlankInput() {
        final GlobusSyncPeerGrantManager mgr = new GlobusSyncPeerGrantManager(
                new FakeGrants(), new FakeClient(), new FakeAuth(), identityFor(), new FakeNodeConfig(SYNC_COLL));
        assertThrows(DataFormatException.class, () -> mgr.add("  ", PEER_A));
    }

    @Test
    void addFailsWhenNoSyncInboxConfigured() {
        final GlobusSyncPeerGrantManager mgr = new GlobusSyncPeerGrantManager(
                new FakeGrants(), new FakeClient(), new FakeAuth(), identityFor(PEER_A), new FakeNodeConfig("  "));
        assertThrows(DataFormatException.class, () -> mgr.add("site1", PEER_A));
    }

    // --- remove ------------------------------------------------------------

    @Test
    void removeDeletesAclAndRecord() throws Exception {
        final FakeGrants grants = new FakeGrants();
        grants.save(grant("site1", PEER_A, "acl-7"));
        final FakeClient client = new FakeClient();
        final GlobusSyncPeerGrantManager mgr = new GlobusSyncPeerGrantManager(
                grants, client, new FakeAuth(), identityFor(), new FakeNodeConfig(SYNC_COLL));

        mgr.remove(PEER_A);

        assertEquals(List.of("acl-7"), client.deleted);
        assertTrue(grants.getAll().isEmpty());
    }

    @Test
    void removeMissingGrantThrowsNotFound() {
        final GlobusSyncPeerGrantManager mgr = new GlobusSyncPeerGrantManager(
                new FakeGrants(), new FakeClient(), new FakeAuth(), identityFor(), new FakeNodeConfig(SYNC_COLL));
        assertThrows(NotFoundException.class, () -> mgr.remove(PEER_X));
    }

    // --- list / reconcile --------------------------------------------------

    @Test
    void listReconcilesActivePendingAndOrphan() {
        final FakeGrants grants = new FakeGrants();
        grants.save(grant("site1", PEER_A, "acl-a")); // matching ACL -> ACTIVE
        grants.save(grant("site2", PEER_B, "acl-b")); // no matching ACL -> PENDING

        final FakeClient client = new FakeClient();
        client.rules = List.of(
                new AccessRule("acl-a", PrincipalType.IDENTITY, PEER_A, "/site1/", Permission.READ_WRITE),
                new AccessRule("acl-x", PrincipalType.IDENTITY, PEER_X, "/sitex/", Permission.READ_WRITE));
        final GlobusSyncPeerGrantManager mgr = new GlobusSyncPeerGrantManager(
                grants, client, new FakeAuth(), identityFor(), new FakeNodeConfig(SYNC_COLL));

        final List<GlobusSyncPeerGrantPojo> list = mgr.list();

        assertEquals(3, list.size());
        assertEquals(GlobusGrantState.ACTIVE.name(), stateOf(list, PEER_A));
        assertEquals(GlobusGrantState.PENDING.name(), stateOf(list, PEER_B));
        assertEquals(GlobusGrantState.ORPHANED.name(), stateOf(list, PEER_X));
        assertEquals("sitex", find(list, PEER_X).getPeerLabel(), "orphan label derived from the ACL path");
    }

    @Test
    void recordIsPendingWhenTheIdentitysAclIsForAnotherSubpath() {
        final FakeGrants grants = new FakeGrants();
        grants.save(grant("site1", PEER_A, "acl-a")); // expects /site1/
        final FakeClient client = new FakeClient();
        // Same identity, but the only ACL is on a different label's subpath.
        client.rules = List.of(
                new AccessRule("acl-other", PrincipalType.IDENTITY, PEER_A, "/elsewhere/", Permission.READ_WRITE));
        final GlobusSyncPeerGrantManager mgr = new GlobusSyncPeerGrantManager(
                grants, client, new FakeAuth(), identityFor(), new FakeNodeConfig(SYNC_COLL));

        final List<GlobusSyncPeerGrantPojo> list = mgr.list();

        assertEquals(GlobusGrantState.PENDING.name(), stateOf(list, PEER_A),
                "an ACL on a different subpath must not mark the record ACTIVE");
    }

    @Test
    void listFallsBackToStoredRecordsWhenGlobusUnreachable() {
        final FakeGrants grants = new FakeGrants();
        grants.save(grant("site1", PEER_A, "acl-a"));
        final FakeClient client = new FakeClient();
        client.listError = new GlobusTransferException("boom");
        final GlobusSyncPeerGrantManager mgr = new GlobusSyncPeerGrantManager(
                grants, client, new FakeAuth(), identityFor(), new FakeNodeConfig(SYNC_COLL));

        final List<GlobusSyncPeerGrantPojo> list = mgr.list();

        assertEquals(1, list.size());
        assertEquals(GlobusGrantState.ACTIVE.name(), list.get(0).getState(), "stored state is returned");
        assertFalse(client.deleted.contains("acl-a"));
    }

    private static String stateOf(final List<GlobusSyncPeerGrantPojo> list, final String clientId) {
        return find(list, clientId).getState();
    }

    private static GlobusSyncPeerGrantPojo find(final List<GlobusSyncPeerGrantPojo> list, final String clientId) {
        return list.stream().filter(p -> clientId.equals(p.getPeerClientId())).findFirst().orElseThrow();
    }
}
