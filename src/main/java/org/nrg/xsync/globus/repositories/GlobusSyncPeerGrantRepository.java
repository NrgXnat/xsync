package org.nrg.xsync.globus.repositories;

import org.nrg.framework.orm.hibernate.AbstractHibernateDAO;
import org.nrg.xsync.globus.entities.GlobusSyncPeerGrant;
import org.springframework.stereotype.Repository;

/**
 * Hibernate repository for {@link GlobusSyncPeerGrant}.
 *
 * @author XSync
 */
@Repository
public class GlobusSyncPeerGrantRepository extends AbstractHibernateDAO<GlobusSyncPeerGrant> {

    /**
     * Find a peer grant by the peer client's identity UUID.
     *
     * @param peerIdentityId the peer client's Globus identity UUID
     * @return the grant, or {@code null} if none matches
     */
    public GlobusSyncPeerGrant findByPeerIdentityId(final String peerIdentityId) {
        return findByUniqueProperty("peerIdentityId", peerIdentityId);
    }
}
