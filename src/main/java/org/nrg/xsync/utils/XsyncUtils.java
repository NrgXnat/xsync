package org.nrg.xsync.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import com.google.common.base.Joiner;
import org.nrg.framework.services.SerializerService;
import org.nrg.xdat.XDAT;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Mohana Ramaratnam
 *
 */
public class XsyncUtils {

	public static final String SYNC_FREQUENCY_DAILY = "daily";
	public static final String SYNC_FREQUENCY_WEEKLY = "weekly";
	public static final String SYNC_FREQUENCY_MONTHLY = "monthly";
	public static final String SYNC_FREQUENCY_ON_DEMAND = "on demand";
	
	public static final String SYNC_STATUS_SYNCED = "SYNCED";
	public static final String SYNC_STATUS_FAILED = "FAILED";
	public static final String SYNC_STATUS_SKIPPED = "SKIPPED";
	
	public static final String SYNC_STATUS_INTERRUPTED = "INTERRUPTED";
	public static final String SYNC_STATUS_CONNECTION_FAILED = "CONNECTION FAILED";
	public static final String SYNC_STATUS_DELETED = "DELETED";
	
	public static final String SYNC_TYPE_ALL = "all";
	public static final String SYNC_TYPE_NONE = "none";
	public static final String SYNC_TYPE_INCLUDE = "include";
	public static final String SYNC_TYPE_EXCLUDE = "exclude";

	public static final String PROJECT_ELEMENT_JSON_NAME = "source_project_id";

	public static final String REMOTE_HOST_URL = "remote_url";
	
	public static final String USER_ACCESS_OWNER = "owner";
	public static final String USER_ACCESS_MEMBER = "member";
	public static final String USER_ACCESS_COLLABORATOR= "collaborator";
	public static final String USER_API_GROUP_ID= "GROUP_ID";
	public static final String USER_API_LOGIN= "login";
	public static final String PROJECT_SYNC_LOG_RESOURCE_LABEL="SYNCHRONIZATION_LOGS";
	
	public static final String SYNC_STATUS_WAITING_TO_SYNC="Waiting to Sync";
	
	public static final String RESOURCE_NO_LABEL = "NO LABEL";
	

	private static final Logger _log = LoggerFactory.getLogger(XsyncUtils.class);

	private final SerializerService          _serializer;
	private final NamedParameterJdbcTemplate _jdbcTemplate;
	private final UserI                      _user;

	public XsyncUtils(final SerializerService serializer, final NamedParameterJdbcTemplate jdbcTemplate, final UserI user) {
		_serializer = serializer;
		_jdbcTemplate = jdbcTemplate;
		_user = user;
	}
	
	public synchronized void loadConfigurationToDB(final File jsonFile) throws Exception{
		try (final InputStream input = new FileInputStream(jsonFile)) {
			JsonNode synchronizationJson = _serializer.deserializeJson(input);
			loadConfigurationToDB(synchronizationJson);
		}
	}

	
	public synchronized  void loadConfigurationToDB(JsonNode synchronizationJson) throws Exception{
        String sourceProjectId = synchronizationJson.get(PROJECT_ELEMENT_JSON_NAME).asText();
		XFTItem item = XFTItem.NewItem(XsyncXsyncprojectdata.SCHEMA_ELEMENT_NAME, _user);
        XsyncXsyncprojectdata syncProject = new XsyncXsyncprojectdata(item);
        XsyncXsyncprojectdata existing = null;
        ArrayList<XsyncXsyncprojectdata> list = XsyncXsyncprojectdata.getXsyncXsyncprojectdatasByField(XsyncXsyncprojectdata.SCHEMA_ELEMENT_NAME+"/source_project_id", sourceProjectId, _user, true);
        if (list != null && list.size() > 0) {
        	existing = list.get(0);
        	syncProject.setItem(existing.getItem());
        }
		syncProject.setSourceProjectId(synchronizationJson.get(PROJECT_ELEMENT_JSON_NAME).asText());
		syncProject.setSyncEnabled(new Boolean(synchronizationJson.get("enabled").asBoolean()));
		item = XFTItem.NewItem(XsyncXsyncinfodata.SCHEMA_ELEMENT_NAME, _user);
		XsyncXsyncinfodata syncinfo = new XsyncXsyncinfodata(item);
        syncinfo.setSyncFrequency(synchronizationJson.get("sync_frequency").asText());
		syncinfo.setSyncNewOnly(new Boolean(synchronizationJson.get("sync_new_only").asBoolean()));
		syncinfo.setIdentifiers(synchronizationJson.get("identifiers").asText());
		syncinfo.setRemoteUrl(synchronizationJson.get("remote_url").asText());
		syncinfo.setRemoteProjectId(synchronizationJson.get("remote_project_id").asText());
		syncinfo.setSyncStartTime(null);
		syncinfo.setSyncEndTime(null);		
		syncProject.setSyncinfo(syncinfo.getItem());
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
        return;
	}
	
	public List<XsyncXsyncprojectdata> getAllProjectsSetToBeSynced() {
		ArrayList<XsyncXsyncprojectdata> xsyncProjects = XsyncXsyncprojectdata.getAllXsyncXsyncprojectdatas(_user, true);
		return xsyncProjects;
	}	
	
	
	
	public List<XsyncXsyncprojectdata> getAllProjectsToBeSyncedDaily() {
		List<XsyncXsyncprojectdata> xsyncThese = new ArrayList<XsyncXsyncprojectdata>();
		List<XsyncXsyncprojectdata> xsyncProjects = getAllProjectsSetToBeSynced();
		if (xsyncProjects != null && xsyncProjects.size() > 0) {
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
		List<XsyncXsyncprojectdata> xsyncThese = new ArrayList<XsyncXsyncprojectdata>();
		List<XsyncXsyncprojectdata> xsyncProjects = getAllProjectsSetToBeSynced();
		if (xsyncProjects != null && xsyncProjects.size() > 0) {
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
		List<XsyncXsyncprojectdata> xsyncThese = new ArrayList<XsyncXsyncprojectdata>();
		List<XsyncXsyncprojectdata> xsyncProjects = getAllProjectsSetToBeSynced();
		if (xsyncProjects != null && xsyncProjects.size() > 0) {
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
		List<XsyncXsyncprojectdata> xsyncThese = new ArrayList<XsyncXsyncprojectdata>();
		List<XsyncXsyncprojectdata> xsyncProjects = getAllProjectsSetToBeSynced();
		if (xsyncProjects != null && xsyncProjects.size() > 0) {
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
			syncData = results.get(0);
		}else {
			_log.error("Unexpected number of results "  + projectId);
		}
		return syncData;

	}

	public ArrayList<XsyncXsyncremotemapdata> getAllRemoteMapDetails() {
		ArrayList<XsyncXsyncremotemapdata> remoteMaps = XsyncXsyncremotemapdata.getAllXsyncXsyncremotemapdatas(_user,true);
		return remoteMaps;
	}
	
	public String getRemoteId(String localProjectId, String localXnatId) {
		String remoteId = null;
		String query = "select remote_xnat_id from xsync_xsyncremotemapdata";
		query += " where project_id=:localProjectId and local_xnat_id=:localXnatId";
		MapSqlParameterSource parameters = new MapSqlParameterSource();
		parameters = new MapSqlParameterSource();
		parameters.addValue("localProjectId", localProjectId);
		parameters.addValue("localXnatId", localXnatId);
		 List<String> results = _jdbcTemplate.queryForList(query, parameters,String.class);
		 if (results !=null && results.size()>1) {
			 remoteId = results.get(0);
		 }
		return remoteId;
	}
	




}
