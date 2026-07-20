package org.nrg.xsync.services.local.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.nrg.config.entities.Configuration;
import org.nrg.config.exceptions.ConfigServiceException;
import org.nrg.config.services.ConfigService;
import org.nrg.framework.constants.Scope;
import org.nrg.xapi.exceptions.DataFormatException;
import org.nrg.xapi.exceptions.NotFoundException;
import org.nrg.xdat.om.XsyncXsyncprojectdata;
import org.nrg.xft.event.EventDetails;
import org.nrg.xft.event.EventMetaI;
import org.nrg.xft.event.EventUtils;
import org.nrg.xft.event.persist.PersistentWorkflowI;
import org.nrg.xft.event.persist.PersistentWorkflowUtils;
import org.nrg.xft.security.UserI;
import org.nrg.xft.utils.SaveItemHelper;
import org.nrg.xft.utils.ValidationUtils.ValidationResults;
import org.nrg.xnat.utils.WorkflowUtils;
import org.nrg.xsync.manifest.history.XsyncProjectHistory;
import org.nrg.xsync.pojo.WhitelistSitePojo;
import org.nrg.xsync.pojo.XsyncDashboardProjectConfigurationPojo;
import org.nrg.xsync.pojo.XsyncRemoteUrlDetailsPojo;
import org.nrg.xsync.pojo.configuration.SyncConfigurationPojo;
import org.nrg.xsync.services.local.XsyncConfigurationService;
import org.nrg.xsync.utils.XsyncUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class XsyncConfigurationServiceImpl implements XsyncConfigurationService {

    @Autowired
    public XsyncConfigurationServiceImpl(ConfigService configService) {
        this.configService = configService;
    }

    @Override
    public List<XsyncRemoteUrlDetailsPojo> createListOfRemoteDestinations(UserI user,
              List<XsyncProjectHistory> allHistoryItems, boolean whitelistEnabled, List<WhitelistSitePojo> whitelist) {
        List<XsyncXsyncprojectdata> xsyncProjectDataList =
                XsyncXsyncprojectdata.getAllXsyncXsyncprojectdatas(user, true);
        return createRemoteUrlList(xsyncProjectDataList, allHistoryItems, whitelistEnabled, whitelist);
    }

    @Override
    public List<XsyncDashboardProjectConfigurationPojo> getAllProjectConnectionsForUrl(UserI user,
                       List<XsyncProjectHistory> allHistoryItems, String inputUrl) {
        List<XsyncXsyncprojectdata> elementsWithCorrectUrl = getAllProjectsForRemoteUrl(user, inputUrl);
        List<XsyncProjectHistory> historyItemsForRemoteHost =
                allHistoryItems.stream().filter(h -> h.getRemoteHost().equals(inputUrl)).toList();

        List<XsyncDashboardProjectConfigurationPojo> configurationPojos = new ArrayList<>();
        for (XsyncXsyncprojectdata element : elementsWithCorrectUrl) {
            XsyncDashboardProjectConfigurationPojo pojo = new XsyncDashboardProjectConfigurationPojo();
            pojo.setLocalProject(element.getSourceProjectId());
            pojo.setRemoteProject(element.getSyncinfo().getRemoteProjectId());
            pojo.setFrequency(element.getSyncinfo().getSyncFrequency());
            pojo.setStatus(element.getSyncEnabled().toString());
            Optional<XsyncProjectHistory> optionalHistory =
                    historyItemsForRemoteHost.stream().filter(h -> h.getLocalProject()
                    .equals(element.getSourceProjectId()))
                    .reduce((a,b) -> a.getTimestamp().after(b.getTimestamp()) ? a:b);
            if (optionalHistory.isPresent()) {
                pojo.setLastSyncStatus(optionalHistory.get().getSyncStatus());
            } else {
                pojo.setLastSyncStatus("Never Synced");
            }
            configurationPojos.add(pojo);
        }
        return configurationPojos;
    }

    @Override
    public List<XsyncRemoteUrlDetailsPojo> getAllNonWhitelistRemoteUrls(UserI user, List<WhitelistSitePojo> whitelist,
                        List<XsyncProjectHistory> allHistoryItems) {
        List<String> allValidSiteUrls = whitelist.stream().map(WhitelistSitePojo::getSiteUrl).toList();
        List<XsyncXsyncprojectdata> nonConformingConfigs =
                XsyncXsyncprojectdata.getAllXsyncXsyncprojectdatas(user, true).stream()
                .filter(x -> !allValidSiteUrls.contains(x.getSyncinfo().getRemoteUrl())).toList();
        return createRemoteUrlList(nonConformingConfigs, allHistoryItems, true, whitelist);
    }

    private List<XsyncRemoteUrlDetailsPojo> createRemoteUrlList(List<XsyncXsyncprojectdata> xsyncProjectDataList,
            List<XsyncProjectHistory> allHistoryItems, boolean whitelistEnabled, List<WhitelistSitePojo> whitelist) {
        Map<String, List<XsyncProjectHistory>> allHistoryMap =
                allHistoryItems.stream().collect(Collectors.groupingBy(XsyncProjectHistory::getLocalProject));

        Map<String, XsyncRemoteUrlDetailsPojo> configurationsMap = new HashMap<>();
        for (XsyncXsyncprojectdata configuration : xsyncProjectDataList) {
            String remoteUrl = configuration.getSyncinfo().getRemoteUrl();
            XsyncRemoteUrlDetailsPojo currentPojo;
            if (configurationsMap.containsKey(remoteUrl)) {
                currentPojo = configurationsMap.get(remoteUrl);
                currentPojo.setNumberProjects(currentPojo.getNumberProjects()+1);
            } else {
                currentPojo = new XsyncRemoteUrlDetailsPojo();
                currentPojo.setRemoteUrl(remoteUrl);
                currentPojo.setNumberProjects(1);
                currentPojo.setNumberErrors(0);
                if (checkForWhitelistConformation(whitelistEnabled, whitelist, remoteUrl)) {
                   whitelist.stream().filter(wl -> wl.getSiteUrl().equals(remoteUrl)).findAny().ifPresent(wl ->{
                             currentPojo.setSiteName(wl.getSiteName());
                             currentPojo.setClassification(wl.getClassification());
                    });
                } else {
                    currentPojo.setSiteName("NOT ON WHITELIST");
                }
            }
            String sourceProjectId = configuration.getSourceProjectId();
            if (allHistoryMap.containsKey(sourceProjectId)) {
                List<XsyncProjectHistory> singleProjectHistoryElements = allHistoryMap.get(sourceProjectId);
                Optional<XsyncProjectHistory> optionalProjHistory = singleProjectHistoryElements.stream()
                        .reduce((a,b) -> a.getTimestamp().after(b.getTimestamp()) ? a:b);
                if (optionalProjHistory.isPresent()) {
                    XsyncProjectHistory latestHistoryEntryForProject = optionalProjHistory.get();
                    if (latestHistoryEntryForProject.getSyncStatus().toLowerCase().contains("fail")) {
                        currentPojo.setNumberErrors(currentPojo.getNumberErrors()+1);
                    }
                }
            }
            configurationsMap.put(remoteUrl, currentPojo);
        }
        return configurationsMap.values().stream().toList();
    }

    @Override
    public boolean checkForWhitelistConformation(boolean whitelistEnabled, List<WhitelistSitePojo> whitelist, String url) {
        return whitelistEnabled && whitelist.stream().map(WhitelistSitePojo::getSiteUrl).toList().contains(url);
    }

    @Override
    public List<XsyncXsyncprojectdata> getAllProjectsSetToBeSynced(UserI user) {
        return XsyncXsyncprojectdata.getAllXsyncXsyncprojectdatas(user, true);
    }

    @Override
    public List<XsyncXsyncprojectdata> getAllProjectsForRemoteUrl(UserI user, String inputUrl) {
        return getAllProjectsSetToBeSynced(user).stream().filter(x -> x.getSyncinfo().getRemoteUrl()
                .equals(inputUrl)).toList();
    }

    @Override
    public List<XsyncXsyncprojectdata> getAllProjectsToBeSyncedDaily(UserI user) {
        return getAllProjectsWithSpecificFrequency(user, XsyncUtils.SYNC_FREQUENCY_DAILY);
    }

    @Override
    public List<XsyncXsyncprojectdata> getAllProjectsToBeSyncedWeekly(UserI user) {
        return getAllProjectsWithSpecificFrequency(user, XsyncUtils.SYNC_FREQUENCY_WEEKLY);
    }

    @Override
    public List<XsyncXsyncprojectdata> getAllProjectsToBeSyncedMonthly(UserI user) {
        return getAllProjectsWithSpecificFrequency(user, XsyncUtils.SYNC_FREQUENCY_MONTHLY);
    }

    @Override
    public List<XsyncXsyncprojectdata> getAllProjectsToBeSyncedOnDemand(UserI user) {
        return getAllProjectsWithSpecificFrequency(user, XsyncUtils.SYNC_FREQUENCY_ON_DEMAND);
    }

    @Override
    public Configuration getGenericXsyncConfiguration(String type, String projectId) {
        return configService.getConfig("xsync", type, Scope.Project, projectId);
    }

    @Override
    public SyncConfigurationPojo getSyncConfiguration(String projectId) throws IOException, NotFoundException {
        final Configuration conf = getGenericXsyncConfiguration("json", projectId);
        final String config = conf != null ? conf.getContents() : null;
        ObjectMapper objectMapper = new ObjectMapper();
        if (StringUtils.isNotBlank(config)) {
            return objectMapper.readValue(config, SyncConfigurationPojo.class);
        } else {
            throw new NotFoundException("Could not find configuration for project: {}", projectId);
        }
    }

    @Override
    public Configuration replaceConfiguration(String username, String type, String inputElement, String projectId) throws ConfigServiceException {
        return configService.replaceConfig(username, "", "xsync", type, inputElement, Scope.Project, projectId);
    }

    @Override
    public void saveConfig(UserI user, SyncConfigurationPojo configurationPojo, String projectId) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String serializedConfig = mapper.writeValueAsString(configurationPojo);
        Configuration newConfiguration = replaceConfiguration(user.getUsername(), "json", serializedConfig, projectId);
        List<Configuration> allConfigurationsRemove = configService.getAll().stream()
                .filter(c -> c.getTool().equals("xsync"))
                .filter(c-> c.getScope().equals(Scope.Project))
                .filter(c -> c.getEntityId().equals(configurationPojo.getSource_project_id()))
                .filter(c -> c.getId() != newConfiguration.getId()).toList();
        for (Configuration config : allConfigurationsRemove) {
            configService.delete(config);
        }
    }

    @Override
    public void changeConnectionEnabled(UserI user, String inputUrl, String projectId, boolean enabled) throws Exception {
        Optional<XsyncXsyncprojectdata> projectConfigurationElement =
                getAllProjectsForRemoteUrl(user, inputUrl).stream()
                .filter(x -> x.getSourceProjectId().equals(projectId)).findFirst();
        if (projectConfigurationElement.isPresent()) {
            XsyncXsyncprojectdata projectConfiguration = projectConfigurationElement.get();
            projectConfiguration.setSyncEnabled(enabled);
            projectConfiguration.setSyncScheduledBy(user.getLogin());
            final ValidationResults vr = projectConfiguration.validate();
            if (vr != null && !vr.isValid()) {
                throw new DataFormatException(projectId + " Xsync Setup failed. Invalid JSON: " + vr.isValid());
            }
            String msg = "Updated Synchronization";
            EventMetaI c = EventUtils.DEFAULT_EVENT(user, msg);

            if (SaveItemHelper.authorizedSave(projectConfiguration, user, false, true, c)) {
                EventDetails details = EventUtils.newEventInstance(EventUtils.CATEGORY.DATA, EventUtils.TYPE.WEB_SERVICE,
                                       EventUtils.getAddModifyAction(projectConfiguration.getXSIType(), false),
                                       "", "");
                PersistentWorkflowI wrk = PersistentWorkflowUtils.buildOpenWorkflow(user,
                    projectConfiguration.getXSIType(), projectConfiguration.getXsyncXsyncprojectdataId()+"",
                    projectConfiguration.getSourceProjectId(), details);
                WorkflowUtils.complete(wrk, c);
            }

            SyncConfigurationPojo configServicePojo = getSyncConfiguration(projectId);
            configServicePojo.setEnabled(enabled);
            saveConfig(user, configServicePojo, projectId);
        } else {
            throw new NotFoundException("Could not find configuration for project: {}", projectId);
        }
    }

    private List<XsyncXsyncprojectdata> getAllProjectsWithSpecificFrequency(UserI user, String syncFrequency) {
        return getAllProjectsSetToBeSynced(user).stream().filter(x -> x.getSyncinfo().getSyncFrequency()
                .equals(syncFrequency)).toList();
    }

    private final ConfigService configService;
}
