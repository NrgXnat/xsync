package org.nrg.xsync.local;

import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.nrg.framework.services.SerializerService;
import org.nrg.xdat.base.BaseElement;
import org.nrg.xdat.model.XnatAbstractresourceI;
import org.nrg.xdat.model.XnatExperimentdataI;
import org.nrg.xdat.model.XnatSubjectdataI;
import org.nrg.xdat.om.XnatAbstractresource;
import org.nrg.xdat.om.XnatExperimentdata;
import org.nrg.xdat.om.XnatImageassessordata;
import org.nrg.xdat.om.XnatProjectdata;
import org.nrg.xdat.om.XnatSubjectdata;
import org.nrg.xft.XFTItem;
import org.nrg.xft.security.UserI;
import org.nrg.xnat.xsync.remote.verify.XsyncProjectVerifier;
import org.nrg.xsync.configuration.ProjectSyncConfiguration;
import org.nrg.xsync.connection.RemoteConnection;
import org.nrg.xsync.connection.RemoteConnectionHandler;
import org.nrg.xsync.connection.RemoteConnectionManager;
import org.nrg.xsync.connection.RemoteConnectionResponse;
import org.nrg.xsync.exception.XsyncRemoteConnectionException;
import org.nrg.xsync.manager.SynchronizationManager;
import org.nrg.xsync.manifest.ExperimentSyncItem;
import org.nrg.xsync.manifest.ResourceSyncItem;
import org.nrg.xsync.manifest.SubjectSyncItem;
import org.nrg.xsync.remote.alias.services.SyncStatusService;
import org.nrg.xsync.tools.XSyncTools;
import org.nrg.xsync.tools.XsyncObserver;
import org.nrg.xsync.tools.XsyncXnatInfo;
import org.nrg.xsync.utils.QueryResultUtil;
import org.nrg.xsync.utils.ResourceUtils;
import org.nrg.xsync.utils.XSyncFailureHandler;
import org.nrg.xsync.utils.XsyncFileUtils;
import org.nrg.xsync.utils.XsyncUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * @author Mohana Ramaratnam
 *
 */
public class RemoteSubject {
	private static final Logger _log = LoggerFactory.getLogger(RemoteSubject.class);

	boolean syncAllStates;
	XnatSubjectdataI localSubject;
	ProjectSyncConfiguration projectSyncConfiguration;
	UserI user;
	SubjectSyncItem subjectSyncInfo ;
	private final XsyncXnatInfo _xnatInfo;
	private final NamedParameterJdbcTemplate _jdbcTemplate;
	private final RemoteConnectionManager _manager;
	private final QueryResultUtil _queryResultUtil;
	private List<XnatAbstractresource> subjectResourcesToBeVerified = new ArrayList<XnatAbstractresource>();
	private XnatProjectdata localProject;
    private final SerializerService          _serializer;
	private final SyncStatusService _syncStatusService;

	
	public RemoteSubject(final RemoteConnectionManager manager, final XsyncXnatInfo xnatInfo, final QueryResultUtil queryResultUtil,
			final JdbcTemplate jdbcTemplate, XnatSubjectdataI localSubject, ProjectSyncConfiguration projectSyncConfiguration,
			UserI user, boolean syncAll, XsyncObserver observer, SerializerService serializer, SyncStatusService syncStatusService) {
		this.localSubject = localSubject;
		this.user = user;
		this.projectSyncConfiguration = projectSyncConfiguration; 
		this.syncAllStates = syncAll;
		subjectSyncInfo = new SubjectSyncItem(localSubject.getId(), localSubject.getLabel());
		subjectSyncInfo.addObserver(observer);
		_jdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
		_manager = manager;
		_xnatInfo = xnatInfo;
		_queryResultUtil = queryResultUtil;
		localProject = XnatProjectdata.getXnatProjectdatasById(localSubject.getProject(), user, false);
		_serializer = serializer;
		_syncStatusService = syncStatusService;
	}
	
	
	public void syncExperiment(String exptId) throws Exception{
		XnatExperimentdata exp = XnatExperimentdata.getXnatExperimentdatasById(exptId, user,true);
		syncExperiment(exp);
	}

