package org.nrg.xsync.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.nrg.xdat.XDAT;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * @author Mohana Ramaratnam
 *
 */
@Component
public class QueryResultUtil {
	
	public static final String PROJECT_QUERY_PARAMETER_NAME="project";
	public static final String REMOTE_PROJECT_QUERY_PARAMETER_NAME="remote_project";
	public static final String REMOTE_URL_QUERY_PARAMETER_NAME="remote_url";
	
		
	public static final String SUBJECT_QUERY_PARAMETER_NAME="subject";
	
	public static final String EXPERIMENT_IDS = "expertment_ids";
	public static final String SUBJECT_IDS = "subject_ids";
	
	public static final String DELETE_STATUS = "deleted" ;
	public static final String ACTIVE_STATUS = "active" ;
	public static final String NEW_STATUS = "new" ;
	public static final String OK_TO_SYNC_STATUS = "ok_to_sync";

	private final NamedParameterJdbcTemplate _jdbcTemplate;

	@Autowired
	public QueryResultUtil(final JdbcTemplate jdbcTemplate) {
        _jdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
	}

	/**
	 * @deprecated This really shouldn't be used. It's necessary for a test command-line application.
     */
	@Deprecated
	public QueryResultUtil() {
		_jdbcTemplate = null;
	}

	public String getQueryForFetchingSubjectsModifiedSinceLastSync() {
		String query = "select s.id, s.label,s.project, sm.status, sm.last_modified,xsi.sync_end_time from xnat_subjectdata_meta_data sm ";
		query += " left join xnat_subjectdata s ON s.subjectdata_info = sm.meta_data_id ";
		query += " left join xnat_projectdata p ON s.project=p.id ";
		query += " left join xsync_xsyncprojectdata xp ON xp.source_project_id=p.id ";
		query += " left join xsync_xsyncinfodata xsi ON xp.syncinfo_xsync_xsyncinfodata_id=xsi.xsync_xsyncinfodata_id ";
		query += " where project=:"+ PROJECT_QUERY_PARAMETER_NAME+" and (sm.last_modified > xsi.sync_end_time or sm.row_last_modified > xsi.sync_end_time) ";
		query += " UNION ";
		query += "select sh.id, sh.label,sh.project, sm.status, sm.last_modified,xsi.sync_end_time from xnat_subjectdata_meta_data sm ";
		query += " left join xnat_subjectdata_history sh ON sh.change_date=sm.row_last_modified ";
		query += " left join xnat_projectdata p ON sh.project=p.id ";
		query += " left join xsync_xsyncprojectdata xp ON xp.source_project_id=p.id ";
		query += " left join xsync_xsyncinfodata xsi ON xp.syncinfo_xsync_xsyncinfodata_id=xsi.xsync_xsyncinfodata_id ";
		query += " where sm.status='"+ DELETE_STATUS + "' and sh.project=:" + PROJECT_QUERY_PARAMETER_NAME +" and (sm.last_modified > xsi.sync_end_time or sm.row_last_modified > xsi.sync_end_time) "; 
		return query;
	}

