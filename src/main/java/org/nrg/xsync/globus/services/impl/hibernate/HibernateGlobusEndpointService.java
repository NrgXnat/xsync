package org.nrg.xsync.globus.services.impl.hibernate;

import java.util.List;

import javax.transaction.Transactional;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.nrg.framework.orm.hibernate.AbstractHibernateEntityService;
import org.nrg.xapi.exceptions.DataFormatException;
import org.nrg.xapi.exceptions.NotFoundException;
import org.nrg.xsync.globus.entities.GlobusEndpoint;
import org.nrg.xsync.globus.repositories.GlobusEndpointRepository;
import org.nrg.xsync.globus.services.GlobusEndpointService;
import org.springframework.stereotype.Service;

/**
 * Hibernate-backed {@link GlobusEndpointService}.
 *
 * @author XSync
 */
@Slf4j
@Service
@Transactional
public class HibernateGlobusEndpointService
        extends AbstractHibernateEntityService<GlobusEndpoint, GlobusEndpointRepository>
        implements GlobusEndpointService {

    @Override
    public List<GlobusEndpoint> getAllEndpoints() {
        return getDao().findAll();
    }

    @Override
    public GlobusEndpoint getByName(final String name) throws NotFoundException {
        final GlobusEndpoint endpoint = getDao().findByName(name);
        if (endpoint == null) {
            throw new NotFoundException("No Globus endpoint named '" + name + "'.");
        }
        return endpoint;
    }

    @Override
    public GlobusEndpoint createOrUpdate(final GlobusEndpoint endpoint) throws DataFormatException {
        if (StringUtils.isAnyBlank(endpoint.getName(), endpoint.getInboxCollectionId())) {
            throw new DataFormatException("A Globus destination requires a name and an inbox collection id.");
        }
        final GlobusEndpoint existing = getDao().findByName(endpoint.getName());
        if (existing == null) {
            getDao().saveOrUpdate(endpoint);
            return endpoint;
        }
        existing.setInboxCollectionId(endpoint.getInboxCollectionId());
        existing.setRemoteInboxPath(endpoint.getRemoteInboxPath());
        existing.setRemoteInboxServerDirectory(endpoint.getRemoteInboxServerDirectory());
        getDao().saveOrUpdate(existing);
        return existing;
    }

    @Override
    public void delete(final String name) throws NotFoundException {
        getDao().delete(getByName(name));
    }
}
