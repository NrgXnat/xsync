package org.nrg.xsync.services.local;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.nrg.xapi.exceptions.NotFoundException;
import org.nrg.xdat.om.XsyncXsyncprojectdata;
import org.nrg.xft.security.UserI;
import org.nrg.xsync.manifest.history.XsyncProjectHistory;
import org.nrg.xsync.pojo.WhitelistSitePojo;
import org.nrg.xsync.pojo.XsyncDashboardProjectConfigurationPojo;
import org.nrg.xsync.pojo.XsyncRemoteUrlDetailsPojo;
import org.nrg.xsync.pojo.configuration.SyncConfigurationPojo;

import java.util.List;

public interface XsyncConfigurationService {

    List<XsyncRemoteUrlDetailsPojo> createListOfRemoteDestinations(UserI user,
              List<XsyncProjectHistory> allHistoryItems, boolean whitelistEnabled, List<WhitelistSitePojo> whitelist);

    List<XsyncDashboardProjectConfigurationPojo> getAllProjectConnectionsForUrl(UserI user,
              List<XsyncProjectHistory> allHistoryItems, String inputUrl);

    List<XsyncRemoteUrlDetailsPojo> getAllNonWhitelistRemoteUrls(UserI user, List<WhitelistSitePojo> whitelist,
             List<XsyncProjectHistory> allHistoryItems);

    List<XsyncXsyncprojectdata> getAllProjectsSetToBeSynced(UserI user);

    List<XsyncXsyncprojectdata> getAllProjectsForRemoteUrl(UserI user, String inputUrl);

    List<XsyncXsyncprojectdata> getAllProjectsToBeSyncedDaily(UserI user);

    List<XsyncXsyncprojectdata> getAllProjectsToBeSyncedWeekly(UserI user);

    List<XsyncXsyncprojectdata> getAllProjectsToBeSyncedMonthly(UserI user);

    List<XsyncXsyncprojectdata> getAllProjectsToBeSyncedOnDemand(UserI user);

    SyncConfigurationPojo getSyncConfiguration(String projectId) throws JsonProcessingException, NotFoundException;

    void saveConfig(UserI user, SyncConfigurationPojo configurationPojo, String projectId) throws Exception;

    void changeConnectionEnabled(UserI user, String inputUrl, String projectId, boolean enabled) throws Exception;
}