	public String getQueryForFetchingSubjectsWhoseExperimentsMarkedOKSinceLastSync(boolean skipSubjectIdCheck) {
		String query = "select s.id, s.label,s.project, sm.status, sm.last_modified,xsi.sync_end_time from xnat_subjectdata_meta_data sm ";
		query += " left join xnat_subjectdata s ON s.subjectdata_info = sm.meta_data_id ";
		query += "  left join xnat_subjectassessordata sa ON sa.subject_id=s.id  ";
		query += " right join xsync_xsyncassessordata xok ON xok.synced_experiment_id =sa.id ";
		query += " left join xnat_projectdata p ON s.project=p.id ";
		query += " left join xsync_xsyncprojectdata xp ON xp.source_project_id=p.id ";
		query += " left join xsync_xsyncinfodata xsi ON xp.syncinfo_xsync_xsyncinfodata_id=xsi.xsync_xsyncinfodata_id ";
		query += " where project=:"+ PROJECT_QUERY_PARAMETER_NAME+" and  xok.sync_status is NULL and xok.oktosync=1 and xok.authorized_time > xsi.sync_end_time  ";
		if (!skipSubjectIdCheck)
			query +=  " and s.id NOT in (:"+SUBJECT_IDS+") ";
		return query;
	}

	
	public String getQueryForFetchingProjectResourcesModifiedSinceLastSync() {
		String query = "select  a.label, p.id, am.status, am.last_modified,xsi.sync_end_time, am.insert_date from xnat_abstractresource a ";
		query += " left join xnat_abstractresource_meta_data am ON a.abstractresource_info = am.meta_data_id ";
		query += " left join xnat_projectdata_resource pr ON a.xnat_abstractresource_id = pr.xnat_abstractresource_xnat_abstractresource_id  ";
		query += " left join xnat_projectdata p ON pr.xnat_projectdata_id=p.id  ";
		query += " left join xsync_xsyncprojectdata xp ON xp.source_project_id=p.id ";
		query += " left join xsync_xsyncinfodata xsi ON xp.syncinfo_xsync_xsyncinfodata_id=xsi.xsync_xsyncinfodata_id  ";
		query += " where p.id=:"+PROJECT_QUERY_PARAMETER_NAME+" and am.row_last_modified > xsi.sync_end_time  ";
		query += " UNION "; 
		query += " select ah.label, p.id, am.status, am.last_modified,xsi.sync_end_time, am.insert_date from xnat_abstractresource_meta_data am  ";
		query += " left join xnat_abstractresource_history ah ON ah.abstractresource_info=am.meta_data_id  ";
		query += " left join xnat_projectdata_resource_history  prh ON prh.xnat_abstractresource_xnat_abstractresource_id = ah.xnat_abstractresource_id ";
		query += " left join xnat_projectdata p ON  prh.xnat_projectdata_id = p.id ";
		query += " left join xsync_xsyncprojectdata xp ON xp.source_project_id=p.id  ";
		query += " left join xsync_xsyncinfodata xsi ON xp.syncinfo_xsync_xsyncinfodata_id=xsi.xsync_xsyncinfodata_id ";
		query += "  where am.status='"+ DELETE_STATUS + "' and prh.xnat_projectdata_id=:"+PROJECT_QUERY_PARAMETER_NAME+" and am.row_last_modified > xsi.sync_end_time "; 
		return query;
	}
	

	public String getQueryForFetchingSubjectResourcesModifiedOrDeletedSinceLastSync() {
		String query = getQueryForFetchingSubjectResourcesModifiedSinceLastSync();
		String deletedQuery = getQueryForFetchingSubjectResourcesDeletedSinceLastSync();
		return query + " UNION " + deletedQuery ;
	}
	public String getQueryForFetchingSubjectResourcesModifiedSinceLastSync() {
		String query = "select  a.xnat_abstractresource_id,a.label, p.id, am.status, am.last_modified,am.row_last_modified,xsi.sync_end_time,am.insert_date from xnat_abstractresource a ";
		query += " left join xnat_abstractresource_meta_data am ON a.abstractresource_info = am.meta_data_id ";
		query += " left join xnat_subjectdata_resource sr ON a.xnat_abstractresource_id = sr.xnat_abstractresource_xnat_abstractresource_id   ";
		query += " left join xnat_subjectdata s on sr.xnat_subjectdata_id=s.id ";
		query += " left join xnat_projectdata p ON s.project=p.id   ";
		query += " left join xsync_xsyncprojectdata xp ON xp.source_project_id=p.id ";
		query += " left join xsync_xsyncinfodata xsi ON xp.syncinfo_xsync_xsyncinfodata_id=xsi.xsync_xsyncinfodata_id   ";
		query += " where s.id=:"+ this.SUBJECT_QUERY_PARAMETER_NAME + " and p.id=:"+PROJECT_QUERY_PARAMETER_NAME+" and am.row_last_modified > xsi.sync_end_time ";  
		return query ;
	}

