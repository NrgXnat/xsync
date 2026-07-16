package org.nrg.xsync.local;

import lombok.extern.slf4j.Slf4j;
import org.nrg.framework.services.SerializerService;
import org.nrg.xdat.XDAT;
import org.nrg.xdat.model.XnatAbstractresourceI;
import org.nrg.xdat.model.XnatSubjectdataI;
import org.nrg.xdat.om.XnatAbstractresource;
import org.nrg.xdat.om.XnatProjectdata;
import org.nrg.xdat.om.XnatSubjectdata;
import org.nrg.xft.security.UserI;
import org.nrg.xnat.xsync.remote.verify.XsyncProjectVerifier;
import org.nrg.xsync.components.XsyncSitePreferencesBean;
import org.nrg.xsync.configuration.ProjectSyncConfiguration;
import org.nrg.xsync.connection.RemoteConnection;
import org.nrg.xsync.connection.RemoteConnectionHandler;
import org.nrg.xsync.connection.RemoteConnectionManager;
import org.nrg.xsync.connection.RemoteConnectionResponse;
import org.nrg.xsync.manifest.ResourceSyncItem;
import org.nrg.xsync.manifest.SubjectSyncItem;
import org.nrg.xsync.remote.alias.services.SyncStatusService;
import org.nrg.xsync.tools.XSyncTools;
import org.nrg.xsync.tools.XsyncXnatInfo;
import org.nrg.xsync.utils.QueryResultUtil;
import org.nrg.xsync.utils.ResourceUtils;
import org.nrg.xsync.utils.XsyncFileUtils;
import org.nrg.xsync.utils.XsyncUtils;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class ResourceSyncManager {

    boolean syncAllStates;
    XnatSubjectdataI localSubject;
    ProjectSyncConfiguration projectSyncConfiguration;
    UserI user;
    SubjectSyncItem subjectSyncInfo ;
    private final XsyncXnatInfo xnatInfo;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final RemoteConnectionManager manager;
    private final QueryResultUtil queryResultUtil;
    private List<XnatAbstractresource> subjectResourcesToBeVerified = new ArrayList<XnatAbstractresource>();
    private XnatProjectdata localProject;
    private final SerializerService serializer;
    private final SyncStatusService syncStatusService;


    public ResourceSyncManager(final RemoteConnectionManager manager, final XsyncXnatInfo xnatInfo, final QueryResultUtil queryResultUtil,
                         final NamedParameterJdbcTemplate jdbcTemplate, XnatSubjectdataI localSubject, ProjectSyncConfiguration projectSyncConfiguration,
                         UserI user, boolean syncAll, SubjectSyncItem  subjectSyncItem, SerializerService serializer, SyncStatusService syncStatusService) {
        this.localSubject = localSubject;
        this.user = user;
        this.projectSyncConfiguration = projectSyncConfiguration;
        this.syncAllStates = syncAll;
        subjectSyncInfo = subjectSyncItem;
        this.jdbcTemplate = jdbcTemplate;
        this.manager = manager;
        this.xnatInfo = xnatInfo;
        this.queryResultUtil = queryResultUtil;
        localProject = XnatProjectdata.getXnatProjectdatasById(localSubject.getProject(), user, false);
        this.serializer = serializer;
        this.syncStatusService = syncStatusService;
    }

    /**
     * Syncs the resources of the subject
     * @param remoteSubject - The subject on the destination XNAT to which the resources are to be synced
     * @param resourcesToBeSynced - The resources to be synced
     * @throws Exception
     */

    public void syncResources(XnatSubjectdataI remoteSubject, Map<String, List<XnatAbstractresourceI>> resourcesToBeSynced) throws Exception{
        List<XnatAbstractresourceI> deletedResources = resourcesToBeSynced.get(QueryResultUtil.DELETE_STATUS);
        List<XnatAbstractresourceI> updatedResources = resourcesToBeSynced.get(QueryResultUtil.ACTIVE_STATUS);
        List<XnatAbstractresourceI> newResources = resourcesToBeSynced.get(QueryResultUtil.NEW_STATUS);
        if (deletedResources != null && !deletedResources.isEmpty()) {
            //Remove each of these resources from the Remote site
            if (syncAllStates) {
                for (XnatAbstractresourceI resource:deletedResources) {
                    try {
                        RemoteConnectionResponse deleteResponse = this.deleteSubjectResource(remoteSubject,resource.getLabel());
                        ResourceSyncItem resourceSyncItem = new ResourceSyncItem(localSubject.getLabel(),resource.getLabel());
                        if (deleteResponse.wasSuccessful()) {
                            resourceSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_DELETED);
                            resourceSyncItem.setMessage("Subject " + localSubject.getLabel() + " resource " + resource.getLabel() + " deleted ");
                            XSyncTools xsyncTools = new XSyncTools(user, jdbcTemplate, queryResultUtil);
                            xsyncTools.deleteXsyncRemoteEntry(localSubject.getId(), resource.getLabel());
                        }else {
                            resourceSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_FAILED);
                            resourceSyncItem.setMessage("Subject " + localSubject.getLabel() + " resource " + resource.getLabel() + " could not be deleted. " + deleteResponse.getResponseBody());
                        }
                        subjectSyncInfo.addResources(resourceSyncItem);
                    }catch(Exception e) {
                        log.error("Could not delete resource {} for subject {}", resource.getLabel(), remoteSubject.getId(), e);
                        ResourceSyncItem resourceSyncItem = new ResourceSyncItem(localSubject.getLabel(),resource.getLabel());
                        resourceSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_FAILED);
                        resourceSyncItem.setMessage("Subject " + localSubject.getLabel() + " resource " + resource.getLabel() + " could not be deleted. " + e.getMessage());
                        subjectSyncInfo.addResources(resourceSyncItem);
                    }
                }
            }else {
                for (XnatAbstractresourceI resource:deletedResources) {
                    ResourceSyncItem resourceSyncItem = new ResourceSyncItem(localSubject.getLabel(),resource.getLabel());
                    resourceSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_SKIPPED);
                    resourceSyncItem.setMessage("Subject " + localSubject.getLabel() + " resource " + resource.getLabel() + " deleted locally, however, not deleted from remote site. ");
                    subjectSyncInfo.addResources(resourceSyncItem);
                }
            }
        }
        //Store the updated or the new resources
        if (syncAllStates){
            for (XnatAbstractresourceI resource:updatedResources) {
                pushResourceInParts(remoteSubject,resource);
            }
            for (XnatAbstractresourceI resource:newResources) {
                pushResourceInParts(remoteSubject,resource);
            }
        }else { //Only New
            for (XnatAbstractresourceI resource:newResources) {
                pushResourceInParts(remoteSubject,resource);
            }
        }
    }

    /**
     * Verify if the resources on the local XNAT have been synced on the remote XNAT for a subject
     * @param remoteSubjectId - The subject on the destination XNAT whose resources have to be verified
     */

    public void verifySubjectResources(String remoteSubjectId) {
        ResourceUtils resourceUtils = new ResourceUtils(projectSyncConfiguration);
        String remoteProjectId = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteProjectId();
        final String remoteUrl = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl();

        XsyncProjectVerifier projectResourceVerifier = new XsyncProjectVerifier(manager, queryResultUtil, jdbcTemplate, projectSyncConfiguration, serializer);
        String localProjectArchivePath = localProject.getArchiveRootPath();

        for (XnatAbstractresource rsc:subjectResourcesToBeVerified) {
            String rLabel = rsc.getLabel() == null ?  XsyncUtils.RESOURCE_NO_LABEL:rsc.getLabel();
            ResourceSyncItem resourceSyncItem = new ResourceSyncItem(localProject.getId(), rLabel);
            if (rsc.getFileCount() != null) {
                resourceSyncItem.setFileCount(rsc.getFileCount());
            }
            if (rsc.getFileSize() != null) {
                resourceSyncItem.setFileSize(rsc.getFileSize());
            }
            String archiveDirectory = rsc.getFullPath(localProjectArchivePath);
            final String uri = remoteUrl+"/data/archive/projects/"+remoteProjectId+"/subjects/"+ remoteSubjectId  + "/resources/"+rsc.getLabel() + "/files?format=JSON";
            Map<String,String> fileComparison;
            try {
                fileComparison = projectResourceVerifier.verify(archiveDirectory, remoteProjectId , rsc.getLabel(), uri);
            }catch(Exception e) {
                log.error("Could not verify remote subject {} resources", remoteSubjectId, e);
                fileComparison = new HashMap<String,String>();
                fileComparison.put(XsyncUtils.XSYNC_VERIFICATION_STATUS, XsyncUtils.XSYNC_VERIFICATION_STATUS_FAILED_TO_CONNECT);
            }
            resourceUtils.setSyncStatus(fileComparison, resourceSyncItem, "Subject " + localSubject.getLabel() + "  resource " + rLabel);
            subjectSyncInfo.addResources(resourceSyncItem);
        }
    }

    private RemoteConnectionResponse deleteSubjectResource(XnatSubjectdataI remoteSubject, String resourceLabel) throws Exception {
        try {
            RemoteConnectionHandler remoteConnectionHandler = new RemoteConnectionHandler(jdbcTemplate, queryResultUtil);
            RemoteConnection connection = remoteConnectionHandler.getConnection(projectSyncConfiguration.getProject().getId(),projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl());
            return manager.deleteSubjectResource(connection, (XnatSubjectdata)remoteSubject, resourceLabel);
        }catch(Exception e) {
            log.error("Error deleting remote subject {} resource {}", remoteSubject.getLabel(), resourceLabel, e);
            throw e;
        }
    }

    private RemoteConnectionResponse updateSubjectResource(XnatSubjectdataI remoteSubject, String resourceLabel, File zipFile, final boolean updateStats) throws Exception {
        try {
            RemoteConnectionHandler remoteConnectionHandler = new RemoteConnectionHandler(jdbcTemplate, queryResultUtil);
            RemoteConnection connection = remoteConnectionHandler.getConnection(projectSyncConfiguration.getProject().getId(),projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl());
            return manager.importSubjectResource(connection, (XnatSubjectdata)remoteSubject, resourceLabel, zipFile, updateStats);
        }catch(Exception e) {
            log.error("Error updating remote subject {} resource {}", remoteSubject.getLabel(), resourceLabel, e);
            throw e;
        }
    }

    private void pushResourceInParts(XnatSubjectdataI remoteSubject, XnatAbstractresourceI resource) {
        Instant start = Instant.now();
        log.info("Preparing to send : " + resource.getLabel());
        String localProjectArchivePath = localProject.getArchiveRootPath();
        String remoteProjectId = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteProjectId();
        String rLabel = resource.getLabel() == null ? XsyncUtils.RESOURCE_NO_LABEL:resource.getLabel();
        final XsyncSitePreferencesBean syncPrefsBean = XDAT.getContextService().getBeanSafely(XsyncSitePreferencesBean.class);
        String archiveDirectory = ((XnatAbstractresource)resource).getFullPath(localProjectArchivePath);
        File resourcePath = new File(archiveDirectory);
        if (resourcePath.exists() && resourcePath.isFile()) {
            resourcePath = resourcePath.getParentFile();
        }

        try {
            List<File> zipFiles = new XsyncFileUtils().buildMultipleZips(remoteProjectId, resourcePath, syncPrefsBean.getSyncMaxUncompressedZipFileSizeAsLong());
            Instant zipBuildTime = Instant.now();
            Duration timeElapsed = Duration.between(start, zipBuildTime);
            log.info("Time taken to build {} zips: {}  milliseconds", zipFiles.size(), timeElapsed.toMillis());

            boolean allPartsSentSuccessfully = true;
            StringBuilder responses = new StringBuilder();
            log.info("Total parts for {} to be synced  is {}", resourcePath, zipFiles.size());
            int counter = 0;
            for (File zipFile : zipFiles) {
                try {
                    ++counter;
                    Instant zipSendBeginTime = Instant.now();
                    RemoteConnectionResponse updateResponse = this.updateSubjectResource(remoteSubject,resource.getLabel(), zipFile, (counter == zipFiles.size()));
                    Instant zipSendEndTime = Instant.now();
                    timeElapsed = Duration.between(zipSendBeginTime, zipSendEndTime);
                    log.info("Time taken to send {} : {} milliseconds", zipFile.getName(), timeElapsed.toMillis());
                    allPartsSentSuccessfully = allPartsSentSuccessfully && updateResponse.wasSuccessful();
                    responses.append(updateResponse.getResponseBody()).append(XSyncTools.NEWLINE);
                } catch (Exception e) {
                    log.error("Push resource encountered {} while pushing part {} for resource  {} ", e.getMessage(), zipFile.getName(), resource.getLabel());
                    throw(e);
                } finally {
                    if (zipFile != null) zipFile.delete();
                }
            }

            if (allPartsSentSuccessfully) {
                try{
                    Thread.sleep(XsyncUtils.THREAD_SLEEP_TIME); //2min Sleep so that the server side catalog refresh is done
                } catch (InterruptedException ignore) {}
                subjectResourcesToBeVerified.add((XnatAbstractresource)resource);
            }else {
                ResourceSyncItem resourceSyncItem = new ResourceSyncItem(localSubject.getLabel(), rLabel);
                if (resource.getFileCount() != null) {
                    resourceSyncItem.setFileCount(resource.getFileCount());
                } else {
                    resourceSyncItem.setFileCount(0);
                }
                if (resource.getFileSize() != null) {
                    resourceSyncItem.setFileSize(resource.getFileSize());
                } else {
                    resourceSyncItem.setFileSize(0L);
                }
                resourceSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_FAILED);
                resourceSyncItem.setMessage("Subject " + localSubject.getLabel() + " resource " + rLabel + " could not be updated. " + responses.toString() );
                subjectSyncInfo.addResources(resourceSyncItem);
            }
        } catch (Exception e) {
            log.error("Could not update resource {} for subject {}", resource.getLabel(), remoteSubject.getId(), e);
            ResourceSyncItem resourceSyncItem = new ResourceSyncItem(localSubject.getLabel(),rLabel);
            if (resource.getFileCount() != null) {
                resourceSyncItem.setFileCount(resource.getFileCount());
            } else {
                resourceSyncItem.setFileCount(0);
            }
            if (resource.getFileSize() != null) {
                resourceSyncItem.setFileSize(resource.getFileSize());
            } else {
                resourceSyncItem.setFileSize(0L);
            }
            resourceSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_FAILED);
            resourceSyncItem.setMessage("Subject " + localSubject.getLabel() + " resource " + rLabel + " could not be updated. " + e.getMessage() );
            subjectSyncInfo.addResources(resourceSyncItem);
        }
    }


}
