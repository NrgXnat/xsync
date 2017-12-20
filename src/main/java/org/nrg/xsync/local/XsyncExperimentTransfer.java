package org.nrg.xsync.local;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.nrg.framework.services.SerializerService;
import org.nrg.xdat.XDAT;
import org.nrg.xdat.base.BaseElement;
import org.nrg.xdat.model.XnatAbstractresourceI;
import org.nrg.xdat.model.XnatExperimentdataI;
import org.nrg.xdat.model.XnatImageassessordataI;
import org.nrg.xdat.model.XnatImagescandataI;
import org.nrg.xdat.model.XnatImagesessiondataI;
import org.nrg.xdat.model.XnatSubjectassessordataI;
import org.nrg.xdat.model.XnatSubjectdataI;
import org.nrg.xdat.om.XnatAbstractresource;
import org.nrg.xdat.om.XnatImageassessordata;
import org.nrg.xdat.om.XnatImagescandata;
import org.nrg.xdat.om.XnatImagesessiondata;
import org.nrg.xdat.om.XnatProjectdata;
import org.nrg.xdat.om.XnatResource;
import org.nrg.xdat.om.XnatSubjectassessordata;
import org.nrg.xdat.om.XnatSubjectdata;
import org.nrg.xft.XFTItem;
import org.nrg.xft.schema.Wrappers.XMLWrapper.SAXReader;
import org.nrg.xft.security.UserI;
import org.nrg.xnat.restlet.representations.ZipRepresentation;
import org.nrg.xnat.xsync.remote.verify.XsyncProjectVerifier;
import org.nrg.xsync.aspera.AsperaClient;
import org.nrg.xsync.aspera.AsperaProjectPrefs;
import org.nrg.xsync.aspera.AsperaSitePrefs;
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
import org.nrg.xsync.remote.alias.services.SyncStatusService;
import org.nrg.xsync.tools.XSyncTools;
import org.nrg.xsync.tools.XsyncURIUtils;
import org.nrg.xsync.tools.XsyncXnatInfo;
import org.nrg.xsync.utils.JSONUtils;
import org.nrg.xsync.utils.QueryResultUtil;
import org.nrg.xsync.utils.ResourceUtils;
import org.nrg.xsync.utils.WorkFlowUtils;
import org.nrg.xsync.utils.XsyncFileUtils;
import org.nrg.xsync.utils.XsyncUtils;
import org.restlet.data.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.xml.sax.SAXException;