	public String getQueryForFetchingSubjectResourcesDeletedSinceLastSync() {
/*		String query = " select ah.xnat_abstractresource_id,ah.label, p.id, am.status, am.last_modified,xsi.sync_end_time,am.insert_date from xnat_abstractresource_meta_data am  "; 
		query += " left join xnat_abstractresource_history ah ON ah.abstractresource_info=am.meta_data_id   ";
		query += " left join xnat_subjectdata_resource_history  srh ON srh.xnat_abstractresource_xnat_abstractresource_id = ah.xnat_abstractresource_id ";
		query += " left join xnat_subjectdata s on srh.xnat_subjectdata_id=s.id ";
		query += " left join xnat_projectdata p ON s.project=p.id   ";
		query += " left join xsync_xsyncprojectdata xp ON xp.source_project_id=p.id ";  
		query += " left join xsync_xsyncinfodata xsi ON xp.syncinfo_xsync_xsyncinfodata_id=xsi.xsync_xsyncinfodata_id "; 
		query += "  where s.id=:" + this.SUBJECT_QUERY_PARAMETER_NAME + " and am.status='"+ DELETE_STATUS + "' and p.id=:"+PROJECT_QUERY_PARAMETER_NAME+" and am.row_last_modified > xsi.sync_end_time ";
		return query;
*/		
		String query = "select ah.xnat_abstractresource_id,ah.label, p.id, am.status, am.last_modified,xsi.sync_end_time,am.insert_date from xnat_abstractresource_meta_data am ";
		query += " 		left join ";
		query += " 		 ( ";
		query += " 		 SELECT DISTINCT ON (xnat_abstractresource_id) ";
		query += " 		   last_value(change_date) OVER wnd as last_change_date, ";
		query += " 		   abstractresource_info, ";
		query += " 		   label,xnat_abstractresource_id FROM xnat_abstractresource_history ";
		query += " 		  WINDOW wnd AS ( ";
		query += " 		    PARTITION BY xnat_abstractresource_id ORDER BY change_date   ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING ";
		query += "  		    ) ";
		query += " 		 ) as ah ON ah.abstractresource_info=am.meta_data_id   ";
		query += " 		left join xnat_subjectdata_resource_history  srh ON srh.xnat_abstractresource_xnat_abstractresource_id = ah.xnat_abstractresource_id ";
		query += " 		left join xnat_subjectdata s on srh.xnat_subjectdata_id=s.id ";
		query += " 		left join xnat_projectdata p ON s.project=p.id   ";
		query += " 		left join xsync_xsyncprojectdata xp ON xp.source_project_id=p.id  ";
		query += " 		left join xsync_xsyncinfodata xsi ON xp.syncinfo_xsync_xsyncinfodata_id=xsi.xsync_xsyncinfodata_id ";
		query += " 		 where s.id=:" + this.SUBJECT_QUERY_PARAMETER_NAME + " and am.status='"+ DELETE_STATUS + "' and p.id=:"+PROJECT_QUERY_PARAMETER_NAME+" and am.row_last_modified > xsi.sync_end_time ";
		return query;
		
	}	
	public String getParametrizedQueryForFetchingConfiguredSubjectResourcesChangedSinceLastSync() {
		String query = getQueryForFetchingSubjectResourcesModifiedSinceLastSync() ; 
		//query += " UNION " + getQueryForFetchingSubjectResourcesDeletedSinceLastSync();
		String selectTheResourcesWhichAreToBeSynced = "select * from (" + query + ") as results ";
		return selectTheResourcesWhichAreToBeSynced;
	}

	public String getParametrizedQueryForFetchingConfiguredSubjectResourcesDeletedSinceLastSync() {
		String 	query = getQueryForFetchingSubjectResourcesDeletedSinceLastSync();
		String selectTheResourcesWhichAreToBeSynced = "select * from (" + query + ") as results ";
		return selectTheResourcesWhichAreToBeSynced;
	}

	public String getQueryForFetchingSubjectExperimentsModifiedSinceLastSync() {
		String query = "select e.id,e.label,xdme.element_name,e.project,em.status,em.last_modified, xsi.sync_end_time,em.insert_date from xnat_experimentdata e ";
		query += " left join xnat_experimentdata_meta_data em ON e.experimentdata_info = em.meta_data_id ";
		query += " left join xdat_meta_element xdme ON e.extension = xdme.xdat_meta_element_id ";
		query += " left join xnat_projectdata p ON e.project=p.id ";
		query += " left join xnat_subjectassessordata sa ON sa.id=e.id "; 
		query += " left join xsync_xsyncprojectdata xp ON xp.source_project_id=p.id ";
		query += " left join xsync_xsyncinfodata xsi ON xp.syncinfo_xsync_xsyncinfodata_id=xsi.xsync_xsyncinfodata_id ";  
		query += " where sa.subject_id=:" +  this.SUBJECT_QUERY_PARAMETER_NAME + " and  p.id=:"+ PROJECT_QUERY_PARAMETER_NAME +  " and em.row_last_modified > xsi.sync_end_time and e.id in (:"+EXPERIMENT_IDS+") ";
		return query;
	}
	
