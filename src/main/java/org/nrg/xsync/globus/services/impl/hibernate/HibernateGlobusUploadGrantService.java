package org.nrg.xsync.globus.services.impl.hibernate;

import java.util.List;
import java.util.Optional;

import javax.transaction.Transactional;

import lombok.extern.slf4j.Slf4j;
import org.nrg.framework.orm.hibernate.AbstractHibernateEntityService;
import org.nrg.xsync.globus.entities.GlobusUploadGrant;
import org.nrg.xsync.globus.repositories.GlobusUploadGrantRepository;
import org.nrg.xsync.globus.services.GlobusUploadGrantService;
import org.springframework.stereotype.Service;

/**
 * Hibernate-backed {@link GlobusUploadGrantService}.
 *
 * @author XSync
 */
@Slf4j
@Service
@Transactional
public class HibernateGlobusUploadGrantService
        extends AbstractHibernateEntityService<GlobusUploadGrant, GlobusUploadGrantRepository>
        implements GlobusUploadGrantService {

    @Override
    public List<GlobusUploadGrant> getByProject(final String projectId) {
        return getDao().findByProject(projectId);
    }

    @Override
    public Optional<GlobusUploadGrant> getByProjectAndIdentity(final String projectId, final String globusIdentityId) {
        return Optional.ofNullable(getDao().findByProjectAndIdentity(projectId, globusIdentityId));
    }

    @Override
    public List<GlobusUploadGrant> getByProjectAndXnatUser(final String projectId, final String xnatUsername) {
        return getDao().findByProjectAndXnatUser(projectId, xnatUsername);
    }

    @Override
    public GlobusUploadGrant save(final GlobusUploadGrant grant) {
        getDao().saveOrUpdate(grant);
        return grant;
    }

    @Override
    public void delete(final GlobusUploadGrant grant) {
        getDao().delete(grant);
    }
}