import com.fasterxml.jackson.databind.JsonNode;


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
    private final SerializerService          _serializer;
	private final SyncStatusService _syncStatusService;
    private final XnatProjectdata _localProject;
    private final boolean _syncIfNotSyncedInPast;
    private final int _asperaRetry = 1;
	final AsperaClient _aspera = XDAT.getContextService().getBean(AsperaClient.class);
	final AsperaProjectPrefs _asperaProjectPrefs = XDAT.getContextService().getBean(AsperaProjectPrefs.class);

	public XsyncExperimentTransfer(final RemoteConnectionManager manager, final XsyncXnatInfo xnatInfo, final QueryResultUtil queryResultUtil,  NamedParameterJdbcTemplate jdbcTemplate,  ProjectSyncConfiguration projectSyncConfiguration, UserI user,SubjectSyncItem subjectSyncInfo,XnatSubjectdataI localSubject, SerializerService serializer, SyncStatusService syncStatusService, XnatProjectdata localProject, boolean syncIfNotSyncedInPast) {
		this.user = user;
		this.subjectSyncInfo = subjectSyncInfo;
		this.localSubject = localSubject;
		this.projectSyncConfiguration = projectSyncConfiguration; 
		_jdbcTemplate = jdbcTemplate;
		_manager = manager;
		_xnatInfo = xnatInfo;
		_queryResultUtil = queryResultUtil;
		_serializer = serializer;
		_syncStatusService = syncStatusService;
		_localProject = localProject;
		_syncIfNotSyncedInPast = syncIfNotSyncedInPast;
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
			if (_syncIfNotSyncedInPast) {
				//Has this entity been synced in the past?
				boolean syncedInPast = hasExperimentBeenSuccessfullySyncedInThePast(orig.getProject(), orig.getId(), orig.getXSIType());
				//If yes, skip it
				if (syncedInPast) {
					 final ExperimentSyncItem expSyncItem = new ExperimentSyncItem(orig.getId(),orig.getLabel());
					 expSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_SKIPPED);
					 expSyncItem.setMessage("Experiment " + orig.getLabel() + " skipped as it has been synced successfully in the past.");
					 subjectSyncInfo.addExperiment(expSyncItem);
					 return;
				}
				
			}
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
						if(cleaned_assessor!=null)
						{
							cleaned_assessor.setProject(remoteSubject.getProject());
							if (cleaned_assessor != null) {
								boolean stored = storeXar((XnatImagesessiondata) orig,remoteSubject.getProject(), (XnatSubjectdata)remoteSubject, cleaned_assessor,updateOkToSyncAssessorStatus);
								if (!stored)
									throw new XsyncStoreException("Unable to store for subject " + remoteSubject.getLabel() + " experiment " + cleaned_assessor.getLabel() );
							}
						}
						else
						{
							final ExperimentSyncItem expSyncItem = new ExperimentSyncItem(orig.getId(),orig.getLabel());
							expSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_SKIPPED_BY_FILTER);
							expSyncItem.setMessage("Experiment " + orig.getLabel() + " skipped due to filter.");
							subjectSyncInfo.addExperiment(expSyncItem);
							//throw new XsyncStoreException("Unable to store for subject " + remoteSubject.getLabel() + " experiment " + orig.getLabel() );
							saveSyncDetails(orig.getId(),remoteSubject.getId(),XsyncUtils.SYNC_STATUS_SKIPPED_BY_FILTER,orig.getXSIType());
							//return;
						}
					}catch(Exception e) {
						cleaned_assessor = null;
						throw e;
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
					XnatSubjectassessordataI targetExp=experimentFilter.prepareSubjectAssessorToSync((XnatSubjectdata)localSubject,(XnatSubjectdata)remoteSubject, orig);
					if(targetExp!=null)
					{
						XnatSubjectassessordata cleaned_assessor = (XnatSubjectassessordata)targetExp;
						cleaned_assessor.getAllXnatSubjectassessordatas(user, false);
						cleaned_assessor.setProject(remoteSubject.getProject());
						boolean stored = storeAssessor(origId, orig, (XnatSubjectdata)remoteSubject, cleaned_assessor, updateOkToSyncAssessorStatus);
						if (!stored)
							throw new XsyncStoreException("Unable to store for subject " + remoteSubject.getLabel() + " experiment " + cleaned_assessor.getLabel() );
					}
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
	
	@SuppressWarnings("unused")
	private boolean subjectAssessorNeedsOkToSync(String xsiType) {
		//return projectSyncConfiguration.getSynchronizationConfiguration().checkSubjectAssessorOkToSync(xsiType);
		return projectSyncConfiguration.subjectAssessorNeedsOkToSync(xsiType);
	}
	
	
	private boolean hasBeenMarkedOkToSyncAndNotSyncedYet(String expId) {
		XSyncTools xsyncTools = new XSyncTools(user, _jdbcTemplate, _queryResultUtil);
		return xsyncTools.hasBeenMarkedOkToSyncAndNotSyncedYet(expId, projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl());
	}
	
	private boolean hasExperimentBeenSuccessfullySyncedInThePast(String localProjectId, String local_id, String xsiType ) {
		String remoteProject = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteProjectId();
		String remoteUrl = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl();
		
		XSyncTools xsyncTools = new XSyncTools(user, _jdbcTemplate, _queryResultUtil);
		return  xsyncTools.hasExperimentBeenSuccessfullySyncedInThePast(localProjectId, local_id, xsiType, remoteProject,remoteUrl); 
	}
	
	
	private boolean storeAssessor(String origId, XnatSubjectassessordata orig, XnatSubjectdata remotesubject, XnatSubjectassessordata assessor, boolean updateSyncAssessor) {
		 boolean stored = false;
		 _syncStatusService.registerCurrentExperiment(_localProject.getId(), assessor.getLabel(), assessor.getXSIType());
		 //String remoteUrl = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl();
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
				 expSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_SYNCED_AND_NOT_VERIFIED);
				 expSyncItem.setMessage("Subject " + localSubject.getLabel() + " assessor " + orig.getLabel() + " has been synced. Remote Id:" + connectionResponse.getResponseBody());
				 String remoteProjectId = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteProjectId();

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
								resourceSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_SYNCED_AND_NOT_VERIFIED);
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
				 verifySync(remoteProjectId,expSyncItem, orig, remotesubject,updateSyncAssessor);
				 _syncStatusService.registerCompletedExperiment(_localProject.getId(), assessor.getLabel(), assessor.getXSIType());
			 }
		 }catch(Exception e) {
			 _log.error("Unable to store assessor " + assessor.getLabel() + " " + e.getLocalizedMessage(),e);
			 //Did we get a 500 - Duplicate Error?
			 //Check if the session exists at the remote site
			 boolean exists = lookForSessionAtDestination(orig,expSyncItem);
			 _syncStatusService.registerFailedExperiment(_localProject.getId(), assessor.getLabel(), assessor.getXSIType());
			 if (!exists) {
				 expSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_FAILED);
				 expSyncItem.setMessage("Subject " + localSubject.getLabel() + " experiment " + orig.getLabel() + " could not be synced. " + e.getMessage());
				 stored = false;
			 }

		 }
		 subjectSyncInfo.addExperiment(expSyncItem);
		 return stored;
	}

	private boolean storeXar( XnatImagesessiondata orig, String targetproject,XnatSubjectdata targetsubject, XnatImagesessiondata target, boolean updateSyncAssessor) throws XsyncRemoteConnectionException{
		 boolean stored = false;
		 _syncStatusService.registerCurrentExperiment(_localProject.getId(), orig.getLabel(), orig.getXSIType());
		 //final String remoteUrl = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl();
		 final String remoteProjectId = targetproject;
		 final RemoteConnectionHandler remoteConnectionHandler = new RemoteConnectionHandler(_jdbcTemplate, _queryResultUtil);
		 final RemoteConnection connection = remoteConnectionHandler.getConnection(projectSyncConfiguration.getProject().getId(),projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl());
		 final ExperimentSyncItem expSyncItem = new ExperimentSyncItem(orig.getId(),orig.getLabel());
		 expSyncItem.setRemoteLabel(target.getLabel());

		 expSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_BEGINING);
		 expSyncItem.stateChanged();
		 final List<XnatImageassessordataI> assessors = target.getAssessors_assessor();
		 final List<XnatAbstractresourceI>  resources	= getExperimentResources(target);
		 
		 expSyncItem.setXsiType(orig.getXSIType());
		 //expSyncItem.extractDetails(target);
		 XsyncURIUtils xsyncUriUtils = new XsyncURIUtils();
		 String remote_id = null;

		 try {
			 removeAssessors(target);
			 xsyncUriUtils.prepareResourceURIForXar(target);

			 //Store the ImageSession with only its meta-data in the XAR. Resources, Scans and Assessors 
			 //would be pushed as separate transactions.
			 // HERE!!!!
			 final File xar=buildImagingSessionXar(orig, targetproject, targetsubject, target);
			 _log.debug("BUILDING XAR FILE:  targetProject=" + targetproject + ", targetSubject=" + targetsubject.getLabel() + 
					 ", target=" + target.getLabel() + ", file=" + xar.getName());
			 
			 final RemoteConnectionResponse connectionResponse = 
					 (shouldUseAspera()) ? asperaXarSend(_localProject.getId(), connection, xar) : _manager.importXar(connection, xar);
				 
			 stored = connectionResponse.wasSuccessful();
			 if (stored) {
				 //final IdMapper idMapper = new IdMapper(user,projectSyncConfiguration);
				 //final String remote_id = idMapper.getRemoteId(remoteUrl,remoteProjectId,targetsubject.getLabel(), target.getLabel(), target.getXSIType());
				 xar.delete();
				 remote_id = xsyncUriUtils.getRemoteAssignedId(connectionResponse);
				 _log.debug("assigned experiment remote_id=" + remote_id);
				 if (remote_id != null && remote_id.matches("^.*_S[0-9]*$")) {
					 _log.error("ERROR:  Experiment appears to have been assigned a subject assession number!!! - " + remote_id);
				 }
				 expSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_IN_PROGRESS);
				 saveSyncDetails(orig.getId(),remote_id,expSyncItem.getSyncStatus(),expSyncItem.getXsiType());
				 final ExperimentFilter experimentMapper = new ExperimentFilter(_manager, _jdbcTemplate, _xnatInfo, _queryResultUtil, user, projectSyncConfiguration);

				 if (remote_id == null) {
					 throw new XsyncStoreException("Could not locate Accession Id for " + target.getLabel() + " in project " + remoteProjectId);
				 }else {
					XFTItem item = target.getItem().copy();
					final XnatImagesessiondata targetWithRemoteId = (XnatImagesessiondata) BaseElement.GetGeneratedItem(item);
					targetWithRemoteId.setId(remote_id);
					expSyncItem.setRemoteId(remote_id);
					//For each resource store the resource
					 for (int i=0; i<resources.size(); i++) {
						 final XnatAbstractresource resource = (XnatAbstractresource)resources.get(i);
						 final String rLabel = resource.getLabel() == null ? XsyncUtils.RESOURCE_NO_LABEL:resource.getLabel();
						 final ResourceSyncItem resourceSyncItem = new ResourceSyncItem(target.getLabel(),rLabel);
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
					 final List<XnatImagescandataI> scans = target.getScans_scan();
					 for (int i=0; i<scans.size(); i++) {
						 final XnatImagescandata scan = (XnatImagescandata)scans.get(i);
						 scan.setImageSessionId(remote_id);
						 scan.setProject(target.getProject());
						 final ScanSyncItem scanSyncItem = new ScanSyncItem(scan.getId(),scan.getType());
						 scanSyncItem.setXsiType(scan.getXSIType());
						 scanSyncItem.extractDetails(scan);

						 final File scanXar=buildImagingScanXar(orig, targetproject, targetsubject, target,scan);

						 if (scanXar != null) {
							 final RemoteConnectionResponse scanConnectionResponse = 
									 (shouldUseAspera()) ? asperaXarSend(_localProject.getId(), connection, scanXar) : _manager.importXar(connection, scanXar);
							 final boolean scanStored = scanConnectionResponse.wasSuccessful();
							 if (scanStored) {
								 scanXar.delete();
								 String remoteScanId = xsyncUriUtils.getRemoteAssignedId(scanConnectionResponse);
								 scanSyncItem.setRemoteId(remoteScanId);
								 scanSyncItem.updateResourceStatus(XsyncUtils.SYNC_STATUS_SYNCED_AND_NOT_VERIFIED);
								 scanSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_SYNCED_AND_NOT_VERIFIED);
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
					 
					 final ExperimentFilter experimentFilter = new ExperimentFilter(_manager, _jdbcTemplate, _xnatInfo, _queryResultUtil, user, projectSyncConfiguration);

					 for (int i=0; i<assessors.size();i++) {
						 final XnatImageassessordata origAss = (XnatImageassessordata)assessors.get(i);
						 item = origAss.getItem().copy();
						 final XnatImageassessordata targetAss = (XnatImageassessordata) BaseElement.GetGeneratedItem(item);
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
							 final RemoteConnectionResponse assConnectionResponse = 
									 (shouldUseAspera()) ? asperaXarSend(_localProject.getId(), connection, assXar) : _manager.importXar(connection, assXar);
							 final boolean assStored = assConnectionResponse.wasSuccessful();
							 if (assStored) {
								 assXar.delete();
								 String remoteAssId = xsyncUriUtils.getRemoteAssignedId(assConnectionResponse);
								 expAssSyncItem.setRemoteId(remoteAssId);
								 expAssSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_SYNCED_AND_NOT_VERIFIED);
								 expAssSyncItem.setMessage("Image session " + orig.getLabel() + " Image Assessor " + target.getLabel() + " has been synced.");
							 }else {
								 expAssSyncItem.setRemoteId(null);
								 expAssSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_FAILED);
								 expAssSyncItem.setMessage(assConnectionResponse.getResponseBody());
							 }
							 expSyncItem.addAssessor(expAssSyncItem);
						 }
					 }
					 verifySync(remoteProjectId,expSyncItem, orig, targetsubject,updateSyncAssessor);
					 _syncStatusService.registerCompletedExperiment(_localProject.getId(), orig.getLabel(), orig.getXSIType());
				 }
			 }else if (connectionResponse.getResponse().getStatusCode().value() == HttpStatus.INTERNAL_SERVER_ERROR.value()) { //Internal server error 
				 //Did we get a 500 - Duplicate Error?
				 //Check if the session exists at the remote site
				 boolean exists = lookForSessionAtDestination(orig,expSyncItem);
				 if (!exists) {
					 _syncStatusService.registerFailedExperiment(_localProject.getId(), orig.getLabel(), orig.getXSIType());
					 expSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_FAILED);
					 expSyncItem.setMessage("Subject " + localSubject.getLabel() + " experiment " + orig.getLabel() + " could not be synced. ");
					 stored = false;
				 }else {
					 stored = true; //Found at the remote site. Skipped to sync
				 }
			 }
		 }catch(Exception e) {
			 _log.error(e.getMessage(),e);
			 _syncStatusService.registerFailedExperiment(_localProject.getId(), orig.getLabel(), orig.getXSIType());
			 expSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_FAILED);
			 expSyncItem.setMessage("Subject " + localSubject.getLabel() + " experiment " + orig.getLabel() + " could not be synced. " + e.getMessage());
			 stored = false;
		 }finally {
				if (remote_id!=null && remote_id.length()>0) {
					saveSyncDetails(orig.getId(),remote_id,expSyncItem.getSyncStatus(),expSyncItem.getXsiType());
				}
				final String anonymizedSessionPath = XsyncFileUtils.getAnonymizedSessionPath(orig);
				final File localPath = new File(anonymizedSessionPath);
				if (localPath.exists() && localPath.isDirectory()) {
					try {
						FileUtils.deleteDirectory(localPath);
					}catch(Exception ioe) {
						_log.error("Unable to delete directory "+localPath, ioe);
					}
				} 
		 }
		 subjectSyncInfo.addExperiment(expSyncItem);
		 return stored;
	}

	private boolean shouldUseAspera() {
		final String projectId = _localProject.getId();
		final Boolean enabled = _asperaProjectPrefs.getAsperaEnabled(projectId);
		if (enabled) {
			final String node = _asperaProjectPrefs.getAsperaNodeUrl(projectId);
			final String aUser = _asperaProjectPrefs.getAsperaNodeUser(projectId);
			if (node == null || node.length()<1 || aUser == null || aUser.length()<1) {
				_log.error("Aspera is enabled but not properly configured.  Using HTTPS transfers instead.");
				return false;
			}
			_log.info("Using Aspera for the data transfer method.");
			return true;
		}
		_log.info("Using HTTPS for the data transfer method.");
		return false;
	}

	private RemoteConnectionResponse asperaXarSend(final String projectID, final RemoteConnection connection, final File xar) throws Exception {
		int retryCount = 0;
		boolean uploadSuccess = false;
		while (!uploadSuccess && retryCount<=_asperaRetry) {
			uploadSuccess = _aspera.upload(projectID, xar);
			retryCount+=1;
		}
		if (uploadSuccess) {
			final String xarPath = _asperaProjectPrefs.getDestinationDirectory(_localProject.getId()) +
					File.separator + xar.getName();
			return _manager.importXar(connection, xarPath);
		} else {
			_log.warn("Aspera upload  and retries failed.  Failing over to standard http send.");
			return _manager.importXar(connection, xar);
		}
	}

	private boolean lookForSessionAtDestination(XnatSubjectassessordata orig, ExperimentSyncItem expSyncItem) {
		final String remoteUrl = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl();
		final String remoteProjectId = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteProjectId();
		boolean exists = false;
		try {
			boolean didXsyncSync = hasExperimentBeenSuccessfullySyncedInThePast(orig.getProject(),expSyncItem.getLocalId(), expSyncItem.getXsiType() );
			if (!didXsyncSync) {
				XsyncProjectVerifier projectVerifier = new XsyncProjectVerifier(_manager,_queryResultUtil,_jdbcTemplate, projectSyncConfiguration, _serializer);
				//TODO - include DICOM UID here
				final String uri = remoteUrl+"/data/archive/experiments?xnat:imagesessiondata/project=" + remoteProjectId + "&xnat:mrsessiondata/label=" + expSyncItem.getRemoteLabel() + "&format=json" ;
			    RemoteConnectionResponse response = projectVerifier.get(uri);
				if (response.wasSuccessful()) {
					try {
						JSONUtils jsonUtils = new JSONUtils(_serializer);
						JsonNode rootNode = jsonUtils.toJSONNode(response.getResponseBody());
					    JsonNode resultSetNode = rootNode.get("ResultSet");
					    int totalRecords = resultSetNode.get("totalRecords").asInt();
					    if (totalRecords == 1) {
					       JsonNode resultNode = resultSetNode.get("Result");
					       Iterator<JsonNode> iterator = resultNode.elements();
					       String remoteId = null;
					       while (iterator.hasNext()) {
					            JsonNode expNode = iterator.next();
					            remoteId = expNode.get("ID").asText();
					            break;
					       }
					       if (remoteId != null) {
					    	   //Insert the remote id to db
					    	   expSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_SKIPPED);
					    	   expSyncItem.setMessage("Appears that the session was transferred without using Xsync");
							   saveSyncDetails(orig.getId(),remoteId,expSyncItem.getSyncStatus(),orig.getXSIType());
								  exists = true;
					       }
					    }
					}catch(Exception e) {
						_log.debug(e.getMessage());
					}
				}
				}
			}catch(Exception e) {
				_log.debug(e.getMessage());
			}
		 return exists;
	}
	
	private void verifySync(String remoteProjectId,ExperimentSyncItem expSyncItem, XnatImagesessiondata orig, XnatSubjectdata remoteSubject, boolean updateSyncAssessor)  {
		String remote_id=expSyncItem.getRemoteId();
		final String remoteUrl = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl();

		//Verify each resource
		ArrayList<ResourceSyncItem> syncedResources = expSyncItem.getResources();
		for (ResourceSyncItem rscItem: syncedResources) {
			if (rscItem.getSyncStatus().equals(XsyncUtils.SYNC_STATUS_SYNCED_AND_NOT_VERIFIED)) {
				verifyResource(orig,expSyncItem,rscItem);
			}
		}
		//Verify each Scan and its resources
		//Get the remote ImageSession Document
		try {
			XsyncProjectVerifier projectVerifier = new XsyncProjectVerifier(_manager,_queryResultUtil,_jdbcTemplate, projectSyncConfiguration, _serializer);
		    final String uri = remoteUrl+"/data/archive/projects/" + remoteProjectId + "/subjects/" + remoteSubject.getLabel() +  "/experiments/"+ expSyncItem.getRemoteId() +"?format=xml";
	        RemoteConnectionResponse response = projectVerifier.get(uri);
	        XnatImagesessiondata remoteImageSession = null;
			if (response.wasSuccessful()) {
				String xml = response.getResponseBody();
				remoteImageSession = readXML(xml);
				
			}else {
				expSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_SYNCED_AND_NOT_VERIFIED);
			}
			
			ArrayList<ScanSyncItem> syncedScans = expSyncItem.getScans();
			for (ScanSyncItem scanItem: syncedScans) {
				if (scanItem.getSyncStatus().equals(XsyncUtils.SYNC_STATUS_SYNCED_AND_NOT_VERIFIED)) {
					if (remoteImageSession != null) {
						XnatImagescandata scanFromRemote = remoteImageSession.getScanById(scanItem.getLocalId());
						if (scanFromRemote != null) {
							scanItem.setSyncStatus(XsyncUtils.SYNC_STATUS_SYNCED_AND_VERIFIED);
							scanItem.setMessage("Experiment " + expSyncItem.getLocalLabel() + " Scan " + scanItem.getLocalLabel() );
						}
					}
					verifyScanResource(orig,expSyncItem,remoteSubject,scanItem);
					scanItem.updateSyncStatus();
				}
			}
			//Verify Each Assessor and its resources
			for (ExperimentSyncItem assExpItem : expSyncItem.getAssessors()) {
				if (remoteImageSession != null) {
					XnatImageassessordata assessorFromRemote = remoteImageSession.getAssessorById(assExpItem.getRemoteId());
					if (assessorFromRemote != null) {
						assExpItem.setSyncStatus(XsyncUtils.SYNC_STATUS_SYNCED_AND_VERIFIED);
						saveSyncDetails(assExpItem.getLocalId(),assExpItem.getRemoteId(),XsyncUtils.SYNC_STATUS_SYNCED_AND_VERIFIED,assExpItem.getXsiType());
					}else {
						saveSyncDetails(assExpItem.getLocalId(),assExpItem.getRemoteId(),XsyncUtils.SYNC_STATUS_SYNCED_AND_NOT_VERIFIED,assExpItem.getXsiType());
					}
				}
				verifyAssessorResource(orig,expSyncItem,remoteSubject,assExpItem);
			}
			
		}catch(Exception e) {
			_log.error(e.getMessage(),e);
			
		}finally {
			expSyncItem.updateSyncStatus(XsyncUtils.SYNC_STATUS_SYNCED_AND_NOT_VERIFIED,"Subject " + localSubject.getLabel() + " experiment " + expSyncItem.getLocalLabel() + " ");
			WorkFlowUtils wrkFlowUtils = new WorkFlowUtils(_manager,_queryResultUtil,_jdbcTemplate,projectSyncConfiguration);
			wrkFlowUtils.createWorkflowAtRemote(orig,remote_id,remoteProjectId,expSyncItem.getSyncStatus().equals(XsyncUtils.SYNC_STATUS_SYNCED_AND_VERIFIED)?"Complete":expSyncItem.getSyncStatus());

			if (updateSyncAssessor) {
				try {
					XSyncTools xsyncTools = new XSyncTools(user, _jdbcTemplate, _queryResultUtil);
					 xsyncTools.updateSyncAssessor(expSyncItem,remoteProjectId ,remoteUrl);
				} catch(Exception e) {_log.error("Failed to update sync assessor " + e.getMessage(),e);}
			 }
			 saveSyncDetails(orig.getId(),remote_id,expSyncItem.getSyncStatus(),orig.getXSIType());
		}
	}
	
	private void verifySync(String remoteProjectId,ExperimentSyncItem expSyncItem, XnatSubjectassessordata orig, XnatSubjectdata remoteSubject, boolean updateSyncAssessor) {
		String remote_id=expSyncItem.getRemoteId();
		final String remoteUrl = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl();

		//Verify each resource
		ArrayList<ResourceSyncItem> syncedResources = expSyncItem.getResources();
		for (ResourceSyncItem rscItem: syncedResources) {
			if (rscItem.getSyncStatus().equals(XsyncUtils.SYNC_STATUS_SYNCED_AND_NOT_VERIFIED)) {
				verifyResource(orig,expSyncItem,rscItem);
			}
		}
		
		expSyncItem.updateSyncStatus(XsyncUtils.SYNC_STATUS_SYNCED_AND_NOT_VERIFIED,"Subject " + localSubject.getLabel() + " experiment " + expSyncItem.getLocalLabel() + " has been synced.");
		if (updateSyncAssessor) {
			try {
			 XSyncTools xsyncTools = new XSyncTools(user, _jdbcTemplate, _queryResultUtil);
			 xsyncTools.updateSyncAssessor(expSyncItem,remoteProjectId ,remoteUrl);
			}catch(Exception e) {_log.error("Failed to update sync assessor " + e.getMessage(),e);}
		 }
		saveSyncDetails(orig.getId(),remote_id,expSyncItem.getSyncStatus(),expSyncItem.getXsiType());
	}
	
	private XnatImagesessiondata readXML(String xml) throws IOException, SAXException {
		final SAXReader reader = new SAXReader(user);
		InputStream is = new ByteArrayInputStream(xml.getBytes());
        final XFTItem item = reader.parse(is);
        XnatImagesessiondata imageSessionData =(XnatImagesessiondata) BaseElement.GetGeneratedItem(item);
        is.close();
        return imageSessionData;

	}
	
    private void verifyResource(XnatSubjectassessordata orig,ExperimentSyncItem expSyncItem,ResourceSyncItem rscItem) {
        ResourceUtils resourceUtils = new ResourceUtils(projectSyncConfiguration);
        String remoteProjectId = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteProjectId();
		final String remoteUrl = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl();

        XsyncProjectVerifier projectResourceVerifier = new XsyncProjectVerifier(_manager,_queryResultUtil,_jdbcTemplate, projectSyncConfiguration, _serializer);
        String localProjectArchivePath = _localProject.getArchiveRootPath();
        
        XnatAbstractresource rsc = null;
        for (XnatAbstractresourceI r: orig.getResources_resource()) {
        	if (r.getLabel().equals(rscItem.getLocalLabel())) {
        		rsc = (XnatAbstractresource)r;
        		break;
        	}
        }
        if (rsc != null) {
            String rLabel = rsc.getLabel() == null ?  XsyncUtils.RESOURCE_NO_LABEL:rsc.getLabel();
            String archiveDirectory = rsc.getFullPath(localProjectArchivePath);
		    final String uri = remoteUrl+"/data/archive/experiments/"+ expSyncItem.getRemoteId() +"/resources/"+rsc.getLabel() + "/files?format=JSON";
		    Map<String,String> fileComparison;
		    try {
			   fileComparison = projectResourceVerifier.verify(archiveDirectory, remoteProjectId , rsc.getLabel(), uri);
		    }catch(Exception e) {
	 			fileComparison = new HashMap<String,String>();
				fileComparison.put(XsyncUtils.XSYNC_VERIFICATION_STATUS, XsyncUtils.XSYNC_VERIFICATION_STATUS_FAILED_TO_CONNECT);
		    }
            resourceUtils.setSyncStatus(fileComparison, rscItem, "Experiment " + orig.getLabel() + "  resource " + rLabel);
        }
    }

    private void verifyScanResource(XnatImagesessiondata orig,ExperimentSyncItem expSyncItem,XnatSubjectdata remoteSubject, ScanSyncItem scanItem) {
        ResourceUtils resourceUtils = new ResourceUtils(projectSyncConfiguration);
        String remoteProjectId = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteProjectId();
		final String remoteUrl = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl();

        XsyncProjectVerifier projectResourceVerifier = new XsyncProjectVerifier(_manager,_queryResultUtil,_jdbcTemplate, projectSyncConfiguration, _serializer);
        String localProjectArchivePath = _localProject.getArchiveRootPath();
        XnatImagescandata scan = orig.getScanById(scanItem.getLocalId());
        
        for (ResourceSyncItem rscItem: scanItem.getResources()) {
            XnatAbstractresource rscAbsRsc = null;
        	if (rscItem.getSyncStatus() != null && !rscItem.getSyncStatus().equals(XsyncUtils.SYNC_STATUS_SKIPPED)) {
        		for (XnatAbstractresourceI r: scan.getFile()) {
        			if (r.getLabel().equals(rscItem.getLocalLabel())) {
        				rscAbsRsc = (XnatAbstractresource)r;
    		            String rLabel = rscAbsRsc.getLabel() == null ?  XsyncUtils.RESOURCE_NO_LABEL:rscAbsRsc.getLabel();
    		            String archiveDirectory = rscAbsRsc.getFullPath(localProjectArchivePath);
    				    final String uri = remoteUrl+"/data/archive/projects/" + remoteProjectId + "/subjects/" + remoteSubject.getLabel() +  "/experiments/"+ expSyncItem.getRemoteId() +"/scans/"+ scan.getId() + "/resources/"+rscAbsRsc.getLabel() + "/files?format=JSON";
    				    Map<String,String> fileComparison;
    				    try {
        				    fileComparison = projectResourceVerifier.verify(archiveDirectory, remoteProjectId , rscAbsRsc.getLabel(), uri);
    				    }catch(Exception e) {
    			 			fileComparison = new HashMap<String,String>();
    						fileComparison.put(XsyncUtils.XSYNC_VERIFICATION_STATUS, XsyncUtils.XSYNC_VERIFICATION_STATUS_FAILED_TO_CONNECT);
    				    }
    		            resourceUtils.setSyncStatus(fileComparison, rscItem, "Experiment " + orig.getLabel() + " Scan " + scanItem.getLocalId() + "  resource " + rLabel);
        				break;
        			}
        		}
        	}
        }
        
    }
    
    private void verifyAssessorResource(XnatImagesessiondata orig,ExperimentSyncItem expSyncItem,XnatSubjectdata remoteSubject, ExperimentSyncItem assItem) {
        ResourceUtils resourceUtils = new ResourceUtils(projectSyncConfiguration);
        String remoteProjectId = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteProjectId();
		final String remoteUrl = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl();

        XsyncProjectVerifier projectResourceVerifier = new XsyncProjectVerifier(_manager,_queryResultUtil,_jdbcTemplate, projectSyncConfiguration, _serializer);
        String localProjectArchivePath = _localProject.getArchiveRootPath();
        XnatImageassessordata assessor = orig.getAssessorById(assItem.getLocalId());
        
        for (ResourceSyncItem rscItem: assItem.getResources()) {
            XnatAbstractresource rscAbsRsc = null;
        	if (rscItem.getSyncStatus() != null && !rscItem.getSyncStatus().equals(XsyncUtils.SYNC_STATUS_SKIPPED)) {
        		for (XnatAbstractresourceI r: assessor.getResources_resource()) {
        			if (r.getLabel().equals(rscItem.getLocalLabel())) {
        				rscAbsRsc = (XnatAbstractresource)r;
    		            String rLabel = rscAbsRsc.getLabel() == null ?  XsyncUtils.RESOURCE_NO_LABEL:rscAbsRsc.getLabel();
    		            String archiveDirectory = rscAbsRsc.getFullPath(localProjectArchivePath);
    				    final String uri = remoteUrl+"/data/archive/projects/" + remoteProjectId + "/subjects/" + remoteSubject.getLabel() +  "/experiments/"+ expSyncItem.getRemoteId() + "/assessors/" + assItem.getRemoteId() +"/resources/"+rscAbsRsc.getLabel() + "/files?format=JSON";
    				    Map<String,String> fileComparison;
    				    try {
        				    fileComparison = projectResourceVerifier.verify(archiveDirectory, remoteProjectId , rscAbsRsc.getLabel(), uri);
    				    }catch(Exception e) {
    			 			fileComparison = new HashMap<String,String>();
    						fileComparison.put(XsyncUtils.XSYNC_VERIFICATION_STATUS, XsyncUtils.XSYNC_VERIFICATION_STATUS_FAILED_TO_CONNECT);
    				    }
    		            resourceUtils.setSyncStatus(fileComparison, rscItem, "Experiment " + orig.getLabel() + " Assessor " + assItem.getLocalLabel() + "  resource " + rLabel);
        				break;
        			}
        		}
        	}
        }
        
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
			
			final String anonymizedSessionPath = XsyncFileUtils.getAnonymizedSessionPath(orig);
			
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
				xarFile = new File(expCachePath, (new Date()).getTime()+"_"+target.getLabel()+".xar");
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
			final String sessionPath = XsyncFileUtils.getAnonymizedSessionPath(origImageSession);
			final String assessorPath = sessionPath + "ASSESSORS" + File.separator + targetAss.getLabel(); 
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
				xarFile = new File(expCachePath, (new Date()).getTime()+"_"+targetAss.getLabel()+".xar");
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
			//XsyncFileUtils xsyncFileUtils = new XsyncFileUtils();
			//String anonymizedSessionPath = xsyncFileUtils.getAnonymizedSessionPath(orig);
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


				
				//File experimentPath = new File(anonymizedSessionPath);
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
				xarFile = new File(expCachePath, (new Date()).getTime()+"_"+targetExperiment.getLabel()+".xar");
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
			final String anonymizedSessionPath = XsyncFileUtils.getAnonymizedSessionPath(orig);
			
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
				xarFile = new File(expCachePath, (new Date()).getTime()+"_"+target.getLabel()+".xar");
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
			String remoteUrl = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl();

			XSyncTools xsyncTools = new XSyncTools(user, _jdbcTemplate, _queryResultUtil);
			xsyncTools.saveSyncDetails(projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSourceProjectId(), local_id, remote_id,syncStatus,xsiType,remoteProjectId, remoteUrl);
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
			//XnatProjectdata localProject = XnatProjectdata.getXnatProjectdatasById(localSubject.getProject(), user, false);
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
                        resourceSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_SYNCED_AND_NOT_VERIFIED);
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
				_log.error("Could not update resource " + resource.getLabel() + " for experiment " + target.getId(),e);
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