	public String getQueryForFetchingSubjectExperimentsMarkedOKSinceLastSync() {
		String query = "select e.id,e.label,xdme.element_name,e.project,em.status,em.last_modified, xsi.sync_end_time,em.insert_date, xok.sync_status from xnat_experimentdata e ";
		query += " right join xsync_xsyncassessordata xok ON xok.synced_experiment_id =e.id ";  
		query += " left join xnat_experimentdata_meta_data em ON e.experimentdata_info = em.meta_data_id ";
		query += " left join xdat_meta_element xdme ON e.extension = xdme.xdat_meta_element_id ";
		query += " left join xnat_projectdata p ON e.project=p.id ";
		query += " left join xnat_subjectassessordata sa ON sa.id=e.id "; 
		query += " left join xsync_xsyncprojectdata xp ON xp.source_project_id=p.id ";
		query += " left join xsync_xsyncinfodata xsi ON xp.syncinfo_xsync_xsyncinfodata_id=xsi.xsync_xsyncinfodata_id ";  
		query += " where sa.subject_id=:" +  this.SUBJECT_QUERY_PARAMETER_NAME + " and  p.id=:"+ PROJECT_QUERY_PARAMETER_NAME +  "  and xok.remote_project_id=:" + REMOTE_PROJECT_QUERY_PARAMETER_NAME +  "  and xok.remote_url=:" + REMOTE_URL_QUERY_PARAMETER_NAME + "  and xok.oktosync=1 "  + " and e.id in (:"+EXPERIMENT_IDS+") ";
		return query;
	}
	
	
	public String getQueryForFetchingSubjectExperimentsDeletedSinceLastSync() {
		//Has two identical rows
		String query = "select eh.id,eh.label,xdme.element_name,eh.project,em.status,em.last_modified, xsi.sync_end_time,em.insert_date from xnat_experimentdata_meta_data em ";
		query += " left join xnat_experimentdata_history eh ON eh.experimentdata_info = em.meta_data_id ";
		query += " left join xdat_meta_element xdme ON eh.extension = xdme.xdat_meta_element_id ";
		query += " left join xnat_projectdata p ON eh.project=p.id ";
		query += " left join xnat_subjectassessordata_history sa ON sa.id=eh.id "; 
		query += " left join xsync_xsyncprojectdata xp ON xp.source_project_id=p.id ";
		query += " left join xsync_xsyncinfodata xsi ON xp.syncinfo_xsync_xsyncinfodata_id=xsi.xsync_xsyncinfodata_id ";  
		query += " where sa.subject_id=:" + this.SUBJECT_QUERY_PARAMETER_NAME +  " and p.id=:"+ PROJECT_QUERY_PARAMETER_NAME +  " and  em.status='"+ DELETE_STATUS + "' and em.row_last_modified > xsi.sync_end_time  ";
		return query;
	}
	
	
	public String getQueryForSubjectsSharedIntoProject(String projectId) {
		String query = "select label,project,subject_id from xnat_projectparticipant where project='"+projectId +"'";
		return query;
	}
	
	public String getQueryForFetchingSubjectExperimentsSinceLastSync() {
		String query = getQueryForFetchingSubjectExperimentsModifiedSinceLastSync();
		query += " UNION ";
		query += getQueryForFetchingSubjectExperimentsDeletedSinceLastSync();
		return query;		
	}

	public void append(Map<String,Object> source, Map<String,Object> destination) {
		for (String columnName : source.keySet()) {
			destination.put(columnName, source.get(columnName));
		}	
	}
	public void append(List<Map<String,Object>> source, Map<String,Object> destination) {
		for (int i=0; i< source.size();i++) {
			append(source.get(i),destination);
		}	
	}
	
