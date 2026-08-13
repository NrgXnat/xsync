package org.nrg.xsync.manifest.whitelist;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.nrg.framework.orm.hibernate.AbstractHibernateEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class WhitelistSite extends AbstractHibernateEntity {

    @Column(name = "siteId", unique = true, nullable = false)
    private String siteId;

    @Column(name = "siteName", unique = true, nullable = false)
    private String siteName;

    @Column(name = "siteUrl", unique = true, nullable = false)
    private String siteUrl;

    private SiteClassification classification;
}
