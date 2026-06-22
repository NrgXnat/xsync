package org.nrg.xsync.utils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.nrg.xdat.om.XsyncXsyncinfodata;
import org.nrg.xdat.om.XsyncXsyncprojectdata;
import org.nrg.xdat.om.XsyncXsyncremotemapdata;
import org.nrg.xft.XFTItem;
import org.nrg.xft.event.EventDetails;
import org.nrg.xft.event.EventMetaI;
import org.nrg.xft.event.EventUtils;
import org.nrg.xft.event.persist.PersistentWorkflowI;
import org.nrg.xft.event.persist.PersistentWorkflowUtils;
import org.nrg.xft.security.UserI;
import org.nrg.xft.utils.SaveItemHelper;
import org.nrg.xft.utils.ValidationUtils.ValidationResults;
import org.nrg.xnat.utils.WorkflowUtils;
import org.nrg.xsync.local.IdMapper;
import org.nrg.xsync.pojo.configuration.SyncConfigurationPojo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import org.springframework.util.CollectionUtils;

/**
 * @author Mohana Ramaratnam
 *
 */
public class XsyncUtils {

	public static final String SYNC_FREQUENCY_DAILY = "daily";
	public static final String SYNC_FREQUENCY_WEEKLY = "weekly";
	public static final String SYNC_FREQUENCY_MONTHLY = "monthly";
	public static final String SYNC_FREQUENCY_ON_DEMAND = "on demand";
	
	public static final String SYNC_STATUS_SYNCED_AND_NOT_VERIFIED = "SYNCED_AND_NOT_VERIFIED";
	public static final String SYNC_STATUS_SYNCED_AND_VERIFIED = "SYNCED_AND_VERIFIED";

	public static final String SYNC_STATUS_FAILED = "FAILED";
	public static final String SYNC_STATUS_SKIPPED = "SKIPPED";
	public static final String SYNC_STATUS_SKIPPED_BY_FILTER = "SKIPPED_BY_FILTER";
	public static final String SYNC_STATUS_INVALID_FILTER = "INVALID_FILTER";
	public static final String SYNC_STATUS_INCOMPLETE = "INCOMPLETE";
	public static final String SYNC_STATUS_FAILED_TO_VERIFY = "FAILED_TO_VERIFY";
	
	public static final String SYNC_STATUS_INTERRUPTED = "INTERRUPTED";
	public static final String SYNC_STATUS_CONNECTION_FAILED = "CONNECTION FAILED";
	
	public static final String SYNC_STATUS_DELETED = "DELETED";
	public static final String SYNC_STATUS_CONFLICT = "CONFLICT";
	public static final String SYNC_STATUS_WAITING_TO_SYNC="Waiting to Sync";
	public static final String SYNC_STATUS_SYNC_REQUESTED="Sync Requested";

	public static final String SYNC_STATUS_BEGINNING ="PREPARING TO SYNC";
	public static final String SYNC_STATUS_IN_PROGRESS="SYNC IN PROGRESS";

	public static final String SYNC_TYPE_ALL = "all";
	public static final String SYNC_TYPE_NONE = "none";
	public static final String SYNC_TYPE_INCLUDE = "include";
	public static final String SYNC_TYPE_EXCLUDE = "exclude";

	public static final String USER_ACCESS_OWNER = "owner";
	public static final String USER_ACCESS_MEMBER = "member";
	public static final String USER_ACCESS_COLLABORATOR= "collaborator";
	public static final String USER_API_GROUP_ID= "GROUP_ID";
	public static final String USER_API_LOGIN= "login";
	public static final String PROJECT_SYNC_LOG_RESOURCE_LABEL="SYNCHRONIZATION_LOGS";

	public static final String RESOURCE_NO_LABEL = "NO LABEL";
	
