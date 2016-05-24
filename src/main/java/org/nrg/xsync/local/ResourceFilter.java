package org.nrg.xsync.local;

import java.util.ArrayList;
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
		Map<String,List<XnatAbstractresourceI>> fileteredResources = new HashMap<String,List<XnatAbstractresourceI>>();
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
		parameters.addValue(QueryResultUtil.PROJECT_QUERY_PARAMETER_NAME, projectSyncConfiguration.getSynchronizationConfiguration().getProject());
		QueryResultUtil queryUtil = new QueryResultUtil();
		NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(
				new JdbcTemplate(XDAT.getDataSource()));
		String query = queryUtil.getQueryForFetchingProjectResourcesModifiedSinceLastSync();
		List<String> resourceLabels = projectSyncConfiguration.getSynchronizationConfiguration().getProjectresources();
		if (resourceLabels.size() > 0 ) { 
			parameters.addValue("resources", resourceLabels);
		}
		List<Map<String,Object>> changedResources = null;
		if (resourcesToBeSynced.size() > 0) {
			//Have these resources been modified
			if (resourceLabels.size() > 0 ) { 
				query += " where label in (:resources)";
				//Columns
				//a.xnat_abstractresource_id,a.label, p.id, am.status, am.last_modified,xsi.sync_start_time
				changedResources = jdbcTemplate.queryForList(query, parameters);
				resourcesDeleted = getAbstractResourceItems(changedResources,QueryResultUtil.DELETE_STATUS);
			}
		}else {
			//There are no project resources which are present for the project
			//which have been configured to be synced.
			//Look for any of the project resources which are configured to be synced
			//Which have been deleted.
			if (resourceLabels.size() > 0 ) { 
				query = queryUtil.getParametrizedQueryForFetchingConfiguredSubjectResourcesDeletedSinceLastSync();
				query += " where label in (:resources)";
				//Columns
				//a.xnat_abstractresource_id,a.label, p.id, am.status, am.last_modified,xsi.sync_start_time
				changedResources = jdbcTemplate.queryForList(query, parameters);
			}
		}
		if (changedResources!=null) {
			resourcesModified = getAbstractResourceItems(changedResources,QueryResultUtil.ACTIVE_STATUS);
			resourcesDeleted = getAbstractResourceItems(changedResources,QueryResultUtil.DELETE_STATUS);
		}
		fileteredResources.put(QueryResultUtil.ACTIVE_STATUS,resourcesModified);
		fileteredResources.put(QueryResultUtil.DELETE_STATUS,resourcesDeleted);
		return fileteredResources;
	}

	
	public Map<String,List<XnatAbstractresourceI>> select(XnatSubjectdata subject, String localSubjectId, ProjectSyncConfiguration projectSyncConfiguration) throws Exception {
		List<XnatAbstractresourceI> resources = subject.getResources_resource();
		List<XnatAbstractresourceI> resourcesToBeSynced = new ArrayList<XnatAbstractresourceI>();

		List<XnatAbstractresourceI> resourcesModified = new ArrayList<XnatAbstractresourceI>();
		List<XnatAbstractresourceI> resourcesDeleted = new ArrayList<XnatAbstractresourceI>();
		Map<String,List<XnatAbstractresourceI>> fileteredResources = new HashMap<String,List<XnatAbstractresourceI>>();
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
		parameters.addValue(QueryResultUtil.PROJECT_QUERY_PARAMETER_NAME, projectSyncConfiguration.getSynchronizationConfiguration().getProject());
		parameters.addValue(QueryResultUtil.SUBJECT_QUERY_PARAMETER_NAME, localSubjectId);
		QueryResultUtil queryUtil = new QueryResultUtil();
		NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(
				new JdbcTemplate(XDAT.getDataSource()));
		String query = queryUtil.getParametrizedQueryForFetchingConfiguredSubjectResourcesChangedSinceLastSync();
		List<String> resourceLabels = projectSyncConfiguration.getSynchronizationConfiguration().getSubjectresources();
		if (resourceLabels.size() > 0 ) { 
			parameters.addValue("resources", resourceLabels);
		}
		List<Map<String,Object>> changedResources = null;
		if (resourcesToBeSynced.size() > 0) {
			//Have these resources been modified
			if (resourceLabels.size() > 0 ) { 
				query += " where label in (:resources)";
				//Columns
				//a.xnat_abstractresource_id,a.label, p.id, am.status, am.last_modified,xsi.sync_start_time
				changedResources = jdbcTemplate.queryForList(query, parameters);
				resourcesDeleted = getAbstractResourceItems(changedResources,QueryResultUtil.DELETE_STATUS);
			}
		}else {
			//There are no subject resources which are present for the subject
			//which have been configured to be synced.
			//Look for any of the subject resources which are configured to be synced
			//Which have been deleted.
			if (resourceLabels.size() > 0 ) { 
				query = queryUtil.getParametrizedQueryForFetchingConfiguredSubjectResourcesDeletedSinceLastSync();
				query += " where label in (:resources)";
				//Columns
				//a.xnat_abstractresource_id,a.label, p.id, am.status, am.last_modified,xsi.sync_start_time
				changedResources = jdbcTemplate.queryForList(query, parameters);
			}
		}
		if (changedResources!=null) {
			resourcesModified = getAbstractResourceItems(changedResources,QueryResultUtil.ACTIVE_STATUS);
			resourcesDeleted = getAbstractResourceItems(changedResources,QueryResultUtil.DELETE_STATUS);
		}
		fileteredResources.put(QueryResultUtil.ACTIVE_STATUS,resourcesModified);
		fileteredResources.put(QueryResultUtil.DELETE_STATUS,resourcesDeleted);
		return fileteredResources;
	}
	
	private List<XnatAbstractresourceI> getAbstractResourceItems(List<Map<String,Object>> rows, String status) {
		List<XnatAbstractresourceI> absResources = new ArrayList<XnatAbstractresourceI>();
		if (rows != null) {
			for (Map<String,Object> row: rows) {
				if (row.get("status").equals(status)) {
					XnatAbstractresource aRsc = XnatAbstractresource.getXnatAbstractresourcesByXnatAbstractresourceId(row.get("xnat_abstractresource_id"), _user, true);
					absResources.add(aRsc);
				}
			}
		}
		return absResources;
	}
	

}
