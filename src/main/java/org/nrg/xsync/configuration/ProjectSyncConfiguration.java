package org.nrg.xsync.configuration;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.nrg.xdat.model.XnatImagesessiondataI;
import org.nrg.xdat.model.XnatSubjectassessordataI;
import org.nrg.xdat.om.XnatImagesessiondata;
import org.nrg.xdat.om.XnatProjectdata;
import org.nrg.xdat.om.XsyncXsyncinfodata;
import org.nrg.xdat.om.XsyncXsyncprojectdata;
import org.nrg.xdat.security.XDATUser;
import org.nrg.xft.event.EventMetaI;
import org.nrg.xft.event.EventUtils;
import org.nrg.xft.security.UserI;
import org.nrg.xsync.configuration.json.SyncConfiguration;
import org.nrg.xsync.configuration.json.SyncConfigurationAdvancedOption;
import org.nrg.xsync.configuration.json.SyncConfigurationImagingSessionAdvancedOption;
import org.nrg.xsync.exception.XsyncNotConfiguredException;
import org.nrg.xsync.utils.XsyncFileUtils;
import org.nrg.xsync.utils.XsyncUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Mohana Ramaratnam
 *
 */
public class ProjectSyncConfiguration {
	private static final Logger _log = LoggerFactory.getLogger(ProjectSyncConfiguration.class);
	private static final int  OLDYEAR = 1970;
	
	XnatProjectdata project;
	SyncConfiguration syncConfiguration = null;
	XsyncXsyncprojectdata syncProjectConfiguration;
	UserI _user;
	
	public ProjectSyncConfiguration(String projectId, UserI user) throws XsyncNotConfiguredException{
		_user = user;
		project = XnatProjectdata.getProjectByIDorAlias(projectId, (XDATUser)user, false);
		setSynchronizationConfigurationFromFile();
		setProjectSyncConfiguration();
	}

	public XnatProjectdata getProject() {
		return project;
	}
	
	private void setProjectSyncConfiguration() throws XsyncNotConfiguredException{
		XsyncUtils xsyncUtils = new XsyncUtils(_user);
		syncProjectConfiguration = xsyncUtils.getSyncDetailsForProject(project.getId());
		if (syncProjectConfiguration == null) {
			_log.error("Could not find sync data for project " + project.getId());
			throw new XsyncNotConfiguredException("Could not find sync data for project " + project.getId());
		}
		boolean save = false;
			//No sync has been done so far. Set a dummy date and then start
			//If this is the first time that the sync is taking place
		       try {
		    	   Date oldDate = getAnOldDate();
		    	   if (syncProjectConfiguration.getSyncinfo().getSyncStartTime() == null) {
		    		   syncProjectConfiguration.getSyncinfo().setSyncStartTime(oldDate);
		    		   save = true;
		    	   }
			   		if (syncProjectConfiguration.getSyncinfo().getSyncEndTime() == null) {
						syncProjectConfiguration.getSyncinfo().setSyncEndTime(oldDate);
						save = true;
					}
		       }catch(ParseException e) {
					throw new XsyncNotConfiguredException(e.getMessage());
		       }
				if (save) {
						try {
							//Backward compatible XNAT 1.6.5 does not have ADMIN_EVENT method
							EventMetaI c = EventUtils.DEFAULT_EVENT(_user,"ADMIN_EVENT occurred");
							syncProjectConfiguration.save(_user, false, true,c);
						}catch(Exception e) {
							_log.debug("Unable to save synchronization  start time: " + " Cause:" + e.getMessage());
							throw new XsyncNotConfiguredException("Unable to save synchronization  start time: " + " Cause:" + e.getMessage());
						}
				}
	}
	
