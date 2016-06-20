package org.nrg.xsync.remote.alias.services.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.nrg.framework.orm.hibernate.AbstractHibernateEntityService;
import org.nrg.xsync.remote.alias.RemoteAliasEntity;
import org.nrg.xsync.remote.alias.RemoteAliasEntityRepository;
import org.nrg.xsync.remote.alias.services.RemoteAliasService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Mohana Ramaratnam
 *
 */
@Service
public class DefaultRemoteAliasServiceImpl extends AbstractHibernateEntityService<RemoteAliasEntity, RemoteAliasEntityRepository> implements RemoteAliasService {
    @Override
    @Transactional
    public 	RemoteAliasEntity getRemoteAliasEntity(final String projectId, final String remoteHost) {
        Map<String,Object>  properties = new HashMap<String, Object>();
        properties.put("local_project", projectId);
        properties.put("remote_host", remoteHost);
    	List<RemoteAliasEntity> entities =  getDao().findByProperties(properties);
    	RemoteAliasEntity remoteAliasEntity = null;
    	if (entities != null && entities.size() > 0) {
    		remoteAliasEntity = entities.get(0);
    	}
    	return remoteAliasEntity;
    }
}