	public void syncExperiment(XnatExperimentdata experiment) throws Exception {
		XnatSubjectdata remoteSubject = null;
		//_syncStatusService.registerCurrentExperiment(localProject.getId(), experiment.getLabel(), experiment.getXSIType());
		IdMapper idMapper = new IdMapper(_manager, _queryResultUtil, _jdbcTemplate, user, projectSyncConfiguration);

		try {
			remoteSubject = syncSubject();
			String subject_remote_id = remoteSubject.getId();
			if (subject_remote_id != null) {
				pushExperiment(experiment,remoteSubject, false);
				//_syncStatusService.registerCompletedExperiment(localProject.getId(), experiment.getLabel(), experiment.getXSIType());
				subjectSyncInfo.stateChanged();
			}	
		}catch(Exception e) {
			_log.error("Error syncing subject " + experiment.getLabel() + "  " + e.getMessage());
			//_syncStatusService.registerFailedExperiment(localProject.getId(), experiment.getLabel(), experiment.getXSIType());
			XSyncFailureHandler.handle(localSubject.getProject(),localSubject.getId(),localSubject.getXSIType(),idMapper.getRemoteAccessionId(this.localSubject.getId()), subjectSyncInfo, e);
			throw e;
		}
		SynchronizationManager.UPDATE_MANIFEST(localSubject.getProject(), subjectSyncInfo);
		_log.debug("Syncing subject END: " + localSubject.getLabel());
	}
	
