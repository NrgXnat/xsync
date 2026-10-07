package org.nrg.xsync.globus.repositories;

import org.nrg.framework.orm.hibernate.AbstractHibernateDAO;
import org.nrg.xsync.globus.entities.GlobusNodeConfig;
import org.springframework.stereotype.Repository;

/**
 * Hibernate repository for {@link GlobusNodeConfig} (a singleton row).
 *
 * @author XSync
 */
@Repository
public class GlobusNodeConfigRepository extends AbstractHibernateDAO<GlobusNodeConfig> {
}
