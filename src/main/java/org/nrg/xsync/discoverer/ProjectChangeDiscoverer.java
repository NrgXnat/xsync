package org.nrg.xsync.discoverer;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.nrg.config.services.ConfigService;
import org.nrg.framework.services.SerializerService;
import org.nrg.mail.services.MailService;
import org.nrg.xdat.XDAT;
import org.nrg.xdat.model.XnatAbstractresourceI;
import org.nrg.xdat.om.XnatAbstractresource;
import org.nrg.xdat.om.XnatProjectdata;
import org.nrg.xdat.om.XnatSubjectdata;
import org.nrg.xft.security.UserI;
import org.nrg.xnat.services.archive.CatalogService;
import org.nrg.xnat.xsync.remote.verify.XsyncProjectVerifier;
import org.nrg.xnat.xsync.report.XsyncProjectReportGenerator;
import org.nrg.xsync.components.SyncStatusHolder.SyncType;
import org.nrg.xsync.components.XsyncSitePreferencesBean;
import org.nrg.xsync.configuration.ProjectSyncConfiguration;
import org.nrg.xsync.connection.RemoteConnection;
import org.nrg.xsync.connection.RemoteConnectionHandler;
import org.nrg.xsync.connection.RemoteConnectionManager;
import org.nrg.xsync.connection.RemoteConnectionResponse;
import org.nrg.xsync.exception.XsyncNotConfiguredException;
import org.nrg.xsync.local.IdMapper;
import org.nrg.xsync.local.SubjectDataSync;
import org.nrg.xsync.manager.SynchronizationManager;
import org.nrg.xsync.manifest.ResourceSyncItem;
import org.nrg.xsync.manifest.SubjectSyncItem;
import org.nrg.xsync.remote.alias.services.SyncStatusService;
import org.nrg.xsync.tools.XSyncTools;
import org.nrg.xsync.tools.XsyncObserver;
import org.nrg.xsync.tools.XsyncXnatInfo;
import org.nrg.xsync.utils.ConflictCheckUtil;
import org.nrg.xsync.utils.QueryResultUtil;
import org.nrg.xsync.utils.ResourceUtils;
import org.nrg.xsync.utils.XSyncFailureHandler;
import org.nrg.xsync.utils.XsyncFileUtils;
import org.nrg.xsync.utils.XsyncUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * @author Mohana Ramaratnam
 */

public class ProjectChangeDiscoverer implements Callable<Void> {
    private static final Logger _log = LoggerFactory.getLogger(ProjectChangeDiscoverer.class);

    //When created entry is in MetaData;
    //status field tells about the status of the entity
    //When updated entry is in History
    private final RemoteConnectionManager _manager;
    private final MailService _mailService;
    private final NamedParameterJdbcTemplate _jdbcTemplate;
    private final QueryResultUtil _queryResultUtil;
    private final XsyncXnatInfo _xnatInfo;
    private final SerializerService _serializer;
    private final CatalogService _catalogService;
    private final String _projectId;
    private final UserI _user;
    private MapSqlParameterSource _parameters;
    private final ProjectSyncConfiguration _projectSyncConfiguration;
    private final boolean _syncAll;
    private XsyncObserver _observer;
    // TODO:  This parameter should be configurable
    private final Integer MAX_FAILURES = 5;
    private XnatProjectdata localProject;
    private final List<XnatAbstractresource> projectResourcesToVerify = new ArrayList<XnatAbstractresource>();
    private final SyncStatusService _syncStatusService;

    public ProjectChangeDiscoverer(final RemoteConnectionManager manager, final ConfigService configService, final SerializerService serializer,
                                   final QueryResultUtil queryResultUtil, final NamedParameterJdbcTemplate jdbcTemplate, final MailService mailService,
                                   final CatalogService catalogService, final XsyncXnatInfo xnatInfo, SyncStatusService syncStatusService,
                                   final String projectId, final UserI user) throws XsyncNotConfiguredException {
        _manager = manager;
        _mailService = mailService;
        _queryResultUtil = queryResultUtil;
        _jdbcTemplate = jdbcTemplate;
        _xnatInfo = xnatInfo;
        _serializer = serializer;
        _catalogService = catalogService;
        _projectId = projectId;
        _user = user;
        _parameters = new MapSqlParameterSource("project", _projectId);
        _projectSyncConfiguration = new ProjectSyncConfiguration(configService, serializer, (JdbcTemplate) jdbcTemplate.getJdbcOperations(), _projectId, _user);
        _syncAll = !_projectSyncConfiguration.isSetToSyncNewOnly();
        _syncStatusService = syncStatusService;
        localProject = null;
    }

