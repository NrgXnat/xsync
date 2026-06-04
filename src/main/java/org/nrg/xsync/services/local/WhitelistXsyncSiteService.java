package org.nrg.xsync.services.local;

import org.nrg.xapi.exceptions.DataFormatException;
import org.nrg.xapi.exceptions.NotFoundException;
import org.nrg.xsync.pojo.WhitelistSitePojo;

import java.util.List;

public interface WhitelistXsyncSiteService {

    List<WhitelistSitePojo> getAllWhitelistedSites();

    void addWhiteListSitesFromJson(List<WhitelistSitePojo> whitelistSitePojos);

    List<WhitelistSitePojo> addOrUpdateWhitelistSiteFromSiteAdmin(WhitelistSitePojo whitelistSitePojo) throws DataFormatException;

    List<WhitelistSitePojo> deleteWhitelistSiteFromSiteAdmin(WhitelistSitePojo whitelistSitePojo) throws NotFoundException;
}
