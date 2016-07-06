package org.nrg.xsync.discoverer;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import javax.mail.MessagingException;

import org.nrg.xdat.XDAT;
import org.nrg.xdat.model.XnatAbstractresourceI;
import org.nrg.xdat.om.XnatAbstractresource;
import org.nrg.xdat.om.XnatProjectdata;
import org.nrg.xdat.om.XnatSubjectdata;
import org.nrg.xdat.turbine.utils.AdminUtils;
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
import org.nrg.xsync.utils.QueryResultUtil;
import org.nrg.xsync.utils.XsyncFileUtils;
import org.nrg.xsync.utils.XsyncUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * @author Mohana Ramaratnam
 *
 */

public class ProjectChangeDiscoverer implements Callable<java.lang.Void>{
	private static final Logger _log = LoggerFactory.getLogger(ProjectChangeDiscoverer.class);

	//When created entry is in MetaData;
	//status field tells about the status of the entity
	//When updated entry is in History
	String _projectId;
	UserI _user;
	MapSqlParameterSource parameters;
	ProjectSyncConfiguration projectSyncConfiguration;
	boolean syncAll;

	public ProjectChangeDiscoverer(String projectId, UserI user) throws XsyncNotConfiguredException{
		_projectId = projectId;
		_user = user;
		parameters = new MapSqlParameterSource();
		parameters.addValue("project", _projectId);
		projectSyncConfiguration = new ProjectSyncConfiguration(_projectId, _user);
		syncAll = this.projectSyncConfiguration.isSetToSyncNewOnly()?false:true;
	}



	/**
	 * @return the _lastSyncStartTime
	 */
	public Object getLastSyncStartTime() {
		Object _lastSyncStartTime = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getSyncStartTime();
		return _lastSyncStartTime;
	}

	public java.lang.Void call() throws Exception {
		this.sync();
		return null;
	}
	private synchronized  void sync() {
		//Create export Build dir
		//Write all the files
		//Upload the XAR
		//_log.debug(projectSyncConfiguration.toString());
		try {
			Boolean isSyncEnabled = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncEnabled();
			if (!isSyncEnabled) {
				return;
			}
			Boolean isSyncBlocked = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncBlocked();
			if (isSyncBlocked != null && isSyncBlocked) {
				try {
					System.out.println("Sync is blocked ");
					XDAT.getMailService().sendHtmlMessage(AdminUtils.getAuthorizerEmailId(), _user.getEmail(), "Project " + _projectId + " sync skipped ",
							"<html><body><p>Project "+ _projectId  + " sync skipped </p></body></html>");
					_log.debug("Sync Blocked");
				} catch (MessagingException me) {
					_log.error("Failed to send email.", me);
				} catch (Exception e) {
					_log.error("Failed to send email.", e);
				}
				return;
			}
			saveSyncBlockStatus(new Boolean(true));
			//try {
			//	Thread.sleep(120000);
			//}catch(Exception e){}
			XnatProjectdata project = projectSyncConfiguration.getProject();
			String remoteProjectId = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteProjectId();
			String remoteHost = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl();

			SynchronizationManager.BEGIN_SYNC(project.getId(),remoteProjectId, remoteHost, _user);
			syncProjectResources();
			List<Map<String,Object>> subjectRows = getSubjectsModifiedSinceLastSync();
			List<String> subjectIds = new ArrayList<String>();
			for (Map<String,Object> row:subjectRows) {
				subjectIds.add((String)row.get("id"));
			}
			for (Map<String,Object> row:subjectRows) {
				_log.debug("Subject " + row.get("id") + " has been modfied since " + this.getLastSyncStartTime());
				XnatSubjectdata localSubject = XnatSubjectdata.getXnatSubjectdatasById(row.get("id"), _user, true);
				if (localSubject == null) {
					//Local Subject has been deleted; Delete the remote subject
					deleteSubject((String)row.get("id"),(String)row.get("label") );
				}else
					syncSubject(localSubject);
			}
			//Save into the DB the starttime and end-time
			//Clear the time logs
			saveSyncBlockStatus(new Boolean(false));
			SynchronizationManager.END_SYNC(project.getId());
		}catch(Exception e) {
			//Roll back the syncBlocked flag
			e.printStackTrace();
			saveSyncBlockStatus(new Boolean(false));
		}
	}

