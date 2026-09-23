package org.nrg.xsync.globus.services.impl.hibernate;

import java.util.List;

import javax.transaction.Transactional;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.nrg.framework.orm.hibernate.AbstractHibernateEntityService;
import org.nrg.xapi.exceptions.DataFormatException;
import org.nrg.xapi.exceptions.NotFoundException;
import org.nrg.xsync.globus.GlobusCredentials;
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
        if (StringUtils.isAnyBlank(endpoint.getName(), endpoint.getClientId())) {
            throw new DataFormatException("A Globus endpoint requires a name and client id.");
        }
        if (StringUtils.isAllBlank(endpoint.getInboxCollectionId(), endpoint.getOutboxCollectionId())) {
            throw new DataFormatException("A Globus endpoint requires an inbox and/or outbox collection id.");
        }
        final GlobusEndpoint existing = getDao().findByName(endpoint.getName());
        if (existing == null) {
            if (StringUtils.isBlank(endpoint.getClientSecret())) {
                throw new DataFormatException("A client secret is required when creating a new Globus endpoint.");
            }
            getDao().saveOrUpdate(endpoint);
            return endpoint;
        }
        existing.setClientId(endpoint.getClientId());
        existing.setInboxCollectionId(endpoint.getInboxCollectionId());
        existing.setOutboxCollectionId(endpoint.getOutboxCollectionId());
        // A blank secret on update leaves the stored secret unchanged (the API
        // never returns it, so edits that don't re-enter it must not clear it).
        if (StringUtils.isNotBlank(endpoint.getClientSecret())) {
            existing.setClientSecret(endpoint.getClientSecret());
        }
        getDao().saveOrUpdate(existing);
        return existing;
    }

    @Override
    public void delete(final String name) throws NotFoundException {
        getDao().delete(getByName(name));
    }

    @Override
    public GlobusCredentials credentialsFor(final String name) throws NotFoundException {
        final GlobusEndpoint endpoint = getByName(name);
        return new GlobusCredentials(endpoint.getClientId(), endpoint.getClientSecret());
    }
}
