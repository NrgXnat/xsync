package org.nrg.xsync.globus.sync;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.nrg.xapi.exceptions.DataFormatException;
import org.nrg.xapi.exceptions.NotFoundException;
import org.nrg.xsync.globus.GlobusAuthService;
import org.nrg.xsync.globus.GlobusClient;
import org.nrg.xsync.globus.GlobusClient.AccessRule;
import org.nrg.xsync.globus.GlobusClient.Permission;
import org.nrg.xsync.globus.GlobusClient.PrincipalType;
import org.nrg.xsync.globus.GlobusCredentials;
import org.nrg.xsync.globus.GlobusIdentityService;
import org.nrg.xsync.globus.entities.GlobusGrantState;
import org.nrg.xsync.globus.entities.GlobusNodeConfig;
import org.nrg.xsync.globus.entities.GlobusSyncPeerGrant;
import org.nrg.xsync.globus.services.GlobusNodeConfigService;
import org.nrg.xsync.globus.services.GlobusSyncPeerGrantService;
import org.nrg.xsync.pojo.GlobusSyncPeerGrantPojo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Orchestrates site-level management of the peer ACLs on this node's <em>sync</em>
 * inbox: it ties the XNAT-owned {@link GlobusSyncPeerGrant} records to the Globus
 * ACLs on the sync inbox collection ({@link GlobusNodeConfig#getInboxCollectionId}).
 * The per-peer counterpart to
 * {@link org.nrg.xsync.globus.upload.GlobusUploadPermissionManager}.
 *
 * <p>All Globus ACL calls run under the node service account's Transfer token,
 * which must hold the {@code access_manager} role on the sync inbox collection;
 * authorization over <em>who</em> may invoke these operations is enforced by the
 * controller's XNAT site-admin role check.</p>
 *
 * <p>Sync ingest is unaffected: the delivered XAR is imported by a server-side
 * import-by-path that the source triggers under its alias token, so these ACLs
 * only govern <em>write</em> access to the inbox — there is no receive-side
 * monitor or per-import check here (contrast the upload feature).</p>
 *
 * @author XSync
 */
@Slf4j
@Component
public class GlobusSyncPeerGrantManager {

    /** Username suffix of a Globus confidential client's identity. */
    private static final String CLIENT_IDENTITY_SUFFIX = "@clients.auth.globus.org";

    private final GlobusSyncPeerGrantService _grants;
    private final GlobusClient _client;
    private final GlobusAuthService _authService;
    private final GlobusIdentityService _identityService;
    private final GlobusNodeConfigService _nodeConfigService;

    @Autowired
    public GlobusSyncPeerGrantManager(final GlobusSyncPeerGrantService grants, final GlobusClient client,
                                      final GlobusAuthService authService,
                                      final GlobusIdentityService identityService,
                                      final GlobusNodeConfigService nodeConfigService) {
        _grants = grants;
        _client = client;
        _authService = authService;
        _identityService = identityService;
        _nodeConfigService = nodeConfigService;
    }

    /** Resolved Globus context for sync-inbox ACL operations. */
    private record Context(String token, String collectionId, GlobusCredentials credentials) {
    }

    /**
     * List the peer grants on this node's sync inbox, reconciled against the live
     * Globus ACLs when reachable (ACTIVE / PENDING, plus ORPHANED for ACLs with no
     * record). Falls back to stored records with their last-known state when
     * Globus cannot be reached or is not configured.
     *
     * @return the reconciled grants for display
     */
    public List<GlobusSyncPeerGrantPojo> list() {
        final List<GlobusSyncPeerGrant> records = _grants.getAll();
        try {
            return reconcile(records);
        } catch (Exception e) {
            log.warn("Could not reconcile Globus sync-inbox grants against the live ACLs "
                    + "(returning stored records): {}", e.getMessage());
            final List<GlobusSyncPeerGrantPojo> pojos = new ArrayList<>();
            for (final GlobusSyncPeerGrant record : records) {
                pojos.add(toPojo(record, record.getState()));
            }
            return pojos;
        }
    }

    /**
     * Grant a peer client write access to this node's sync inbox: validate the
     * client id resolves to a Globus client identity, create the collection ACL on
     * the peer's subpath, and persist the binding.
     *
     * @param peerLabel    the peer's name / subpath segment (e.g. {@code site1})
     * @param peerClientId the peer confidential client's id (a UUID; equals its identity id)
     * @return the created grant
     * @throws NotFoundException   if the node's Globus configuration is not set
     * @throws DataFormatException if inputs are missing/invalid, the client identity
     *                             does not resolve, or a grant for it already exists
     */
    public GlobusSyncPeerGrantPojo add(final String peerLabel, final String peerClientId)
            throws NotFoundException, DataFormatException {
        if (StringUtils.isAnyBlank(peerLabel, peerClientId)) {
            throw new DataFormatException("A peer label and a peer client id are both required.");
        }
        final UUID identityId;
        try {
            identityId = UUID.fromString(peerClientId);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new DataFormatException("Peer client id must be a UUID: " + peerClientId);
        }
        final String canonicalId = identityId.toString();
        final Context ctx = context();
        // Validate the client id is a real Globus client identity (reject typos).
        _identityService.resolveByUsername(ctx.credentials(), canonicalId + CLIENT_IDENTITY_SUFFIX)
                .orElseThrow(() -> new DataFormatException("No Globus client identity found for client id '"
                        + peerClientId + "'."));
        if (_grants.getByPeerIdentityId(canonicalId).isPresent()) {
            throw new DataFormatException("A sync grant for peer client '" + peerClientId + "' already exists.");
        }
        final String subpath = subpath(peerLabel);
        final String aclId = _client.createAccessRule(ctx.token(), ctx.collectionId(), identityId,
                subpath, Permission.READ_WRITE);

        final GlobusSyncPeerGrant grant = new GlobusSyncPeerGrant();
        grant.setPeerLabel(peerLabel);
        grant.setPeerIdentityId(canonicalId);
        grant.setSubpath(subpath);
        grant.setGlobusAclId(aclId);
        grant.setState(GlobusGrantState.ACTIVE);
        _grants.save(grant);
        return toPojo(grant, GlobusGrantState.ACTIVE);
    }

    /**
     * Revoke a peer grant: delete its Globus ACL (if provisioned) and remove the
     * XNAT record. Does not affect already-imported data.
     *
     * @param peerIdentityId the peer client's identity UUID whose grant to remove
     * @throws NotFoundException   if no such grant exists, or Globus is not configured
     * @throws DataFormatException if the node's sync inbox collection is not configured
     */
    public void remove(final String peerIdentityId) throws NotFoundException, DataFormatException {
        final GlobusSyncPeerGrant grant = _grants.getByPeerIdentityId(peerIdentityId)
                .orElseThrow(() -> new NotFoundException("No Globus sync grant for peer client '"
                        + peerIdentityId + "'."));
        final Context ctx = context();
        if (StringUtils.isNotBlank(grant.getGlobusAclId())) {
            _client.deleteAccessRule(ctx.token(), ctx.collectionId(), grant.getGlobusAclId());
        }
        _grants.delete(grant);
    }

    /**
     * Reconcile stored records against the live ACLs on the sync inbox collection.
     * Only ACLs on a {@code /<peer>/} subpath are considered — the per-peer grants
     * this panel manages. A record is ACTIVE when such an ACL binds the same peer
     * identity to the same subpath; otherwise PENDING. A peer-subpath ACL with no
     * matching record is ORPHANED.
     *
     * <p>Collection-level permissions (the node service account, the collection
     * owner, the owning user) live at the collection root, not a peer subpath, so
     * they are excluded here and never appear as ORPHANED.</p>
     *
     * @param records the stored peer-grant records
     * @return records (as ACTIVE/PENDING) plus any ORPHANED peer-subpath ACLs
     * @throws NotFoundException   if Globus is not configured
     * @throws DataFormatException if the sync inbox collection is not configured
     */
    private List<GlobusSyncPeerGrantPojo> reconcile(final List<GlobusSyncPeerGrant> records)
            throws NotFoundException, DataFormatException {
        final Context ctx = context();
        // Only identity ACLs on a /<peer>/ subpath are peer grants this panel manages;
        // labelFromPath is null exactly for collection-root ACLs, which we drop so the
        // owner/service-account/owning-user permissions never surface as ORPHANED.
        final List<AccessRule> peerSubpathRules = _client.listAccessRules(ctx.token(), ctx.collectionId()).stream()
                .filter(rule -> rule.principalType() == PrincipalType.IDENTITY && labelFromPath(rule.path()) != null)
                .toList();

        final List<GlobusSyncPeerGrantPojo> pojos = new ArrayList<>();
        for (final GlobusSyncPeerGrant record : records) {
            final boolean active = peerSubpathRules.stream().anyMatch(rule -> matchesRecord(record, rule));
            pojos.add(toPojo(record, active ? GlobusGrantState.ACTIVE : GlobusGrantState.PENDING));
        }
        for (final AccessRule rule : peerSubpathRules) {
            if (records.stream().noneMatch(record -> matchesRecord(record, rule))) {
                pojos.add(orphanPojo(rule));
            }
        }
        return pojos;
    }

    /** Whether an identity ACL corresponds to a peer-grant record: same identity and same subpath label. */
    private static boolean matchesRecord(final GlobusSyncPeerGrant record, final AccessRule rule) {
        return record.getPeerIdentityId().equals(rule.principal())
                && record.getPeerLabel().equals(labelFromPath(rule.path()));
    }

    /** Build a pojo for an ACL that has no XNAT record (ORPHANED). */
    private GlobusSyncPeerGrantPojo orphanPojo(final AccessRule rule) {
        return new GlobusSyncPeerGrantPojo(labelFromPath(rule.path()), rule.principal(), rule.path(),
                rule.permissions().wire(), GlobusGrantState.ORPHANED.name(), rule.id());
    }

    private GlobusSyncPeerGrantPojo toPojo(final GlobusSyncPeerGrant grant, final GlobusGrantState state) {
        return new GlobusSyncPeerGrantPojo(grant.getPeerLabel(), grant.getPeerIdentityId(), grant.getSubpath(),
                Permission.READ_WRITE.wire(), state.name(), grant.getGlobusAclId());
    }

    /** The collection-relative subpath a peer writes to: {@code /<peerLabel>/}. */
    private static String subpath(final String peerLabel) {
        return "/" + peerLabel + "/";
    }

    /** Extract the first path segment ({@code /<peer>/...}) as the peer label, or null. */
    private static String labelFromPath(final String path) {
        return Arrays.stream(StringUtils.defaultString(path).split("/"))
                .filter(StringUtils::isNotBlank)
                .findFirst()
                .orElse(null);
    }

    /**
     * Resolve the Globus context (token, sync inbox collection, credentials).
     *
     * @throws NotFoundException   if the node's Globus service account is not set
     * @throws DataFormatException if no sync inbox collection is configured
     */
    private Context context() throws NotFoundException, DataFormatException {
        final GlobusCredentials credentials = _nodeConfigService.getServiceCredentials();
        final String collectionId = _nodeConfigService.getConfig()
                .map(GlobusNodeConfig::getInboxCollectionId)
                .filter(StringUtils::isNotBlank)
                .orElseThrow(() -> new DataFormatException("No Globus sync inbox collection is configured for this node."));
        final String token = _authService.getTransferToken(credentials);
        return new Context(token, collectionId, credentials);
    }
}
