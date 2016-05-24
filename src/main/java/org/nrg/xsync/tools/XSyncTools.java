package org.nrg.xsync.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.nrg.xdat.XDAT;
import org.nrg.xdat.om.XsyncXsyncassessordata;
import org.nrg.xdat.om.XsyncXsyncremotemapdata;
import org.nrg.xft.event.EventMetaI;
import org.nrg.xft.event.EventUtils;
import org.nrg.xft.security.UserI;
import org.nrg.xsync.manifest.ExperimentSyncItem;
import org.nrg.xsync.utils.QueryResultUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * @author Mohana Ramaratnam
 *
 */
public class XSyncTools {
	private static final Logger _log = LoggerFactory.getLogger(XSyncTools.class);

	UserI user;
	
	public XSyncTools(UserI user) {
		this.user = user;
	}
	public void saveSyncDetails(String localProjectId, String local_id,String remote_id, String syncStatus, String xsiType) {
		XsyncXsyncremotemapdata subjectRemoteMap = new XsyncXsyncremotemapdata();
		subjectRemoteMap.setProjectId(localProjectId);
		subjectRemoteMap.setLocalXnatId(local_id);
		subjectRemoteMap.setXsitype(xsiType);
		subjectRemoteMap.setRemoteXnatId(remote_id);
		subjectRemoteMap.setSyncStatus(syncStatus);
		try {
			//Backward compatible XNAT 1.6.5 does not have ADMIN_EVENT method
			EventMetaI c = EventUtils.DEFAULT_EVENT(user,"ADMIN_EVENT occurred");
			subjectRemoteMap.save(user, false, true,c);
		}catch(Exception e) {
			_log.debug("Unable to save remote  details. Local: " + local_id + " Remote: " + remote_id );
			
		}
	}
	
	public boolean hasBeenSyncedAlready(String localProjectId, String local_id, String xsiType) {
		 boolean hasBeenSyncedAlready = false;
		 QueryResultUtil queryUtil = new QueryResultUtil();
		 String query = queryUtil.getXsyncRemoteMapQueryString();
		 NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(
					new JdbcTemplate(XDAT.getDataSource()));
			MapSqlParameterSource parameters = new MapSqlParameterSource();
			parameters.addValue("PROJECT_ID", localProjectId);
			parameters.addValue("LOCAL_XNAT_ID", local_id);
			parameters.addValue("XSITYPE", xsiType);
		 List<Map<String,Object>> syncMapRows = jdbcTemplate.queryForList(query, parameters);
		 if (syncMapRows != null && syncMapRows.size() > 0) {
			 hasBeenSyncedAlready = true;
		 }
		 return hasBeenSyncedAlready;
		
	}	

	public boolean deleteXsyncRemoteEntry(String localProjectId, String local_id) {
		 QueryResultUtil queryUtil = new QueryResultUtil();
		 String query = queryUtil.deleteXsyncRemoteMapQueryString();
		 NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(
					new JdbcTemplate(XDAT.getDataSource()));
			MapSqlParameterSource parameters = new MapSqlParameterSource();
			parameters.addValue("PROJECT_ID", localProjectId);
			parameters.addValue("LOCAL_XNAT_ID", local_id);
		    int items_deleted = jdbcTemplate.update(query, parameters);
		 return (items_deleted>0?true:false);
		
	}	
	
	public boolean hasBeenMarkedOkToSync(String exptId, String remote_url) {
		boolean okToSync = false;
		XsyncXsyncassessordata assessor = getXsyncAssessor(exptId,remote_url);
		if (assessor != null) {
			okToSync = assessor.getOktosync().booleanValue();
		}
		return okToSync;
	}
	
	public XsyncXsyncassessordata getXsyncAssessor(String exptId, String remote_url) {
		XsyncXsyncassessordata assessor = null;
        ArrayList<XsyncXsyncassessordata> okToSyncDatas = XsyncXsyncassessordata.getXsyncXsyncassessordatasByField("xsync:xsyncAssessorData/synced_experiment_id", exptId,user, true);
		if (okToSyncDatas != null && okToSyncDatas.size()>0) {
			for (XsyncXsyncassessordata okToSyncData: okToSyncDatas) {
				if (okToSyncData.getRemoteUrl().equals(remote_url)) {
					assessor = okToSyncData;
					break;
				}
			}
		}
		return assessor;
	}
	
	
	
	public void updateSyncAssessor(ExperimentSyncItem expSyncItem, String remoteProjectId, String remote_url) throws Exception{
		XsyncXsyncassessordata assessor = getXsyncAssessor(expSyncItem.getLocalId(),remote_url);
		if (assessor == null) {
			throw new Exception("Expected a Sync Assessor for " + expSyncItem.getLocalId());
		}else {
			//Update the existing one
			assessor.setSyncTime(expSyncItem.getSyncTime());
			assessor.setSyncStatus(expSyncItem.getSyncStatus());
			assessor.setRemoteId(expSyncItem.getRemoteId());
			assessor.setRemoteProjectId(remoteProjectId);
			assessor.setRemoteUrl(remote_url);
   			EventMetaI c = EventUtils.DEFAULT_EVENT(user,"ADMIN_EVENT occurred");
			assessor.save(user, false, true,c);
		}
	}

}
