package org.nrg.xsync.globus.upload;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.nrg.xapi.exceptions.DataFormatException;
import org.nrg.xapi.exceptions.NotFoundException;
import org.nrg.xsync.globus.GlobusClient;
import org.nrg.xsync.globus.GlobusClient.AccessRule;
import org.nrg.xsync.globus.GlobusClient.Permission;
import org.nrg.xsync.globus.GlobusClient.PrincipalType;
import org.nrg.xsync.globus.GlobusCredentials;
import org.nrg.xsync.globus.GlobusIdentityService;
import org.nrg.xsync.globus.GlobusIdentityService.GlobusIdentity;
import org.nrg.xsync.globus.GlobusAuthService;
import org.nrg.xsync.globus.entities.GlobusNodeConfig;
import org.nrg.xsync.globus.entities.GlobusUploadGrant;
import org.nrg.xsync.globus.entities.GlobusUploadGrantState;
import org.nrg.xsync.globus.services.GlobusNodeConfigService;
import org.nrg.xsync.globus.services.GlobusUploadGrantService;
import org.nrg.xsync.pojo.GlobusUploadGrantPojo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Orchestrates per-user Globus upload-permission management for a project: it
 * ties the XNAT-owned {@link GlobusUploadGrant} records to the Globus ACLs on
 * the node's upload collection (design
 * {@code XSYNC_GLOBUS_UPLOAD_ACL_UI_DESIGN.md} §5&ndash;§8).
 *
 * <p>Per the §8 decision (option 1), all Globus ACL calls run under the node
 * service account's Transfer token, which must hold the {@code access_manager}
 * role on the upload collection; authorization over <em>who</em> may invoke
 * these operations is enforced by the controller's XNAT project-role check.</p>
 *
 * @author XSync
 */
@Slf4j
@Component
public class GlobusUploadPermissionManager {

    private static final String PERMISSION_RW = "rw";

    private final GlobusUploadGrantService _grants;
    private final GlobusClient _client;
    private final GlobusAuthService _authService;
    private final GlobusIdentityService _identityService;
    private final GlobusNodeConfigService _nodeConfigService;

    @Autowired
    public GlobusUploadPermissionManager(final GlobusUploadGrantService grants, final GlobusClient client,
                                         final GlobusAuthService authService,
                                         final GlobusIdentityService identityService,
                                         final GlobusNodeConfigService nodeConfigService) {
        _grants = grants;
        _client = client;
        _authService = authService;
        _identityService = identityService;
        _nodeConfigService = nodeConfigService;
    }

    /** Resolved Globus context for upload-ACL operations. */
    private record Context(String token, String collectionId, GlobusCredentials credentials) {
    }

    /**
     * List a project's upload grants, reconciled against the live Globus ACLs
     * when reachable: stored records are marked {@code ACTIVE} or {@code PENDING}
     * by whether a matching ACL exists, and ACLs with no record are returned as
     * {@code ORPHANED}. If Globus cannot be reached (or is not configured), the
     * stored records are returned with their last-known state.
     *
     * @param projectId the XNAT project id
     * @return the reconciled grants for display
     */
    public List<GlobusUploadGrantPojo> list(final String projectId) {
        final List<GlobusUploadGrant> records = _grants.getByProject(projectId);
        try {
            return reconcile(projectId, records);
        } catch (Exception e) {
            log.warn("Could not reconcile Globus upload grants for project {} against the live ACLs "
                    + "(returning stored records): {}", projectId, e.getMessage());
            final List<GlobusUploadGrantPojo> pojos = new ArrayList<>();
            for (final GlobusUploadGrant record : records) {
                pojos.add(toPojo(record, record.getState()));
            }
            return pojos;
        }
    }

    /**
     * Grant a Globus identity upload access as an XNAT user: resolve the Globus
     * username to a UUID, create the collection ACL on the per-user subpath, and
     * persist the binding.
     *
     * @param projectId      the XNAT project id
     * @param globusUsername the Globus username/email to grant
     * @param xnatUsername   the XNAT user whose permissions uploads import under
     * @return the created grant
     * @throws NotFoundException   if the node's Globus configuration is not set
     * @throws DataFormatException if inputs are missing, the identity does not resolve,
     *                             or a grant for that identity already exists
     */
    public GlobusUploadGrantPojo add(final String projectId, final String globusUsername, final String xnatUsername)
            throws NotFoundException, DataFormatException {
        if (StringUtils.isAnyBlank(projectId, globusUsername, xnatUsername)) {
            throw new DataFormatException("A Globus username and an XNAT user are both required.");
        }
        final Context ctx = context();
        final GlobusIdentity identity = _identityService.resolveByUsername(ctx.credentials(), globusUsername)
                .orElseThrow(() -> new DataFormatException("No Globus identity found for '" + globusUsername + "'."));
        if (_grants.getByProjectAndIdentity(projectId, identity.id()).isPresent()) {
            throw new DataFormatException("An upload grant for Globus identity '" + globusUsername
                    + "' already exists in this project.");
        }
        final UUID identityId;
        try {
            identityId = UUID.fromString(identity.id());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new DataFormatException("Globus returned a non-UUID identity id for '" + globusUsername
                    + "': " + identity.id());
        }
        final String subpath = subpath(projectId, xnatUsername);
        final String aclId = _client.createAccessRule(ctx.token(), ctx.collectionId(), identityId,
                subpath, Permission.READ_WRITE);

        final GlobusUploadGrant grant = new GlobusUploadGrant();
        grant.setProjectId(projectId);
        grant.setGlobusUsername(StringUtils.defaultString(identity.username(), globusUsername));
        grant.setGlobusIdentityId(identity.id());
        grant.setXnatUsername(xnatUsername);
        grant.setSubpath(subpath);
        grant.setGlobusAclId(aclId);
        grant.setState(GlobusUploadGrantState.ACTIVE);
        _grants.save(grant);
        return toPojo(grant, GlobusUploadGrantState.ACTIVE);
    }