	public void addColumn(String columnName,Object columnValue,List<Map<String,Object>> destination){
		for (int i=0; i< destination.size();i++) {
			Map<String,Object> row = destination.get(i);
			row.put(columnName, columnValue);
		}	

	}
	

	
	public Map<Object,List<Map<String,Object>>> separateByColumn(List<Map<String,Object>> queryResults, String separatorColumnName) {
		Map<Object,List<Map<String,Object>>> reOrganizedRows = new HashMap<Object,List<Map<String,Object>>>();
		Set<Object> distinctColumnValues = getDistinctValuesInColumn(queryResults,separatorColumnName);
		for (Object dcv : distinctColumnValues) {
			List<Map<String,Object>> subRows = getRows(queryResults,separatorColumnName,dcv,true); 
			reOrganizedRows.put(dcv, subRows);
		}
		return reOrganizedRows;
	}
	
	public List<Map<String,Object>> getRows(List<Map<String,Object>> queryResults, String columnName, Object columnValue, boolean dropColumn) {
		List<Map<String,Object>> sublist = new ArrayList<Map<String,Object>>();
		for (int i=0; i<queryResults.size(); i++) { //For each row
			Map<String,Object> row = queryResults.get(i);
			Object rowColumnValue = row.get(columnName);
			if (rowColumnValue.equals(columnValue)) {
				if (dropColumn){
					Map<String,Object> newRow = new HashMap<String,Object>();
					for (String queryColumnName : row.keySet()) {
					    if (!columnName.equals(queryColumnName)) {
					    	newRow.put(queryColumnName,row.get(queryColumnName));
					    }
					}
					sublist.add(newRow);
				}else 
				   sublist.add(row);
			}
		}
		return sublist;
	}
	
	public Set<Object> getDistinctValuesInColumn(List<Map<String,Object>> queryResults, String columnName) {
		Hashtable<Object,String> columnValues = new Hashtable<Object,String>();
		for (int i=0; i<queryResults.size(); i++) { //For each row
			Map<String,Object> row = queryResults.get(i);
			Object columnValue = row.get(columnName);
			if (!columnValues.containsKey(columnValue)) {
				columnValues.put(columnValue, "1");
			}
		}
		return columnValues.keySet();
	}

	public List<Object> getValuesInColumn(List<Map<String,Object>> queryResults, String columnName) {
		List<Object> distinctValues = new ArrayList<Object>();
		for (int i=0; i<queryResults.size(); i++) { //For each row
			Map<String,Object> row = queryResults.get(i);
			Object columnValue = row.get(columnName);
			distinctValues.add(columnValue);
		}
		return distinctValues;
	}

	public Object getValueInColumnInRowWithColumnValue(List<Map<String,Object>> queryResults, String desiredColumnName, String filterColumnName, Object filterColumnValue) {
		Object value = null;
		for (int i=0; i<queryResults.size(); i++) { //For each row
			Map<String,Object> row = queryResults.get(i);
			Object columnValue = row.get(filterColumnName);
			if (columnValue.equals(filterColumnValue)) {
				value = row.get(desiredColumnName);
			}
		}
		return value;
	}
	
