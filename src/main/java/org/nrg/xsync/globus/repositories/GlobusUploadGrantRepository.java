package org.nrg.xsync.globus.repositories;

import java.util.List;
import java.util.Map;

import org.nrg.framework.orm.hibernate.AbstractHibernateDAO;
import org.nrg.xsync.globus.entities.GlobusUploadGrant;
import org.springframework.stereotype.Repository;

/**
 * Hibernate repository for {@link GlobusUploadGrant}.
 *
 * @author XSync
 */
@Repository
public class GlobusUploadGrantRepository extends AbstractHibernateDAO<GlobusUploadGrant> {

    /**
     * Find all upload grants for a project.
     *
     * @param projectId the XNAT project id
     * @return the project's grants (possibly empty)
     */
    public List<GlobusUploadGrant> findByProject(final String projectId) {
        return findByProperty("projectId", projectId);
    }

    /**
     * Find the grant for one Globus identity in one project.
     *
     * @param projectId        the XNAT project id
     * @param globusIdentityId the Globus identity UUID
     * @return the grant, or {@code null} if none matches
     */
    public GlobusUploadGrant findByProjectAndIdentity(final String projectId, final String globusIdentityId) {
        return findByUniqueProperties(Map.of("projectId", projectId, "globusIdentityId", globusIdentityId));
    }
}
