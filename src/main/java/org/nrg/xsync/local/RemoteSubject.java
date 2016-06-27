package org.nrg.xsync.local;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
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
import org.nrg.xsync.utils.QueryResultUtil;
import org.nrg.xsync.utils.XSyncFailureHandler;
import org.nrg.xsync.utils.XsyncFileUtils;
import org.nrg.xsync.utils.XsyncUtils;
import org.restlet.data.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Mohana Ramaratnam
 *
 */
public class RemoteSubject {
	private static final Logger _log = LoggerFactory.getLogger(RemoteSubject.class);


	XnatSubjectdataI localSubject;
	ProjectSyncConfiguration projectSyncConfiguration;
	UserI user;
	SubjectSyncItem subjectSyncInfo ;

	
	public RemoteSubject(XnatSubjectdataI localSubject,ProjectSyncConfiguration projectSyncConfiguration,UserI user) {
		this.localSubject = localSubject;
		this.user = user;
		this.projectSyncConfiguration = projectSyncConfiguration; 
		subjectSyncInfo = new SubjectSyncItem(localSubject.getId(), localSubject.getLabel());
	}
	
	
	public void sync() {
		_log.debug("Syncing subject BEGIN: " + localSubject.getLabel());
		XFTItem item = ((XnatSubjectdata)localSubject).getItem().copy();
		String remoteProjectId = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteProjectId();
	
		XnatSubjectdata newSubject = (XnatSubjectdata) BaseElement.GetGeneratedItem(item);
		newSubject.setProject(remoteProjectId);
		IdMapper idMapper = new IdMapper(user,projectSyncConfiguration);

		try {
			
			idMapper.correctIDandLabel(newSubject);
			
			//Go through resources; if they are in config and modified since last sync, keep them
			ResourceFilter resourceMapper = new ResourceFilter(user);
			Map<String,List<XnatAbstractresourceI>> resourcesToBeSynced = resourceMapper.select(newSubject, localSubject.getId(), projectSyncConfiguration);

			//Go through experiments; if they are in config, keep them
			ExperimentFilter experimentMapper = new ExperimentFilter(user, projectSyncConfiguration);
			Map<String,List<XnatExperimentdataI>> experimentsToBeSynced = experimentMapper.select(newSubject, localSubject.getId());
			//Store the subject
			//Get its remote id
			//Store the remote id

			RemoteConnectionResponse response=storeSubject(newSubject);
			if (response.wasSuccessful()) {
				//Get the response body. This is the remote XNAT ID assigned
				String subject_remote_id = response.getResponseBody();
				
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
			}else {
				XSyncFailureHandler.handle(projectSyncConfiguration.getProjectSyncConfigurationFromDB().getProjectId(),localSubject.getId(),localSubject.getXSIType(),idMapper.getRemoteAccessionId(this.localSubject.getId()), subjectSyncInfo, response);
			}
		}catch(Exception e) {
			_log.error("Could not correct subject ID and Label " + e.getMessage());
			e.printStackTrace();
			XSyncFailureHandler.handle(localSubject.getProject(),localSubject.getId(),localSubject.getXSIType(),idMapper.getRemoteAccessionId(this.localSubject.getId()), subjectSyncInfo, e);
		}
		SynchronizationManager.UPDATE_MANIFEST(localSubject.getProject(), subjectSyncInfo);
		_log.debug("Syncing subject END: " + localSubject.getLabel());
	}


	private RemoteConnectionResponse storeSubject(XnatSubjectdataI remoteSubject) throws Exception {
	 RemoteConnectionManager remoteConnectionManager = new RemoteConnectionManager();
	 try {
		 RemoteConnectionHandler remoteConnectionHandler = new RemoteConnectionHandler();
		 RemoteConnection connection = remoteConnectionHandler.getConnection(projectSyncConfiguration.getProject().getId(),projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl());
 
		 RemoteConnectionResponse response =  remoteConnectionManager.importSubject(connection, (XnatSubjectdata)remoteSubject);
		 return response;
	 }catch(Exception e) {
		 _log.error(e.toString());
		 throw e;
	 }
	}

