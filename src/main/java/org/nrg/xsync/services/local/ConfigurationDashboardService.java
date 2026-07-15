package org.nrg.xsync.services.local;

import org.nrg.xft.security.UserI;
import org.nrg.xsync.manifest.XsyncProjectHistory;
import org.nrg.xsync.pojo.WhitelistSitePojo;
import org.nrg.xsync.pojo.XsyncDashboardProjectConfigurationPojo;
import org.nrg.xsync.pojo.XsyncRemoteUrlDetailsPojo;

import java.util.List;

public interface ConfigurationDashboardService {

    List<XsyncRemoteUrlDetailsPojo> createListOfRemoteDestinations(UserI user,
              List<XsyncProjectHistory> allHistoryItems, boolean whitelistEnabled, List<WhitelistSitePojo> whitelist);

    List<XsyncDashboardProjectConfigurationPojo> getAllProjectConnectionsForUrl(UserI user,
              List<XsyncProjectHistory> allHistoryItems, String inputUrl);
}
