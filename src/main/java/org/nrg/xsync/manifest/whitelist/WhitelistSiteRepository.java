package org.nrg.xsync.manifest.whitelist;

import org.nrg.framework.orm.hibernate.AbstractHibernateDAO;
import org.springframework.stereotype.Repository;

@Repository
public class WhitelistSiteRepository extends AbstractHibernateDAO<WhitelistSite> {

    public WhitelistSite findWhitelistSiteBySiteId(String siteId) {
        return findByUniqueProperty("siteId", siteId);
    }
}