	private RemoteConnectionResponse deleteSubjectResource(XnatSubjectdataI remoteSubject, String resourceLabel) throws Exception {
		 RemoteConnectionManager remoteConnectionManager = new RemoteConnectionManager();
		 try {
			 RemoteConnectionHandler remoteConnectionHandler = new RemoteConnectionHandler();
			 RemoteConnection connection = remoteConnectionHandler.getConnection(projectSyncConfiguration.getProject().getId(),projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl());
			 RemoteConnectionResponse response =  remoteConnectionManager.deleteSubjectResource(connection, (XnatSubjectdata)remoteSubject, resourceLabel);
			 return response;
		 }catch(Exception e) {
			 _log.error(e.toString());
			 throw e;
		 }
		}

	private RemoteConnectionResponse updateSubjectResource(XnatSubjectdataI remoteSubject, String resourceLabel, File zipFile) throws Exception {
		 RemoteConnectionManager remoteConnectionManager = new RemoteConnectionManager();
		 try {
			 RemoteConnectionHandler remoteConnectionHandler = new RemoteConnectionHandler();
			 RemoteConnection connection = remoteConnectionHandler.getConnection(projectSyncConfiguration.getProject().getId(),projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl());
			 RemoteConnectionResponse response =  remoteConnectionManager.importSubjectResource(connection, (XnatSubjectdata)remoteSubject, resourceLabel, zipFile);
			 
			 return response;
		 }catch(Exception e) {
			 _log.error(e.toString());
			 throw e;
		 }
		}

	private RemoteConnectionResponse updateSubjectAssessorResource(XnatSubjectdataI remoteSubject, XnatSubjectassessordata subjectAssessor, String resourceLabel, File zipFile) throws Exception {
		 RemoteConnectionManager remoteConnectionManager = new RemoteConnectionManager();
		 try {
			 RemoteConnectionHandler remoteConnectionHandler = new RemoteConnectionHandler();
			 RemoteConnection connection = remoteConnectionHandler.getConnection(projectSyncConfiguration.getProject().getId(),projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl());
			 RemoteConnectionResponse response =  remoteConnectionManager.importSubjectAssessorResource(connection, (XnatSubjectdata)remoteSubject, subjectAssessor, resourceLabel, zipFile);
			 return response;
		 }catch(Exception e) {
			 _log.error(e.toString());
			 throw e;
		 }
		}

	private RemoteConnectionResponse deleteExperiment(XnatExperimentdata experiment) throws Exception {
		//If the experiment was already stored, we have the remote id
		//Use that id to delete the experiment
		//If not, the experiment was never synced. So ignore.
		IdMapper idMapper = new IdMapper(user,projectSyncConfiguration);
		String remoteId = idMapper.getRemoteAccessionId(experiment.getId());
		 ExperimentSyncItem expSyncItem = new ExperimentSyncItem(experiment.getId(),experiment.getLabel());
		 expSyncItem.setRemoteId(remoteId);
		if (remoteId != null)  {
			experiment.setId(remoteId);
			RemoteConnectionManager remoteConnectionManager = new RemoteConnectionManager();
			 try {
				 RemoteConnectionHandler remoteConnectionHandler = new RemoteConnectionHandler();
				 RemoteConnection connection = remoteConnectionHandler.getConnection(projectSyncConfiguration.getProject().getId(),projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl());
				 RemoteConnectionResponse response =  remoteConnectionManager.deleteExperiment(connection, experiment);
				 if (response.wasSuccessful()) {
					 expSyncItem.setMessage("Subject " + localSubject.getLabel() + " experiment " + experiment.getLabel() + " deleted. " + response.getResponseBody());
					 expSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_DELETED);
					 XSyncTools xsyncTools = new XSyncTools(user);
					 xsyncTools.deleteXsyncRemoteEntry(experiment.getId(), experiment.getLabel());
				 }else {
					 expSyncItem.setMessage("Subject " + localSubject.getLabel() + " experiment " + experiment.getLabel() + " could not be deleted. " + response.getResponseBody());
					 expSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_FAILED);
				 }
				 subjectSyncInfo.addExperiment(expSyncItem);
				 return response;
			 }catch(Exception e) {
				 _log.error(e.toString());
				 expSyncItem.setMessage("Subject " + localSubject.getLabel() + " experiment " + experiment.getLabel() + " could not be deleted. " + e.getMessage());
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

		XSyncTools xsyncTools = new XSyncTools(user);
		xsyncTools.saveSyncDetails(projectSyncConfiguration.getProjectSyncConfigurationFromDB().getProjectId(), local_id, remote_id,syncStatus,xsiType);
	}

