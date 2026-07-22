package org.nrg.xsync.services.local;

import org.nrg.config.entities.Configuration;
import org.nrg.config.exceptions.ConfigServiceException;
import org.nrg.xapi.exceptions.NotFoundException;
import org.nrg.xdat.om.XsyncXsyncprojectdata;
import org.nrg.xft.security.UserI;
import org.nrg.xsync.manifest.history.XsyncProjectHistory;
import org.nrg.xsync.pojo.WhitelistSitePojo;
import org.nrg.xsync.pojo.XsyncDashboardProjectConfigurationPojo;
import org.nrg.xsync.pojo.XsyncRemoteUrlDetailsPojo;
import org.nrg.xsync.pojo.configuration.SyncConfigurationPojo;

import java.io.IOException;
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

    boolean checkForWhitelistConformation(boolean whitelistEnabled, List<WhitelistSitePojo> whitelist, String url);

    List<XsyncXsyncprojectdata> getAllProjectsToBeSyncedDaily(UserI user);

    List<XsyncXsyncprojectdata> getAllProjectsToBeSyncedWeekly(UserI user);

    List<XsyncXsyncprojectdata> getAllProjectsToBeSyncedMonthly(UserI user);

    List<XsyncXsyncprojectdata> getAllProjectsToBeSyncedOnDemand(UserI user);

    Configuration getGenericXsyncConfiguration(String type, String projectId);

    Configuration replaceConfiguration(String username, String type, String inputElement, String projectId) throws ConfigServiceException;

    SyncConfigurationPojo getSyncConfiguration(String projectId) throws IOException, NotFoundException;

    void saveConfig(UserI user, SyncConfigurationPojo configurationPojo, String projectId) throws Exception;

    void changeEnabledForUrl(UserI user, String inputUrl, boolean enabled) throws Exception;

    void changeConnectionEnabled(UserI user, String inputUrl, String projectId, boolean enabled) throws Exception;
}
