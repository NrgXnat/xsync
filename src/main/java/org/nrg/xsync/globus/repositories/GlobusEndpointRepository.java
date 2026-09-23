package org.nrg.xsync.globus.repositories;

import org.nrg.framework.orm.hibernate.AbstractHibernateDAO;
import org.nrg.xsync.globus.entities.GlobusEndpoint;
import org.springframework.stereotype.Repository;

/**
 * Hibernate repository for {@link GlobusEndpoint}.
 *
 * @author XSync
 */
@Repository
public class GlobusEndpointRepository extends AbstractHibernateDAO<GlobusEndpoint> {

    /**
     * Find an endpoint by its unique name.
     *
     * @param name the endpoint name
     * @return the endpoint, or {@code null} if none matches
     */
    public GlobusEndpoint findByName(final String name) {
        return findByUniqueProperty("name", name);
    }
}
