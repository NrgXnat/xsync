package org.nrg.xsync.globus.services;

import java.util.List;
import java.util.Optional;

import org.nrg.xsync.globus.entities.GlobusUploadGrant;

/**
 * Persistence for {@link GlobusUploadGrant} records — the XNAT-owned bindings of
 * Globus identities to XNAT users for per-project upload. Provisioning the
 * matching Globus ACL is orchestrated by
 * {@link org.nrg.xsync.globus.upload.GlobusUploadPermissionManager}; this service
 * only stores and retrieves the records.
 *
 * @author XSync
 */
public interface GlobusUploadGrantService {

    /**
     * @param projectId the XNAT project id
     * @return the project's upload-grant records (possibly empty)
     */
    List<GlobusUploadGrant> getByProject(String projectId);

    /**
     * @param projectId        the XNAT project id
     * @param globusIdentityId the Globus identity UUID
     * @return the grant for that identity in that project, if present
     */
    Optional<GlobusUploadGrant> getByProjectAndIdentity(String projectId, String globusIdentityId);

    /**
     * Persist (create or update) a grant record.
     *
     * @param grant the record to save
     * @return the saved record
     */
    GlobusUploadGrant save(GlobusUploadGrant grant);

    /**
     * Delete a grant record.
     *
     * @param grant the record to delete
     */
    void delete(GlobusUploadGrant grant);
}