	private Date getAnOldDate() throws ParseException{
		//int year = 1970;
	    int month = 0;
	    int day = 1;
	    String date = this.OLDYEAR + "/" + month + "/" + day;
	    Date oldDate = null;
	    try {
	        SimpleDateFormat formatter = new SimpleDateFormat("yyyy/MM/dd");
	        oldDate = formatter.parse(date);
	        return oldDate;
	    }catch(ParseException e) {
	        _log.debug("Could not create an old dtae " + e.getMessage());
	        throw e;
	    }
	}

	public XsyncXsyncprojectdata getProjectSyncConfigurationFromDB() {
		return syncProjectConfiguration ;
	}
	
	private void setSynchronizationConfigurationFromFile() throws XsyncNotConfiguredException {
		File syncConfigFile = new File(getSynchronizationConfigurationFilePath());
		if (syncConfigFile.exists()) {
			ObjectMapper objectMapper = new ObjectMapper();
			try {
				syncConfiguration = objectMapper.readValue(syncConfigFile, SyncConfiguration.class);
			}catch(Exception e) {
				e.printStackTrace();
			}
			
		}else {
			throw new XsyncNotConfiguredException("Synchronization Configuration File does not exist " + syncConfigFile.getAbsolutePath());
		}
	}

	public SyncConfiguration getSynchronizationConfiguration() {
		return syncConfiguration;
	}
	
	private String getSynchronizationConfigurationFilePath() {
		String filePath = project.getArchiveRootPath() + File.separator + "resources" + File.separator + XsyncFileUtils.SYNCHRONIZATION_LABEL + File.separator + "sync_config.json";
		return filePath;
	}

	public static String GetAnonymizationFilePath(String projectArchiveRootPath, String fileType) {
		String filePath = getAnonymizationFilePath(projectArchiveRootPath,fileType ) ;
		return filePath;
	}

	public static String GetAnonymizationFilePath(XnatImagesessiondata s, String fileType) throws Exception { 
		return GetAnonymizationFilePath(s.getArchiveRootPath(),fileType);
	}


	
	private static String getAnonymizationFilePath(String projectArchiveRootPath, String fileType) {
		String filePath = projectArchiveRootPath + File.separator + "resources" + File.separator + "synchronization" + File.separator + fileType+"_anon.das";
		return filePath;
	}
	
	public boolean isResourceToBeSynced(String resourceLabel) {
		if (syncConfiguration == null) 
			return false;
		else 
			return syncConfiguration.isProjectResourceAllowedToSync(resourceLabel);
	}

	public boolean isSubjectResourceToBeSynced(String resourceLabel) {
		if (syncConfiguration == null) 
			return false;
		else 
			return syncConfiguration.isSubjectResourceAllowedToSync(resourceLabel);
	}
	
	public boolean isSubjectAssessorToBeSynced(String assessorXsiType) {
		if (syncConfiguration == null) 
			return false;
		else {
			return syncConfiguration.isSubjectAssessorAllowedToSync(assessorXsiType);
		}
	}

	public boolean isSubjectAssessorResourceToBeSynced(String assessorXsiType, String resourceLabel) {
		if (syncConfiguration.hasSubjectAssessorConfigurationDefinition()) {
			SyncConfigurationAdvancedOption advOption = syncConfiguration.getSubjectAssessorAdvancedOptions(assessorXsiType);
			return advOption.isResourceAllowedToSync(resourceLabel);
		}else {
			return true; //Default - when not specified all is pushed
		}
	}

	public boolean subjectAssessorNeedsOkToSync(String assessorXsiType) {
		if (syncConfiguration.hasSubjectAssessorConfigurationDefinition()) {
			SyncConfigurationAdvancedOption advOption = syncConfiguration.getSubjectAssessorAdvancedOptions(assessorXsiType);
			return advOption.getNeeds_ok_to_sync();
		}else 
			return false;
	}

	public boolean imagingSessionNeedsOkToSync(String xsiType) {
		if (syncConfiguration.hasImagingSessionConfigurationDefinition()) {
			SyncConfigurationImagingSessionAdvancedOption advOption = syncConfiguration.getImagingSessionAdvancedOptions(xsiType);
			return advOption.getNeeds_ok_to_sync();
		}else 
			return false;
	}

