package org.nrg.xsync.local;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.nrg.xdat.base.BaseElement;
import org.nrg.xdat.model.XnatAbstractresourceI;
import org.nrg.xdat.model.XnatExperimentdataI;
import org.nrg.xdat.model.XnatImageassessordataI;
import org.nrg.xdat.model.XnatImagescandataI;
import org.nrg.xdat.model.XnatReconstructedimagedataI;
import org.nrg.xdat.model.XnatSubjectdataI;
import org.nrg.xdat.om.XnatAbstractresource;
import org.nrg.xdat.om.XnatExperimentdata;
import org.nrg.xdat.om.XnatImageassessordata;
import org.nrg.xdat.om.XnatImagesessiondata;
import org.nrg.xdat.om.XnatProjectdata;
import org.nrg.xdat.om.XnatResource;
import org.nrg.xdat.om.XnatResourceseries;
import org.nrg.xdat.om.XnatSubjectassessordata;
import org.nrg.xdat.om.XnatSubjectdata;
import org.nrg.xft.XFTItem;
import org.nrg.xft.security.UserI;
import org.nrg.xnat.restlet.representations.ZipRepresentation;
import org.nrg.xsync.configuration.ProjectSyncConfiguration;
import org.nrg.xsync.connection.RemoteConnection;
import org.nrg.xsync.connection.RemoteConnectionHandler;
import org.nrg.xsync.connection.RemoteConnectionManager;
import org.nrg.xsync.connection.RemoteConnectionResponse;
import org.nrg.xsync.exception.XsyncRemoteConnectionException;
import org.nrg.xsync.exception.XsyncStoreException;
import org.nrg.xsync.manager.SynchronizationManager;
import org.nrg.xsync.manifest.ExperimentSyncItem;
import org.nrg.xsync.manifest.ResourceSyncItem;
import org.nrg.xsync.manifest.SubjectSyncItem;
import org.nrg.xsync.tools.XSyncTools;
import org.nrg.xsync.tools.XsyncObserver;
import org.nrg.xsync.tools.XsyncXnatInfo;
import org.nrg.xsync.utils.QueryResultUtil;
import org.nrg.xsync.utils.XSyncFailureHandler;
import org.nrg.xsync.utils.XsyncFileUtils;
import org.nrg.xsync.utils.XsyncUtils;
import org.restlet.data.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

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
	
	public RemoteSubject(final RemoteConnectionManager manager, final XsyncXnatInfo xnatInfo, final QueryResultUtil queryResultUtil, final JdbcTemplate jdbcTemplate, XnatSubjectdataI localSubject, ProjectSyncConfiguration projectSyncConfiguration, UserI user, boolean syncAll, XsyncObserver observer) {
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
	}
	
	
	public void sync() throws Exception{
		_log.debug("Syncing subject BEGIN: " + localSubject.getLabel());
		XFTItem item = ((XnatSubjectdata)localSubject).getItem().copy();
		String remoteProjectId = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteProjectId();
	
		XnatSubjectdata newSubject = (XnatSubjectdata) BaseElement.GetGeneratedItem(item);
		newSubject.setProject(remoteProjectId);
		IdMapper idMapper = new IdMapper(_manager, _queryResultUtil, _jdbcTemplate, user, projectSyncConfiguration);

		try {
			
			idMapper.correctIDandLabel(newSubject);
			
			//Go through resources; if they are in config and modified since last sync, keep them
			ResourceFilter resourceMapper = new ResourceFilter(user, _jdbcTemplate, _queryResultUtil);
			Map<String,List<XnatAbstractresourceI>> resourcesToBeSynced = resourceMapper.select(newSubject, localSubject.getId(), projectSyncConfiguration);

			//Go through experiments; if they are in config, keep them
			ExperimentFilter experimentMapper = new ExperimentFilter(_manager, _jdbcTemplate, _xnatInfo, _queryResultUtil, user, projectSyncConfiguration);
			Map<String,List<XnatExperimentdataI>> experimentsToBeSynced = experimentMapper.select(newSubject, localSubject.getId());
			//Store the subject
			//Get its remote id
			//Store the remote id

			String subject_remote_id=storeSubject(newSubject);
			if (subject_remote_id != null) {
				newSubject.setId(subject_remote_id);
				saveSyncDetails(localSubject.getId(),subject_remote_id,newSubject.getLabel(), XsyncUtils.SYNC_STATUS_SYNCED,localSubject.getXSIType());
				//Now among the ones which are configured and not deleted
				//Change the ids
				//Check the ImagingSessions
				//   for Scans configured
				//   for Resources configured 
				//   for ImageAssessors configured
				//   Anonymize the resources
				syncResources(newSubject,resourcesToBeSynced);
				syncExperiments(newSubject,experimentsToBeSynced);
			}	
		}catch(Exception e) {
			_log.error("Error syncing subject " + newSubject.getLabel() + "  " + e.getMessage());
			XSyncFailureHandler.handle(localSubject.getProject(),localSubject.getId(),localSubject.getXSIType(),idMapper.getRemoteAccessionId(this.localSubject.getId()), subjectSyncInfo, e);
			throw e;
		}
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
			 if (response.wasSuccessful()) 
				 subject_remote_id = response.getResponseBody();
			 else 
				 XSyncFailureHandler.handle(projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSourceProjectId(),localSubject.getId(),localSubject.getXSIType(),null, subjectSyncInfo, response);
			 return subject_remote_id;
		 }catch(Exception e) {
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

	private RemoteConnectionResponse updateSubjectAssessorResource(XnatSubjectdataI remoteSubject, XnatSubjectassessordata subjectAssessor, String resourceLabel, File zipFile) throws Exception {
		 try {
			 RemoteConnectionHandler remoteConnectionHandler = new RemoteConnectionHandler(_jdbcTemplate, _queryResultUtil);
			 RemoteConnection connection = remoteConnectionHandler.getConnection(projectSyncConfiguration.getProject().getId(),projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl());
			 return _manager.importSubjectAssessorResource(connection, (XnatSubjectdata)remoteSubject, subjectAssessor, resourceLabel, zipFile);
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
		XSyncTools xsyncTools = new XSyncTools(user, _jdbcTemplate, _queryResultUtil);
		xsyncTools.saveSyncDetails(projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSourceProjectId(), local_id, remote_id,syncStatus,xsiType,remoteProjectId);
	}

	private void saveSyncDetails(String local_id, String remote_id,  String syncStatus, String xsiType) {
		String remoteProjectId = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteProjectId();
		XSyncTools xsyncTools = new XSyncTools(user, _jdbcTemplate, _queryResultUtil);
		xsyncTools.saveSyncDetails(projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSourceProjectId(), local_id, remote_id,syncStatus,xsiType,remoteProjectId);
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
						_log.error("Could not delete resource " + resource.getLabel() + " for subject " + remoteSubject.getId());
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
		XnatProjectdata localProject = XnatProjectdata.getXnatProjectdatasById(localSubject.getProject(), user, false);
		String localProjectArchivePath = localProject.getArchiveRootPath();
		String remoteProjectId = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteProjectId();
		String rLabel = resource.getLabel() == null ? XsyncUtils.RESOURCE_NO_LABEL:resource.getLabel();
		ResourceSyncItem resourceSyncItem = new ResourceSyncItem(localSubject.getLabel(),rLabel);
		if (resource.getFileCount() != null)
			resourceSyncItem.setFileCount(resource.getFileCount());
		else 
			resourceSyncItem.setFileCount(0);
		if (resource.getFileSize() != null)
			resourceSyncItem.setFileSize(resource.getFileSize());
		else 
			resourceSyncItem.setFileSize(new Long(0));
		try {
			String archiveDirectory = ((XnatAbstractresource)resource).getFullPath(localProjectArchivePath);
			File resourcePath = new File(archiveDirectory);
			if (resourcePath.exists() && resourcePath.isFile()) {
				resourcePath = resourcePath.getParentFile();
			}
			File zipFile = new XsyncFileUtils().buildZip(remoteProjectId, resourcePath);
			RemoteConnectionResponse updateResponse = this.updateSubjectResource(remoteSubject,resource.getLabel(), zipFile);
			if (updateResponse.wasSuccessful()) {
				resourceSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_SYNCED);
				resourceSyncItem.setMessage("Subject " + localSubject.getLabel() + " resource " + rLabel + " updated. " );
			}else {
				resourceSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_FAILED);
				resourceSyncItem.setMessage("Subject " + localSubject.getLabel() + " resource " + rLabel + " could not be updated. " + updateResponse.getResponseBody() );
			}
			subjectSyncInfo.addResources(resourceSyncItem);
		}catch(Exception e) {
			_log.error("Could not update resource " + resource.getLabel() + " for subject " + remoteSubject.getId());
			resourceSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_FAILED);
			resourceSyncItem.setMessage("Subject " + localSubject.getLabel() + " resource " + rLabel + " could not be updated. " + e.getMessage() );
			subjectSyncInfo.addResources(resourceSyncItem);
		}
	}
	
	private void syncExperiments(XnatSubjectdataI remoteSubject,Map<String,List<XnatExperimentdataI>> experimentsToBeSynced) throws Exception{
		String remoteProjectId = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteProjectId();
		List<XnatExperimentdataI> deletedExperiments = experimentsToBeSynced.get(QueryResultUtil.DELETE_STATUS);
		List<XnatExperimentdataI> updatedExperiments = experimentsToBeSynced.get(QueryResultUtil.ACTIVE_STATUS);
		List<XnatExperimentdataI> newExperiments = experimentsToBeSynced.get(QueryResultUtil.NEW_STATUS);
		List<XnatExperimentdataI> okToSyncExperiments = experimentsToBeSynced.get(QueryResultUtil.OK_TO_SYNC_STATUS);

		if (syncAllStates) {
			//Delete experiments
			if (deletedExperiments != null && deletedExperiments.size() > 0) {
				//Remove each of these resources from the Remote site
				for (XnatExperimentdataI experiment:deletedExperiments) {
					if (experiment.getXSIType().startsWith("xsync:")) {
						continue;
					}
					try {
						XnatExperimentdata exp = (XnatExperimentdata)experiment;
						exp.setProject(remoteProjectId);
						exp.getItem().setProperty("subject_ID", remoteSubject.getId());
						this.deleteExperiment(exp);
					}catch(Exception e) {
						_log.error("Could not delete experiment " + experiment.getId() + " for subject " + remoteSubject.getId() + " " + e.getMessage());
					}
				}
			}
			//Update the modified experiments
			if (updatedExperiments != null && updatedExperiments.size() > 0) {
				for (XnatExperimentdataI experiment:updatedExperiments) {
					pushExperiment(experiment,remoteSubject);
				}
			}
		}
		//Push the new experiments
		if (newExperiments != null && newExperiments.size() > 0) {
			for (XnatExperimentdataI experiment:newExperiments) {
				pushExperiment(experiment,remoteSubject);
			}
		}
		//Irrespective of the syncOnlyNwew Flag, the OK to Sync experiments must be pushed
		if (okToSyncExperiments != null && okToSyncExperiments.size() > 0) {
			for (XnatExperimentdataI experiment:okToSyncExperiments) {
				pushExperiment(experiment,remoteSubject);
			}
		}
	}
	
	private void pushExperiment(XnatExperimentdataI assess, XnatSubjectdataI remoteSubject) throws Exception {
		if (assess.getXSIType().startsWith("xsync:")) {
			return;
		}
		String origId = assess.getId();
		ExperimentFilter experimentFilter = new ExperimentFilter(_manager, _jdbcTemplate, _xnatInfo, _queryResultUtil, user, projectSyncConfiguration);
		if (assess instanceof XnatImageassessordata) {
			return;
		}
		if (assess instanceof XnatImagesessiondata) {
			XnatImagesessiondata orig = (XnatImagesessiondata) XnatImagesessiondata.getXnatImagesessiondatasById(origId, user, true);
			if (isImagingSessionConfiguredToBeSyned(orig.getXSIType())) {
				if (imagingSessionNeedsOkToSync(orig.getXSIType())) {
					boolean updateOkToSyncAssessorStatus = true;
					if (hasBeenMarkedOkToSyncAndNotSyncedYet(orig.getId())) {
						XnatImagesessiondata cleaned_assessor = null;
						try {
						cleaned_assessor = experimentFilter.prepareImagingSessionToSync((XnatSubjectdata)remoteSubject,orig);
						cleaned_assessor.setProject(remoteSubject.getProject());
						}catch(Exception e) {
							cleaned_assessor = null;
							throw e;
						}
						if (cleaned_assessor != null) {
							boolean stored = storeXar((XnatImagesessiondata) orig,remoteSubject.getProject(), (XnatSubjectdata)remoteSubject, cleaned_assessor, updateOkToSyncAssessorStatus);
							if (!stored)
								throw new XsyncStoreException("Unable to store for subject " + remoteSubject.getLabel() + " experiment " + cleaned_assessor.getLabel() );
						}
					}else { //Needs OKToSync which has not been marked yet. So Skip
						 ExperimentSyncItem expSyncItem = new ExperimentSyncItem(orig.getId(),orig.getLabel());
						 expSyncItem.setXsiType(orig.getXSIType());
						 expSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_SKIPPED);
						 expSyncItem.setMessage("Subject " + localSubject.getLabel() + " experiment " + orig.getLabel() + " has been skipped as it has not been marked ok to sync");
						 subjectSyncInfo.addExperiment(expSyncItem);
					}
				}else { //Does not need an OK to Sync - so Push it. 
					boolean updateOkToSyncAssessorStatus = false; //No SyncAssessor exists in this case
					XnatImagesessiondata cleaned_assessor = null;
					try {
						cleaned_assessor = experimentFilter.prepareImagingSessionToSync((XnatSubjectdata)remoteSubject,orig);
						cleaned_assessor.setProject(remoteSubject.getProject());
					}catch(Exception e) {
						cleaned_assessor = null;
						throw e;
					}
					if (cleaned_assessor != null) {
						boolean stored = storeXar((XnatImagesessiondata) orig,remoteSubject.getProject(), (XnatSubjectdata)remoteSubject, cleaned_assessor,updateOkToSyncAssessorStatus);
						if (!stored)
							throw new XsyncStoreException("Unable to store for subject " + remoteSubject.getLabel() + " experiment " + cleaned_assessor.getLabel() );
					}
				}
			}
		} else { //Its a Subject Assessor
			XnatSubjectassessordata orig = (XnatSubjectassessordata) XnatSubjectassessordata.getXnatSubjectassessordatasById(origId, user, true);
			if (isSubjectAssessorConfiguredToBeSyned(orig.getXSIType())) {
			/*	if (subjectAssessorNeedsOkToSync(orig.getXSIType())) {
					if (hasBeenMarkedOkToSyncAndNotSyncedYet(orig.getId())) {
						boolean updateOkToSyncAssessorStatus = true;
						XnatSubjectassessordata cleaned_assessor = (XnatSubjectassessordata)experimentFilter.prepareSubjectAssessorToSync((XnatSubjectdata)localSubject,(XnatSubjectdata)remoteSubject, orig);
						cleaned_assessor.setProject(remoteSubject.getProject());
						boolean stored = storeAssessor(origId, orig, (XnatSubjectdata)remoteSubject, cleaned_assessor, updateOkToSyncAssessorStatus);
						if (!stored)
							throw new XsyncStoreException("Unable to store for subject " + remoteSubject.getLabel() + " experiment " + cleaned_assessor.getLabel() );
					}else {
						//Not marked OK to Sync - skip it
						 ExperimentSyncItem expSyncItem = new ExperimentSyncItem(orig.getId(),orig.getLabel());
						 expSyncItem.setXsiType(orig.getXSIType());
						 expSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_SKIPPED);
						 expSyncItem.setMessage("Subject " + localSubject.getLabel() + " experiment " + orig.getLabel() + " has been skipped as it has not been marked ok to sync");
						 subjectSyncInfo.addExperiment(expSyncItem);
					}					
				}else { */ // NO OKs required, sync it
					boolean updateOkToSyncAssessorStatus = false;
					XnatSubjectassessordata cleaned_assessor = (XnatSubjectassessordata)experimentFilter.prepareSubjectAssessorToSync((XnatSubjectdata)localSubject,(XnatSubjectdata)remoteSubject, orig);
					cleaned_assessor.setProject(remoteSubject.getProject());
					boolean stored = storeAssessor(origId, orig, (XnatSubjectdata)remoteSubject, cleaned_assessor, updateOkToSyncAssessorStatus);
					if (!stored)
						throw new XsyncStoreException("Unable to store for subject " + remoteSubject.getLabel() + " experiment " + cleaned_assessor.getLabel() );
				//}
			}
		}
	}
	
	private boolean isImagingSessionConfiguredToBeSyned(String xsiType) {
		return projectSyncConfiguration.isImagingSessionToBeSynced(xsiType);
	}
	private boolean isSubjectAssessorConfiguredToBeSyned(String xsiType) {
		return projectSyncConfiguration.isSubjectAssessorToBeSynced(xsiType);
	}
	
	private boolean imagingSessionNeedsOkToSync(String xsiType) {
		//return projectSyncConfiguration.getSynchronizationConfiguration().checkImagingSessionOkToSync(xsiType);
		try {
			return projectSyncConfiguration.getSynchronizationConfiguration().getImagingSession(xsiType).getNeeds_ok_to_sync().booleanValue();
		}catch(NullPointerException npe) {
			return false;
		}

	}
	
	private boolean subjectAssessorNeedsOkToSync(String xsiType) {
		//return projectSyncConfiguration.getSynchronizationConfiguration().checkSubjectAssessorOkToSync(xsiType);
		return projectSyncConfiguration.subjectAssessorNeedsOkToSync(xsiType);
	}
	
	
	private boolean hasBeenMarkedOkToSyncAndNotSyncedYet(String expId) {
		XSyncTools xsyncTools = new XSyncTools(user, _jdbcTemplate, _queryResultUtil);
		return xsyncTools.hasBeenMarkedOkToSyncAndNotSyncedYet(expId, projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl());
	}
	
	private boolean storeAssessor(String origId, XnatSubjectassessordata orig, XnatSubjectdata remotesubject, XnatSubjectassessordata assessor, boolean updateSyncAssessor) {
		 boolean stored = false;
		 String remoteUrl = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl();
		 ExperimentSyncItem expSyncItem = new ExperimentSyncItem(orig.getId(),orig.getLabel());
		 expSyncItem.setXsiType(orig.getXSIType());
		 try {
			 prepareResourceURI(assessor);
			 XnatProjectdata localProject = XnatProjectdata.getXnatProjectdatasById(localSubject.getProject(), user, false);
			 RemoteConnectionHandler remoteConnectionHandler = new RemoteConnectionHandler(_jdbcTemplate, _queryResultUtil);
			 RemoteConnection connection = remoteConnectionHandler.getConnection(projectSyncConfiguration.getProject().getId(),projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl());
			 RemoteConnectionResponse connectionResponse = _manager.importSubjectAssessor(connection, remotesubject, assessor);
			 stored = connectionResponse.wasSuccessful();
			 String remote_id = connectionResponse.getResponseBody();
			 if (stored) {
				 expSyncItem.setRemoteId(remote_id);
				 expSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_SYNCED);
				 expSyncItem.setMessage("Subject " + localSubject.getLabel() + " assessor " + orig.getLabel() + " has been synced. Remote Id:" + connectionResponse.getResponseBody());
				 String remoteProjectId = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteProjectId();

				 saveSyncDetails(origId,remote_id,XsyncUtils.SYNC_STATUS_SYNCED,assessor.getXSIType());
				//Store the resources of the assessor
				String localProjectArchivePath = localProject.getArchiveRootPath();

				for (XnatAbstractresourceI resource: orig.getResources_resource()) {
					if (projectSyncConfiguration.isSubjectAssessorResourceToBeSynced(assessor.getXSIType(),resource.getLabel())) {
						String archiveDirectory = ((XnatAbstractresource)resource).getFullPath(localProjectArchivePath);
						File resourcePath = new File(archiveDirectory);
						if (resourcePath.exists() && resourcePath.isFile()) {
							resourcePath = resourcePath.getParentFile();
						} 
					    File zipFile = new XsyncFileUtils().buildZip(remoteProjectId,resourcePath);
						RemoteConnectionResponse updateResponse = this.updateSubjectAssessorResource(remotesubject, assessor,resource.getLabel(), zipFile);
						ResourceSyncItem resourceSyncItem = new ResourceSyncItem(null,resource.getLabel());
						resourceSyncItem.setFileCount(resource.getFileCount()!=null?resource.getFileCount():0);
						resourceSyncItem.setFileSize(resource.getFileSize()!=null?resource.getFileSize():new Long(0));
						if (updateResponse.wasSuccessful()) {
							resourceSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_SYNCED);
						}else {
							resourceSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_FAILED);
						}
						expSyncItem.addResources(resourceSyncItem);
					}
				}
				if (updateSyncAssessor) {
					XSyncTools xsyncTools = new XSyncTools(user, _jdbcTemplate, _queryResultUtil);
					 xsyncTools.updateSyncAssessor(expSyncItem,remoteProjectId ,remoteUrl);
				}
			 }
		 }catch(Exception e) {
			 _log.debug("Unable to store assessor " + assessor.getLabel() + " " + e.getLocalizedMessage());
			 expSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_FAILED);
			 expSyncItem.setMessage("Subject " + localSubject.getLabel() + " assessor " + orig.getLabel() + " could not be  synced. " + e.getLocalizedMessage());
		 }
		 subjectSyncInfo.addExperiment(expSyncItem);
		 return stored;
	}

	private boolean storeXar( XnatImagesessiondata orig, String targetproject,XnatSubjectdata targetsubject, XnatImagesessiondata target, boolean updateSyncAssessor) throws XsyncRemoteConnectionException{
		 boolean stored;
		 final String remoteUrl = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl();
		 final String remoteProjectId = targetproject;
		 final RemoteConnectionHandler remoteConnectionHandler = new RemoteConnectionHandler(_jdbcTemplate, _queryResultUtil);
		 final RemoteConnection connection = remoteConnectionHandler.getConnection(projectSyncConfiguration.getProject().getId(),projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl());
		 final ExperimentSyncItem expSyncItem = new ExperimentSyncItem(orig.getId(),orig.getLabel());
		 List<XnatImageassessordataI> assessors = target.getAssessors_assessor();

		 expSyncItem.setXsiType(orig.getXSIType());
		 expSyncItem.extractDetails(target);
		 
		 try {
			 removeAssessors(target);
			 prepareResourceURIForXar(target);

			 final File xar=buildxar(orig, targetproject, targetsubject, target);

			 final RemoteConnectionResponse connectionResponse = _manager.importXar(connection, xar);
			 stored = connectionResponse.wasSuccessful();
			 if (stored) {
				 //final IdMapper idMapper = new IdMapper(user,projectSyncConfiguration);
				 //final String remote_id = idMapper.getRemoteId(remoteUrl,remoteProjectId,targetsubject.getLabel(), target.getLabel(), target.getXSIType());
				 xar.delete();
				 String remote_id = getRemoteAssignedId(connectionResponse);
				 ExperimentFilter experimentMapper = new ExperimentFilter(_manager, _jdbcTemplate, _xnatInfo, _queryResultUtil, user, projectSyncConfiguration);

				 if (remote_id == null) {
					 throw new XsyncStoreException("Could not locate Accession Id for " + target.getLabel() + " in project " + remoteProjectId);
				 }else {
					 ExperimentFilter experimentFilter = new ExperimentFilter(_manager, _jdbcTemplate, _xnatInfo, _queryResultUtil, user, projectSyncConfiguration);

					 for (int i=0; i<assessors.size();i++) {
						 XnatImageassessordata origAss = (XnatImageassessordata)assessors.get(i);
						 XFTItem item = origAss.getItem().copy();
						 XnatImageassessordata targetAss = (XnatImageassessordata) BaseElement.GetGeneratedItem(item);
						 experimentFilter.correctIDandLabel(targetAss, origAss, remote_id, target.getProject());

						 for (final XnatAbstractresourceI res : targetAss.getResources_resource()) {
							 experimentMapper.modifyExptResource((XnatAbstractresource) res, orig);
						 }

						 for (final XnatAbstractresourceI res : targetAss.getIn_file()) {
							 experimentMapper.modifyExptResource((XnatAbstractresource) res, orig);
						 }

						 for (final XnatAbstractresourceI res : targetAss.getOut_file()) {
							 experimentMapper.modifyExptResource((XnatAbstractresource) res, orig);

						 }
						 prepareResourceURIForXar(target,targetAss);
						 final ExperimentSyncItem expAssSyncItem = new ExperimentSyncItem(origAss.getId(),origAss.getLabel());
						 expAssSyncItem.setXsiType(targetAss.getXSIType());
						 expAssSyncItem.extractAssessorDetails(targetAss);

						 final File assXar=buildxar(orig,  origAss, targetAss);

						 if (assXar != null) {
							 final RemoteConnectionResponse assConnectionResponse = _manager.importXar(connection, assXar);
							 boolean assStored = assConnectionResponse.wasSuccessful();
							 if (assStored) {
								 assXar.delete();
								 String remoteAssId = getRemoteAssignedId(assConnectionResponse);
								 expAssSyncItem.setRemoteId(remoteAssId);
								 expAssSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_SYNCED);
								 expAssSyncItem.setMessage("Image session " + orig.getLabel() + " Image Assessor " + target.getLabel() + " has been synced.");
								 saveSyncDetails(origAss.getId(),remoteAssId,XsyncUtils.SYNC_STATUS_SYNCED,origAss.getXSIType());
							 }else {
								 expAssSyncItem.setRemoteId(null);
								 expAssSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_FAILED);
								 expAssSyncItem.setMessage(assConnectionResponse.getResponseBody());
							 }
							 expSyncItem.addAssessor(expAssSyncItem);
						 }
					 }
					 expSyncItem.setRemoteId(remote_id);
					 expSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_SYNCED);
					 expSyncItem.setMessage("Subject " + localSubject.getLabel() + " experiment " + orig.getLabel() + " has been synced.");
//					 expSyncItem.setMessage("Subject " + localSubject.getLabel() + " experiment " + orig.getLabel() + " has been synced. " );

					 if (updateSyncAssessor) {
						 XSyncTools xsyncTools = new XSyncTools(user, _jdbcTemplate, _queryResultUtil);
						 xsyncTools.updateSyncAssessor(expSyncItem,remoteProjectId ,remoteUrl);
					 }

					 saveSyncDetails(orig.getId(),remote_id,XsyncUtils.SYNC_STATUS_SYNCED,orig.getXSIType());
				 }
			 }
		 }catch(Exception e) {
			 _log.error(e.getMessage());
			 expSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_FAILED);
			 expSyncItem.setMessage("Subject " + localSubject.getLabel() + " experiment " + orig.getLabel() + " could not be synced. " + e.getMessage());
			 stored = false;
		 }
		 subjectSyncInfo.addExperiment(expSyncItem);
		 return stored;
	}
	
	private String getRemoteAssignedId(final RemoteConnectionResponse connectionResponse) {
		 String remote_id_with_line_breaks = connectionResponse.getResponseBody();
		 remote_id_with_line_breaks = remote_id_with_line_breaks.replaceAll("\\r\\n|\\r|\\n", "");
		 String remote_id_parts[] = remote_id_with_line_breaks.split("/");
		 String remote_id = remote_id_parts[remote_id_parts.length-1];
		 return remote_id;
	}
	
	private void removeAssessors(XnatImagesessiondata target) {
			while (removeSingleAssessor(target));
	}

	private boolean removeSingleAssessor(XnatImagesessiondata target) {
		if (target.getAssessors_assessor().size() > 0) {
			target.removeAssessors_assessor(0);
			return true;
		}else 
			return false;
	}

	private void modifyExptResource(XnatAbstractresourceI resource, XnatExperimentdata orig)  {
		if (resource instanceof XnatResource) {
			if (((XnatResource) resource).getUri() != null) {
				String path = ((XnatResource) resource).getUri();
				int exp_label_index = path.indexOf("/"+ orig.getLabel()+"/");
				if (exp_label_index != -1) {
					String newURI = path.substring(exp_label_index+orig.getLabel().length()+2);
					if (newURI.startsWith(File.separator) || newURI.startsWith("/")) {
						newURI=newURI.substring(1);
					}
					((XnatResource) resource).setUri(newURI);
				}
			}
			//Clear the catalog entries metadata
		} else if (resource instanceof XnatResourceseries) {
			if (((XnatResourceseries) resource).getPath() != null) {
				String path = ((XnatResourceseries) resource).getPath();
				int exp_label_index = path.indexOf(orig.getLabel());
				String newURI = path.substring(exp_label_index+orig.getLabel().length());
				if (newURI.startsWith(File.separator)) {
					newURI=newURI.substring(1);
				}
				((XnatResource) resource).setUri(newURI);
			}
		}
		//Negative values indicate that the resource is being skipped
		if (resource.getFileCount() != null ) {
			resource.setFileCount(resource.getFileCount()>0?resource.getFileCount():-1*resource.getFileCount());
		}if (resource.getFileSize() != null) {
			resource.setFileSize((Long)resource.getFileSize()>0?resource.getFileSize():-1*(Long)resource.getFileSize());
		}
	}
	private void prepareResourceURI(XnatExperimentdata exp){
		for (final XnatAbstractresourceI res : exp.getResources_resource()) {
			modifyExptResource((XnatAbstractresource) res, exp);
		}
	}
	
	private void prepareResourceURIForXar(XnatImagesessiondata exp,XnatImageassessordata assess){
		for (final XnatAbstractresourceI res : assess.getResources_resource()) {
			modifyExptResource((XnatAbstractresource) res, assess);
		}

		for (final XnatAbstractresourceI res : assess.getIn_file()) {
			modifyExptResource((XnatAbstractresource) res, assess);
		}

		for (final XnatAbstractresourceI res : assess.getOut_file()) {
			modifyExptResource((XnatAbstractresource) res, assess);
		}

	}
	
	private void prepareResourceURIForXar(XnatImagesessiondata exp){
		for (final XnatAbstractresourceI res : exp.getResources_resource()) {
			modifyExptResource((XnatAbstractresource) res, exp);
		}
		for (final XnatImagescandataI scan : ((XnatImagesessiondata) exp).getScans_scan()) {
			scan.setImageSessionId(exp.getLabel());
			for (final XnatAbstractresourceI res : scan.getFile()) {
				modifyExptResource((XnatAbstractresource) res,exp);
			}
		}
		for (final XnatReconstructedimagedataI recon : exp.getReconstructions_reconstructedimage()) {
			for (final XnatAbstractresourceI res : recon.getIn_file()) {
				modifyExptResource((XnatAbstractresource) res, exp);
			}
			for (final XnatAbstractresourceI res : recon.getOut_file()) {
				modifyExptResource((XnatAbstractresource) res, exp);
			}
		}
		for (final XnatImageassessordataI assess : exp.getAssessors_assessor()) {
			for (final XnatAbstractresourceI res : assess.getResources_resource()) {
				modifyExptResource((XnatAbstractresource) res, exp);
			}

			for (final XnatAbstractresourceI res : assess.getIn_file()) {
				modifyExptResource((XnatAbstractresource) res, exp);
			}

			for (final XnatAbstractresourceI res : assess.getOut_file()) {
				modifyExptResource((XnatAbstractresource) res, exp);
			}

		}
	}

	
	private void prepareResourceURIForXar(XnatSubjectassessordata exp){
		for (final XnatAbstractresourceI res : exp.getResources_resource()) {
			modifyExptResource((XnatAbstractresource) res, exp);
		}
	}

	File buildxar(XnatImagesessiondata orig, String targetproject,XnatSubjectdata targetsubject, XnatImagesessiondata target) throws Exception {
		File xarFile;
		String anonymizedSessionPath = getAnonymizedSessionPath(orig);
		
		try {
			File experimentPath = new File(anonymizedSessionPath);
			ZipRepresentation rep=new ZipRepresentation(MediaType.APPLICATION_ZIP,(orig).getArchiveDirectoryName(),0);
			List<File> files = new ArrayList<File>();
			if (experimentPath.exists()) {
				files = (List<File>) FileUtils.listFiles(experimentPath,null,true);
			}
			String expCachePath = SynchronizationManager.GET_SYNC_XAR_PATH(targetproject,orig);
			new File(expCachePath).mkdirs();
			File outF = new File(expCachePath, "expt_" + (new Date()).getTime() + ".xml");

			outF.deleteOnExit();
			
			//target.setId("");
			target.setProject(target.getProject());
			target.setSubjectId(targetsubject.getLabel());
			if (target.getScans_scan() != null && target.getScans_scan().size() > 0) {
				for (XnatImagescandataI scan : target.getScans_scan()) {
					scan.setImageSessionId(target.getId());
				}
			}
			FileWriter fw = new FileWriter(outF);
			target.toXML(fw, false);
			fw.close();
			
			rep.addEntry(((XnatSubjectassessordata)target).getLabel() + ".xml",outF);
			if (files.size() > 0) {
				rep.addAll(files);
			}
			
			rep.setDownloadName(target.getLabel()+".xar");
			xarFile = new File(expCachePath, (new Date()).getTime()+".xar");
			rep.write(new FileOutputStream(xarFile));
		} catch (Exception e) {
			_log.debug(e.toString() + "  " + e.getMessage());
			//e.printStackTrace();
			throw new Exception("Unable to retrieve/save session XML."+e.getMessage());
		}
		return xarFile;
		

		// return

	}

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
				
			}
		}
		return path;
	}

	private String getResourcePath(String parent, XnatAbstractresource resource) {
		String path  = null;
		try {
			path = resource.getFullPath(parent);
			System.out.println("Resource Path " + path + " Label " + resource.getLabel());
		}catch(Exception e) {
			
		}
		return path;
	}

	
	File buildxar(XnatImagesessiondata origImageSession, XnatImageassessordata origAss, XnatImageassessordata targetAss) throws Exception {
		File xarFile;
		String sessionPath = getAnonymizedSessionPath(origImageSession);
		String assessorPath = sessionPath + "ASSESSORS" + File.separator + targetAss.getLabel(); 
		//String zipToken = origImageSession.getLabel() +"/ASSESSORS/"  +(targetAss).getArchiveDirectoryName();
		String zipToken = (targetAss).getArchiveDirectoryName();
		try {
			ZipRepresentation rep=new ZipRepresentation(MediaType.APPLICATION_ZIP, zipToken,0);
			List<File> files = new ArrayList<File>();
			File assessorFolder = new File(assessorPath);
			if (assessorFolder.exists()) {
				files = (List<File>) FileUtils.listFiles(assessorFolder,null,true);
			}

			if (files.size() > 0) {
				rep.addAll(files);
			}

			String expCachePath = SynchronizationManager.GET_SYNC_XAR_PATH(targetAss.getProject(),origImageSession);
			new File(expCachePath).mkdirs();
			File outF = new File(expCachePath, "assessor_" + (new Date()).getTime() + ".xml");
			outF.deleteOnExit();
			
			FileWriter fw = new FileWriter(outF);
			targetAss.toXML(fw, false);
			fw.close();
		
			rep.addEntry(((XnatImageassessordata)targetAss).getLabel() + ".xml",outF);
			
			rep.setDownloadName(targetAss.getLabel()+".xar");
			xarFile = new File(expCachePath, (new Date()).getTime()+".xar");
			rep.write(new FileOutputStream(xarFile));
		} catch (Exception e) {
			_log.debug(e.toString() + "  " + e.getMessage());
			//e.printStackTrace();
			throw new Exception("Unable to retrieve/save session XML."+e.getMessage());
		}
		return xarFile;
		

		// return

	}

	
	
	private String getAnonymizedSessionPath(XnatExperimentdata orig) {
		return SynchronizationManager.GET_SYNC_FILE_PATH_TO_SESSION(orig.getProject(),orig) ;

	}

}
