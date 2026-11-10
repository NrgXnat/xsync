package org.nrg.xsync.globus.upload;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import org.nrg.xdat.XDAT;
import org.nrg.xdat.security.helpers.Permissions;
import org.nrg.xdat.security.helpers.Users;
import org.nrg.xft.security.UserI;
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
 * the node's upload collection.
 *
 * <p>All Globus ACL calls run under the node service account's Transfer token,
 * which must hold the {@code access_manager} role on the upload collection;
 * authorization over <em>who</em> may invoke these operations is enforced by the
 * controller's XNAT project-role check.</p>
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
        if (!canEditProject(xnatUsername, projectId)) {
            throw new DataFormatException("XNAT user '" + xnatUsername + "' must be a project member with edit "
                    + "access to receive upload permission.");
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
        // The ACL only grants access to the path; the directory must exist on disk
        // for the user to see and upload to it in the Globus File Manager.
        ensureUploadDirectory(projectId, xnatUsername);
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
     * the XNAT record, then remove the user's upload directory if it is empty.
     * Removing a grant cuts future access; it does not affect already-imported
     * data, and a non-empty directory (e.g. an in-flight upload) is left in place.
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
        removeUploadDirectoryIfEmpty(projectId, grant.getXnatUsername());
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

    /**
     * Whether an XNAT user exists and has edit access to the project — the gate
     * on who may be granted upload permission (only edit-capable project
     * members). A seam so the add path is unit-testable without a live XNAT
     * security context.
     *
     * @param username  the candidate XNAT username
     * @param projectId the project
     * @return {@code true} if the user exists and can edit the project
     */
    protected boolean canEditProject(final String username, final String projectId) {
        try {
            final UserI user = Users.getUser(username);
            return user != null && Permissions.canEditProject(user, projectId);
        } catch (Exception e) {
            log.debug("Edit-access check failed for user {} on project {}: {}", username, projectId, e.getMessage());
            return false;
        }
    }

    /**
     * Create the per-user upload directory on disk ({@code <inboxPath>/<project>/<user>})
     * if it does not already exist, so the user can see and upload to their subpath
     * in the Globus File Manager — granting the ACL alone does not create the path.
     * XNAT runs as the identity-mapped account and shares the staging filesystem
     * with GCS, so the directory is created with the right ownership. A seam:
     * overridable for tests, and the single place the on-disk creation policy lives.
     *
     * @param projectId    the project
     * @param xnatUsername the XNAT user (the subpath segment)
     * @throws DataFormatException if the resolved path escapes the inbox root
     */
    protected void ensureUploadDirectory(final String projectId, final String xnatUsername)
            throws DataFormatException {
        final String inboxPath = inboxPath();
        if (StringUtils.isBlank(inboxPath)) {
            throw new DataFormatException("XNAT inbox path is not configured; cannot create the upload directory.");
        }
        final Path base = Paths.get(inboxPath).normalize();
        final Path dir = base.resolve(projectId).resolve(xnatUsername).normalize();
        if (!dir.startsWith(base)) {
            throw new DataFormatException("Invalid project/user path for upload directory.");
        }
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directory " + dir + ": " + e.getMessage(), e);
        }
    }

    /**
     * Remove the per-user upload directory ({@code <inboxPath>/<project>/<user>})
     * on grant revocation, but <strong>only if it is empty</strong> — a directory
     * holding files (e.g. an in-flight or not-yet-imported upload) is left alone.
     * Best-effort: a failure is logged, not propagated, so it never blocks the
     * revocation. A seam: overridable for tests.
     *
     * @param projectId    the project
     * @param xnatUsername the XNAT user (the subpath segment)
     */
    protected void removeUploadDirectoryIfEmpty(final String projectId, final String xnatUsername) {
        final String inboxPath = inboxPath();
        if (StringUtils.isBlank(inboxPath)) {
            return;
        }
        final Path base = Paths.get(inboxPath).normalize();
        final Path dir = base.resolve(projectId).resolve(xnatUsername).normalize();
        if (!dir.startsWith(base) || !Files.isDirectory(dir)) {
            return;
        }
        try (var entries = Files.list(dir)) {
            if (entries.findAny().isPresent()) {
                log.debug("Upload directory {} is not empty; leaving it in place on grant revocation.", dir);
                return;
            }
        } catch (IOException e) {
            log.warn("Could not inspect upload directory {} on grant revocation: {}", dir, e.getMessage());
            return;
        }
        try {
            Files.delete(dir);
        } catch (IOException e) {
            // e.g. a DirectoryNotEmptyException from a concurrent upload between the check and the delete.
            log.warn("Could not remove empty upload directory {} on grant revocation: {}", dir, e.getMessage());
        }
    }

    /** @return XNAT's site-configured inbox path (the upload collection's on-disk root). A seam for tests. */
    protected String inboxPath() {
        return XDAT.getSiteConfigPreferences().getInboxPath();
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