	private void saveSyncDetails(String local_id, String remote_id,  String syncStatus, String xsiType) {
		XSyncTools xsyncTools = new XSyncTools(user);
		xsyncTools.saveSyncDetails(projectSyncConfiguration.getProjectSyncConfigurationFromDB().getProjectId(), local_id, remote_id,syncStatus,xsiType);
	}

	
	private void syncResources(XnatSubjectdataI remoteSubject,Map<String,List<XnatAbstractresourceI>> resourcesToBeSynced) throws Exception{
		List<XnatAbstractresourceI> deletedResources = resourcesToBeSynced.get(QueryResultUtil.DELETE_STATUS);
		List<XnatAbstractresourceI> activeResources = resourcesToBeSynced.get(QueryResultUtil.ACTIVE_STATUS);
		if (deletedResources != null && deletedResources.size() > 0) {
			//Remove each of these resources from the Remote site
			for (XnatAbstractresourceI resource:deletedResources) {
				try {
					RemoteConnectionResponse deleteResponse = this.deleteSubjectResource(remoteSubject,resource.getLabel());
					ResourceSyncItem resourceSyncItem = new ResourceSyncItem(localSubject.getLabel(),resource.getLabel());
					if (deleteResponse.wasSuccessful()) {
						resourceSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_DELETED);
						resourceSyncItem.setMessage("Subject " + localSubject.getLabel() + " resource " + resource.getLabel() + " deleted ");
						XSyncTools xsyncTools = new XSyncTools(user);
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
		}
		//Store the active resources
		XnatProjectdata localProject = XnatProjectdata.getXnatProjectdatasById(localSubject.getProject(), user, false);
		String localProjectArchivePath = localProject.getArchiveRootPath();
		String remoteProjectId = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteProjectId();

		if (activeResources != null && activeResources.size() > 0) {
			for (XnatAbstractresourceI resource:activeResources) {
				ResourceSyncItem resourceSyncItem = new ResourceSyncItem(localSubject.getLabel(),resource.getLabel());
				resourceSyncItem.setFileCount(resource.getFileCount());
				resourceSyncItem.setFileSize(resource.getFileSize());
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
						resourceSyncItem.setMessage("Subject " + localSubject.getLabel() + " resource " + resource.getLabel() + " updated. " );
					}else {
						resourceSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_FAILED);
						resourceSyncItem.setMessage("Subject " + localSubject.getLabel() + " resource " + resource.getLabel() + " could not be updated. " + updateResponse.getResponseBody() );
					}
					subjectSyncInfo.addResources(resourceSyncItem);
				}catch(Exception e) {
					_log.error("Could not update resource " + resource.getLabel() + " for subject " + remoteSubject.getId());
					resourceSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_FAILED);
					resourceSyncItem.setMessage("Subject " + localSubject.getLabel() + " resource " + resource.getLabel() + " could not be updated. " + e.getMessage() );
					subjectSyncInfo.addResources(resourceSyncItem);
				}
			}
		}	
	}
	
	private void syncExperiments(XnatSubjectdataI remoteSubject,Map<String,List<XnatExperimentdataI>> experimentsToBeSynced) throws Exception{
		String remoteProjectId = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteProjectId();
		List<XnatExperimentdataI> deletedExperiments = experimentsToBeSynced.get(QueryResultUtil.DELETE_STATUS);
		List<XnatExperimentdataI> activeExperiments = experimentsToBeSynced.get(QueryResultUtil.ACTIVE_STATUS);
		if (deletedExperiments != null && deletedExperiments.size() > 0) {
			//Remove each of these resources from the Remote site
			for (XnatExperimentdataI experiment:deletedExperiments) {
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
		ExperimentFilter experimentFilter = new ExperimentFilter(user, projectSyncConfiguration);
		XSyncTools xsyncTools = new XSyncTools(user);
		boolean updateSyncAssessor = false;
		for (XnatExperimentdataI assess : activeExperiments) {
			// filter experiment stuff
			String origId = assess.getId();
			if (assess instanceof XnatImagesessiondata) {
				XnatImagesessiondata orig = (XnatImagesessiondata) XnatImagesessiondata.getXnatImagesessiondatasById(origId, user, true);
				updateSyncAssessor = false;
				if (projectSyncConfiguration.isImagingSessionToBeSynced(orig.getXSIType())) {
					//Does the imaging session have to be checked whether Ok To Sync has been set or not? 
					if (projectSyncConfiguration.getSynchronizationConfiguration().checkImagingSessionOkToSync(orig.getXSIType())) {
						if (xsyncTools.hasBeenMarkedOkToSync(origId, projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl())) {
							updateSyncAssessor = true;
							XnatImagesessiondata cleaned_assessor = experimentFilter.prepareImagingSessionToSync((XnatSubjectdata)remoteSubject,orig);
							cleaned_assessor.setProject(remoteSubject.getProject());
							storeXar((XnatImagesessiondata) orig,remoteSubject.getProject(), (XnatSubjectdata)remoteSubject, cleaned_assessor, updateSyncAssessor);
						}else {//Else skip it - it cannt be synced
							 ExperimentSyncItem expSyncItem = new ExperimentSyncItem(orig.getId(),orig.getLabel());
							 expSyncItem.setXsiType(orig.getXSIType());
							 expSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_SKIPPED);
							 expSyncItem.setMessage("Subject " + localSubject.getLabel() + " experiment " + orig.getLabel() + " has been skipped as it has not been marked ok to sync");
							 subjectSyncInfo.addExperiment(expSyncItem);
						}
					}else {
						updateSyncAssessor = false; //No SyncAssessor exists in this case
						XnatImagesessiondata cleaned_assessor = experimentFilter.prepareImagingSessionToSync((XnatSubjectdata)remoteSubject,orig);
						cleaned_assessor.setProject(remoteSubject.getProject());
						storeXar((XnatImagesessiondata) orig,remoteSubject.getProject(), (XnatSubjectdata)remoteSubject, cleaned_assessor, updateSyncAssessor);
					}
				}
			} else { //Its a Subject Assessor
				XnatSubjectassessordata orig = (XnatSubjectassessordata) XnatSubjectassessordata.getXnatSubjectassessordatasById(origId, user, true);
				if (projectSyncConfiguration.isSubjectAssessorToBeSynced(orig.getXSIType())) {
					if (projectSyncConfiguration.getSynchronizationConfiguration().checkSubjectAssessorOkToSync(orig.getXSIType())) {
						//Check if the Subject Assessor is marked for Ok to Sync
						//If yes, go ahead and store it
						if (xsyncTools.hasBeenMarkedOkToSync(origId, projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl())) {
							updateSyncAssessor = true;
							XnatSubjectassessordata cleaned_assessor = (XnatSubjectassessordata)experimentFilter.prepareSubjectAssessorToSync((XnatSubjectdata)localSubject,(XnatSubjectdata)remoteSubject, orig);
							cleaned_assessor.setProject(remoteSubject.getProject());
							storeAssessor(origId, orig, (XnatSubjectdata)remoteSubject, cleaned_assessor, updateSyncAssessor);
						}else {
							//If not skip it
							 ExperimentSyncItem expSyncItem = new ExperimentSyncItem(orig.getId(),orig.getLabel());
							 expSyncItem.setXsiType(orig.getXSIType());
							 expSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_SKIPPED);
							 expSyncItem.setMessage("Subject " + localSubject.getLabel() + " experiment " + orig.getLabel() + " has been skipped as it has not been marked ok to sync");
							 subjectSyncInfo.addExperiment(expSyncItem);
						}
					}else {
						updateSyncAssessor = false;
						XnatSubjectassessordata cleaned_assessor = (XnatSubjectassessordata)experimentFilter.prepareSubjectAssessorToSync((XnatSubjectdata)localSubject,(XnatSubjectdata)remoteSubject, orig);
						cleaned_assessor.setProject(remoteSubject.getProject());
						storeAssessor(origId, orig, (XnatSubjectdata)remoteSubject, cleaned_assessor, updateSyncAssessor);
					}
					
				}
			}
		}
	}
	
	private boolean storeAssessor(String origId, XnatSubjectassessordata orig, XnatSubjectdata remotesubject, XnatSubjectassessordata assessor, boolean updateSyncAssessor) {
		 RemoteConnectionManager remoteConnectionManager = new RemoteConnectionManager();
		 boolean stored = false;
		 String remoteUrl = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl();
		 ExperimentSyncItem expSyncItem = new ExperimentSyncItem(orig.getId(),orig.getLabel());
		 expSyncItem.setXsiType(orig.getXSIType());
		 try {
			 prepareResourceURI(assessor);
			 XnatProjectdata localProject = XnatProjectdata.getXnatProjectdatasById(localSubject.getProject(), user, false);
			 RemoteConnectionHandler remoteConnectionHandler = new RemoteConnectionHandler();
			 RemoteConnection connection = remoteConnectionHandler.getConnection(projectSyncConfiguration.getProject().getId(),projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl());
			 RemoteConnectionResponse connectionResponse =  remoteConnectionManager.importSubjectAssessor(connection, remotesubject, assessor);
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
						if (resource.getFileCount()!=null && resource.getFileCount().intValue() > 0) {
						    File zipFile = new XsyncFileUtils().buildZip(remoteProjectId,resourcePath);
							RemoteConnectionResponse updateResponse = this.updateSubjectAssessorResource(remotesubject, assessor,resource.getLabel(), zipFile);
							ResourceSyncItem resourceSyncItem = new ResourceSyncItem(null,resource.getLabel());
							resourceSyncItem.setFileCount(resource.getFileCount());
							resourceSyncItem.setFileSize(resource.getFileSize());
							if (updateResponse.wasSuccessful()) {
								resourceSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_SYNCED);
							}else {
								resourceSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_FAILED);
							}
							expSyncItem.addResources(resourceSyncItem);
						}
					}
				}
				if (updateSyncAssessor) {
					 XSyncTools xsyncTools = new XSyncTools(user);
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
		 final RemoteConnectionManager remoteConnectionManager = new RemoteConnectionManager();
		 boolean stored = false;
		 final String remoteUrl = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl();
		 final String remoteProjectId = targetproject;
		 final RemoteConnectionHandler remoteConnectionHandler = new RemoteConnectionHandler();
		 final RemoteConnection connection = remoteConnectionHandler.getConnection(projectSyncConfiguration.getProject().getId(),projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl());
		 final ExperimentSyncItem expSyncItem = new ExperimentSyncItem(orig.getId(),orig.getLabel());
		 expSyncItem.setXsiType(orig.getXSIType());
		 try {
			 prepareResourceURIForXar(target);
			 final File xar=buildxar( (XnatImagesessiondata) orig,targetproject, targetsubject, target);
			 final RemoteConnectionResponse connectionResponse =  remoteConnectionManager.importXar(connection, xar);
			 stored = connectionResponse.wasSuccessful();
			 if (stored) {
				 final IdMapper idMapper = new IdMapper(user,projectSyncConfiguration);
				 final String remote_id = idMapper.getRemoteId(remoteUrl,remoteProjectId,targetsubject.getLabel(), target.getLabel(), target.getXSIType());
				 //String remote_id = connectionResponse.getResponseBody();
				 if (remote_id == null) {
					 throw new XsyncStoreException("Could not locate Accession Id for " + target.getLabel() + " in project " + remoteProjectId);
				 }else {
					 expSyncItem.setRemoteId(remote_id);
					 expSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_SYNCED);
					 expSyncItem.setMessage("Subject " + localSubject.getLabel() + " experiment " + orig.getLabel() + " has been synced. Remote Id:" + remote_id);
//					 expSyncItem.setMessage("Subject " + localSubject.getLabel() + " experiment " + orig.getLabel() + " has been synced. " );

					 if (updateSyncAssessor) {
						 XSyncTools xsyncTools = new XSyncTools(user);
						 xsyncTools.updateSyncAssessor(expSyncItem,remoteProjectId ,remoteUrl);
					 }
					 saveSyncDetails(orig.getId(),remote_id,orig.getXSIType(),XsyncUtils.SYNC_STATUS_SYNCED);
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


	private void modifyExptResource(XnatAbstractresourceI resource, XnatExperimentdata orig)  {
		if (resource instanceof XnatResource) {
			String path = ((XnatResource) resource).getUri();
			int exp_label_index = path.indexOf(orig.getLabel());
			String newURI = path.substring(exp_label_index);
			((XnatResource) resource).setUri(newURI);
		} else if (resource instanceof XnatResourceseries) {
			String path = ((XnatResourceseries) resource).getPath();
			int exp_label_index = path.indexOf(orig.getLabel());
			String newURI = path.substring(exp_label_index);
			((XnatResource) resource).setUri(newURI);
		}
	}
	private void prepareResourceURI(XnatExperimentdata exp){
		for (final XnatAbstractresourceI res : exp.getResources_resource()) {
			modifyExptResource((XnatAbstractresource) res, exp);
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
		try {
			String anonymizedSessionPath = SynchronizationManager.GET_SYNC_FILE_PATH_TO_SESSION(orig.getProject(),orig) ;
			File experimentPath = new File(anonymizedSessionPath);

			ZipRepresentation rep=new ZipRepresentation(MediaType.APPLICATION_ZIP,(orig).getArchiveDirectoryName(),0);

			List<File> files = (List<File>) FileUtils.listFiles(experimentPath,null,true);
		
			String expCachePath = SynchronizationManager.GET_SYNC_XAR_PATH(targetproject,orig);
			new File(expCachePath).mkdirs();
			File outF = new File(expCachePath, "expt_" + (new Date()).getTime() + ".xml");

			outF.deleteOnExit();
//			FileOutputStream fos = new FileOutputStream(outF);
//			SAXWriter writer = new SAXWriter(fos, true);
//			writer.setAllowSchemaLocation(true);
//			writer.setLocation(expCachePath);
//			writer.setRelativizePath(((XnatSubjectassessordata) target).getArchiveDirectoryName() + "/");
			
			target.setId("");
			target.setProject(target.getProject());
			target.setSubjectId(targetsubject.getLabel());
			for (XnatImagescandataI scan : target.getScans_scan()) {
				scan.setImageSessionId("");
			}
			for (XnatImageassessordataI assessor : target.getAssessors_assessor()) {
				assessor.setImagesessionId("");
			}
			FileWriter fw = new FileWriter(outF);
			target.toXML(fw, false);
			fw.close();
//			writer.write(target.getItem());
			
			rep.addEntry(((XnatSubjectassessordata)target).getLabel() + ".xml",outF);
			rep.addAll(files);
			
			rep.setDownloadName(target.getLabel()+".xar");
			xarFile = new File(expCachePath, (new Date()).getTime()+".xar");
			xarFile.deleteOnExit();
			rep.write(new FileOutputStream(xarFile));
		} catch (Exception e) {
			_log.debug(e.toString() + "  " + e.getMessage());
			//e.printStackTrace();
			throw new Exception("Unable to retrieve/save session XML."+e.getMessage());
		}
		return xarFile;
		

		// return

	}


	
	File buildxar(XnatImageassessordata orig, String targetproject,XnatSubjectdata targetsubject, XnatImageassessordata target) throws Exception {
		File xarFile;
		try {
			File experimentPath = new File(orig.getArchiveRootPath() + "arc001/" + orig.getArchiveDirectoryName());

			ZipRepresentation rep=new ZipRepresentation(MediaType.APPLICATION_ZIP,(orig).getArchiveDirectoryName(),0);

			List<File> files = (List<File>) FileUtils.listFiles(experimentPath,null,true);

			String expCachePath = SynchronizationManager.GET_SYNC_XAR_PATH(targetproject,orig);
			new File(expCachePath).mkdirs();
			File outF = new File(expCachePath, "expt_" + (new Date()).getTime() + ".xml");
			outF.deleteOnExit();
//			FileOutputStream fos = new FileOutputStream(outF);
//			SAXWriter writer = new SAXWriter(fos, true);
//			writer.setAllowSchemaLocation(true);
//			writer.setLocation(expCachePath);
//			writer.setRelativizePath(((XnatImageassessordata) orig).getArchiveDirectoryName() + "/");
			
			target.setId("");
			target.setProject(target.getProject());
			target.setImagesessionId("");
			FileWriter fw = new FileWriter(outF);
			target.toXML(fw, false);
			fw.close();

//			writer.write(target.getItem());
			
			rep.addEntry(((XnatImageassessordata)target).getLabel() + ".xml",outF);
			rep.addAll(files);
			
			rep.setDownloadName(target.getLabel()+".xar");
			xarFile = new File(expCachePath, (new Date()).getTime()+".xar");
			xarFile.deleteOnExit();
			rep.write(new FileOutputStream(xarFile));
		} catch (Exception e) {
			_log.debug(e.toString() + "  " + e.getMessage());
			//e.printStackTrace();
			throw new Exception("Unable to retrieve/save session XML."+e.getMessage());
		}
		return xarFile;
		

		// return

	}

	

}
