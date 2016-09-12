package org.nrg.xsync.discoverer;

import org.nrg.config.services.ConfigService;
import org.nrg.framework.services.SerializerService;
import org.nrg.mail.services.MailService;
import org.nrg.xdat.model.XnatAbstractresourceI;
import org.nrg.xdat.om.XnatAbstractresource;
import org.nrg.xdat.om.XnatProjectdata;
import org.nrg.xdat.om.XnatSubjectdata;
import org.nrg.xft.event.EventMetaI;
import org.nrg.xft.event.EventUtils;
import org.nrg.xft.security.UserI;
import org.nrg.xsync.configuration.ProjectSyncConfiguration;
import org.nrg.xsync.connection.RemoteConnection;
import org.nrg.xsync.connection.RemoteConnectionHandler;
import org.nrg.xsync.connection.RemoteConnectionManager;
import org.nrg.xsync.connection.RemoteConnectionResponse;
import org.nrg.xsync.exception.XsyncNotConfiguredException;
import org.nrg.xsync.local.IdMapper;
import org.nrg.xsync.local.RemoteSubject;
import org.nrg.xsync.manager.SynchronizationManager;
import org.nrg.xsync.manifest.ResourceSyncItem;
import org.nrg.xsync.manifest.SubjectSyncItem;
import org.nrg.xsync.tools.XSyncTools;
import org.nrg.xsync.tools.XsyncXnatInfo;
import org.nrg.xsync.utils.QueryResultUtil;
import org.nrg.xsync.utils.XSyncFailureHandler;
import org.nrg.xsync.utils.XsyncFileUtils;
import org.nrg.xsync.utils.XsyncUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * @author Mohana Ramaratnam
 */

public class ProjectChangeDiscoverer implements Callable<Void> {
    private static final Logger _log = LoggerFactory.getLogger(ProjectChangeDiscoverer.class);

    //When created entry is in MetaData;
    //status field tells about the status of the entity
    //When updated entry is in History
    private final RemoteConnectionManager    _manager;
    private final MailService                _mailService;
    private final NamedParameterJdbcTemplate _jdbcTemplate;
    private final QueryResultUtil            _queryResultUtil;
    private final XsyncXnatInfo              _xnatInfo;
    private final SerializerService          _serializer;
    private final String                     _projectId;
    private final UserI                      _user;
    private       MapSqlParameterSource      _parameters;
    private final ProjectSyncConfiguration   _projectSyncConfiguration;
    private final boolean                    _syncAll;

    public ProjectChangeDiscoverer(final RemoteConnectionManager manager, final ConfigService configService, final SerializerService serializer, final QueryResultUtil queryResultUtil, final NamedParameterJdbcTemplate jdbcTemplate, final MailService mailService, final XsyncXnatInfo xnatInfo, final String projectId, final UserI user) throws XsyncNotConfiguredException {
        _manager = manager;
        _mailService = mailService;
        _queryResultUtil = queryResultUtil;
        _jdbcTemplate = jdbcTemplate;
        _xnatInfo = xnatInfo;
        _serializer = serializer;
        _projectId = projectId;
        _user = user;
        _parameters = new MapSqlParameterSource("project", _projectId);
        _projectSyncConfiguration = new ProjectSyncConfiguration(configService, serializer, (JdbcTemplate) jdbcTemplate.getJdbcOperations(), _projectId, _user);
        _syncAll = !_projectSyncConfiguration.isSetToSyncNewOnly();
    }

