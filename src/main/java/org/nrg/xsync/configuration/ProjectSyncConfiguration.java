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
import org.nrg.xsync.configuration.json.ImagingScanConfiguration;
import org.nrg.xsync.configuration.json.ImagingSessionConfiguration;
import org.nrg.xsync.configuration.json.SubjectAssessorConfiguration;
import org.nrg.xsync.configuration.json.SyncConfiguration;
import org.nrg.xsync.exception.XsyncNotConfiguredException;
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

	XnatProjectdata project;
	SyncConfiguration syncConfiguration = null;
	XsyncXsyncprojectdata syncProjectConfiguration;
	UserI _user;
	
	public ProjectSyncConfiguration(String projectId, UserI user) throws XsyncNotConfiguredException{
		_user = user;
		project = XnatProjectdata.getProjectByIDorAlias(projectId, (XDATUser)user, false);
		setSynchronizationConfiguration();
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
			throw new XsyncNotConfiguredException();
		} 
		if (syncProjectConfiguration.getSyncinfo().getSyncStartTime() == null) {
			//No sync has been done so far. Set a dummy date and then start
			//If this is the first time that the sync is taking place
				int year = 1970;
			    int month = 0;
			    int day = 1;
			    String date = year + "/" + month + "/" + day;
			    try {
			        SimpleDateFormat formatter = new SimpleDateFormat("yyyy/MM/dd");
			        Date oldDate = formatter.parse(date);
					syncProjectConfiguration.getSyncinfo().setSyncStartTime(oldDate);
					try {
						//Backward compatible XNAT 1.6.5 does not have ADMIN_EVENT method
						EventMetaI c = EventUtils.DEFAULT_EVENT(_user,"ADMIN_EVENT occurred");
						syncProjectConfiguration.save(_user, false, true,c);
					}catch(Exception e) {
						_log.debug("Unable to save synchronization  start time: " + " Cause:" + e.getMessage());
						
					}
			    } catch (ParseException e) {
			        _log.debug("Could not set the sync start time " + e.getMessage());
			    }
		}
	}

	public XsyncXsyncprojectdata getProjectSyncConfigurationFromDB() {
		return syncProjectConfiguration ;
	}
	
	private void setSynchronizationConfiguration() throws XsyncNotConfiguredException {
		File syncConfigFile = new File(getSynchronizationConfigurationFilePath());
		if (syncConfigFile.exists()) {
			ObjectMapper objectMapper = new ObjectMapper();
			try {
				syncConfiguration = objectMapper.readValue(syncConfigFile, SyncConfiguration.class);
			}catch(Exception e) {
				e.printStackTrace();
			}
			
		}else {
			throw new XsyncNotConfiguredException();
		}
	}

	public SyncConfiguration getSynchronizationConfiguration() {
		return syncConfiguration;
	}
	
	private String getSynchronizationConfigurationFilePath() {
		String filePath = project.getArchiveRootPath() + File.separator + "resources" + File.separator + "synchronization" + File.separator + "sync_config.json";
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
	
	public boolean isResourceToBeSynced(String resourceName) {
		boolean isToBeSynced = false;
		if (syncConfiguration == null) return false;
		List<String> resources = syncConfiguration.getProjectresources();
		if (resources == null || resources.size() < 1) return false;
		for (String resource: resources) {
			if (resource.equals(resourceName)) {
				isToBeSynced = true;
				break;
			}
		}
		return isToBeSynced;
	}

	public boolean isSubjectResourceToBeSynced(String resourceName) {
		boolean isToBeSynced = false;
		if (syncConfiguration == null) return false;
		List<String> subjectresources = syncConfiguration.getSubjectresources();
		if (subjectresources == null || subjectresources.size() < 1) return false;
		if (subjectresources.contains(resourceName))
				isToBeSynced = true;
		return isToBeSynced;
	}
	
	public boolean isSubjectAssessorToBeSynced(String assessorXsiType) {
		boolean isToBeSynced = false;
		if (syncConfiguration == null) return false;
		List<SubjectAssessorConfiguration> subjectassessors = syncConfiguration.getSubjectassessors();
		if (subjectassessors == null || subjectassessors.size() < 1) return false;
		for (SubjectAssessorConfiguration assessor: subjectassessors) {
			if (assessor.getXsiType()!= null && assessor.getXsiType().equals(assessorXsiType)) {
				isToBeSynced = true;
				break;
			}
		}
		if (isToBeSynced) _log.debug(assessorXsiType + " needs to be synced " + isToBeSynced);
		return isToBeSynced;
	}

	public boolean isSubjectAssessorResourceToBeSynced(String assessorXsiType, String resourceLabel) {
		boolean isToBeSynced = false;
		if (syncConfiguration == null) return false;
		SubjectAssessorConfiguration requiredAssessor = null;
		List<SubjectAssessorConfiguration> subjectassessors = syncConfiguration.getSubjectassessors();
		if (subjectassessors == null || subjectassessors.size() < 1) return false;
		for (SubjectAssessorConfiguration assessor: subjectassessors) {
			if (assessor.getXsiType()!= null && assessor.getXsiType().equals(assessorXsiType)) {
				requiredAssessor = assessor;
				break;
			}
		}
		if (requiredAssessor != null) {
			List<String> resources = requiredAssessor.getResources();
			if (resources != null) {
				for (String r:resources) {
					if (r.equals(resourceLabel)) {
						isToBeSynced = true;
						break;
					}
				}
			}
		}
		if (isToBeSynced) _log.debug(assessorXsiType + " resource " + resourceLabel + " needs to be synced " + isToBeSynced);
		return isToBeSynced;
	}

	
	public boolean isOnlyASubjectAssessor(XnatSubjectassessordataI experiment) {
		boolean isOnlyASubjectAssessor = true;
		if (experiment instanceof XnatImagesessiondataI) {
			isOnlyASubjectAssessor = false;
		}
		return isOnlyASubjectAssessor;
	}	

	public boolean isImagingSessionToBeSynced(String imagingSessionXsiType) {
		boolean isToBeSynced = false;
		if (syncConfiguration == null) return false;
		List<ImagingSessionConfiguration> imagingSessions = syncConfiguration.getImagingsessions();
		if (imagingSessions == null || imagingSessions.size() < 1) return false;
		for (ImagingSessionConfiguration imagingSession: imagingSessions) {
			if (imagingSession.getXsiType().equals(imagingSessionXsiType)) {
				isToBeSynced = true;
				break;
			}
		}
		if (isToBeSynced) _log.debug(imagingSessionXsiType + " needs to be synced " + isToBeSynced);
		return isToBeSynced;
	}
	
	public boolean isImagingSessionScanToBeSynced(String imagingScanType) {
		boolean isToBeSynced = false;
		if (syncConfiguration == null) return false;
		List<ImagingSessionConfiguration> imagingSessions = syncConfiguration.getImagingsessions();
		if (imagingSessions == null || imagingSessions.size() < 1) return false;
		for (ImagingSessionConfiguration imagingSession: imagingSessions) {
			for (ImagingScanConfiguration imagingScan:imagingSession.getScans()) {
				if (imagingScan.getType().equals(imagingScanType)) {
					isToBeSynced = true;
					break;
				}
			}
			if (isToBeSynced) break;
		}
		return isToBeSynced;
	}
	
	public boolean isImagingSessionScanResourceToBeSynced(ImagingScanConfiguration imagingScan, String imagingScanResourceName) {
			boolean isToBeSynced = false;
			if (syncConfiguration == null) return false;
			List<String> scanResources = imagingScan.getResources();
			for (String resource: scanResources) {
				if (resource.equals(imagingScanResourceName)) {
					isToBeSynced = true;
					break;
				}
			}
			return isToBeSynced;
	}
	
	public ImagingSessionConfiguration getImagingSessionConfiguration(String xsiType) {
		List<ImagingSessionConfiguration> imagingSessions = syncConfiguration.getImagingsessions();
		ImagingSessionConfiguration session = null;
		if (imagingSessions == null || imagingSessions.size() < 1) return null;
		for (ImagingSessionConfiguration imagingSession : imagingSessions) {
			if (imagingSession.getXsiType().equals(xsiType)) {
				session = imagingSession;
				break;
			}
		}
		return session;
	}
	
	public boolean isSetToAutoUpdate() {
		//return getProjectSyncConfigurationFromDB().getSyncinfo().getAutoSync();
		return this.getSynchronizationConfiguration().getAuto_sync().booleanValue();
	}
	
	public boolean isThisProjectBeingSyncedForTheFirstTime() {
		boolean beingSyncedForTheFirstTime = false;
		XsyncXsyncinfodata syncInfo = getProjectSyncConfigurationFromDB().getSyncinfo();
		if (syncInfo.getSyncEndTime() == null && syncInfo.getSyncStatus() == null ) {
			beingSyncedForTheFirstTime = true;
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
			return self;		
		}


}