    /**
     * Revoke an upload grant: delete its Globus ACL (if provisioned) and remove
     * the XNAT record. Removing a grant cuts future access; it does not affect
     * already-imported data.
     *
     * @param projectId        the XNAT project id
     * @param globusIdentityId the Globus identity UUID whose grant to remove
     * @throws NotFoundException   if no such grant exists, or Globus is not configured
     * @throws DataFormatException if the node's upload collection is not configured
     */
    public void remove(final String projectId, final String globusIdentityId)
            throws NotFoundException, DataFormatException {
        final GlobusUploadGrant grant = _grants.getByProjectAndIdentity(projectId, globusIdentityId)
                .orElseThrow(() -> new NotFoundException("No Globus upload grant for identity '"
                        + globusIdentityId + "' in project '" + projectId + "'."));
        final Context ctx = context();
        if (StringUtils.isNotBlank(grant.getGlobusAclId())) {
            _client.deleteAccessRule(ctx.token(), ctx.collectionId(), grant.getGlobusAclId());
        }
        _grants.delete(grant);
    }

    /**
     * Reconcile stored records against the live ACLs for this project's subpaths.
     *
     * @param projectId the XNAT project id
     * @param records   the project's stored grant records
     * @return records (as ACTIVE/PENDING) plus any ORPHANED ACLs
     * @throws NotFoundException   if Globus is not configured
     * @throws DataFormatException if the upload collection is not configured
     */
    private List<GlobusUploadGrantPojo> reconcile(final String projectId, final List<GlobusUploadGrant> records)
            throws NotFoundException, DataFormatException {
        final Context ctx = context();
        final String prefix = "/" + projectId + "/";
        final List<AccessRule> rules = _client.listAccessRules(ctx.token(), ctx.collectionId());

        final Set<String> grantedPrincipals = new HashSet<>();
        final List<GlobusUploadGrantPojo> pojos = new ArrayList<>();
        for (final GlobusUploadGrant record : records) {
            final boolean active = rules.stream().anyMatch(r -> r.principalType() == PrincipalType.IDENTITY
                    && record.getGlobusIdentityId().equals(r.principal()));
            pojos.add(toPojo(record, active ? GlobusUploadGrantState.ACTIVE : GlobusUploadGrantState.PENDING));
            grantedPrincipals.add(record.getGlobusIdentityId());
        }

        for (final AccessRule rule : rules) {
            if (rule.principalType() != PrincipalType.IDENTITY || rule.path() == null || !rule.path().startsWith(prefix)
                    || grantedPrincipals.contains(rule.principal())) {
                continue;
            }
            pojos.add(orphanPojo(projectId, rule, ctx));
        }
        return pojos;
    }

    /** Build a pojo for an ACL that has no XNAT record (ORPHANED). */
    private GlobusUploadGrantPojo orphanPojo(final String projectId, final AccessRule rule, final Context ctx) {
        final String globusUsername = _identityService.resolveById(ctx.credentials(), rule.principal())
                .map(GlobusIdentity::username).orElse(null);
        return new GlobusUploadGrantPojo(projectId, globusUsername, rule.principal(),
                usernameFromPath(rule.path()), rule.permissions().wire(),
                GlobusUploadGrantState.ORPHANED.name(), rule.id());
    }

    private GlobusUploadGrantPojo toPojo(final GlobusUploadGrant grant, final GlobusUploadGrantState state) {
        return new GlobusUploadGrantPojo(grant.getProjectId(), grant.getGlobusUsername(), grant.getGlobusIdentityId(),
                grant.getXnatUsername(), PERMISSION_RW, state.name(), grant.getGlobusAclId());
    }

    /** The collection-relative subpath a grant writes to: {@code /<projectId>/<xnatUsername>/}. */
    private static String subpath(final String projectId, final String xnatUsername) {
        return "/" + projectId + "/" + xnatUsername + "/";
    }

    /** Extract the user segment ({@code /<project>/<user>/...}) from an ACL path, or null. */
    private static String usernameFromPath(final String path) {
        final String[] segments = Arrays.stream(StringUtils.defaultString(path).split("/"))
                .filter(StringUtils::isNotBlank)
                .toArray(String[]::new);
        return segments.length >= 2 ? segments[1] : null;
    }

    /**
     * Resolve the Globus context (token, upload collection, credentials).
     *
     * @throws NotFoundException   if the node's Globus service account is not set
     * @throws DataFormatException if no upload inbox collection is configured
     */
    private Context context() throws NotFoundException, DataFormatException {
        final GlobusCredentials credentials = _nodeConfigService.getServiceCredentials();
        final String collectionId = _nodeConfigService.getConfig()
                .map(GlobusNodeConfig::getUploadInboxCollectionId)
                .filter(StringUtils::isNotBlank)
                .orElseThrow(() -> new DataFormatException("No Globus upload inbox collection is configured for this node."));
        final String token = _authService.getTransferToken(credentials);
        return new Context(token, collectionId, credentials);
    }
}