	private void saveSyncBlockStatus(Boolean status) {
		try {
			projectSyncConfiguration.getProjectSyncConfigurationFromDB().setSyncBlocked(status);
			//Backward compatible XNAT 1.6.5 does not have ADMIN_EVENT method
			EventMetaI c = EventUtils.DEFAULT_EVENT(_user,"ADMIN_EVENT occurred");
			projectSyncConfiguration.getProjectSyncConfigurationFromDB().save(_user, false, true,c);
		}catch(Exception e) {
			_log.debug("Unable to save synchronization  details for project: " + _projectId + " Cause:" + e.getMessage());
			
		}
	}
	//TODO
	//Change the implementation to use the ResourceFilter class -
	//This class returns  NEW, UPDATED and DELETED lists of resources
	private void syncProjectResources() {
		List<Map<String,Object>> resourceRows = getProjectResourcesModifiedSinceLastSync();
		XnatProjectdata localProject = XnatProjectdata.getXnatProjectdatasById(_projectId, _user, false);
		String localProjectArchivePath = localProject.getArchiveRootPath();
		for (Map<String,Object> row:resourceRows) {
			String label = (String)row.get("label");
			_log.debug("Resource " + row.get("label") + " has been modfied since " + this.getLastSyncStartTime());
			if (projectSyncConfiguration.isResourceToBeSynced(label) ) {
				String status = (String)row.get("status");
				if (syncAll) {
					if (QueryResultUtil.DELETE_STATUS.equals(status) ) {
						deleteProjectResource(label);
					}else { //Resource is active and has to be updated
						updateProjectResource(localProjectArchivePath,label);
					}
				}else {
					//If its a new addition, sync it. If its an update or a delete skip it.
					if (QueryResultUtil.DELETE_STATUS.equals(status) ) {
						  ResourceSyncItem resourceSyncItem = new ResourceSyncItem(_projectId,label);
						  resourceSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_SKIPPED);
						  resourceSyncItem.setMessage("Project resource " + label + " has been deleted, however, it was not synced as project is configured not to sync automatically ");
						  SynchronizationManager.UPDATE_MANIFEST(_projectId, resourceSyncItem);
					}else {
						//Check if its a new resource or an updated resource
						XSyncTools xsyncTools = new XSyncTools(_user);
						XnatAbstractresource resource = getResource(label);
						if (xsyncTools.hasBeenSyncedAlready(_projectId, label,resource.getXSIType())) {
							 //This is an instance of Update and auto-update is set to false; skip this resource
							  ResourceSyncItem resourceSyncItem = new ResourceSyncItem(_projectId,label);
							  resourceSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_SKIPPED);
							  resourceSyncItem.setMessage("Project resource " + label + " has been updated, however, it was not synced as project is configured not to sync automatically ");
							  SynchronizationManager.UPDATE_MANIFEST(_projectId, resourceSyncItem);
						}else {
							//New resource has been added. Push this resource
							updateProjectResource(localProjectArchivePath,label);
						}
					}
				}
			}
		}
	}

	private void deleteProjectResource(String resourceLabel) {
		 RemoteConnectionManager remoteConnectionManager = new RemoteConnectionManager();
		 try {
			String remoteProjectId = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteProjectId();
			 RemoteConnectionHandler remoteConnectionHandler = new RemoteConnectionHandler();
			 RemoteConnection connection = remoteConnectionHandler.getConnection(_projectId,projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl());

			RemoteConnectionResponse response =  remoteConnectionManager.deleteProjectResource(connection,  remoteProjectId, resourceLabel);
			ResourceSyncItem resourceSyncItem = new ResourceSyncItem(_projectId,resourceLabel);
			if (response.wasSuccessful()) {
				resourceSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_DELETED);
				resourceSyncItem.setMessage("Project resource " + resourceLabel + " deleted ");
				//Remove the entry from the remote map table; so that in the future we can have same named resource
				XSyncTools xsyncTools = new XSyncTools(_user);
				xsyncTools.deleteXsyncRemoteEntry(_projectId, resourceLabel);
			}else {
				resourceSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_FAILED);
				resourceSyncItem.setMessage("Project resource " + resourceLabel + " could not be deleted. Cause: " + response.getResponseBody());
			}
			SynchronizationManager.UPDATE_MANIFEST(_projectId, resourceSyncItem);
		 }catch(Exception e) {
			 _log.error(e.toString());
			ResourceSyncItem resourceSyncItem = new ResourceSyncItem(_projectId,resourceLabel);
			resourceSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_FAILED);
			resourceSyncItem.setMessage("Project resource " + resourceLabel + " could not be deleted. Cause: " + e.getMessage());
			SynchronizationManager.UPDATE_MANIFEST(_projectId, resourceSyncItem);
		 }
	}

	private void updateProjectResource(String localProjectArchivePath, String resourceLabel) {
		 RemoteConnectionManager remoteConnectionManager = new RemoteConnectionManager();
		 String remoteProjectId = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteProjectId();
		 try {
			   XnatAbstractresource resource = getResource(resourceLabel);
				ResourceSyncItem resourceSyncItem = new ResourceSyncItem(_projectId,resourceLabel);
				resourceSyncItem.setFileCount(resource.getFileCount());
				resourceSyncItem.setFileSize(resource.getFileSize());
			    if (resource != null) {
					String archiveDirectory = resource.getFullPath(localProjectArchivePath);
					File resourcePath = new File(archiveDirectory);
					if (resourcePath.exists() && resourcePath.isFile()) {
						resourcePath = resourcePath.getParentFile();
						File zipFile = new XsyncFileUtils().buildZip(remoteProjectId,resourcePath);
						 RemoteConnectionHandler remoteConnectionHandler = new RemoteConnectionHandler();
						 RemoteConnection connection = remoteConnectionHandler.getConnection(_projectId,projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl());
					    RemoteConnectionResponse response =  remoteConnectionManager.importProjectResource(connection,  remoteProjectId, resourceLabel, zipFile);
						if (response.wasSuccessful()) {
							if(zipFile.exists()) zipFile.delete();
							resourceSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_SYNCED);
							resourceSyncItem.setMessage("Project resource " + resourceLabel + " updated ");
							XSyncTools xsyncTools = new XSyncTools(_user);
							xsyncTools.saveSyncDetails(_projectId, resource.getLabel(),resource.getLabel(),XsyncUtils.SYNC_STATUS_SYNCED,resource.getXSIType());
						}else {
							resourceSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_FAILED);
							resourceSyncItem.setMessage("Project resource " + resourceLabel + " could not be updated. Cause: " + response.getResponseBody());
						}
						SynchronizationManager.UPDATE_MANIFEST(_projectId, resourceSyncItem);
					}
			   }else {
					resourceSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_FAILED);
					resourceSyncItem.setMessage("Project resource " + resourceLabel + " could not be updated. Cause: No files found on filesystem");
				    SynchronizationManager.UPDATE_MANIFEST(_projectId, resourceSyncItem);
			   }
		 }catch(Exception e) {
			 _log.error(e.toString());
				ResourceSyncItem resourceSyncItem = new ResourceSyncItem(_projectId,resourceLabel);
				resourceSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_FAILED);
				resourceSyncItem.setMessage("Project resource " + resourceLabel + " could not be updated. Cause: " + e.getMessage());
				SynchronizationManager.UPDATE_MANIFEST(_projectId, resourceSyncItem);
		 }
	}


	private XnatAbstractresource getResource(String resourceLabel) {
		XnatProjectdata project = projectSyncConfiguration.getProject();
		XnatAbstractresource projectResource = null;
		List<XnatAbstractresourceI> resources = project.getResources_resource();
		for (XnatAbstractresourceI resource:resources) {
			if (resource.getLabel().equals(resourceLabel)) {
				projectResource = (XnatAbstractresource)resource;
				break;
			}
		}
		return projectResource;
	}

	private List<Map<String,Object>> getSubjectsModifiedSinceLastSync() {
		//Any entity  that is derived from the subject or linked to the subject
		//if modified, would result in an update in the last_modified column
		//This list would contain any change to any SubjectAssessors
		QueryResultUtil queryUtil = new QueryResultUtil();
		String query = queryUtil.getQueryForFetchingSubjectsModifiedSinceLastSync();
		NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(
				new JdbcTemplate(XDAT.getDataSource()));
		 List<Map<String,Object>> results = jdbcTemplate.queryForList(query, parameters);
		return results;
	}

	private List<Map<String,Object>> getQueryForFetchingSubjectsWhoseExperimentsMarkedOKSinceLastSync(List<String> excludeIds) {
		QueryResultUtil queryUtil = new QueryResultUtil();
		MapSqlParameterSource parameters = new MapSqlParameterSource();
		parameters.addValue("project", _projectId);
		boolean skipSubjectIdCheck = false;
		if (excludeIds.size() > 0) {
			parameters.addValue(QueryResultUtil.SUBJECT_IDS, excludeIds);
		}else
			skipSubjectIdCheck = true;
		String query = queryUtil.getQueryForFetchingSubjectsWhoseExperimentsMarkedOKSinceLastSync(skipSubjectIdCheck);
		NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(
				new JdbcTemplate(XDAT.getDataSource()));
		 List<Map<String,Object>> results = jdbcTemplate.queryForList(query, parameters);
		return results;
	}

	private List<Map<String,Object>> getProjectResourcesModifiedSinceLastSync() {
		QueryResultUtil queryUtil = new QueryResultUtil();
		String query = queryUtil.getQueryForFetchingProjectResourcesModifiedSinceLastSync();
		NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(
				new JdbcTemplate(XDAT.getDataSource()));
		 List<Map<String,Object>> results = jdbcTemplate.queryForList(query, parameters);
		return results;
	}

	private void syncSubject(XnatSubjectdata localSubject) throws Exception{
		_log.debug("Exporting " + localSubject.getId());
		RemoteSubject remoteSubject = new RemoteSubject(localSubject,projectSyncConfiguration,_user, syncAll);
		remoteSubject.sync();
	}

	private void deleteSubject(String deletedSubjectLocalId, String deletedSubjectLabel) {
		//Get the remote ID
		//If it exists; delete the remote subject
		IdMapper idMapper = new IdMapper(_user,projectSyncConfiguration);
		String remoteId = idMapper.getRemoteAccessionId(deletedSubjectLocalId);
		if (syncAll) {
			if (remoteId != null) {
				//Delete the remote subject
				XnatSubjectdata subject = new XnatSubjectdata();
				subject.setProject(projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteProjectId());
				subject.setId(remoteId);
				 _log.debug("Deleting subject " + subject.getId() + " from remote project " + subject.getProject());
				 try {
					 RemoteConnectionManager remoteConnectionManager = new RemoteConnectionManager();
					 RemoteConnectionHandler remoteConnectionHandler = new RemoteConnectionHandler();
					 RemoteConnection connection = remoteConnectionHandler.getConnection(_projectId,projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl());
					 RemoteConnectionResponse response =  remoteConnectionManager.deleteSubject(connection, subject);
					 if (response.wasSuccessful()) {
						  SubjectSyncItem subjectSyncItem = new SubjectSyncItem(deletedSubjectLocalId,deletedSubjectLabel);
						  subjectSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_DELETED);
						  subjectSyncItem.setMessage("Subject " + deletedSubjectLocalId + " has been deleted.");
						  SynchronizationManager.UPDATE_MANIFEST(_projectId, subjectSyncItem);
						  XSyncTools xsyncTools = new XSyncTools(_user);
						  xsyncTools.saveSyncDetails(projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSourceProjectId(), deletedSubjectLocalId, remoteId,XsyncUtils.SYNC_STATUS_DELETED, subject.getXSIType());
						  xsyncTools.deleteXsyncRemoteEntry(_projectId,deletedSubjectLocalId);
					 }
				 }catch(Exception e) {
					 _log.error(e.toString());
					  SubjectSyncItem subjectSyncItem = new SubjectSyncItem(deletedSubjectLocalId,deletedSubjectLabel);
					  subjectSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_FAILED);
					  subjectSyncItem.setMessage("Subject " + deletedSubjectLocalId + " could not be deleted.");
					  SynchronizationManager.UPDATE_MANIFEST(_projectId, subjectSyncItem);
				 }
			}else {
				_log.info("Appears that " + deletedSubjectLocalId + " has been locally deleted between two syncs. Ignoring");
				  SubjectSyncItem subjectSyncItem = new SubjectSyncItem(deletedSubjectLocalId,deletedSubjectLabel);
				  subjectSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_SKIPPED);
				  subjectSyncItem.setMessage("Subject " + deletedSubjectLocalId + " has been skipped as it appeards to have been deleted between two sync events.");
				  SynchronizationManager.UPDATE_MANIFEST(_projectId, subjectSyncItem);
			}
		}else {
			 if (remoteId != null) {
				  SubjectSyncItem subjectSyncItem = new SubjectSyncItem(deletedSubjectLocalId,deletedSubjectLabel);
				  subjectSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_SKIPPED);
				  subjectSyncItem.setMessage("Subject " + deletedSubjectLocalId + " has been skipped as it appeards to have been deleted locally but synced in the past");
				  SynchronizationManager.UPDATE_MANIFEST(_projectId, subjectSyncItem);
			 }
		}
	}



}