    /**
     * @return the _lastSyncStartTime
     */
    //@SuppressWarnings("WeakerAccess")
    public Object getLastSyncStartTime() {
        return _projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getSyncStartTime();
    }

    public Void call() throws Exception {
        sync();
        return null;
    }

    private synchronized void sync() {
        //Create export Build dir
        //Write all the files
        //Upload the XAR
        //_log.debug(_projectSyncConfiguration.toString());
        XnatAbstractresourceI synchronizationResource = null;
        localProject = XnatProjectdata.getXnatProjectdatasById(_projectId, _user, false);

        XnatProjectdata project = null;
        //SyncStatusUpdater syncStatusUpdater = new SyncStatusUpdater(_projectId, _projectSyncConfiguration, _user);
        try {
            final Boolean isSyncEnabled = _projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncEnabled();
            if (!isSyncEnabled) {
                return;
            }
            //Boolean isSyncBlocked = _projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncBlocked();
            final Boolean isSyncBlocked = _syncStatusService.isCurrentlySyncing(_projectId);
            if (isSyncBlocked != null && isSyncBlocked) {
                try {
                    _mailService.sendHtmlMessage(_xnatInfo.getAdminEmail(), _user.getEmail(), "Project " + _projectId + " sync skipped ",
                            "<html><body>"
                                    + "<p>Sync was skipped.  See information below:</p>"
                                    + "<p>PROJECT: " + _projectId + "</p>"
                                    + "<p>SITE: " + _manager.getSiteId() + "</p>"
                                    + "<p>USER: " + _user.getLogin() + "</p>"
                                    + "</body></html>");
                    _log.debug("Sync Blocked");
                } catch (Exception e) {
                    _log.error("Failed to send email.", e);
                }
                return;
            }
            //syncStatusUpdater.saveSyncBlockStatus(Boolean.TRUE);
            project = _projectSyncConfiguration.getProject();
            synchronizationResource = XsyncFileUtils.createSynchronizationLogResource(project, _user);
            String remoteProjectId = _projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteProjectId();
            String remoteHost = _projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl();

            //Get the Last Sync Details
            //If the sync status is other can Complete - this implies some in the batch of last sync have failed to sync
            //Out of the list of those that are gathered to be synced. Check if they have already been synced.

            SynchronizationManager.BEGIN_SYNC(_manager.getSyncManifestService(), _xnatInfo, project.getId(), remoteProjectId, remoteHost, _user, _mailService, _jdbcTemplate);
            _syncStatusService.registerSyncStart(_projectId, SyncType.PROJECT_SYNC, SynchronizationManager.getProjectManifest(project.getId()));
            _observer = new XsyncObserver(_projectId);
            syncProjectResources();
            final List<Map<String, Object>> subjectRows = getSubjectsModifiedSinceLastSync();
            appendFailedSubjects(subjectRows);
            appendSubjectsWithFailedAssessorSyncs(subjectRows);
            final List<String> subjectIds = new ArrayList<>();
            for (final Map<String, Object> row : subjectRows) {
                if (!subjectIds.contains(row.get("id"))) {
                    subjectIds.add((String) row.get("id"));
                }
            }
            int failCount = 0;
            _syncStatusService.registerInitialSubjectList(_projectId, subjectIds);
            final List<String> processedSubjects = new ArrayList<>();
            for (Map<String, Object> row : subjectRows) {
                if (row.get("id") == null || processedSubjects.contains(row.get("id").toString())) {
                    continue;
                }
                processedSubjects.add(row.get("id").toString());
                _syncStatusService.registerCurrentSubject(_projectId, row.get("id").toString());
                _log.debug("Subject " + row.get("id") + " has been modfied since " + this.getLastSyncStartTime());
                String subjectLabel = null;
                try {
                    XnatSubjectdata localSubject = XnatSubjectdata.getXnatSubjectdatasById(row.get("id"), _user, true);
                    subjectLabel = (localSubject != null) ? localSubject.getLabel() : row.get("id").toString();
                    _syncStatusService.registerCurrentSubject(_projectId, subjectLabel);
                    if (localSubject == null) {
                        //Local Subject has been deleted; Delete the remote subject
                        deleteSubject((String) row.get("id"), (String) row.get("label"));
                    } else {
                        syncSubject(localSubject);
                    }
                    _syncStatusService.registerCompletedSubject(_projectId, subjectLabel);
                } catch (Exception e) {
                    _syncStatusService.registerFailedSubject(_projectId, (subjectLabel != null) ? subjectLabel : row.get("id").toString());
                    failCount++;
                    if (failCount > MAX_FAILURES) {
                        throw e;
                    }
                }
            }
            //Mark the shared subjects as skipped
            List<Map<String, Object>> sharedSubjects = getSubjectsSharedIntoProject();
            for (Map<String, Object> row : sharedSubjects) {
                String subjectProjectLabel = (String) row.get("label");
                String subjectId = (String) row.get("subject_id");
                SubjectSyncItem subjectSyncInfo = new SubjectSyncItem(subjectId, subjectProjectLabel);
                subjectSyncInfo.addObserver(_observer);
                subjectSyncInfo.setSyncStatus(XsyncUtils.SYNC_STATUS_SKIPPED);
                subjectSyncInfo.setMessage("Shared Subject");
                subjectSyncInfo.setRemoteId("");
                subjectSyncInfo.setXsiType(XnatSubjectdata.SCHEMA_ELEMENT_NAME);
                subjectSyncInfo.setRemoteLabel("");
                subjectSyncInfo.stateChanged();
                SynchronizationManager.UPDATE_MANIFEST(_projectId, subjectSyncInfo);

            }
            //Verify All the Transfers went through
            verifyProjectResources();
            //Save into the DB the starttime and end-time
            //Clear the time logs
            //syncStatusUpdater.saveSyncBlockStatus(Boolean.FALSE);
            SynchronizationManager.END_SYNC(project.getId(), _jdbcTemplate, true, true);
            _syncStatusService.registerSyncEnd(_projectId, SynchronizationManager.getProjectManifest(project.getId()));
        } catch (Exception e) {
            //Roll back the syncBlocked flag
            _log.debug(e.getLocalizedMessage());
            _log.error(e.getMessage(), e);
            //syncStatusUpdater.saveSyncBlockStatus(Boolean.FALSE);
            try {
                XSyncFailureHandler.handle(_mailService, _xnatInfo.getAdminEmail(), _manager.getSiteId(), _projectId, e, "Sync failed");
            } catch (Throwable t) {
                throw t;
            } finally {
                _syncStatusService.registerSyncEnd(_projectId, SynchronizationManager.getProjectManifest(project.getId()));
            }
        } finally {
            _observer.close(synchronizationResource);
            if (synchronizationResource != null && project != null) {
                //RefreshCatalog
                //EventMetaI now = EventUtils.DEFAULT_EVENT(_user, "Synchronization Log Added");
                try {
                    final List<CatalogService.Operation> _operations = new ArrayList<>();
                    final String _resource = "/data/archive/projects/" + _projectId + "/resources/" + synchronizationResource.getLabel();
                    _operations.addAll(CatalogService.Operation.ALL);
                    _catalogService.refreshResourceCatalog(_user, _resource, _operations.toArray(new CatalogService.Operation[_operations.size()]));
                    //ResourceUtils.refreshResourceCatalog((XnatAbstractresource)synchronizationResource, project.getArchiveRootPath(), true, true, true, true, _user, now);
                } catch (Exception e) {
                    _log.debug("Unable to refresh catalog");
                }
            }
        }
    }