	private XnatSubjectdata syncSubject() throws Exception {
		_log.debug("Syncing subject BEGIN: " + localSubject.getLabel());
		final XFTItem item = ((XnatSubjectdata)localSubject).getItem().copy();
		// Remove assessors from the item.  They will be synced separately.
		final List<XFTItem> subjAssessors  = item.getChildrenOfType("xnat:subjectAssessorData", true);
		for (final XFTItem subjAssessor : subjAssessors) {
			item.removeItem(subjAssessor);
		}
		
		final String remoteProjectId = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteProjectId();
		final XnatSubjectdata newSubject = (XnatSubjectdata) BaseElement.GetGeneratedItem(item);
		newSubject.setProject(remoteProjectId);
		
		final IdMapper idMapper = new IdMapper(_manager, _queryResultUtil, _jdbcTemplate, user, projectSyncConfiguration);
		
		idMapper.correctIDandLabel(newSubject);
		
		//Go through resources; if they are in config and modified since last sync, keep them
		final ResourceFilter resourceMapper = new ResourceFilter(user, _jdbcTemplate, _queryResultUtil);
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
			syncResources(newSubject,resourcesToBeSynced);
		}else {
			newSubject.setId(null);
		}
		return newSubject;
	}
	
	public void sync() throws Exception{
		_log.debug("Syncing subject BEGIN: " + localSubject.getLabel());
		XFTItem item = ((XnatSubjectdata)localSubject).getItem().copy();
		String remoteProjectId = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteProjectId();
	
		XnatSubjectdata newSubject = (XnatSubjectdata) BaseElement.GetGeneratedItem(item);
		newSubject.setProject(remoteProjectId);
		IdMapper idMapper = new IdMapper(_manager, _queryResultUtil, _jdbcTemplate, user, projectSyncConfiguration);
		String subject_remote_id = null;
		subjectSyncInfo.setSyncStatus(XsyncUtils.SYNC_STATUS_BEGINING);
		subjectSyncInfo.stateChanged();
		try {
			
			idMapper.correctIDandLabel(newSubject);
			
			//Go through resources; if they are in config and modified since last sync, keep them
			ResourceFilter resourceMapper = new ResourceFilter(user, _jdbcTemplate, _queryResultUtil);
			Map<String,List<XnatAbstractresourceI>> resourcesToBeSynced = resourceMapper.select(newSubject, localSubject.getId(), projectSyncConfiguration);

			//Go through experiments; if they are in config, keep them
			ExperimentFilter experimentMapper = new ExperimentFilter(_manager, _jdbcTemplate, _xnatInfo, _queryResultUtil, user, projectSyncConfiguration);
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
				syncResources(newSubject,resourcesToBeSynced);
				syncExperiments(newSubject,experimentsToBeSynced);
//				subjectSyncInfo.stateChanged();
			}	
		}catch(Exception e) {
			_log.error("Error syncing subject " + newSubject.getLabel() + "  " + e.getMessage());
			XSyncFailureHandler.handle(localSubject.getProject(),localSubject.getId(),localSubject.getXSIType(),idMapper.getRemoteAccessionId(this.localSubject.getId()), subjectSyncInfo, e);
			throw e;
		}
		verifySubjectResources(subject_remote_id);
		subjectSyncInfo.updateSyncStatus(XsyncUtils.SYNC_STATUS_SYNCED_AND_NOT_VERIFIED,"Subject " + localSubject.getLabel() );
		subjectSyncInfo.stateChanged();
		SynchronizationManager.UPDATE_MANIFEST(localSubject.getProject(), subjectSyncInfo);
		_log.debug("Syncing subject END: " + localSubject.getLabel());	
	}

	

	private String storeSubject(XnatSubjectdataI remoteSubject) throws Exception {
		//October 3, 2016 - MR - Xsync will push all the subject metadata irrespective of syncNewOnly or not
		return syncSubjectMetaData(remoteSubject);
	}

	private String syncSubjectMetaData(XnatSubjectdataI remoteSubject) throws Exception {
		String subject_remote_id  = null;
		try {
			 RemoteConnectionHandler remoteConnectionHandler = new RemoteConnectionHandler(_jdbcTemplate, _queryResultUtil);
			 RemoteConnection connection = remoteConnectionHandler.getConnection(projectSyncConfiguration.getProject().getId(),projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl());
			 RemoteConnectionResponse response = _manager.importSubject(connection, (XnatSubjectdata)remoteSubject);
			 if (response.wasSuccessful()) {
				 subject_remote_id = response.getResponseBody();
				 //WorkFlowUtils wrkFlowUtils = new WorkFlowUtils(_manager, _queryResultUtil,_jdbcTemplate, projectSyncConfiguration);
				 //wrkFlowUtils.createWorkflowAtRemote((XnatSubjectdata)remoteSubject,subject_remote_id,remoteSubject.getProject(),"Complete");
			 } else 
				 XSyncFailureHandler.handle(projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSourceProjectId(),localSubject.getId(),localSubject.getXSIType(),null, subjectSyncInfo, response);
			 return subject_remote_id;
		 }catch(Exception e) {
			  saveSyncDetails(localSubject.getId(), null, null, XsyncUtils.SYNC_STATUS_FAILED, localSubject.getXSIType());
			 _log.error(e.toString());
			 throw e;
		 }
		
	}
	
	private RemoteConnectionResponse deleteSubjectResource(XnatSubjectdataI remoteSubject, String resourceLabel) throws Exception {
		 try {
			 RemoteConnectionHandler remoteConnectionHandler = new RemoteConnectionHandler(_jdbcTemplate, _queryResultUtil);
			 RemoteConnection connection = remoteConnectionHandler.getConnection(projectSyncConfiguration.getProject().getId(),projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl());
			 return _manager.deleteSubjectResource(connection, (XnatSubjectdata)remoteSubject, resourceLabel);
		 }catch(Exception e) {
			 _log.error(e.toString());
			 throw e;
		 }
		}

	private RemoteConnectionResponse updateSubjectResource(XnatSubjectdataI remoteSubject, String resourceLabel, File zipFile) throws Exception {
		 try {
			 RemoteConnectionHandler remoteConnectionHandler = new RemoteConnectionHandler(_jdbcTemplate, _queryResultUtil);
			 RemoteConnection connection = remoteConnectionHandler.getConnection(projectSyncConfiguration.getProject().getId(),projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl());

			 return _manager.importSubjectResource(connection, (XnatSubjectdata)remoteSubject, resourceLabel, zipFile);
		 }catch(Exception e) {
			 _log.error(e.toString());
			 throw e;
		 }
		}


	private RemoteConnectionResponse deleteExperiment(XnatExperimentdata experiment) throws Exception {
		//If the experiment was already stored, we have the remote id
		//Use that id to delete the experiment
		//If not, the experiment was never synced. So ignore.
		IdMapper idMapper = new IdMapper(_manager, _queryResultUtil, _jdbcTemplate, user,projectSyncConfiguration);
		String remoteId = idMapper.getRemoteAccessionId(experiment.getId());
		String localId = experiment.getId();
		String localLabel = experiment.getLabel();
		 ExperimentSyncItem expSyncItem = new ExperimentSyncItem(experiment.getId(),experiment.getLabel());
		 expSyncItem.setRemoteId(remoteId);
		if (remoteId != null)  {
			experiment.setId(remoteId);
			 try {
				 RemoteConnectionHandler remoteConnectionHandler = new RemoteConnectionHandler(_jdbcTemplate, _queryResultUtil);
				 RemoteConnection connection = remoteConnectionHandler.getConnection(projectSyncConfiguration.getProject().getId(),projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl());
				 RemoteConnectionResponse response = _manager.deleteExperiment(connection, experiment);
				 if (response.wasSuccessful()) {
					 expSyncItem.setMessage("Subject " + localSubject.getLabel() + " experiment " + localLabel + " deleted. " );
					 expSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_DELETED);
					 XSyncTools xsyncTools = new XSyncTools(user, _jdbcTemplate, _queryResultUtil);
					 xsyncTools.deleteXsyncRemoteEntry(this.localSubject.getProject(), localId);
				 }else {
					 expSyncItem.setMessage("Subject " + localSubject.getLabel() + " experiment " + localLabel + " could not be deleted. " + response.getResponseBody());
					 expSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_FAILED);
				 }
				 subjectSyncInfo.addExperiment(expSyncItem);
				 return response;
			 }catch(Exception e) {
				 _log.error(e.toString());
				 expSyncItem.setMessage("Subject " + localSubject.getLabel() + " experiment " + localLabel + " could not be deleted. " + e.getMessage());
				 expSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_FAILED);
				 subjectSyncInfo.addExperiment(expSyncItem);
				 throw e;
			 }
		}else 
			return null;
	}
	
	private void saveSyncDetails(String local_id, String remote_id, String remote_label, String syncStatus,String xsiType) {
		subjectSyncInfo.setSyncStatus(syncStatus);
		subjectSyncInfo.setRemoteId(remote_id);
		subjectSyncInfo.setXsiType(xsiType);
		subjectSyncInfo.setRemoteLabel(remote_label);
		String remoteProjectId = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteProjectId();
		String remoteUrl = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl();
		
		XSyncTools xsyncTools = new XSyncTools(user, _jdbcTemplate, _queryResultUtil);
		xsyncTools.saveSyncDetails(projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSourceProjectId(), local_id, remote_id,syncStatus,xsiType,remoteProjectId, remoteUrl);
	}


	
	private void syncResources(XnatSubjectdataI remoteSubject,Map<String,List<XnatAbstractresourceI>> resourcesToBeSynced) throws Exception{
		List<XnatAbstractresourceI> deletedResources = resourcesToBeSynced.get(QueryResultUtil.DELETE_STATUS);
		List<XnatAbstractresourceI> updatedResources = resourcesToBeSynced.get(QueryResultUtil.ACTIVE_STATUS);
		List<XnatAbstractresourceI> newResources = resourcesToBeSynced.get(QueryResultUtil.NEW_STATUS);
		if (deletedResources != null && deletedResources.size() > 0) {
			//Remove each of these resources from the Remote site
			if (syncAllStates) {
				for (XnatAbstractresourceI resource:deletedResources) {
					try {
						RemoteConnectionResponse deleteResponse = this.deleteSubjectResource(remoteSubject,resource.getLabel());
						ResourceSyncItem resourceSyncItem = new ResourceSyncItem(localSubject.getLabel(),resource.getLabel());
						if (deleteResponse.wasSuccessful()) {
							resourceSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_DELETED);
							resourceSyncItem.setMessage("Subject " + localSubject.getLabel() + " resource " + resource.getLabel() + " deleted ");
							XSyncTools xsyncTools = new XSyncTools(user, _jdbcTemplate, _queryResultUtil);
							xsyncTools.deleteXsyncRemoteEntry(localSubject.getId(), resource.getLabel());
						}else {
							resourceSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_FAILED);
							resourceSyncItem.setMessage("Subject " + localSubject.getLabel() + " resource " + resource.getLabel() + " could not be deleted. " + deleteResponse.getResponseBody());
						}
						subjectSyncInfo.addResources(resourceSyncItem);
					}catch(Exception e) {
						_log.error("Could not delete resource " + resource.getLabel() + " for subject " + remoteSubject.getId(),e);
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
				pushResource(remoteSubject,resource);
			}
			for (XnatAbstractresourceI resource:newResources) {
				pushResource(remoteSubject,resource);
			}
		}else { //Only New
			for (XnatAbstractresourceI resource:newResources) {
				pushResource(remoteSubject,resource);
			}
		}
	}

	private void pushResource(XnatSubjectdataI remoteSubject,XnatAbstractresourceI resource) {
		String localProjectArchivePath = localProject.getArchiveRootPath();
		String remoteProjectId = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteProjectId();
		String rLabel = resource.getLabel() == null ? XsyncUtils.RESOURCE_NO_LABEL:resource.getLabel();
		try {
			String archiveDirectory = ((XnatAbstractresource)resource).getFullPath(localProjectArchivePath);
			File resourcePath = new File(archiveDirectory);
			if (resourcePath.exists() && resourcePath.isFile()) {
				resourcePath = resourcePath.getParentFile();
			}
			File zipFile = null;
			try {
				zipFile = new XsyncFileUtils().buildZip(remoteProjectId, resourcePath);
				RemoteConnectionResponse updateResponse = this.updateSubjectResource(remoteSubject,resource.getLabel(), zipFile);
				if (updateResponse.wasSuccessful()) {
					subjectResourcesToBeVerified.add((XnatAbstractresource)resource);
				}else {
					ResourceSyncItem resourceSyncItem = new ResourceSyncItem(localSubject.getLabel(),rLabel);
					if (resource.getFileCount() != null)
						resourceSyncItem.setFileCount(resource.getFileCount());
					else 
						resourceSyncItem.setFileCount(0);
					if (resource.getFileSize() != null)
						resourceSyncItem.setFileSize(resource.getFileSize());
					else 
						resourceSyncItem.setFileSize(new Long(0));
					resourceSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_FAILED);
					resourceSyncItem.setMessage("Subject " + localSubject.getLabel() + " resource " + rLabel + " could not be updated. " + updateResponse.getResponseBody() );
					subjectSyncInfo.addResources(resourceSyncItem);
				}
			}catch(Exception e) {
				throw(e);
			}finally {
				if (zipFile != null) zipFile.delete();
			}
		}catch(Exception e) {
			_log.error("Could not update resource " + resource.getLabel() + " for subject " + remoteSubject.getId(),e);
			ResourceSyncItem resourceSyncItem = new ResourceSyncItem(localSubject.getLabel(),rLabel);
			if (resource.getFileCount() != null)
				resourceSyncItem.setFileCount(resource.getFileCount());
			else 
				resourceSyncItem.setFileCount(0);
			if (resource.getFileSize() != null)
				resourceSyncItem.setFileSize(resource.getFileSize());
			else 
				resourceSyncItem.setFileSize(new Long(0));
			resourceSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_FAILED);
			resourceSyncItem.setMessage("Subject " + localSubject.getLabel() + " resource " + rLabel + " could not be updated. " + e.getMessage() );
			subjectSyncInfo.addResources(resourceSyncItem);
		}
	}
	
	
	private void syncExperiments(XnatSubjectdataI remoteSubject,Map<String,List<XnatExperimentdataI>> experimentsToBeSynced) throws Exception{
		final String remoteProjectId = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteProjectId();
		final List<XnatExperimentdataI> deletedExperiments = experimentsToBeSynced.get(QueryResultUtil.DELETE_STATUS);
		final List<XnatExperimentdataI> updatedExperiments = experimentsToBeSynced.get(QueryResultUtil.ACTIVE_STATUS);
		final List<XnatExperimentdataI> newExperiments = experimentsToBeSynced.get(QueryResultUtil.NEW_STATUS);
		final List<XnatExperimentdataI> okToSyncExperiments = experimentsToBeSynced.get(QueryResultUtil.OK_TO_SYNC_STATUS);
		final List<XnatExperimentdataI> failedSyncExperiments = experimentsToBeSynced.get(QueryResultUtil.FAILED_STATUS);
		final List<XnatExperimentdataI> syncCalledExperiments = new ArrayList<>();

		if (syncAllStates) {
			//Delete experiments
			if (deletedExperiments != null && deletedExperiments.size() > 0) {
				//Remove each of these resources from the Remote site
				for (final XnatExperimentdataI experiment:deletedExperiments) {
					if (experiment.getXSIType().startsWith("xsync:")) {
						continue;
					}
					try {
						final XnatExperimentdata exp = (XnatExperimentdata)experiment;
						exp.setProject(remoteProjectId);
						exp.getItem().setProperty("subject_ID", remoteSubject.getId());
						this.deleteExperiment(exp);
					}catch(Exception e) {
						_log.error("Could not delete experiment " + experiment.getId() + " for subject " + remoteSubject.getId() + " " + e.getMessage(),e);
					}
				}
			}
			//Update the modified experiments
			if (updatedExperiments != null && updatedExperiments.size() > 0) {
				for (final XnatExperimentdataI experiment:updatedExperiments) {
					pushExperiment(experiment,remoteSubject,false);
					syncCalledExperiments.add(experiment);
				}
			}
		}
		//Push the new experiments
		if (newExperiments != null && newExperiments.size() > 0) {
			for (final XnatExperimentdataI experiment : newExperiments) {
				if (!experimentExistsAtRemoteSiteWhenPushingNew(experiment)) {
					pushExperiment(experiment,remoteSubject, true);
					syncCalledExperiments.add(experiment);
				} else {
					final ExperimentSyncItem expSyncItem = new ExperimentSyncItem(experiment.getId(),experiment.getLabel());
					expSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_SKIPPED);
					// TODO:  Should probably separate out the last case.  Maybe report on the last status.
					expSyncItem.setMessage("Appears that the session was transferred without using XSync, that the configured " +
											"XSync remote ID has changed since this session was sent or that this session was " +
											"sent as part of a failed sync process.");
					subjectSyncInfo.addExperiment(expSyncItem);
				}
			}
		}
		//Irrespective of the syncOnlyNwew Flag, the OK to Sync experiments must be pushed
		if (okToSyncExperiments != null && okToSyncExperiments.size() > 0) {
			for (final XnatExperimentdataI experiment:okToSyncExperiments) {
				pushExperiment(experiment,remoteSubject,false);
				syncCalledExperiments.add(experiment);
			}
		}
		// If it hasn't already been synced, sync any experiments with a failed sync status
		for (final XnatExperimentdataI experiment:failedSyncExperiments) {
			if (!syncCalledExperiments.contains(experiment)) {
				pushExperiment(experiment,remoteSubject, false);
			}
		}
	}
	
	private boolean experimentExistsAtRemoteSiteWhenPushingNew(XnatExperimentdataI experiment) throws Exception {
		final String remoteUrl = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl();
		final String remoteProjectId = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteProjectId();
		final String uri = remoteUrl+"/data/archive/experiments?xnat:imagesessiondata/project=" + remoteProjectId + "&xnat:mrsessiondata/label=" + experiment.getLabel() + "&format=json" ;
		final XsyncProjectVerifier projectVerifier = new XsyncProjectVerifier(_manager,_queryResultUtil,_jdbcTemplate, projectSyncConfiguration, _serializer);
	    final RemoteConnectionResponse response = projectVerifier.get(uri);
	    if (!response.wasSuccessful()) {
	    	throw new XsyncRemoteConnectionException("ERROR:  Could not check remote site for experiment"); 
	    } else {
			final Type type = new TypeToken<Map<String, Map<String,Object>>>(){}.getType();
			final Map<String, Map<String,Object>> configMap = new Gson().fromJson(response.getResponseBody(), type);
			if (configMap.containsKey("ResultSet") && configMap.get("ResultSet").containsKey("totalRecords")) {
				final Integer totalRecords = Integer.valueOf(configMap.get("ResultSet").get("totalRecords").toString());
				if (totalRecords>=1) {
					return true;
				}
			} else {
				throw new XsyncRemoteConnectionException("ERROR:  Could not check remote site for experiment (unexpected results)"); 
			}
	    }
	    return false;
	}


	private void pushExperiment(XnatExperimentdataI assess, XnatSubjectdataI remoteSubject, boolean syncIfNotSyncedInPast) throws Exception {
		XsyncExperimentTransfer syncExptransfer = new XsyncExperimentTransfer(_manager,_xnatInfo, _queryResultUtil, _jdbcTemplate, projectSyncConfiguration, user,
					subjectSyncInfo, localSubject, _serializer, _syncStatusService, localProject, syncIfNotSyncedInPast);
		syncExptransfer.syncExperiment(assess, remoteSubject);

	}
	


	@SuppressWarnings("unused")
	private String getResourcePath(XnatImageassessordata orig, XnatAbstractresource resource) {
		String path  = null;
		XnatAbstractresource origResource = null;
		for (XnatAbstractresourceI r:orig.getResources_resource()) {
			if (r.getLabel().equals(resource.getLabel())) {
				origResource = (XnatAbstractresource)r;
				break;
			}
		}
		if (origResource != null) {
			try {
				path = origResource.getFullPath(orig.getArchiveRootPath());
			}catch(Exception e) {
				_log.error(e.getMessage(),e);
			}
		}
		return path;
	}

	@SuppressWarnings("unused")
	private String getResourcePath(String parent, XnatAbstractresource resource) {
		String path  = null;
		try {
			path = resource.getFullPath(parent);
			System.out.println("Resource Path " + path + " Label " + resource.getLabel());
		}catch(Exception e) {
			_log.error(e.getMessage(),e);
		}
		return path;
	}

	
    private void verifySubjectResources(String remoteSubjectId) {
        ResourceUtils resourceUtils = new ResourceUtils(projectSyncConfiguration);
        String remoteProjectId = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteProjectId();
		final String remoteUrl = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl();

        XsyncProjectVerifier projectResourceVerifier = new XsyncProjectVerifier(_manager,_queryResultUtil,_jdbcTemplate, projectSyncConfiguration, _serializer);
        String localProjectArchivePath = localProject.getArchiveRootPath();

        for (XnatAbstractresource rsc:subjectResourcesToBeVerified) {
            String rLabel = rsc.getLabel() == null ?  XsyncUtils.RESOURCE_NO_LABEL:rsc.getLabel();
        	ResourceSyncItem resourceSyncItem = new ResourceSyncItem(localProject.getId(), rLabel);
    		if (rsc.getFileCount() != null)
    			resourceSyncItem.setFileCount(rsc.getFileCount());
            if (rsc.getFileSize() != null)
            	resourceSyncItem.setFileSize(rsc.getFileSize());
            String archiveDirectory = rsc.getFullPath(localProjectArchivePath);
		    final String uri = remoteUrl+"/data/archive/projects/"+remoteProjectId+"/subjects/"+ remoteSubjectId  + "/resources/"+rsc.getLabel() + "/files?format=JSON";
		    Map<String,String> fileComparison;
		    try {
			    fileComparison = projectResourceVerifier.verify(archiveDirectory, remoteProjectId , rsc.getLabel(), uri);
		    }catch(Exception e) {
		    	_log.error(e.getMessage(),e);
	 			fileComparison = new HashMap<String,String>();
				fileComparison.put(XsyncUtils.XSYNC_VERIFICATION_STATUS, XsyncUtils.XSYNC_VERIFICATION_STATUS_FAILED_TO_CONNECT);
		    }
            resourceUtils.setSyncStatus(fileComparison, resourceSyncItem, "Subject " + localSubject.getLabel() + "  resource " + rLabel);
			subjectSyncInfo.addResources(resourceSyncItem);
        }
    }

	

}