	public static final String XSYNC_VERIFICATION_STATUS = "XSYNC_VERIFICATION_STATUS";
	public static final String XSYNC_VERIFICATION_STATUS_MISSING_FILES = "MISSING_FILES";
	public static final String XSYNC_VERIFICATION_STATUS_VERIFIED_AND_COMPLETE = "VERIFIED_AND_COMPLETE";
	public static final String XSYNC_VERIFICATION_STATUS_FAILED_TO_CONNECT = "FAILED_TO_CONNECT";
	public static final String XSYNC_VERIFICATION_STATUS_FAILED_DUE_TO_EXCEPTION = "FAILED_DUE_TO_EXCEPTION";

	public static final long GLOBAL_SLEEP_IN_MILLIS = 900000; //15 Minutes
	public static final int GLOBAL_RETRY_COUNTS = 3; //15 Minutes

	public static final String REPORT_FORMAT_JSON = "JSON";
	public static final String REPORT_FORMAT_CSV = "CSV";
	public static final String OBJECT_TYPE = "objectType";
	public static final String REPORT_FORMAT = "reportFormat";

	public static final long THREAD_SLEEP_TIME = 120000;
	
	public enum FilterType 
	{
		CONTAINS,
		REGEX
	}
	
	private static final Logger _log = LoggerFactory.getLogger(XsyncUtils.class);

	private final NamedParameterJdbcTemplate _jdbcTemplate;
	private final UserI                      _user;

	public XsyncUtils(final NamedParameterJdbcTemplate jdbcTemplate, final UserI user) {
		_jdbcTemplate = jdbcTemplate;
		_user = user;
	}
	
	public synchronized void loadConfigurationToDB(final File jsonFile) throws Exception{
		ObjectMapper objectMapper = new ObjectMapper();
		SyncConfigurationPojo configurationPojo = objectMapper.readValue(jsonFile, SyncConfigurationPojo.class);
		loadConfigurationToDB(configurationPojo);
	}

	public synchronized void loadConfigurationToDB(SyncConfigurationPojo configurationPojo) throws Exception{
        String sourceProjectId = configurationPojo.getSource_project_id();
		XFTItem item = XFTItem.NewItem(XsyncXsyncprojectdata.SCHEMA_ELEMENT_NAME, _user);
        XsyncXsyncprojectdata syncProject = new XsyncXsyncprojectdata(item);
        XsyncXsyncprojectdata existing = null;
        ArrayList<XsyncXsyncprojectdata> list = XsyncXsyncprojectdata.getXsyncXsyncprojectdatasByField(XsyncXsyncprojectdata.SCHEMA_ELEMENT_NAME+"/source_project_id", sourceProjectId, _user, true);
        boolean hasExisting = false;
        XsyncXsyncinfodata existingSyncInfo = null;
        if (!CollectionUtils.isEmpty(list)) {
        	existing = list.getFirst();
        	syncProject.setItem(existing.getItem());
        	hasExisting = true;
        	existingSyncInfo = syncProject.getSyncinfo();
        }
        
		syncProject.setSourceProjectId(configurationPojo.getSource_project_id());
		syncProject.setNotificationEmails(configurationPojo.getNotification_emails());
		syncProject.setSyncEnabled(configurationPojo.getEnabled());
		item = XFTItem.NewItem(XsyncXsyncinfodata.SCHEMA_ELEMENT_NAME, _user);
		XsyncXsyncinfodata syncInfo = new XsyncXsyncinfodata(item);
        syncInfo.setSyncFrequency(configurationPojo.getSync_frequency());
		syncInfo.setSyncNewOnly(configurationPojo.getSync_new_only());
		syncInfo.setIdentifiers(configurationPojo.getIdentifiers());
		if (syncInfo.getIdentifiers().equals(IdMapper.USE_CUSTOM)) {
			syncInfo.setCustomIdentifierClass(configurationPojo.getCustomIdentifiers());
		}
		syncInfo.setRemoteUrl(configurationPojo.getRemote_url());
		syncInfo.setRemoteProjectId(configurationPojo.getRemote_project_id());
		boolean destinationChange = false;
		if (hasExisting && existingSyncInfo != null) {
			if (!syncInfo.getRemoteUrl().equals(existingSyncInfo.getRemoteUrl()))
				destinationChange = true;
			if (!syncInfo.getRemoteProjectId().equals(existingSyncInfo.getRemoteProjectId()))
				destinationChange = true;
		}
		//Did the configuration change to a different Remote Project and a different Remote URL?
		if (destinationChange) {
			syncInfo.setSyncStartTime(null);
			syncInfo.setSyncEndTime(null);
		}else {
			if (existingSyncInfo != null) {
				syncInfo.setSyncStartTime(existingSyncInfo.getSyncStartTime());
				syncInfo.setSyncEndTime(existingSyncInfo.getSyncEndTime());
			}else {
				syncInfo.setSyncStartTime(null);
				syncInfo.setSyncEndTime(null);
			}
		}
		syncProject.setSyncinfo(syncInfo.getItem());
		syncProject.setSyncScheduledBy(_user.getLogin());
        final ValidationResults vr = syncProject.validate();
        if (vr != null && !vr.isValid()) {
        	throw new Exception(sourceProjectId + " Xsync Setup failed. Invalid JSON: " + vr.isValid());
        }
        String msg = (existing==null)?"Added Synchronization":"Updated Synchronization";
        EventMetaI c = EventUtils.DEFAULT_EVENT(_user, msg);
        
        if (SaveItemHelper.authorizedSave(syncProject, _user, false, true, c)) {
            EventDetails details = EventUtils.newEventInstance(EventUtils.CATEGORY.DATA, EventUtils.TYPE.WEB_SERVICE,  EventUtils.getAddModifyAction(syncProject.getXSIType(), (existing == null)), "", "");
        	PersistentWorkflowI wrk = PersistentWorkflowUtils.buildOpenWorkflow(_user, syncProject.getXSIType(),syncProject.getXsyncXsyncprojectdataId()+"",syncProject.getSourceProjectId(), details);
        	WorkflowUtils.complete(wrk, c);
        }
	}
	
