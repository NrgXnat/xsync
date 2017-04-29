package org.nrg.xsync.tools;

import org.nrg.xdat.om.XsyncXsyncassessordata;
import org.nrg.xdat.om.XsyncXsyncremotemapdata;
import org.nrg.xft.event.EventMetaI;
import org.nrg.xft.event.EventUtils;
import org.nrg.xft.security.UserI;
import org.nrg.xsync.manifest.ExperimentSyncItem;
import org.nrg.xsync.utils.QueryResultUtil;
import org.nrg.xsync.utils.XsyncUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Mohana Ramaratnam
 */
public class XSyncTools {
    private static final Logger _log = LoggerFactory.getLogger(XSyncTools.class);

    private final UserI                      _user;
    private final NamedParameterJdbcTemplate _jdbcTemplate;
    private final QueryResultUtil            _queryResultUtil;
	public static final String NEWLINE = System.getProperty("line.separator");


    public XSyncTools(final UserI user, final NamedParameterJdbcTemplate jdbcTemplate, final QueryResultUtil queryResultUtil) {
        _user = user;
        _jdbcTemplate = jdbcTemplate;
        _queryResultUtil = queryResultUtil;
    }

    public void saveSyncDetails(String localProjectId, String local_id, String remote_id, String syncStatus, String xsiType, String remote_project_id, String remoteUrl) {
        XsyncXsyncremotemapdata subjectRemoteMap = new XsyncXsyncremotemapdata();
        subjectRemoteMap.setSourceProjectId(localProjectId);
        subjectRemoteMap.setLocalXnatId(local_id);
        subjectRemoteMap.setXsitype(xsiType);
        subjectRemoteMap.setRemoteXnatId(remote_id);
        subjectRemoteMap.setSyncStatus(syncStatus);
        subjectRemoteMap.setRemoteProjectId(remote_project_id);
        subjectRemoteMap.setRemoteHostUrl(remoteUrl);
        try {
            //Backward compatible XNAT 1.6.5 does not have ADMIN_EVENT method
            EventMetaI c = EventUtils.DEFAULT_EVENT(_user, "ADMIN_EVENT occurred");
            subjectRemoteMap.save(_user, false, true, c);
        } catch (Exception e) {
            _log.debug("Unable to save remote  details. Local: " + local_id + " Remote: " + remote_id);

        }
    }

    public boolean hasBeenSyncedAlready(String localProjectId, String local_id, String xsiType, String remoteProject, String remoteUrl) {
        boolean hasBeenSyncedAlready = false;
        String query = _queryResultUtil.getXsyncRemoteMapQueryString();
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("PROJECT_ID", localProjectId);
        parameters.addValue("LOCAL_XNAT_ID", local_id);
        parameters.addValue("XSITYPE", xsiType);
        parameters.addValue("REMOTE_PROJECT", remoteProject);
        parameters.addValue("REMOTE_URL", remoteUrl);

        List<Map<String, Object>> syncMapRows = _jdbcTemplate.queryForList(query, parameters);
        if (syncMapRows != null && syncMapRows.size() > 0) {
            hasBeenSyncedAlready = true;
        }
        return hasBeenSyncedAlready;

    }

    public boolean deleteXsyncRemoteEntry(String localProjectId, String local_id) {
        String query = _queryResultUtil.deleteXsyncRemoteMapQueryString();
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("PROJECT_ID", localProjectId);
        parameters.addValue("LOCAL_XNAT_ID", local_id);
        int items_deleted = _jdbcTemplate.update(query, parameters);
        return (items_deleted > 0);
    }

    public boolean hasBeenMarkedOkToSyncAndNotSyncedYet(String exptId, String remote_url) {
        boolean okToSync = false;
        XsyncXsyncassessordata assessor = getXsyncAssessor(exptId, remote_url);
        if (assessor != null) {
            okToSync = assessor.getOktosync();
            //if (okToSync) {
            //	okToSync = XsyncUtils.SYNC_STATUS_SYNCED.equals(assessor.getSyncStatus())?false:true;
            //}
        }
        return okToSync;
    }

    public void updateSyncAssessor(ExperimentSyncItem expSyncItem, String remoteProjectId, String remote_url) throws Exception {
        XsyncXsyncassessordata assessor = getXsyncAssessor(expSyncItem.getLocalId(), remote_url);
        if (assessor == null) {
            throw new Exception("Expected a Sync Assessor for " + expSyncItem.getLocalId());
        } else {
            //Update the existing one
            assessor.setSyncTime(expSyncItem.getSyncTime());
            assessor.setSyncStatus(expSyncItem.getSyncStatus());
            assessor.setRemoteId(expSyncItem.getRemoteId());
            assessor.setRemoteProjectId(remoteProjectId);
            assessor.setRemoteUrl(remote_url);
            EventMetaI c = EventUtils.DEFAULT_EVENT(_user, "ADMIN_EVENT occurred");
            assessor.save(_user, false, true, c);
        }
    }

    private XsyncXsyncassessordata getXsyncAssessor(String exptId, String remote_url) {
        XsyncXsyncassessordata assessor = null;
        ArrayList<XsyncXsyncassessordata> okToSyncDatas = XsyncXsyncassessordata.getXsyncXsyncassessordatasByField("xsync:xsyncAssessorData/synced_experiment_id", exptId, _user, true);
        if (okToSyncDatas != null && okToSyncDatas.size() > 0) {
            for (XsyncXsyncassessordata okToSyncData : okToSyncDatas) {
                if (okToSyncData.getRemoteUrl().equals(remote_url)) {
                    assessor = okToSyncData;
                    break;
                }
            }
        }
        return assessor;
    }
    
	public boolean hasExperimentBeenSuccessfullySyncedInThePast(String localProjectId, String local_id, String xsiType, String remoteProject, String remoteUrl) {
	       boolean hasBeenSyncedAlready = false;
	        String query = _queryResultUtil.getXsyncRemoteMapQueryString();

	        MapSqlParameterSource parameters = new MapSqlParameterSource();
	        parameters.addValue("PROJECT_ID", localProjectId);
	        parameters.addValue("LOCAL_XNAT_ID", local_id);
	        parameters.addValue("XSITYPE", xsiType);
	        parameters.addValue("REMOTE_PROJECT", remoteProject);
	        parameters.addValue("REMOTE_URL", remoteUrl);
	        
	        List<Map<String, Object>> syncMapRows = _jdbcTemplate.queryForList(query, parameters);
	        if (syncMapRows != null && syncMapRows.size() > 0) {
	            hasBeenSyncedAlready = true;
	            Map<String, Object> row = syncMapRows.get(0);
	            String syncStatus = (String)row.get("sync_status");
	            if (syncStatus.equals(XsyncUtils.SYNC_STATUS_SYNCED_AND_VERIFIED)) {
	            	hasBeenSyncedAlready = true;
	            }
	        }
	        return hasBeenSyncedAlready;
	}


}
