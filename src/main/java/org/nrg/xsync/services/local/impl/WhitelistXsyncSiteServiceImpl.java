package org.nrg.xsync.services.local.impl;

import lombok.extern.slf4j.Slf4j;
import org.nrg.framework.orm.hibernate.AbstractHibernateEntityService;
import org.nrg.xapi.exceptions.DataFormatException;
import org.nrg.xapi.exceptions.NotFoundException;
import org.nrg.xdat.XDAT;
import org.nrg.xsync.manifest.whitelist.SiteClassification;
import org.nrg.xsync.manifest.whitelist.WhitelistSite;
import org.nrg.xsync.manifest.whitelist.WhitelistSiteRepository;
import org.nrg.xsync.pojo.WhitelistSitePojo;
import org.nrg.xsync.services.local.WhitelistXsyncSiteService;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@Transactional
public class WhitelistXsyncSiteServiceImpl extends AbstractHibernateEntityService<WhitelistSite, WhitelistSiteRepository> implements WhitelistXsyncSiteService {

    @Override
    public List<WhitelistSitePojo> getAllWhitelistedSites() {
        List<WhitelistSite> allWhitelistedSites = getDao().findAll();
        List<WhitelistSitePojo> allSitePojos = new ArrayList<>();
        if (allWhitelistedSites.stream().noneMatch(w -> w.getSiteUrl().equals(XDAT.getSiteUrl()))) {
            WhitelistSite sourceSiteWhitelist = new WhitelistSite(XDAT.getSiteId(), XDAT.getSiteId(), XDAT.getSiteUrl(),
                                                           SiteClassification.PUBLIC);
            getDao().saveOrUpdate(sourceSiteWhitelist);
            allWhitelistedSites.add(sourceSiteWhitelist);
        }
        for (WhitelistSite site : allWhitelistedSites) {
            WhitelistSitePojo pojo = new WhitelistSitePojo(site.getSiteId(), site.getSiteName(), site.getSiteUrl(),
                                                           site.getClassification().toString());
            allSitePojos.add(pojo);
        }
        return allSitePojos;
    }

    @Override
    public void addWhiteListSitesFromJson(List<WhitelistSitePojo> whitelistSitePojos) {
        for (WhitelistSitePojo sitePojo: whitelistSitePojos) {
            if (getDao().findWhitelistSiteBySiteId(sitePojo.getSiteId()) == null) {
                try {
                    SiteClassification newSiteClassification = getSiteClassification(sitePojo);
                    WhitelistSite newSite = new WhitelistSite(sitePojo.getSiteId(), sitePojo.getSiteName(),
                                                              sitePojo.getSiteUrl(), newSiteClassification);
                    getDao().saveOrUpdate(newSite);
                } catch (DataFormatException e) {
                    log.error("Input whitelist listing with id {} uses an unknown site classification: {}. This site " +
                              "has not been added to the whitelist.", sitePojo.getSiteId(), sitePojo.getClassification());
                }
            }
        }
    }

    @Override
    public List<WhitelistSitePojo> addOrUpdateWhitelistSiteFromSiteAdmin(WhitelistSitePojo whitelistSitePojo) throws DataFormatException {
        SiteClassification newSiteClassification = getSiteClassification(whitelistSitePojo);
        WhitelistSite newSite;
        if (getDao().findWhitelistSiteBySiteId(whitelistSitePojo.getSiteId()) == null) {
            newSite = new WhitelistSite(whitelistSitePojo.getSiteId(), whitelistSitePojo.getSiteName(),
                                                      whitelistSitePojo.getSiteUrl(), newSiteClassification);
        } else {
            newSite = getDao().findWhitelistSiteBySiteId(whitelistSitePojo.getSiteId());
            newSite.setSiteName(whitelistSitePojo.getSiteName());
            newSite.setSiteUrl(whitelistSitePojo.getSiteUrl());
            newSite.setClassification(newSiteClassification);
        }

        getDao().saveOrUpdate(newSite);
        return getAllWhitelistedSites();
    }

    @Override
    public List<WhitelistSitePojo> deleteWhitelistSiteFromSiteAdmin(WhitelistSitePojo whitelistSitePojo) throws NotFoundException {
        WhitelistSite siteFromPojo = getDao().findWhitelistSiteBySiteId(whitelistSitePojo.getSiteId());
        if (siteFromPojo != null) {
            getDao().delete(siteFromPojo);
            return getAllWhitelistedSites();
        } else {
            throw new NotFoundException("Site with id: " + whitelistSitePojo.getSiteId() +  " not found.");
        }
    }

    private static SiteClassification getSiteClassification(WhitelistSitePojo whitelistSitePojo) throws DataFormatException {
        SiteClassification newSiteClassification;
        return switch (whitelistSitePojo.getClassification().toLowerCase()) {
            case "clinical" -> SiteClassification.CLINICAL;
            case "research" -> SiteClassification.RESEARCH;
            case "public" -> SiteClassification.PUBLIC;
            default ->
                    throw new DataFormatException("Input whitelist listing with id " + whitelistSitePojo.getSiteId() + " uses an " +
                                                          "unknown site classification: " + whitelistSitePojo.getClassification() + ". This site has not " +
                                                          "been added to the whitelist.");
        };
    }
}