	public List<XsyncXsyncprojectdata> getAllProjectsSetToBeSynced() {
		return XsyncXsyncprojectdata.getAllXsyncXsyncprojectdatas(_user, true);
	}
	
	public List<XsyncXsyncprojectdata> getAllProjectsToBeSyncedDaily() {
		List<XsyncXsyncprojectdata> xsyncThese = new ArrayList<>();
		List<XsyncXsyncprojectdata> xsyncProjects = getAllProjectsSetToBeSynced();
		if (xsyncProjects != null && !xsyncProjects.isEmpty()) {
			for (XsyncXsyncprojectdata projectXsync:xsyncProjects) {
				XsyncXsyncinfodata xsyncInfo = projectXsync.getSyncinfo();
				if (SYNC_FREQUENCY_DAILY.equals(xsyncInfo.getSyncFrequency())) {
					xsyncThese.add(projectXsync);
				}
			}
		}
		return xsyncThese;
	}

	public List<XsyncXsyncprojectdata> getAllProjectsToBeSyncedWeekly() {
		List<XsyncXsyncprojectdata> xsyncThese = new ArrayList<>();
		List<XsyncXsyncprojectdata> xsyncProjects = getAllProjectsSetToBeSynced();
		if (xsyncProjects != null && !xsyncProjects.isEmpty()) {
			for (XsyncXsyncprojectdata projectXsync:xsyncProjects) {
				XsyncXsyncinfodata xsyncInfo = projectXsync.getSyncinfo();
				if (SYNC_FREQUENCY_WEEKLY.equals(xsyncInfo.getSyncFrequency())) {
					xsyncThese.add(projectXsync);
				}
			}
		}
		return xsyncThese;
	}
	
	public List<XsyncXsyncprojectdata> getAllProjectsToBeSyncedMonthly() {
		List<XsyncXsyncprojectdata> xsyncThese = new ArrayList<>();
		List<XsyncXsyncprojectdata> xsyncProjects = getAllProjectsSetToBeSynced();
		if (xsyncProjects != null && !xsyncProjects.isEmpty()) {
			for (XsyncXsyncprojectdata projectXsync:xsyncProjects) {
				XsyncXsyncinfodata xsyncInfo = projectXsync.getSyncinfo();
				if (SYNC_FREQUENCY_MONTHLY.equals(xsyncInfo.getSyncFrequency())) {
					xsyncThese.add(projectXsync);
				}
			}
		}
		return xsyncThese;
	}
	