    /**
     * @return the _lastSyncStartTime
     */
    @SuppressWarnings("WeakerAccess")
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
        try {
            Boolean isSyncEnabled = _projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncEnabled();
            if (!isSyncEnabled) {
                return;
            }
            Boolean isSyncBlocked = _projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncBlocked();
            if (isSyncBlocked != null && isSyncBlocked) {
                try {
                    System.out.println("Sync is blocked ");
                    _mailService.sendHtmlMessage(_xnatInfo.getAdminEmail(), _user.getEmail(), "Project " + _projectId + " sync skipped ",
                                                          "<html><body><p>Project " + _projectId + " sync skipped </p></body></html>");
                    _log.debug("Sync Blocked");
                } catch (Exception e) {
                    _log.error("Failed to send email.", e);
                }
                return;
            }
            saveSyncBlockStatus(Boolean.TRUE);
            //try {
            //	Thread.sleep(120000);
            //}catch(Exception e){}
            XnatProjectdata project = _projectSyncConfiguration.getProject();
            String remoteProjectId = _projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteProjectId();
            String remoteHost = _projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl();

            SynchronizationManager.BEGIN_SYNC(_manager.getSyncManifestService(), _xnatInfo, project.getId(), remoteProjectId, remoteHost, _user, _mailService);
            syncProjectResources();
            List<Map<String, Object>> subjectRows = getSubjectsModifiedSinceLastSync();
            List<String> subjectIds = new ArrayList<>();
            for (Map<String, Object> row : subjectRows) {
                subjectIds.add((String) row.get("id"));
            }
            for (Map<String, Object> row : subjectRows) {
                _log.debug("Subject " + row.get("id") + " has been modfied since " + this.getLastSyncStartTime());
                XnatSubjectdata localSubject = XnatSubjectdata.getXnatSubjectdatasById(row.get("id"), _user, true);
                if (localSubject == null) {
                    //Local Subject has been deleted; Delete the remote subject
                    deleteSubject((String) row.get("id"), (String) row.get("label"));
                } else {
                    syncSubject(localSubject);
                }
            }
            //Mark the shared subjects as skipped
            List<Map<String, Object>> sharedSubjects = getSubjectsSharedIntoProject();
            for (Map<String, Object> row : sharedSubjects) {
            	String subjectProjectLabel = (String) row.get("label");
            	String subjectId = (String) row.get("subject_id");
            	SubjectSyncItem subjectSyncInfo = new SubjectSyncItem(subjectId,subjectProjectLabel);
        		subjectSyncInfo.setSyncStatus(XsyncUtils.SYNC_STATUS_SKIPPED);
        		subjectSyncInfo.setMessage("Shared Subject");
        		subjectSyncInfo.setRemoteId("");
        		subjectSyncInfo.setXsiType(XnatSubjectdata.SCHEMA_ELEMENT_NAME);
        		subjectSyncInfo.setRemoteLabel("");
        		SynchronizationManager.UPDATE_MANIFEST(_projectId, subjectSyncInfo);

            }            
            //Save into the DB the starttime and end-time
            //Clear the time logs
            saveSyncBlockStatus(Boolean.FALSE);
            SynchronizationManager.END_SYNC(_serializer, project.getId(), _jdbcTemplate);
        } catch (Exception e) {
            //Roll back the syncBlocked flag
            _log.debug(e.getLocalizedMessage());
            saveSyncBlockStatus(Boolean.FALSE);
            XSyncFailureHandler.handle(_mailService, _xnatInfo.getAdminEmail(), _manager.getSiteId(), _projectId, e, "Sync failed");
        }
    }

    private void saveSyncBlockStatus(Boolean status) {
        try {
            _projectSyncConfiguration.getProjectSyncConfigurationFromDB().setSyncBlocked(status);
            //Backward compatible XNAT 1.6.5 does not have ADMIN_EVENT method
            EventMetaI c = EventUtils.DEFAULT_EVENT(_user, "ADMIN_EVENT occurred");
            _projectSyncConfiguration.getProjectSyncConfigurationFromDB().save(_user, false, true, c);
        } catch (Exception e) {
            _log.debug("Unable to save synchronization  details for project: " + _projectId + " Cause:" + e.getMessage());

        }
    }

    //TODO
    //Change the implementation to use the ResourceFilter class -
    //This class returns  NEW, UPDATED and DELETED lists of resources
    private void syncProjectResources() {
        List<Map<String, Object>> resourceRows = getProjectResourcesModifiedSinceLastSync();
        if (resourceRows == null || resourceRows.size() < 1) {
            return;
        }
        String remoteProjectId = _projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteProjectId();
        XnatProjectdata localProject = XnatProjectdata.getXnatProjectdatasById(_projectId, _user, false);
        String localProjectArchivePath = localProject.getArchiveRootPath();
        for (Map<String, Object> row : resourceRows) {
            String label = (String) row.get("label");
            _log.debug("Resource " + row.get("label") + " has been modfied since " + this.getLastSyncStartTime());
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
                        resourceSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_SKIPPED);
                        resourceSyncItem.setMessage("Project resource " + label + " has been deleted, however, it was not synced as project is configured not to sync automatically ");
                        SynchronizationManager.UPDATE_MANIFEST(_projectId, resourceSyncItem);
                    } else {
                        //Check if its a new resource or an updated resource
                        XSyncTools xsyncTools = new XSyncTools(_user, _jdbcTemplate, _queryResultUtil);
                        XnatAbstractresource resource = getResource(label);
                        if (xsyncTools.hasBeenSyncedAlready(_projectId, label, resource.getXSIType(),remoteProjectId)) {
                            //This is an instance of Update and auto-update is set to false; skip this resource
                            ResourceSyncItem resourceSyncItem = new ResourceSyncItem(_projectId, label);
                            resourceSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_SKIPPED);
                            resourceSyncItem.setMessage("Project resource " + label + " has been updated, however, it was not synced as project is configured not to sync automatically ");
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
        try {
            String remoteProjectId = _projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteProjectId();
            RemoteConnectionHandler remoteConnectionHandler = new RemoteConnectionHandler(_jdbcTemplate, _queryResultUtil);
            RemoteConnection connection = remoteConnectionHandler.getConnection(_projectId, _projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl());

            RemoteConnectionResponse response = _manager.deleteProjectResource(connection, remoteProjectId, resourceLabel);
            ResourceSyncItem resourceSyncItem = new ResourceSyncItem(_projectId, resourceLabel);
            if (response.wasSuccessful()) {
                resourceSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_DELETED);
                resourceSyncItem.setMessage("Project resource " + resourceLabel + " deleted ");
                //Remove the entry from the remote map table; so that in the future we can have same named resource
                XSyncTools xsyncTools = new XSyncTools(_user, _jdbcTemplate, _queryResultUtil);
                xsyncTools.deleteXsyncRemoteEntry(_projectId, resourceLabel);
            } else {
                resourceSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_FAILED);
                resourceSyncItem.setMessage("Project resource " + resourceLabel + " could not be deleted. Cause: " + response.getResponseBody());
            }
            SynchronizationManager.UPDATE_MANIFEST(_projectId, resourceSyncItem);
        } catch (Exception e) {
            _log.error(e.toString());
            ResourceSyncItem resourceSyncItem = new ResourceSyncItem(_projectId, resourceLabel);
            resourceSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_FAILED);
            resourceSyncItem.setMessage("Project resource " + resourceLabel + " could not be deleted. Cause: " + e.getMessage());
            SynchronizationManager.UPDATE_MANIFEST(_projectId, resourceSyncItem);
        }
    }

    private void updateProjectResource(String localProjectArchivePath, String resourceLabel) {
        String remoteProjectId = _projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteProjectId();
        try {
            XnatAbstractresource resource = getResource(resourceLabel);
            ResourceSyncItem resourceSyncItem = new ResourceSyncItem(_projectId, resourceLabel);
            resourceSyncItem.setFileCount(resource.getFileCount());
            resourceSyncItem.setFileSize(resource.getFileSize());
            String archiveDirectory = resource.getFullPath(localProjectArchivePath);
            File resourcePath = new File(archiveDirectory);
            if (resourcePath.exists() && resourcePath.isFile()) {
                resourcePath = resourcePath.getParentFile();
                File zipFile = new XsyncFileUtils().buildZip(remoteProjectId, resourcePath);
                RemoteConnectionHandler remoteConnectionHandler = new RemoteConnectionHandler(_jdbcTemplate, _queryResultUtil);
                RemoteConnection connection = remoteConnectionHandler.getConnection(_projectId, _projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl());
                RemoteConnectionResponse response = _manager.importProjectResource(connection, remoteProjectId, resourceLabel, zipFile);
                if (response.wasSuccessful()) {
                    if (zipFile.exists()) {
                        zipFile.delete();
                    }
                    resourceSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_SYNCED);
                    resourceSyncItem.setMessage("Project resource " + resourceLabel + " updated ");
                    XSyncTools xsyncTools = new XSyncTools(_user, _jdbcTemplate, _queryResultUtil);
                    xsyncTools.saveSyncDetails(_projectId, resource.getLabel(), resource.getLabel(), XsyncUtils.SYNC_STATUS_SYNCED, resource.getXSIType(),remoteProjectId);
                } else {
                    resourceSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_FAILED);
                    resourceSyncItem.setMessage("Project resource " + resourceLabel + " could not be updated. Cause: " + response.getResponseBody());
                }
                SynchronizationManager.UPDATE_MANIFEST(_projectId, resourceSyncItem);
            }
        } catch (Exception e) {
            _log.error(e.toString());
            ResourceSyncItem resourceSyncItem = new ResourceSyncItem(_projectId, resourceLabel);
            resourceSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_FAILED);
            resourceSyncItem.setMessage("Project resource " + resourceLabel + " could not be updated. Cause: " + e.getMessage());
            SynchronizationManager.UPDATE_MANIFEST(_projectId, resourceSyncItem);
        }
    }

    private XnatAbstractresource getResource(String resourceLabel) {
        XnatProjectdata project = _projectSyncConfiguration.getProject();
        XnatAbstractresource projectResource = null;
        List<XnatAbstractresourceI> resources = project.getResources_resource();
        for (XnatAbstractresourceI resource : resources) {
            if (resource.getLabel().equals(resourceLabel)) {
                projectResource = (XnatAbstractresource) resource;
                break;
            }
        }
        return projectResource;
    }

    private List<Map<String, Object>> getSubjectsModifiedSinceLastSync() {
        //Any entity  that is derived from the subject or linked to the subject
        //if modified, would result in an update in the last_modified column
        //This list would contain any change to any SubjectAssessors
        String query = _queryResultUtil.getQueryForFetchingSubjectsModifiedSinceLastSync();
        return _jdbcTemplate.queryForList(query, _parameters);
    }

    private List<Map<String, Object>> getSubjectsSharedIntoProject() {
        String query = _queryResultUtil.getQueryForSubjectsSharedIntoProject(_projectId);
        return _jdbcTemplate.queryForList(query, _parameters);
    }

    
    private List<Map<String, Object>> getQueryForFetchingSubjectsWhoseExperimentsMarkedOKSinceLastSync(List<String> excludeIds) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("project", _projectId);
        boolean skipSubjectIdCheck = false;
        if (excludeIds.size() > 0) {
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
        _log.debug("Exporting " + localSubject.getId());
        RemoteSubject remoteSubject = new RemoteSubject(_manager, _xnatInfo, _queryResultUtil, (JdbcTemplate) _jdbcTemplate.getJdbcOperations(), localSubject, _projectSyncConfiguration, _user, _syncAll);
        remoteSubject.sync();
    }

    private void deleteSubject(String deletedSubjectLocalId, String deletedSubjectLabel) {
        //Get the remote ID
        //If it exists; delete the remote subject
        String remoteProjectId = _projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteProjectId();
        IdMapper idMapper = new IdMapper(_manager, _queryResultUtil, _jdbcTemplate, _user, _projectSyncConfiguration);
        String remoteId = idMapper.getRemoteAccessionId(deletedSubjectLocalId);
        if (_syncAll) {
            if (remoteId != null) {
                //Delete the remote subject
                XnatSubjectdata subject = new XnatSubjectdata();
                subject.setProject(_projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteProjectId());
                subject.setId(remoteId);
                _log.debug("Deleting subject " + subject.getId() + " from remote project " + subject.getProject());
                try {
                    RemoteConnectionHandler remoteConnectionHandler = new RemoteConnectionHandler(_jdbcTemplate, _queryResultUtil);
                    RemoteConnection connection = remoteConnectionHandler.getConnection(_projectId, _projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl());
                    RemoteConnectionResponse response = _manager.deleteSubject(connection, subject);
                    if (response.wasSuccessful()) {
                        SubjectSyncItem subjectSyncItem = new SubjectSyncItem(deletedSubjectLocalId, deletedSubjectLabel);
                        subjectSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_DELETED);
                        subjectSyncItem.setMessage("Subject " + deletedSubjectLocalId + " has been deleted.");
                        SynchronizationManager.UPDATE_MANIFEST(_projectId, subjectSyncItem);
                        XSyncTools xsyncTools = new XSyncTools(_user, _jdbcTemplate, _queryResultUtil);
                        xsyncTools.saveSyncDetails(_projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSourceProjectId(), deletedSubjectLocalId, remoteId, XsyncUtils.SYNC_STATUS_DELETED, subject.getXSIType(),remoteProjectId);
                        xsyncTools.deleteXsyncRemoteEntry(_projectId, deletedSubjectLocalId);
                    }
                } catch (Exception e) {
                    _log.error(e.toString());
                    SubjectSyncItem subjectSyncItem = new SubjectSyncItem(deletedSubjectLocalId, deletedSubjectLabel);
                    subjectSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_FAILED);
                    subjectSyncItem.setMessage("Subject " + deletedSubjectLocalId + " could not be deleted.");
                    SynchronizationManager.UPDATE_MANIFEST(_projectId, subjectSyncItem);
                }
            } else {
                _log.info("Appears that " + deletedSubjectLocalId + " has been locally deleted between two syncs. Ignoring");
                SubjectSyncItem subjectSyncItem = new SubjectSyncItem(deletedSubjectLocalId, deletedSubjectLabel);
                subjectSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_SKIPPED);
                subjectSyncItem.setMessage("Subject " + deletedSubjectLocalId + " has been deleted locally but has not been synced in the past and hence is being skipped.");
                SynchronizationManager.UPDATE_MANIFEST(_projectId, subjectSyncItem);
            }
        } else {
            if (remoteId != null) {
                SubjectSyncItem subjectSyncItem = new SubjectSyncItem(deletedSubjectLocalId, deletedSubjectLabel);
                subjectSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_SKIPPED);
                subjectSyncItem.setMessage("Subject " + deletedSubjectLocalId + " has been skipped as it appeards to have been deleted locally but synced in the past");
                SynchronizationManager.UPDATE_MANIFEST(_projectId, subjectSyncItem);
            }
        }
    }

}
