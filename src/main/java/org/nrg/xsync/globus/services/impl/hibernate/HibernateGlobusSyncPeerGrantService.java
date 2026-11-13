package org.nrg.xsync.globus.services.impl.hibernate;

import java.util.List;
import java.util.Optional;

import javax.transaction.Transactional;

import lombok.extern.slf4j.Slf4j;
import org.nrg.framework.orm.hibernate.AbstractHibernateEntityService;
import org.nrg.xsync.globus.entities.GlobusSyncPeerGrant;
import org.nrg.xsync.globus.repositories.GlobusSyncPeerGrantRepository;
import org.nrg.xsync.globus.services.GlobusSyncPeerGrantService;
import org.springframework.stereotype.Service;

/**
 * Hibernate-backed {@link GlobusSyncPeerGrantService}.
 *
 * @author XSync
 */
@Slf4j
@Service
@Transactional
public class HibernateGlobusSyncPeerGrantService
        extends AbstractHibernateEntityService<GlobusSyncPeerGrant, GlobusSyncPeerGrantRepository>
        implements GlobusSyncPeerGrantService {

    @Override
    public List<GlobusSyncPeerGrant> getAll() {
        return getDao().findAll();
    }

    @Override
    public Optional<GlobusSyncPeerGrant> getByPeerIdentityId(final String peerIdentityId) {
        return Optional.ofNullable(getDao().findByPeerIdentityId(peerIdentityId));
    }

    @Override
    public GlobusSyncPeerGrant save(final GlobusSyncPeerGrant grant) {
        getDao().saveOrUpdate(grant);
        return grant;
    }

    @Override
    public void delete(final GlobusSyncPeerGrant grant) {
        getDao().delete(grant);
    }
}
