package org.nrg.xsync.local;

import org.restlet.data.MediaType;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

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
import org.nrg.xsync.manifest.ScanSyncItem;
import org.nrg.xsync.manifest.SubjectSyncItem;
import org.nrg.xsync.tools.XSyncTools;
import org.nrg.xsync.tools.XsyncXnatInfo;
import org.nrg.xsync.utils.QueryResultUtil;
import org.nrg.xsync.utils.XsyncFileUtils;
import org.nrg.xsync.utils.XsyncUtils;

import org.nrg.xdat.base.BaseElement;
import org.nrg.xdat.model.XnatAbstractresourceI;
import org.nrg.xdat.model.XnatExperimentdataI;
import org.nrg.xdat.model.XnatImageassessordataI;
import org.nrg.xdat.model.XnatImagescandataI;
import org.nrg.xdat.model.XnatImagesessiondataI;
import org.nrg.xdat.model.XnatReconstructedimagedataI;
import org.nrg.xdat.model.XnatSubjectdataI;
import org.nrg.xdat.om.WrkWorkflowdata;
import org.nrg.xdat.om.XnatAbstractresource;
import org.nrg.xdat.om.XnatExperimentdata;
import org.nrg.xdat.om.XnatImageassessordata;
import org.nrg.xdat.om.XnatImagescandata;
import org.nrg.xdat.om.XnatImagesessiondata;
import org.nrg.xdat.om.XnatProjectdata;
import org.nrg.xdat.om.XnatResource;
import org.nrg.xdat.om.XnatResourceseries;
import org.nrg.xdat.om.XnatSubjectassessordata;
import org.nrg.xdat.om.XnatSubjectdata;
import org.nrg.xft.ItemI;
import org.nrg.xft.XFTItem;
import org.nrg.xft.security.UserI;
import org.nrg.xnat.restlet.representations.ZipRepresentation;
import org.nrg.xsync.configuration.ProjectSyncConfiguration;
import org.nrg.xsync.tools.XsyncURIUtils;
import org.nrg.xsync.utils.WorkFlowUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.exception.ExceptionUtils;


/**
 * @author Mohana Ramaratnam
 *
 */
public class XsyncExperimentTransfer {
	
	private static final Logger _log = LoggerFactory.getLogger(XsyncExperimentTransfer.class);

	ProjectSyncConfiguration projectSyncConfiguration;
	UserI user;
	SubjectSyncItem subjectSyncInfo ;
	private final XsyncXnatInfo _xnatInfo;
	private final NamedParameterJdbcTemplate _jdbcTemplate;
	private final RemoteConnectionManager _manager;
	private final QueryResultUtil _queryResultUtil;
	private XnatSubjectdataI localSubject;
	
	public XsyncExperimentTransfer(final RemoteConnectionManager manager, final XsyncXnatInfo xnatInfo, final QueryResultUtil queryResultUtil,  NamedParameterJdbcTemplate jdbcTemplate,  ProjectSyncConfiguration projectSyncConfiguration, UserI user,SubjectSyncItem subjectSyncInfo,XnatSubjectdataI localSubject) {
		this.user = user;
		this.subjectSyncInfo = subjectSyncInfo;
		this.localSubject = localSubject;
		this.projectSyncConfiguration = projectSyncConfiguration; 
		_jdbcTemplate = jdbcTemplate;
		_manager = manager;
		_xnatInfo = xnatInfo;
		_queryResultUtil = queryResultUtil;
	}
	
	
	
