package org.nrg.xsync.local;

import lombok.extern.slf4j.Slf4j;
import org.nrg.framework.services.SerializerService;
import org.nrg.xdat.base.BaseElement;
import org.nrg.xdat.model.XnatAbstractresourceI;
import org.nrg.xdat.model.XnatExperimentdataI;
import org.nrg.xdat.model.XnatSubjectdataI;
import org.nrg.xdat.om.XnatAbstractresource;
import org.nrg.xdat.om.XnatProjectdata;
import org.nrg.xdat.om.XnatSubjectdata;
import org.nrg.xft.XFTItem;
import org.nrg.xft.security.UserI;
import org.nrg.xsync.configuration.ProjectSyncConfiguration;
import org.nrg.xsync.connection.RemoteConnection;
import org.nrg.xsync.connection.RemoteConnectionHandler;
import org.nrg.xsync.connection.RemoteConnectionManager;
import org.nrg.xsync.connection.RemoteConnectionResponse;
import org.nrg.xsync.manager.SynchronizationManager;
import org.nrg.xsync.manifest.SubjectSyncItem;
import org.nrg.xsync.remote.alias.services.SyncStatusService;
import org.nrg.xsync.tools.XSyncTools;
import org.nrg.xsync.tools.XsyncObserver;
import org.nrg.xsync.tools.XsyncXnatInfo;
import org.nrg.xsync.utils.QueryResultUtil;
import org.nrg.xsync.utils.XSyncFailureHandler;
import org.nrg.xsync.utils.XsyncUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public class SubjectDataSync {

    public SubjectDataSync(final RemoteConnectionManager manager, final XsyncXnatInfo xnatInfo, final QueryResultUtil queryResultUtil,
                           final JdbcTemplate jdbcTemplate, XnatSubjectdataI localSubject, ProjectSyncConfiguration projectSyncConfiguration,
                           UserI user, boolean syncAll, XsyncObserver observer, SerializerService serializer, SyncStatusService syncStatusService) {
        this.localSubject = localSubject;
        this.user = user;
        this.projectSyncConfiguration = projectSyncConfiguration;
        this.syncAllStates = syncAll;
        subjectSyncInfo = new SubjectSyncItem(localSubject.getId(), localSubject.getLabel());
        subjectSyncInfo.addObserver(observer);
        this.jdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
        this.manager = manager;
        this.xnatInfo = xnatInfo;
        this.queryResultUtil = queryResultUtil;
        localProject = XnatProjectdata.getXnatProjectdatasById(localSubject.getProject(), user, false);
        this.serializer = serializer;
        this.syncStatusService = syncStatusService;
    }

    public SubjectDataSync(final RemoteConnectionManager manager, final XsyncXnatInfo xnatInfo, final QueryResultUtil queryResultUtil,
                           final NamedParameterJdbcTemplate jdbcTemplate, XnatSubjectdataI localSubject, ProjectSyncConfiguration projectSyncConfiguration,
                           UserI user, boolean syncAll, XsyncObserver observer, SerializerService serializer, SyncStatusService syncStatusService) {
        this.localSubject = localSubject;
        this.user = user;
        this.projectSyncConfiguration = projectSyncConfiguration;
        this.syncAllStates = syncAll;
        subjectSyncInfo = new SubjectSyncItem(localSubject.getId(), localSubject.getLabel());
        subjectSyncInfo.addObserver(observer);
        this.jdbcTemplate = jdbcTemplate;
        this.manager = manager;
        this.xnatInfo = xnatInfo;
        this.queryResultUtil = queryResultUtil;
        localProject = XnatProjectdata.getXnatProjectdatasById(localSubject.getProject(), user, false);
        this.serializer = serializer;
        this.syncStatusService = syncStatusService;
    }


   public SubjectDataSync(final RemoteConnectionManager manager, final XsyncXnatInfo xnatInfo, final QueryResultUtil queryResultUtil,
                           final NamedParameterJdbcTemplate jdbcTemplate, XnatSubjectdataI localSubject, ProjectSyncConfiguration projectSyncConfiguration,
                           UserI user, boolean syncAll, final SubjectSyncItem subjectSyncItem, SerializerService serializer, SyncStatusService syncStatusService) {
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
     * Sync the subject identified by the field localSubject
     * @param syncExperimentsAlso - boolean: should the experiments of the subject be also synced or only the subject metadat/resources
     * @throws Exception
     */

    public void sync(boolean syncExperimentsAlso) throws Exception{
        log.debug("Syncing subject BEGIN: " + localSubject.getLabel());
        XFTItem item = ((XnatSubjectdata)localSubject).getItem().copy();
        String remoteProjectId = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteProjectId();

        XnatSubjectdata newSubject = (XnatSubjectdata) BaseElement.GetGeneratedItem(item);
        newSubject.setProject(remoteProjectId);
        IdMapper idMapper = new IdMapper(manager, queryResultUtil, jdbcTemplate, user, projectSyncConfiguration);
        String subject_remote_id;
        subjectSyncInfo.setSyncStatus(XsyncUtils.SYNC_STATUS_BEGINNING);
        subjectSyncInfo.stateChanged();
        ResourceSyncManager resourceSyncManager = new ResourceSyncManager(
                manager, xnatInfo,queryResultUtil,
                jdbcTemplate,  localSubject,  projectSyncConfiguration,
                user, syncAllStates, subjectSyncInfo,  serializer, syncStatusService
        );
        BatchExperimentSync batchExperimentSync = new BatchExperimentSync(
                manager, xnatInfo,queryResultUtil,
                jdbcTemplate,  localSubject,  projectSyncConfiguration,
                user, syncAllStates, subjectSyncInfo,  serializer, syncStatusService
        );

        try {

            idMapper.correctIDandLabel(newSubject);

            //Go through resources; if they are in config and modified since last sync, keep them
            ResourceFilter resourceMapper = new ResourceFilter(user, jdbcTemplate, queryResultUtil);
            Map<String,List<XnatAbstractresourceI>> resourcesToBeSynced = resourceMapper.select(newSubject, localSubject.getId(), projectSyncConfiguration);

            ExperimentFilter experimentMapper = new ExperimentFilter(manager, jdbcTemplate, xnatInfo, queryResultUtil, user, projectSyncConfiguration);
            Map<String,List<XnatExperimentdataI>> experimentsToBeSynced = experimentMapper.select(newSubject, localSubject.getId(), localSubject.getProject());

            //Store the subject
            //Get its remote id
            //Store the remote id

            subject_remote_id=storeSubject(newSubject);
            if (subject_remote_id != null) {
                newSubject.setId(subject_remote_id);
                saveSyncDetails(localSubject.getId(),subject_remote_id,newSubject.getLabel(), XsyncUtils.SYNC_STATUS_SYNCED_AND_NOT_VERIFIED,localSubject.getXSIType());

                //Now among the ones which are configured and not deleted
                //Change the ids
                //Check the ImagingSessions
                //   for Scans configured
                //   for Resources configured
                //   for ImageAssessors configured
                //   Anonymize the resources
                resourceSyncManager.syncResources(newSubject,resourcesToBeSynced);
                if (syncExperimentsAlso) {
                    //Go through experiments; if they are in config, keep them
                    batchExperimentSync.syncExperiments(newSubject, experimentsToBeSynced);
                }
//				subjectSyncInfo.stateChanged();
            }
        }catch(Exception e) {
            log.error("Error syncing subject {}", newSubject.getLabel(), e);
            XSyncFailureHandler.handle(localSubject.getProject(),localSubject.getId(),localSubject.getXSIType(),idMapper.getRemoteAccessionId(this.localSubject.getId()), subjectSyncInfo, e);
            throw e;
        }
        resourceSyncManager.verifySubjectResources(subject_remote_id);
        subjectSyncInfo.updateSyncStatus(XsyncUtils.SYNC_STATUS_SYNCED_AND_NOT_VERIFIED,"Subject " + localSubject.getLabel() );
        subjectSyncInfo.stateChanged();
        SynchronizationManager.UPDATE_MANIFEST(localSubject.getProject(), subjectSyncInfo);
        log.debug("Syncing subject END: {}", localSubject.getLabel());
    }


    //TODO
    //This is ugly - we have two methods, sync and syncSubject; almost doing the same things.
    //Fix this in the next revision of code - MR - 3/11/2022
    public XnatSubjectdata syncSubject() throws Exception {
        log.debug("Syncing subject BEGIN: {}", localSubject.getLabel());
        final XFTItem item = ((XnatSubjectdata)localSubject).getItem().copy();
        XnatSubjectdata newSubject = (XnatSubjectdata) BaseElement.GetGeneratedItem(item);
        final String remoteProjectId = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteProjectId();
        newSubject.setProject(remoteProjectId);

        final IdMapper idMapper = new IdMapper(manager, queryResultUtil, jdbcTemplate, user, projectSyncConfiguration);
        idMapper.correctIDandLabel(newSubject);

        // Remove assessors from the item.  They will be synced separately.
        // This is the easiest way to remove assessors from the subject bean
        final XFTItem newItem = newSubject.getItem();
        final List<XFTItem> subjAssessors  = newItem.getChildrenOfType("xnat:subjectAssessorData", true);
        for (final XFTItem subjAssessor : subjAssessors) {
            newItem.removeItem(subjAssessor);
        }
        newSubject = (XnatSubjectdata) BaseElement.GetGeneratedItem(item);

        //Go through resources; if they are in config and modified since last sync, keep them
        final ResourceFilter resourceMapper = new ResourceFilter(user, jdbcTemplate, queryResultUtil);
        final Map<String,List<XnatAbstractresourceI>> resourcesToBeSynced = resourceMapper.select(newSubject, localSubject.getId(), projectSyncConfiguration);

        //Store the subject
        //Get its remote id
        //Store the remote id

        final String subject_remote_id=storeSubject(newSubject);
        if (subject_remote_id != null) {
            newSubject.setId(subject_remote_id);
            saveSyncDetails(localSubject.getId(),subject_remote_id,newSubject.getLabel(), XsyncUtils.SYNC_STATUS_SYNCED_AND_NOT_VERIFIED,localSubject.getXSIType());

            //Now among the ones which are configured and not deleted
            //Change the ids
            //Check the ImagingSessions
            //   for Scans configured
            //   for Resources configured
            //   for ImageAssessors configured
            //   Anonymize the resources
            ResourceSyncManager resourceSyncManager = new ResourceSyncManager(
                    manager, xnatInfo,queryResultUtil,
                    jdbcTemplate,  localSubject,  projectSyncConfiguration,
                    user, syncAllStates, subjectSyncInfo,  serializer, syncStatusService
            );
            resourceSyncManager.syncResources(newSubject,resourcesToBeSynced);
            resourceSyncManager.verifySubjectResources(subject_remote_id);
        }else {
            newSubject.setId(null);
        }
        log.debug("Syncing subject END: {}", localSubject.getLabel());
        return newSubject;
    }

    private String storeSubject(XnatSubjectdataI remoteSubject) throws Exception {
        //October 3, 2016 - MR - Xsync will push all the subject metadata irrespective of syncNewOnly or not
        return syncSubjectMetaData(remoteSubject);
    }

    private String syncSubjectMetaData(XnatSubjectdataI remoteSubject) throws Exception {
        String subject_remote_id  = null;
        try {
            RemoteConnectionHandler remoteConnectionHandler = new RemoteConnectionHandler(jdbcTemplate, queryResultUtil);
            RemoteConnection connection = remoteConnectionHandler.getConnection(projectSyncConfiguration.getProject().getId(),projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl());
            RemoteConnectionResponse response = manager.importSubject(connection, (XnatSubjectdata)remoteSubject);
            if (response.wasSuccessful()) {
                subject_remote_id = response.getResponseBody();
                //WorkFlowUtils wrkFlowUtils = new WorkFlowUtils(_manager, _queryResultUtil,_jdbcTemplate, projectSyncConfiguration);
                //wrkFlowUtils.createWorkflowAtRemote((XnatSubjectdata)remoteSubject,subject_remote_id,remoteSubject.getProject(),"Complete");
            } else {
                XSyncFailureHandler.handle(projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSourceProjectId(), localSubject.getId(), localSubject.getXSIType(), null, subjectSyncInfo, response);
            }
            return subject_remote_id;
        }catch(Exception e) {
            saveSyncDetails(localSubject.getId(), null, null, XsyncUtils.SYNC_STATUS_FAILED, localSubject.getXSIType());
            log.error("Error syncing subject metadata, remote label: {}", remoteSubject.getLabel(), e);
            throw e;
        }
    }

    private void saveSyncDetails(String local_id, String remote_id, String remote_label, String syncStatus,String xsiType) {
        subjectSyncInfo.setSyncStatus(syncStatus);
        subjectSyncInfo.setRemoteId(remote_id);
        subjectSyncInfo.setXsiType(xsiType);
        subjectSyncInfo.setRemoteLabel(remote_label);
        String remoteProjectId = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteProjectId();
        String remoteUrl = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl();

        XSyncTools xsyncTools = new XSyncTools(user, jdbcTemplate, queryResultUtil);
        xsyncTools.saveSyncDetails(projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSourceProjectId(), local_id, remote_id,syncStatus,xsiType,remoteProjectId, remoteUrl);
    }





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
    private final SerializerService  serializer;
    private final SyncStatusService syncStatusService;


}