	public List<XsyncXsyncprojectdata> getAllProjectsToBeSyncedOnDemand() {
		List<XsyncXsyncprojectdata> xsyncThese = new ArrayList<>();
		List<XsyncXsyncprojectdata> xsyncProjects = getAllProjectsSetToBeSynced();
		if (xsyncProjects != null && !xsyncProjects.isEmpty()) {
			for (XsyncXsyncprojectdata projectXsync:xsyncProjects) {
				XsyncXsyncinfodata xsyncInfo = projectXsync.getSyncinfo();
				if (SYNC_FREQUENCY_ON_DEMAND.equals(xsyncInfo.getSyncFrequency())) {
					xsyncThese.add(projectXsync);
				}
			}
		}
		return xsyncThese;
	}

	public boolean isProjectConfiguredToSyncOnDemand(String projectId) {
		boolean isToBeSyncedOnDemand = false;
		XsyncXsyncprojectdata xsyncProject = getSyncDetailsForProject(projectId);
		XsyncXsyncinfodata xsyncInfo = xsyncProject.getSyncinfo();
		if (SYNC_FREQUENCY_ON_DEMAND.equals(xsyncInfo.getSyncFrequency())) {
			isToBeSyncedOnDemand = true;
		}
		return isToBeSyncedOnDemand;
	}

	public 	XsyncXsyncprojectdata getSyncDetailsForProject(String projectId){
		XsyncXsyncprojectdata syncData = null; 
		ArrayList<XsyncXsyncprojectdata> results = XsyncXsyncprojectdata.getXsyncXsyncprojectdatasByField("xsync:xsyncProjectData/source_project_id",projectId,_user,false);
		if (results != null && results.size() == 1) {
			syncData = results.getFirst();
		}else {
            _log.error("Unexpected number of results {}", projectId);
		}
		return syncData;
	}

	public ArrayList<XsyncXsyncremotemapdata> getAllRemoteMapDetails() {
		return XsyncXsyncremotemapdata.getAllXsyncXsyncremotemapdatas(_user,true);
	}
	
	public String getRemoteId(String localProjectId, String localXnatId) {
		String remoteId = null;
		String query = "select remote_xnat_id from xsync_xsyncremotemapdata where project_id=:localProjectId and local_xnat_id=:localXnatId";
		final MapSqlParameterSource parameters = new MapSqlParameterSource();
		parameters.addValue("localProjectId", localProjectId);
		parameters.addValue("localXnatId", localXnatId);
		 List<String> results = _jdbcTemplate.queryForList(query, parameters,String.class);
		 if (results !=null && results.size()>1) {
			 remoteId = results.getFirst();
		 }
		return remoteId;
	}
	
	public List<Map<String,Object>> getIdAndLabelMapForSyncedData(String localProjectId,String objectType) {
		String query = "select distinct rm.local_xnat_id as local_xnat_id,rm.remote_xnat_id remote_xnat_id,sub.label as label from xsync_xsyncremotemapdata rm,xnat_subjectdata sub " + 
				"where rm.source_project_id=:localProjectId and rm.local_xnat_id=sub.id";
		if("Experiment".equalsIgnoreCase(objectType))
		{
			query="select distinct rm.local_xnat_id  as local_xnat_id,rm.remote_xnat_id as remote_xnat_id,exp.label  as label from xsync_xsyncremotemapdata rm," + 
					"xnat_experimentdata exp where rm.source_project_id=:localProjectId and rm.local_xnat_id= exp.id";
		}
		
		final MapSqlParameterSource parameters = new MapSqlParameterSource();
		parameters.addValue("localProjectId", localProjectId);
        return _jdbcTemplate.queryForList(query, parameters);
	}
}