	public void syncExperiment(XnatExperimentdataI assess, XnatSubjectdataI remoteSubject) throws Exception {
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
						 //expSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_SKIPPED);
						 expSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_WAITING_TO_SYNC);
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
		 XsyncURIUtils xsyncUriUtils = new XsyncURIUtils();
		 try {
			 
			 xsyncUriUtils.prepareResourceURI(assessor);
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
						File zipFile =null;
						try {
						    zipFile = new XsyncFileUtils().buildZip(remoteProjectId,resourcePath);
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
						}catch(Exception e) {
							throw(e);
						}finally {
							if (zipFile != null) zipFile.delete();
						}
						
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
		 List<XnatAbstractresourceI>  resources	= getExperimentResources(target);
		 
		 expSyncItem.setXsiType(orig.getXSIType());
		 //expSyncItem.extractDetails(target);
		 XsyncURIUtils xsyncUriUtils = new XsyncURIUtils();

		 try {
			 removeAssessors(target);
			 xsyncUriUtils.prepareResourceURIForXar(target);

			 //Store the ImageSession with only its meta-data in the XAR. Resources, Scans and Assessors 
			 //would be pushed as separate transactions.
			 final File xar=buildImagingSessionXar(orig, targetproject, targetsubject, target);

			 final RemoteConnectionResponse connectionResponse = _manager.importXar(connection, xar);
			 stored = connectionResponse.wasSuccessful();
			 if (stored) {
				 //final IdMapper idMapper = new IdMapper(user,projectSyncConfiguration);
				 //final String remote_id = idMapper.getRemoteId(remoteUrl,remoteProjectId,targetsubject.getLabel(), target.getLabel(), target.getXSIType());
				 xar.delete();
				 String remote_id = xsyncUriUtils.getRemoteAssignedId(connectionResponse);
				 ExperimentFilter experimentMapper = new ExperimentFilter(_manager, _jdbcTemplate, _xnatInfo, _queryResultUtil, user, projectSyncConfiguration);

				 if (remote_id == null) {
					 throw new XsyncStoreException("Could not locate Accession Id for " + target.getLabel() + " in project " + remoteProjectId);
				 }else {
					XFTItem item = target.getItem().copy();
					XnatImagesessiondata targetWithRemoteId = (XnatImagesessiondata) BaseElement.GetGeneratedItem(item);
					targetWithRemoteId.setId(remote_id);
					 //For each resource store the resource
					 for (int i=0; i<resources.size(); i++) {
						 XnatAbstractresource resource = (XnatAbstractresource)resources.get(i);
						 String rLabel = resource.getLabel() == null ? XsyncUtils.RESOURCE_NO_LABEL:resource.getLabel();
						 ResourceSyncItem resourceSyncItem = new ResourceSyncItem(target.getLabel(),rLabel);
						 if (resource.getFileCount() != null && resource.getFileSize()!=null) {
							 boolean hasBeenSkipped = resource.getFileCount()<0 && (Long)resource.getFileSize()<0;
							 if (hasBeenSkipped) {
								    resourceSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_SKIPPED);
								    resourceSyncItem.setFileCount(0);
									resourceSyncItem.setFileSize(new Long(0));
									expSyncItem.addResources(resourceSyncItem);
							 } else	 
								 pushImagingSessionResource(remoteProjectId,targetsubject,orig,  targetWithRemoteId,expSyncItem,resource);
						 }
					 }					 
					 //For each scan in the session, store the scan XML with its resources
					 List<XnatImagescandataI> scans = target.getScans_scan();
					 for (int i=0; i<scans.size(); i++) {
						 XnatImagescandata scan = (XnatImagescandata)scans.get(i);
						 scan.setImageSessionId(remote_id);
						 scan.setProject(target.getProject());
						 final ScanSyncItem scanSyncItem = new ScanSyncItem(scan.getId(),scan.getType());
						 scanSyncItem.setXsiType(scan.getXSIType());
						 scanSyncItem.extractDetails(scan);

						 final File scanXar=buildImagingScanXar(orig, targetproject, targetsubject, target,scan);

						 if (scanXar != null) {
							 final RemoteConnectionResponse scanConnectionResponse = _manager.importXar(connection, scanXar);
							 boolean scanStored = scanConnectionResponse.wasSuccessful();
							 if (scanStored) {
								 scanXar.delete();
								 String remoteScanId = xsyncUriUtils.getRemoteAssignedId(scanConnectionResponse);
								 scanSyncItem.setRemoteId(remoteScanId);
								 scanSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_SYNCED);
								 scanSyncItem.setMessage("Image session " + orig.getLabel() + " Image Scan " + scan.getId() + " has been synced.");
								 //saveSyncDetails(origAss.getId(),remoteScanId,XsyncUtils.SYNC_STATUS_SYNCED,origAss.getXSIType());
							 }else {
								 scanSyncItem.setRemoteId(null);
								 scanSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_FAILED);
								 scanSyncItem.setMessage(scanConnectionResponse.getResponseBody());
							 }
							 expSyncItem.addScan(scanSyncItem);
						 }
					 }
					 
					 ExperimentFilter experimentFilter = new ExperimentFilter(_manager, _jdbcTemplate, _xnatInfo, _queryResultUtil, user, projectSyncConfiguration);

					 for (int i=0; i<assessors.size();i++) {
						 XnatImageassessordata origAss = (XnatImageassessordata)assessors.get(i);
						 item = origAss.getItem().copy();
						 XnatImageassessordata targetAss = (XnatImageassessordata) BaseElement.GetGeneratedItem(item);
						 experimentFilter.correctIDandLabel(targetAss, origAss, remote_id, target.getProject());

						 for (final XnatAbstractresourceI res : targetAss.getResources_resource()) {
							 experimentMapper.modifyExptResource((XnatAbstractresource) res, orig,true);
						 }

						 for (final XnatAbstractresourceI res : targetAss.getIn_file()) {
							 experimentMapper.modifyExptResource((XnatAbstractresource) res, orig,true);
						 }

						 for (final XnatAbstractresourceI res : targetAss.getOut_file()) {
							 experimentMapper.modifyExptResource((XnatAbstractresource) res, orig,true);

						 }
						 xsyncUriUtils.prepareResourceURIForXar(target,targetAss);
						 final ExperimentSyncItem expAssSyncItem = new ExperimentSyncItem(origAss.getId(),origAss.getLabel());
						 expAssSyncItem.setXsiType(targetAss.getXSIType());
						 expAssSyncItem.extractAssessorDetails(targetAss);

						 final File assXar=buildxar(orig,  origAss, targetAss);

						 if (assXar != null) {
							 final RemoteConnectionResponse assConnectionResponse = _manager.importXar(connection, assXar);
							 boolean assStored = assConnectionResponse.wasSuccessful();
							 if (assStored) {
								 assXar.delete();
								 String remoteAssId = xsyncUriUtils.getRemoteAssignedId(assConnectionResponse);
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
					 expSyncItem.updateSyncStatus(XsyncUtils.SYNC_STATUS_SYNCED,"Subject " + localSubject.getLabel() + " experiment " + orig.getLabel() + " has been synced.");
					 WorkFlowUtils wrkFlowUtils = new WorkFlowUtils(_manager,_queryResultUtil,_jdbcTemplate,projectSyncConfiguration);
					 wrkFlowUtils.createWorkflowAtRemote(orig,remote_id,remoteProjectId,"Complete");
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
		 }finally {
				String anonymizedSessionPath = XsyncFileUtils.getAnonymizedSessionPath(orig);
				File localPath = new File(anonymizedSessionPath);
				if (localPath.exists() && localPath.isDirectory()) {
					try {
						FileUtils.deleteDirectory(localPath);
					}catch(Exception ioe) {
						
					}
				} 
					
		 }
		 subjectSyncInfo.addExperiment(expSyncItem);
		 return stored;
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
			
			
		private void removeScans(XnatImagesessiondata target) {
			while (removeSingleScan(target));
		}
		
		private boolean removeSingleScan(XnatImagesessiondata target) {
			if (target.getScans_scan().size() > 0) {
				target.removeScans_scan(0);
				return true;
			}else 
				return false;
		}
		
		private void removeResources(XnatImagesessiondata target) {
			while (removeSingleResource(target));
		}
		
		private boolean removeSingleResource(XnatImagesessiondata target) {
			if (target.getResources_resource().size() > 0) {
				target.removeResources_resource(0);
				return true;
			}else 
				return false;
		}
		
		private void modifyExptScanResource(XnatAbstractresourceI resource, String scanid)  {
			if (resource instanceof XnatResource) {
				if (((XnatResource) resource).getUri() != null) {
					String path = ((XnatResource) resource).getUri();
					String search_string = "SCANS/"+ scanid+"/";
					int scan_id_label_index = path.indexOf(search_string);
					if (scan_id_label_index != -1) {
						String newURI = path.substring(search_string.length());
						if (newURI.startsWith(File.separator) || newURI.startsWith("/")) {
							newURI=newURI.substring(1);
						}
						((XnatResource) resource).setUri(newURI);
					}
				}
			}
		}
		

		File buildxar(XnatImagesessiondata orig, String targetproject,XnatSubjectdata targetsubject, XnatImagesessiondata target) throws Exception {
			File xarFile;
			
			String anonymizedSessionPath = XsyncFileUtils.getAnonymizedSessionPath(orig);
			
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

		File buildxar(XnatImagesessiondata origImageSession, XnatImageassessordata origAss, XnatImageassessordata targetAss) throws Exception {
			File xarFile;
			String sessionPath = XsyncFileUtils.getAnonymizedSessionPath(origImageSession);
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


		
		File buildImagingSessionXar(XnatImagesessiondata orig, String targetproject,XnatSubjectdata targetsubject, XnatImagesessiondata targetOrig) throws Exception {
			File xarFile;
			XsyncFileUtils xsyncFileUtils = new XsyncFileUtils();
			String anonymizedSessionPath = xsyncFileUtils.getAnonymizedSessionPath(orig);
			XFTItem item = targetOrig.getItem().copy();
			XnatImagesessiondata targetExperiment = (XnatImagesessiondata) BaseElement.GetGeneratedItem(item);

			try {
				targetExperiment.setProject(targetExperiment.getProject());
				targetExperiment.setSubjectId(targetsubject.getLabel());
				if (targetExperiment.getResources_resource() != null && targetExperiment.getResources_resource().size() > 0) {
					removeResources(targetExperiment);
				}			

				if (targetExperiment.getScans_scan() != null && targetExperiment.getScans_scan().size() > 0) {
					removeScans(targetExperiment);
				}			


				
				File experimentPath = new File(anonymizedSessionPath);
				ZipRepresentation rep=new ZipRepresentation(MediaType.APPLICATION_ZIP,(orig).getArchiveDirectoryName(),0);
				List<File> files = new ArrayList<File>();
				//if (experimentPath.exists()) {
				//	File experimentResourcePath = new File(anonymizedSessionPath+"RESOURCES");
				//	if (experimentResourcePath.exists()) {
				//		files = (List<File>) FileUtils.listFiles(experimentResourcePath,null,true);
				//	}
				//}
				String expCachePath = SynchronizationManager.GET_SYNC_XAR_PATH(targetproject,orig);
				new File(expCachePath).mkdirs();
				File outF = new File(expCachePath, "expt_" + (new Date()).getTime() + ".xml");

				outF.deleteOnExit();
				
				FileWriter fw = new FileWriter(outF);
				targetExperiment.toXML(fw, false);
				fw.close();
				
				rep.addEntry(((XnatSubjectassessordata)targetExperiment).getLabel() + ".xml",outF);
				if (files.size() > 0) {
					rep.addAll(files);
				}
				
				rep.setDownloadName(targetExperiment.getLabel()+".xar");
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

		File buildImagingScanXar(XnatImagesessiondata orig, String targetproject,XnatSubjectdata targetsubject, XnatImagesessiondata target,XnatImagescandata scan) throws Exception {
			File xarFile;
			XsyncFileUtils xsyncFileUtils = new XsyncFileUtils();
			String anonymizedSessionPath = xsyncFileUtils.getAnonymizedSessionPath(orig);
			
			try {
				File experimentPath = new File(anonymizedSessionPath);
				ZipRepresentation rep=new ZipRepresentation(MediaType.APPLICATION_ZIP,scan.getId(),0);
				List<File> files = new ArrayList<File>();
				if (experimentPath.exists()) {
					File experimentScanPath = new File(anonymizedSessionPath+"SCANS"+File.separator+scan.getId());
					if (experimentScanPath.exists()) {
						files = (List<File>) FileUtils.listFiles(experimentScanPath,null,true);
					}
				}
				String expCachePath = SynchronizationManager.GET_SYNC_XAR_PATH(targetproject,orig);
				new File(expCachePath).mkdirs();
				File outF = new File(expCachePath, target.getLabel()+"_scan_"+ scan.getId()+"_" + (new Date()).getTime() + ".xml");

				for (final XnatAbstractresourceI res : scan.getFile()) {
					modifyExptScanResource((XnatAbstractresource) res,scan.getId());
				}
				
				outF.deleteOnExit();
				
				FileWriter fw = new FileWriter(outF);
				scan.toXML(fw, false);
				fw.close();
				
			
				
				rep.addEntry(target.getLabel()+"_"+scan.getId() + ".xml",outF);
				if (files.size() > 0) {
					rep.addAll(files);
				}
				
				rep.setDownloadName(target.getLabel()+"_"+scan.getId()+".xar");
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


		private void saveSyncDetails(String local_id, String remote_id,  String syncStatus, String xsiType) {
			String remoteProjectId = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteProjectId();
			XSyncTools xsyncTools = new XSyncTools(user, _jdbcTemplate, _queryResultUtil);
			xsyncTools.saveSyncDetails(projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSourceProjectId(), local_id, remote_id,syncStatus,xsiType,remoteProjectId);
		}


		private RemoteConnectionResponse updateImagingSessionResource(XnatImagesessiondataI remoteImagingSession, String resourceLabel, File zipFile) throws Exception {
			 try {
				 RemoteConnectionHandler remoteConnectionHandler = new RemoteConnectionHandler(_jdbcTemplate, _queryResultUtil);
				 RemoteConnection connection = remoteConnectionHandler.getConnection(projectSyncConfiguration.getProject().getId(),projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl());

				 return _manager.importImageSessionResource(connection, (XnatImagesessiondata)remoteImagingSession, resourceLabel, zipFile);
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
		
		private void pushImagingSessionResource( String remoteProjectId,XnatSubjectdata targetsubject,XnatImagesessiondata orig, XnatImagesessiondata target,ExperimentSyncItem expSyncItem,XnatAbstractresourceI resource) {
			XnatProjectdata localProject = XnatProjectdata.getXnatProjectdatasById(localSubject.getProject(), user, false);
			String rLabel = resource.getLabel() == null ? XsyncUtils.RESOURCE_NO_LABEL:resource.getLabel();
			ResourceSyncItem resourceSyncItem = new ResourceSyncItem(orig.getLabel(),rLabel);
			if (resource.getFileCount() != null)
				resourceSyncItem.setFileCount(resource.getFileCount()>0?resource.getFileCount():0);
			else 
				resourceSyncItem.setFileCount(0);
			if (resource.getFileSize() != null)
				resourceSyncItem.setFileSize((Long)resource.getFileSize()>0?resource.getFileSize():new Long(0));
			else 
				resourceSyncItem.setFileSize(new Long(0));
			try {
				String filepath = orig.getArchiveRootPath() + "arc001/";
				//Fix what is done in ExperimnetFilter. modifyExptResource
				String newFilepath = SynchronizationManager.GET_SYNC_FILE_PATH(orig.getProject(),orig);
				newFilepath = newFilepath.replace(File.pathSeparator,"/").replace("\\","/");
				String archiveDirectory = ((XnatAbstractresource)resource).getFullPath(orig.getArchiveRootPath());
				if (archiveDirectory.startsWith(newFilepath)) {
					archiveDirectory = archiveDirectory.replace(newFilepath, filepath);
				}
				File resourcePath = new File(archiveDirectory);
				if (resourcePath.exists() && resourcePath.isFile()) {
					resourcePath = resourcePath.getParentFile();
				}
				File zipFile = null;
				try {
					zipFile = new XsyncFileUtils().buildZip(remoteProjectId, resourcePath);
					RemoteConnectionResponse updateResponse = this.updateImagingSessionResource(target,resource.getLabel(), zipFile);
					if (updateResponse.wasSuccessful()) {
						resourceSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_SYNCED);
						resourceSyncItem.setMessage("Exoeriment " + target.getLabel() + " resource " + rLabel + " updated. " );
					}else {
						resourceSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_FAILED);
						resourceSyncItem.setMessage("Experiment " + target.getLabel() + " resource " + rLabel + " could not be updated. " + updateResponse.getResponseBody() );
					}
					expSyncItem.addResources(resourceSyncItem);
				}catch(Exception e) {
					throw(e);
				}finally {
					if (zipFile != null) 
						zipFile.delete();
				}
			}catch(Exception e) {
				_log.error("Could not update resource " + resource.getLabel() + " for experiment " + target.getId());
				resourceSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_FAILED);
				resourceSyncItem.setMessage("Experiment " + target.getLabel() + " resource " + rLabel + " could not be updated. " + ExceptionUtils.getFullStackTrace(e) );
				expSyncItem.addResources(resourceSyncItem);
			}
		}


		private List<XnatAbstractresourceI> getExperimentResources(XnatImagesessiondata orig) {
			 List<XnatAbstractresourceI> resources = null;
			 XFTItem item = orig.getItem().copy();
			 XnatImagesessiondata targetExperiment = (XnatImagesessiondata) BaseElement.GetGeneratedItem(item);
			 resources = targetExperiment.getResources_resource();
			 return resources;
		}

}
