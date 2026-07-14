package org.nrg.xsync.services.local.impl;

import lombok.extern.slf4j.Slf4j;
import org.nrg.xdat.om.XsyncXsyncprojectdata;
import org.nrg.xft.security.UserI;
import org.nrg.xsync.manifest.XsyncProjectHistory;
import org.nrg.xsync.pojo.WhitelistSitePojo;
import org.nrg.xsync.pojo.XsyncDashboardProjectConfigurationPojo;
import org.nrg.xsync.pojo.XsyncRemoteUrlDetailsPojo;
import org.nrg.xsync.services.local.ConfigurationDashboardService;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class ConfigurationDashboardServiceImpl implements ConfigurationDashboardService {

    public List<XsyncRemoteUrlDetailsPojo> createListOfRemoteDestinations(UserI user,
              List<XsyncProjectHistory> allHistoryItems, boolean whitelistEnabled, List<WhitelistSitePojo> whitelist) {
        List<XsyncXsyncprojectdata> xsyncProjectDataList =
                XsyncXsyncprojectdata.getAllXsyncXsyncprojectdatas(user, true);
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
                if (whitelistEnabled) {
                    WhitelistSitePojo whitelistElement = whitelist.stream()
                            .filter(wl -> wl.getSiteUrl().equals(remoteUrl)).findAny().get();
                    currentPojo.setSiteName(whitelistElement.getSiteName());
                    currentPojo.setClassification(whitelistElement.getClassification());
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
    public List<XsyncDashboardProjectConfigurationPojo> getAllProjectConnectionsForUrl(UserI user,
                       List<XsyncProjectHistory> allHistoryItems, String inputUrl) {
        List<XsyncXsyncprojectdata> xsyncProjectDataList =
                XsyncXsyncprojectdata.getAllXsyncXsyncprojectdatas(user, true);
        List<XsyncXsyncprojectdata> elementsWithCorrectUrl =
                xsyncProjectDataList.stream().filter(x -> x.getSyncinfo().getRemoteUrl().equals(inputUrl)).toList();

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
}
