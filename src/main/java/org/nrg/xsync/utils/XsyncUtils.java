package org.nrg.xsync.utils;

import java.util.ArrayList;
import java.util.List;

import org.nrg.xdat.XDAT;
import org.nrg.xdat.om.XnatSubjectdata;
import org.nrg.xdat.om.XsyncXsyncinfodata;
import org.nrg.xdat.om.XsyncXsyncprojectdata;
import org.nrg.xdat.om.XsyncXsyncremotemapdata;
import org.nrg.xft.security.UserI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

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
	
	
	UserI _user;
	private static final Logger _log = LoggerFactory.getLogger(XsyncUtils.class);
	
	
	public XsyncUtils(UserI user) {
		_user = user;
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
		ArrayList<XsyncXsyncprojectdata> results = XsyncXsyncprojectdata.getXsyncXsyncprojectdatasByField("xsync:xsyncProjectData/project_id",projectId,_user,false);
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
		NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(
				new JdbcTemplate(XDAT.getDataSource()));
		 List<String> results = jdbcTemplate.queryForList(query, parameters,String.class);
		 if (results !=null && results.size()>1) {
			 remoteId = results.get(0);
		 }
		return remoteId;
	}
	




}
