package org.nrg.xsync.local;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.nrg.xdat.XDAT;
import org.nrg.xdat.model.XnatAbstractresourceI;
import org.nrg.xdat.om.XnatAbstractresource;
import org.nrg.xdat.om.XnatProjectdata;
import org.nrg.xdat.om.XnatSubjectdata;
import org.nrg.xft.security.UserI;
import org.nrg.xsync.configuration.ProjectSyncConfiguration;
import org.nrg.xsync.configuration.json.SyncConfigurationResource;
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
public class ResourceFilter {
	private static final Logger _log = LoggerFactory.getLogger(ResourceFilter.class);

	
	UserI _user;
	
	public ResourceFilter(UserI user) {
		_user = user;
	}

	public Map<String,List<XnatAbstractresourceI>> select(XnatProjectdata project, ProjectSyncConfiguration projectSyncConfiguration) throws Exception {
		List<XnatAbstractresourceI> resources = project.getResources_resource();
		List<XnatAbstractresourceI> resourcesToBeSynced = new ArrayList<XnatAbstractresourceI>();

		List<XnatAbstractresourceI> resourcesModified = new ArrayList<XnatAbstractresourceI>();
		List<XnatAbstractresourceI> resourcesDeleted = new ArrayList<XnatAbstractresourceI>();
		List<XnatAbstractresourceI> resourcesAdded = new ArrayList<XnatAbstractresourceI>();
		
		Map<String,List<XnatAbstractresourceI>> filteredResources = new HashMap<String,List<XnatAbstractresourceI>>();
		int total_resources = resources.size();
		int i = 0;
		while(total_resources > 0) {
			XnatAbstractresourceI resource = resources.get(i);
			if (projectSyncConfiguration.isResourceToBeSynced(resource.getLabel())) {
				   resourcesToBeSynced.add(resource);	
			}
			project.removeResources_resource(i);
			resources = project.getResources_resource();
			total_resources = project.getResources_resource().size();
		}
		MapSqlParameterSource parameters = new MapSqlParameterSource();
		parameters.addValue(QueryResultUtil.PROJECT_QUERY_PARAMETER_NAME, projectSyncConfiguration.getSynchronizationConfiguration().getSource_project_id());
		QueryResultUtil queryUtil = new QueryResultUtil();
		NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(
				new JdbcTemplate(XDAT.getDataSource()));
		String query = queryUtil.getQueryForFetchingProjectResourcesModifiedSinceLastSync();
//		List<String> resourceLabels = projectSyncConfiguration.getSynchronizationConfiguration().getProjectresources();
		List<Map<String,Object>> changedResources = null;
		if (resourcesToBeSynced.size() > 0) {
			//Have these resources been modified
			List<String> resourceLabels = new ArrayList<String>();
			for (XnatAbstractresourceI rsc:resourcesToBeSynced) {
				resourceLabels.add(rsc.getLabel());
			}
			if (resourceLabels.size() > 0 ) { 
				parameters.addValue("resources", resourceLabels);
			}
			if (resourceLabels.size() > 0 ) { 
				query += " where label in (:resources)";
				//Columns
				//a.xnat_abstractresource_id,a.label, p.id, am.status, am.last_modified,xsi.sync_start_time
				changedResources = jdbcTemplate.queryForList(query, parameters);
			}
		}else {
			//There are no project resources which are present for the project
			//which have been configured to be synced.
			//Look for any of the project resources which are configured to be synced
			//Which have been deleted.
			query = queryUtil.getParametrizedQueryForFetchingConfiguredSubjectResourcesDeletedSinceLastSync();
			//Columns
			//a.xnat_abstractresource_id,a.label, p.id, am.status, am.last_modified,xsi.sync_start_time
			changedResources = jdbcTemplate.queryForList(query, parameters);
		}
		if (changedResources!=null) {
			Date syncEndDate = (Date)projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getSyncEndTime();
			SyncConfigurationResource resource = projectSyncConfiguration.getSynchronizationConfiguration().getProject_resources();
			resourcesAdded = getAbstractResourceItems(changedResources,QueryResultUtil.NEW_STATUS,syncEndDate,resource);
			resourcesModified = getAbstractResourceItems(changedResources,QueryResultUtil.ACTIVE_STATUS, syncEndDate,resource);
			resourcesDeleted = getAbstractResourceItems(changedResources,QueryResultUtil.DELETE_STATUS, syncEndDate,resource);
		}
		filteredResources.put(QueryResultUtil.ACTIVE_STATUS,resourcesModified);
		filteredResources.put(QueryResultUtil.DELETE_STATUS,resourcesDeleted);
		filteredResources.put(QueryResultUtil.NEW_STATUS,resourcesAdded);
		
		return filteredResources;
	}

	
	public Map<String,List<XnatAbstractresourceI>> select(XnatSubjectdata subject, String localSubjectId, ProjectSyncConfiguration projectSyncConfiguration) throws Exception {
		List<XnatAbstractresourceI> resources = subject.getResources_resource();
		List<XnatAbstractresourceI> resourcesToBeSynced = new ArrayList<XnatAbstractresourceI>();

		List<XnatAbstractresourceI> resourcesModified = new ArrayList<XnatAbstractresourceI>();
		List<XnatAbstractresourceI> resourcesDeleted = new ArrayList<XnatAbstractresourceI>();
		List<XnatAbstractresourceI> resourcesAdded = new ArrayList<XnatAbstractresourceI>();

		Map<String,List<XnatAbstractresourceI>> filteredResources = new HashMap<String,List<XnatAbstractresourceI>>();
		int total_resources = resources.size();
		int i = 0;
		while(total_resources > 0) {
			XnatAbstractresourceI resource = resources.get(i);
			if (projectSyncConfiguration.isSubjectResourceToBeSynced(resource.getLabel())) {
				   resourcesToBeSynced.add(resource);	
			}
			subject.removeResources_resource(i);
			resources = subject.getResources_resource();
			total_resources = subject.getResources_resource().size();
		}
		MapSqlParameterSource parameters = new MapSqlParameterSource();
		parameters.addValue(QueryResultUtil.PROJECT_QUERY_PARAMETER_NAME, projectSyncConfiguration.getSynchronizationConfiguration().getSource_project_id());
		parameters.addValue(QueryResultUtil.SUBJECT_QUERY_PARAMETER_NAME, localSubjectId);
		QueryResultUtil queryUtil = new QueryResultUtil();
		NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(
				new JdbcTemplate(XDAT.getDataSource()));
		String query = queryUtil.getParametrizedQueryForFetchingConfiguredSubjectResourcesChangedSinceLastSync();
		List<Map<String,Object>> changedResources = null;
		if (resourcesToBeSynced.size() > 0) {
			//Have these resources been modified
			List<String> resourceLabels = new ArrayList<String>();
			for (XnatAbstractresourceI rsc:resourcesToBeSynced) {
				resourceLabels.add(rsc.getLabel());
			}
			if (resourceLabels.size() > 0 ) { 
				parameters.addValue("resources", resourceLabels);
			}
			if (resourceLabels.size() > 0 ) { 
				query += " where label in (:resources)";
				//Columns
				//a.xnat_abstractresource_id,a.label, p.id, am.status, am.last_modified,xsi.sync_end_time,am.insert_date
				changedResources = jdbcTemplate.queryForList(query, parameters);
			}
		}else {
			//There are no subject resources which are present for the subject
			//which have been configured to be synced.
			//Look for any of the subject resources which are configured to be synced
			//Which have been deleted.
			query = queryUtil.getParametrizedQueryForFetchingConfiguredSubjectResourcesDeletedSinceLastSync();
			//Columns
			//a.xnat_abstractresource_id,a.label, p.id, am.status, am.last_modified,xsi.sync_end_time,am.insert_date
			changedResources = jdbcTemplate.queryForList(query, parameters);
		}
		if (changedResources!=null) {
			Date syncEndDate = (Date)projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getSyncEndTime();
			SyncConfigurationResource resource = projectSyncConfiguration.getSynchronizationConfiguration().getSubject_resources();
			resourcesAdded = getAbstractResourceItems(changedResources,QueryResultUtil.NEW_STATUS,syncEndDate, resource);
			resourcesModified = getAbstractResourceItems(changedResources,QueryResultUtil.ACTIVE_STATUS,syncEndDate, resource);
			resourcesDeleted = getAbstractResourceItems(changedResources,QueryResultUtil.DELETE_STATUS,syncEndDate, resource);
		}
		filteredResources.put(QueryResultUtil.ACTIVE_STATUS,resourcesModified);
		filteredResources.put(QueryResultUtil.DELETE_STATUS,resourcesDeleted);
		filteredResources.put(QueryResultUtil.NEW_STATUS,resourcesAdded);
		return filteredResources;
	}
	