	public boolean imagingAssessorNeedsOkToSync(String xsiType, String assessorXsiType) {
		if (syncConfiguration.hasImagingSessionConfigurationDefinition()) {
			SyncConfigurationImagingSessionAdvancedOption advOption = syncConfiguration.getImagingSessionAdvancedOptions(xsiType);
			try {
				SyncConfigurationAdvancedOption advSessionAssessorOption = advOption.getSession_assessors().getAdvancedOption(assessorXsiType);
				return advSessionAssessorOption.getNeeds_ok_to_sync();
			}catch(NullPointerException npe) {
				return false; // Not present defaults to ok not required
			}
		}else 
			return false;
	}

	
	public boolean isOnlyASubjectAssessor(XnatSubjectassessordataI experiment) {
		boolean isOnlyASubjectAssessor = true;
		if (experiment instanceof XnatImagesessiondataI) {
			isOnlyASubjectAssessor = false;
		}
		return isOnlyASubjectAssessor;
	}	

	public boolean isImagingSessionToBeSynced(String imagingSessionXsiType) {
		if (syncConfiguration == null) 
			return false;
		else {
			return syncConfiguration.isImagingSessionAllowedToSync(imagingSessionXsiType);
		}
	}
	
	public boolean isImagingSessionScanToBeSynced(String imagingSessionXsiType, String imagingScanType) {
		if (syncConfiguration == null) 
			return false;
		else {
			SyncConfigurationImagingSessionAdvancedOption imgSessionAdvOption = syncConfiguration.getImagingSessionAdvancedOptions(imagingSessionXsiType);
			return imgSessionAdvOption.isAllowedToSyncScan(imagingScanType);
		}

	}
	
	public boolean isImagingSessionScanResourceToBeSynced(String imagingSessionXsiType, String imagingScanType, String imagingScanResourceName) {
			if (syncConfiguration == null) return false;
			SyncConfigurationImagingSessionAdvancedOption imgSessionAdvOption = syncConfiguration.getImagingSessionAdvancedOptions(imagingSessionXsiType);
			if (imgSessionAdvOption.isAllowedToSyncScan(imagingScanType)) {
				return imgSessionAdvOption.isAllowedToSyncScanResource(imagingScanResourceName);
			}else {
				return false;
			}
	}
	
	
	public boolean isSetToSyncNewOnly() {
		return this.getSynchronizationConfiguration().getSync_new_only().booleanValue();
	}
	
	@SuppressWarnings("deprecation")
	public boolean isThisProjectBeingSyncedForTheFirstTime() {
		boolean beingSyncedForTheFirstTime = false;
		XsyncXsyncinfodata syncInfo = getProjectSyncConfigurationFromDB().getSyncinfo();
		if (syncInfo.getSyncEndTime() == null && syncInfo.getSyncStatus() == null ) {
			beingSyncedForTheFirstTime = true;
		}else {
			Object date = syncInfo.getSyncEndTime();
			if (date != null) {
				if ((((Date)date).getYear() == OLDYEAR) && syncInfo.getSyncStatus() == null) {
					beingSyncedForTheFirstTime = true;		
				}
			}
		}
		return beingSyncedForTheFirstTime;
	}

		
	public String toString() {
			String self = "";
			self += " Project : " + project + "\n";
			self += "SyncConfiguration: " + syncConfiguration + "\n";
			self += " DB SyncInfo:  " +"\n";
			self += "Remote Project: " + syncProjectConfiguration.getSyncinfo().getRemoteProjectId() + "\n"; 
			self += "Remote URL: " + syncProjectConfiguration.getSyncinfo().getRemoteUrl();
			self += "Sync_Blocked: " + syncProjectConfiguration.getSyncBlocked();
			return self;		
		}


}