	public Set<Object> getDistinctValuesInColumn(Map<Object,List<Map<String,Object>>> queryResults) {
		return queryResults.keySet();
	}

	
	public List<Map<String,Object>> reorganizeAsPivotColumnArray(List<Map<String,Object>> queryResults, String collatorColumn, String pivotColumnName) {
		List<Map<String,Object>> rowsCollated = new ArrayList<Map<String,Object>>();
		Hashtable<String,Integer> rowIndices = new Hashtable<String,Integer>();
		for (int i=0; i<queryResults.size(); i++) { //For each row
			Map<String,Object> row = queryResults.get(i);
			Set<String> columns = row.keySet();
			for (String columnName : columns) {
			    if (columnName.equals(collatorColumn)) {
			    	Object columnRowValue = row.get(columnName); 
			    	if (rowIndices.containsKey(columnRowValue)) {
			    		Integer rowIndex = rowIndices.get(columnRowValue);
			    		Map<String,Object> appendToThisRow = rowsCollated.get(rowIndex.intValue());
			    		ArrayList<Object> collatedValues = (ArrayList)appendToThisRow.get(pivotColumnName);
			    		collatedValues.add(row.get(pivotColumnName));
			    	}else {
			    		//Insert the row - the first time you see the collatorColumn
		    			Map<String,Object> newRow = new HashMap<String,Object>();
			    		for (String column : row.keySet()) {
			    			if (column.equals(pivotColumnName)) {
			    				ArrayList<Object> pivotedArray = new ArrayList<Object>();
			    				pivotedArray.add(row.get(column));
			    				newRow.put(column, pivotedArray);
			    			}else {
			    				newRow.put(column, row.get(column));
			    			}
			    		}
			    		int insertionPoint = (rowsCollated.size()-1)>0?(rowsCollated.size()-1):0;
			    		rowsCollated.add(insertionPoint,newRow);
			    		rowIndices.put(columnRowValue.toString(),new Integer(insertionPoint));
			    	}
			    	break; //Move to next row
			    }
			}
		}
		return rowsCollated;
	}
	
	public String getXsyncRemoteMapQueryString() {
		String query = "select * from xsync_xsyncremotemapdata pr where ";
		query += " pr.source_project_id=:PROJECT_ID " ;
		query += " and pr.local_xnat_id=:LOCAL_XNAT_ID " ;
		query += " and pr.xsitype=:XSITYPE " ;
		query += " and pr.remote_project_id=:REMOTE_PROJECT ";
		query += " and pr.remote_project_id=:REMOTE_URL ";

		return query;
	}

	public String getProjectsToSync() {
		String query = "select pr.source_project_id, pr.sync_blocked, pr.sync_scheduled_by, xi.sync_frequency from xsync_xsyncprojectdata pr  ";
		query += " LEFT JOIN xsync_xsyncinfodata xi ON xi.xsync_xsyncinfodata_id=pr.syncinfo_xsync_xsyncinfodata_id " ;
		query += "  where xi.sync_frequency=:SYNC_FREQUENCY ";
		return query;
	}
	
	public String getProjectSyncDetails() {
		String query = "select pr.source_project_id, pr.sync_blocked, pr.sync_enabled from xsync_xsyncprojectdata pr  ";
		query += "  where pr.source_project_id=:LOCAL_PROJECT ";
		return query;
	}

	public List<Map<String,Object>> getProjectSyncDetails(String projectId) {
		 String query = getProjectSyncDetails();
		MapSqlParameterSource parameters = new MapSqlParameterSource();
		parameters.addValue("LOCAL_PROJECT", projectId);
		return _jdbcTemplate.queryForList(query, parameters);
	}


	public List<Map<String,Object>> getProjectsTobeSynced(String frequency) {
		 String query = getProjectsToSync();
		MapSqlParameterSource parameters = new MapSqlParameterSource();
		parameters.addValue("SYNC_FREQUENCY", frequency);
		return _jdbcTemplate.queryForList(query, parameters);
	}
	
	public List<Map<String,Object>> getProjectsTobeSyncedDaily() {
		return getProjectsTobeSynced("daily");
	}

	public List<Map<String,Object>> getProjectsTobeSyncedHourly() {
		return getProjectsTobeSynced("hourly");
	}

	
	public List<Map<String,Object>> getProjectsTobeSyncedMonthly() {
		return getProjectsTobeSynced("monthly");
	}

	public List<Map<String,Object>> getProjectsTobeSyncedWeekly() {
		return getProjectsTobeSynced("weekly");
	}
	
	public String deleteXsyncRemoteMapQueryString() {
		String query = "delete from xsync_xsyncremotemapdata pr where ";
		query += " pr.source_project_id=:PROJECT_ID " ;
		query += " and pr.local_xnat_id=:LOCAL_XNAT_ID " ;
		//query += " and pr.xsitype=:XSITYPE " ;
		return query;
	}

	
	public String getAllRemoteConnections() {
		String query = "select * from xhbm_remote_alias_entity";
		return query;
	}

	public String getRemoteConnectionQuery() {
		String query = "select * from xhbm_remote_alias_entity where local_project=:LOCAL_PROJECT and remote_host=:REMOTE_HOST";
		return query;
	}
	
}
