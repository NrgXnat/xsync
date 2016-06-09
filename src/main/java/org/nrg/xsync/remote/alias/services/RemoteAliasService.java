package org.nrg.xsync.remote.alias.services;

import org.nrg.framework.orm.hibernate.BaseHibernateService;
import org.nrg.xsync.remote.alias.RemoteAliasEntity;
import org.springframework.stereotype.Service;

/**
 * @author Mohana Ramaratnam
 *
 */
@Service
public interface RemoteAliasService extends BaseHibernateService<RemoteAliasEntity> {
	RemoteAliasEntity getRemoteAliasEntity(final String projectId, final String remoteHost);
}