	private List<XnatAbstractresourceI> getAbstractResourceItems(List<Map<String,Object>> rows, String status, Date syncEndDate,SyncConfigurationResource resource) {
		List<XnatAbstractresourceI> absResources = new ArrayList<XnatAbstractresourceI>();
		if (rows != null) {
			if (QueryResultUtil.NEW_STATUS.equals(status)) {
				//Look only for resources added since last sync (end_time)
				for (Map<String,Object> row: rows) {
					Date insertDate = (Date)row.get("insert_date");
					int dateComparison = insertDate.compareTo(syncEndDate);
					if (dateComparison >= 0 ) {
						//If no resource rule is specified, defaults to push all
						if (resource == null || resource.isAllowedToSync((String)row.get("label"))) { //Inserted at endTime or After endTime
							XnatAbstractresource aRsc = XnatAbstractresource.getXnatAbstractresourcesByXnatAbstractresourceId(row.get("xnat_abstractresource_id"), _user, true);
							absResources.add(aRsc);
						}
					}
				}
			}else {
				for (Map<String,Object> row: rows) {
					if (row.get("status").equals(status)) {
						if(resource == null ||  resource.isAllowedToSync((String)row.get("label"))) {
							XnatAbstractresource aRsc = XnatAbstractresource.getXnatAbstractresourcesByXnatAbstractresourceId(row.get("xnat_abstractresource_id"), _user, true);
							absResources.add(aRsc);
						}
					}
				}
			}
		}
		return absResources;
	}
	

}