    //TODO
    //Change the implementation to use the ResourceFilter class -
    //This class returns  NEW, UPDATED and DELETED lists of resources
    private void syncProjectResources() {
        ResourceUtils resourceUtils = new ResourceUtils(_projectSyncConfiguration);

        List<Map<String, Object>> resourceRows = getProjectResourcesModifiedSinceLastSync();
        if (!CollectionUtils.isEmpty(resourceRows)) {
            return;
        }
        String remoteProjectId = _projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteProjectId();
        String remoteUrl = _projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl();

        String localProjectArchivePath = localProject.getArchiveRootPath();
        for (Map<String, Object> row : resourceRows) {
            String label = (String) row.get("label");
            if (label != null && label.equalsIgnoreCase(XsyncUtils.PROJECT_SYNC_LOG_RESOURCE_LABEL)) {
                continue;
            }
            _log.debug("Resource {} has been modified since {}", row.get("label"), this.getLastSyncStartTime());
            if (_projectSyncConfiguration.isResourceToBeSynced(label)) {
                String status = (String) row.get("status");
                if (_syncAll) {
                    if (QueryResultUtil.DELETE_STATUS.equals(status)) {
                        deleteProjectResource(label);
                    } else { //Resource is active and has to be updated
                        updateProjectResource(localProjectArchivePath, label);
                    }
                } else {
                    //If its a new addition, sync it. If its an update or a delete skip it.
                    if (QueryResultUtil.DELETE_STATUS.equals(status)) {
                        ResourceSyncItem resourceSyncItem = new ResourceSyncItem(_projectId, label);
                        resourceSyncItem.addObserver(_observer);
                        resourceSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_SKIPPED);
                        resourceSyncItem.setMessage("Project resource " + label + " has been deleted, however, it was not synced as project is configured not to sync automatically ");
                        resourceSyncItem.stateChanged();
                        SynchronizationManager.UPDATE_MANIFEST(_projectId, resourceSyncItem);
                    } else {
                        //Check if its a new resource or an updated resource
                        XSyncTools xsyncTools = new XSyncTools(_user, _jdbcTemplate, _queryResultUtil);
                        XnatAbstractresource resource = resourceUtils.getResource(label);
                        if (xsyncTools.hasBeenSyncedAlready(_projectId, label, resource.getXSIType(), remoteProjectId, remoteUrl)) {
                            //This is an instance of Update and auto-update is set to false; skip this resource
                            ResourceSyncItem resourceSyncItem = new ResourceSyncItem(_projectId, label);
                            resourceSyncItem.addObserver(_observer);
                            resourceSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_SKIPPED);
                            resourceSyncItem.setMessage("Project resource " + label + " has been updated, however, it was not synced as project is configured not to sync automatically ");
                            resourceSyncItem.stateChanged();
                            SynchronizationManager.UPDATE_MANIFEST(_projectId, resourceSyncItem);
                        } else {
                            //New resource has been added. Push this resource
                            updateProjectResource(localProjectArchivePath, label);
                        }
                    }
                }
            }
        }
    }

    private void deleteProjectResource(String resourceLabel) {
        String rLabel = resourceLabel == null ? XsyncUtils.RESOURCE_NO_LABEL : resourceLabel;
        try {
            String remoteProjectId = _projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteProjectId();

            RemoteConnectionHandler remoteConnectionHandler = new RemoteConnectionHandler(_jdbcTemplate, _queryResultUtil);
            RemoteConnection connection = remoteConnectionHandler.getConnection(_projectId, _projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl());

            RemoteConnectionResponse response = _manager.deleteProjectResource(connection, remoteProjectId, resourceLabel);
            ResourceSyncItem resourceSyncItem = new ResourceSyncItem(_projectId, rLabel);
            resourceSyncItem.addObserver(_observer);
            if (response.wasSuccessful()) {
                resourceSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_DELETED);
                resourceSyncItem.setMessage("Project resource " + rLabel + " deleted ");
                resourceSyncItem.stateChanged();
                //Remove the entry from the remote map table; so that in the future we can have same named resource
                XSyncTools xsyncTools = new XSyncTools(_user, _jdbcTemplate, _queryResultUtil);
                xsyncTools.deleteXsyncRemoteEntry(_projectId, resourceLabel);
            } else {
                resourceSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_FAILED);
                resourceSyncItem.setMessage("Project resource " + rLabel + " could not be deleted. Cause: " + response.getResponseBody());
                resourceSyncItem.stateChanged();
            }
            SynchronizationManager.UPDATE_MANIFEST(_projectId, resourceSyncItem);
        } catch (Exception e) {
            _log.error(e.toString());
            ResourceSyncItem resourceSyncItem = new ResourceSyncItem(_projectId, rLabel);
            resourceSyncItem.addObserver(_observer);
            resourceSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_FAILED);
            resourceSyncItem.setMessage("Project resource " + rLabel + " could not be deleted. Cause: " + e.getMessage());
            resourceSyncItem.stateChanged();
            SynchronizationManager.UPDATE_MANIFEST(_projectId, resourceSyncItem);
        }
    }

    private void updateProjectResource(String localProjectArchivePath, String resourceLabel) {
        String remoteProjectId = _projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteProjectId();
        String rLabel = resourceLabel == null ? XsyncUtils.RESOURCE_NO_LABEL : resourceLabel;
        ResourceUtils resourceUtils = new ResourceUtils(_projectSyncConfiguration);

        try {
            XnatAbstractresource resource = resourceUtils.getResource(resourceLabel);
            String archiveDirectory = resource.getFullPath(localProjectArchivePath);
            File resourcePath = new File(archiveDirectory);
            if (resourcePath.exists() && resourcePath.isFile()) {
                resourcePath = resourcePath.getParentFile();
                final XsyncSitePreferencesBean syncPrefsBean = XDAT.getContextService().getBeanSafely(XsyncSitePreferencesBean.class);

                List<File> zipFiles = new XsyncFileUtils().buildMultipleZips(remoteProjectId, resourcePath, syncPrefsBean.getSyncMaxUncompressedZipFileSizeAsLong());
                boolean allResponsesWereSuccessful = true;
                StringBuilder responses = new StringBuilder();
                int counter = 0;
                for (File zipFile : zipFiles) {
                    try {
                        ++counter;
                        RemoteConnectionHandler remoteConnectionHandler = new RemoteConnectionHandler(_jdbcTemplate, _queryResultUtil);
                        RemoteConnection connection = remoteConnectionHandler.getConnection(_projectId, _projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl());
                        RemoteConnectionResponse response = _manager.importProjectResource(connection, remoteProjectId, resourceLabel, zipFile,  (counter == zipFiles.size()));
                        allResponsesWereSuccessful = allResponsesWereSuccessful && response.wasSuccessful();
                        responses.append(response.getResponseBody());
                    } finally {
                        if (zipFile.exists()) {
                            zipFile.delete();
                        }
                    }
                }
                if (allResponsesWereSuccessful) {
                    try{
                        Thread.sleep(XsyncUtils.THREAD_SLEEP_TIME); //Sleep so that the server side catalog refresh is done
                    } catch (InterruptedException ignore){}
                    projectResourcesToVerify.add(resource);
                } else {
                    ResourceSyncItem resourceSyncItem = new ResourceSyncItem(_projectId, rLabel);
                    resourceSyncItem.addObserver(_observer);
                    if (resource.getFileCount() != null)
                        resourceSyncItem.setFileCount(resource.getFileCount());
                    if (resource.getFileSize() != null)
                        resourceSyncItem.setFileSize(resource.getFileSize());
                    resourceSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_FAILED);
                    resourceSyncItem.setMessage("Project resource " + rLabel + " could not be updated. Cause: " + responses.toString());
                    resourceSyncItem.stateChanged();
                    SynchronizationManager.UPDATE_MANIFEST(_projectId, resourceSyncItem);
                }
            }
        } catch (Exception e) {
            _log.error(e.toString());
            ResourceSyncItem resourceSyncItem = new ResourceSyncItem(_projectId, resourceLabel);
            resourceSyncItem.addObserver(_observer);
            resourceSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_FAILED);
            resourceSyncItem.setMessage("Project resource " + rLabel + " could not be updated. Cause: " + e.getMessage());
            resourceSyncItem.stateChanged();
            SynchronizationManager.UPDATE_MANIFEST(_projectId, resourceSyncItem);
        }
    }

    private List<Map<String, Object>> getSubjectsModifiedSinceLastSync() {
        //Any entity  that is derived from the subject or linked to the subject
        //if modified, would result in an update in the last_modified column
        //This list would contain any change to any SubjectAssessors
        final String query = _queryResultUtil.getQueryForFetchingSubjectsModifiedSinceLastSync();
        return _jdbcTemplate.queryForList(query, _parameters);
    }

    private void appendSubjectsWithFailedAssessorSyncs(List<Map<String, Object>> subjectRows) {
        final String query = _queryResultUtil.getQueryForFetchingSubjectsWithFailedOrSkippedByFilterAssessorSyncs(_projectSyncConfiguration.getSynchronizationConfiguration().getNo_of_retry_days());
        final List<Map<String, Object>> queryResults = _jdbcTemplate.queryForList(query, _parameters);
        subjectRows.addAll(queryResults);
    }

    private List<Map<String, Object>> getSubjectsSharedIntoProject() {
        final String query = _queryResultUtil.getQueryForSubjectsSharedIntoProject(_projectId);
        return _jdbcTemplate.queryForList(query, _parameters);
    }

    /**
     * Append failed subjects.
     *
     * @param subjectRows the subject rows
     */
    private void appendFailedSubjects(List<Map<String, Object>> subjectRows) {
        final String query = _queryResultUtil.getQueryForFetchingFailedSubjects();
        final List<Map<String, Object>> queryResults = _jdbcTemplate.queryForList(query, _parameters);
        subjectRows.addAll(queryResults);
    }


    @SuppressWarnings("unused")
    private List<Map<String, Object>> getQueryForFetchingSubjectsWhoseExperimentsMarkedOKSinceLastSync(List<String> excludeIds) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("project", _projectId);
        boolean skipSubjectIdCheck = false;
        if (!excludeIds.isEmpty()) {
            parameters.addValue(QueryResultUtil.SUBJECT_IDS, excludeIds);
        } else {
            skipSubjectIdCheck = true;
        }
        String query = _queryResultUtil.getQueryForFetchingSubjectsWhoseExperimentsMarkedOKSinceLastSync(skipSubjectIdCheck);
        return _jdbcTemplate.queryForList(query, parameters);
    }

    private List<Map<String, Object>> getProjectResourcesModifiedSinceLastSync() {
        String query = _queryResultUtil.getQueryForFetchingProjectResourcesModifiedSinceLastSync();
        return _jdbcTemplate.queryForList(query, _parameters);
    }

    private void syncSubject(XnatSubjectdata localSubject) throws Exception {
        _log.debug("Exporting {}", localSubject.getId());
        SubjectDataSync remoteSubject = new SubjectDataSync(_manager, _xnatInfo, _queryResultUtil, (JdbcTemplate) _jdbcTemplate.getJdbcOperations(),
                localSubject, _projectSyncConfiguration, _user, _syncAll, _observer, _serializer, _syncStatusService);
        remoteSubject.sync(true);
    }

    private void deleteSubject(String deletedSubjectLocalId, String deletedSubjectLabel) throws Exception {
        //Get the remote ID
        //If it exists; delete the remote subject
        String remoteProjectId = _projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteProjectId();
        String remoteUrl = _projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl();

        IdMapper idMapper = new IdMapper(_manager, _queryResultUtil, _jdbcTemplate, _user, _projectSyncConfiguration);
        String remoteId = idMapper.getRemoteAccessionId(deletedSubjectLocalId);
        if (_syncAll) {
            if (remoteId != null) {
                //Delete the remote subject
                XnatSubjectdata subject = new XnatSubjectdata();
                subject.setProject(_projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteProjectId());
                subject.setId(remoteId);
                ConflictCheckUtil.checkForConflict(subject, remoteId, _projectSyncConfiguration,
                        _jdbcTemplate, _queryResultUtil, _manager);
                _log.debug("Deleting subject {} from remote project {}", subject.getId(), subject.getProject());
                try {
                    RemoteConnectionHandler remoteConnectionHandler = new RemoteConnectionHandler(_jdbcTemplate, _queryResultUtil);
                    RemoteConnection connection = remoteConnectionHandler.getConnection(_projectId, remoteUrl);
                    RemoteConnectionResponse response = _manager.deleteSubject(connection, subject);
                    if (response.wasSuccessful()) {
                        SubjectSyncItem subjectSyncItem = new SubjectSyncItem(deletedSubjectLocalId, deletedSubjectLabel);
                        subjectSyncItem.addObserver(_observer);
                        subjectSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_DELETED);
                        subjectSyncItem.setMessage("Subject " + deletedSubjectLocalId + " has been deleted.");
                        subjectSyncItem.stateChanged();
                        SynchronizationManager.UPDATE_MANIFEST(_projectId, subjectSyncItem);
                        XSyncTools xsyncTools = new XSyncTools(_user, _jdbcTemplate, _queryResultUtil);
                        xsyncTools.saveSyncDetails(_projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSourceProjectId(), deletedSubjectLocalId, remoteId, XsyncUtils.SYNC_STATUS_DELETED, subject.getXSIType(), remoteProjectId, remoteUrl);
                        xsyncTools.deleteXsyncRemoteEntry(_projectId, deletedSubjectLocalId);
                    }
                } catch (Exception e) {
                    _log.error(e.toString());
                    SubjectSyncItem subjectSyncItem = new SubjectSyncItem(deletedSubjectLocalId, deletedSubjectLabel);
                    subjectSyncItem.addObserver(_observer);
                    subjectSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_FAILED);
                    subjectSyncItem.setMessage("Subject " + deletedSubjectLocalId + " could not be deleted.");
                    subjectSyncItem.stateChanged();
                    SynchronizationManager.UPDATE_MANIFEST(_projectId, subjectSyncItem);
                }
            } else {
                _log.info("Appears that " + deletedSubjectLocalId + " has been locally deleted between two syncs. Ignoring");
                SubjectSyncItem subjectSyncItem = new SubjectSyncItem(deletedSubjectLocalId, deletedSubjectLabel);
                subjectSyncItem.addObserver(_observer);
                subjectSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_SKIPPED);
                subjectSyncItem.setMessage("Subject " + deletedSubjectLocalId + " has been deleted locally but has not been synced in the past and hence is being skipped.");
                subjectSyncItem.stateChanged();
                SynchronizationManager.UPDATE_MANIFEST(_projectId, subjectSyncItem);
            }
        } else {
            if (remoteId != null) {
                SubjectSyncItem subjectSyncItem = new SubjectSyncItem(deletedSubjectLocalId, deletedSubjectLabel);
                subjectSyncItem.addObserver(_observer);
                subjectSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_SKIPPED);
                subjectSyncItem.setMessage("Subject " + deletedSubjectLocalId + " has been skipped as it appeards to have been deleted locally but synced in the past");
                subjectSyncItem.stateChanged();
                SynchronizationManager.UPDATE_MANIFEST(_projectId, subjectSyncItem);
            }
        }
    }

    private void verifyProjectResources() {
        ResourceUtils resourceUtils = new ResourceUtils(_projectSyncConfiguration);
        String remoteProjectId = _projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteProjectId();
        final String remoteUrl = _projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl();

        XsyncProjectVerifier projectResourceVerifier = new XsyncProjectVerifier(_manager, _queryResultUtil, _jdbcTemplate, _projectSyncConfiguration, _serializer);
        String localProjectArchivePath = localProject.getArchiveRootPath();

        for (XnatAbstractresource rsc : projectResourcesToVerify) {
            String rLabel = rsc.getLabel() == null ? XsyncUtils.RESOURCE_NO_LABEL : rsc.getLabel();

            ResourceSyncItem resourceSyncItem = new ResourceSyncItem(_projectId, rLabel);
            resourceSyncItem.addObserver(_observer);
            if (rsc.getFileCount() != null)
                resourceSyncItem.setFileCount(rsc.getFileCount());
            if (rsc.getFileSize() != null)
                resourceSyncItem.setFileSize(rsc.getFileSize());
            String archiveDirectory = rsc.getFullPath(localProjectArchivePath);
            final String uri = remoteUrl + "/data/archive/projects/" + remoteProjectId + "/resources/" + rsc.getLabel() + "/files?format=JSON";
            Map<String, String> fileComparison;
            try {
                fileComparison = projectResourceVerifier.verify(archiveDirectory, remoteProjectId, rsc.getLabel(), uri);
            } catch (Exception e) {
                fileComparison = new HashMap<String, String>();
                fileComparison.put(XsyncUtils.XSYNC_VERIFICATION_STATUS, XsyncUtils.XSYNC_VERIFICATION_STATUS_FAILED_TO_CONNECT);
            }
            resourceUtils.setSyncStatus(fileComparison, resourceSyncItem, "Project resource " + rLabel);
            resourceSyncItem.stateChanged();
            XSyncTools xsyncTools = new XSyncTools(_user, _jdbcTemplate, _queryResultUtil);
            xsyncTools.saveSyncDetails(_projectId, rLabel, rLabel, resourceSyncItem.getSyncStatus(), rsc.getXSIType(), remoteProjectId, remoteUrl);
            SynchronizationManager.UPDATE_MANIFEST(_projectId, resourceSyncItem);
        }
    }

    /**
     * Generate mapping report.
     *
     * @param reportFormat the requested report type
     * @return the string
     * @throws Exception the exception
     */
    public String generateMappingReport(String reportFormat, String objectType) throws Exception {
        XsyncProjectReportGenerator reportGenerator = new XsyncProjectReportGenerator(_manager, _queryResultUtil, _jdbcTemplate, _projectSyncConfiguration, _serializer, _user);
        return reportGenerator.generateMappingReport(reportFormat, objectType);
    }
}
