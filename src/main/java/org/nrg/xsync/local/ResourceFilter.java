package org.nrg.xsync.local;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.nrg.xdat.XDAT;
import org.nrg.xdat.base.BaseElement;
import org.nrg.xdat.model.XnatAbstractresourceI;
import org.nrg.xdat.om.XnatAbstractresource;
import org.nrg.xdat.om.XnatResource;
import org.nrg.xdat.om.XnatSubjectdata;
import org.nrg.xft.ItemI;
import org.nrg.xft.XFTItem;
import org.nrg.xft.exception.ElementNotFoundException;
import org.nrg.xft.exception.FieldNotFoundException;
import org.nrg.xft.exception.XFTInitException;
import org.nrg.xft.search.ItemSearch;
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

	private final UserI _user;
	private final NamedParameterJdbcTemplate _jdbcTemplate;
	private final QueryResultUtil _queryResultUtil;
	
	public ResourceFilter(final UserI user, final NamedParameterJdbcTemplate jdbcTemplate, final QueryResultUtil queryResultUtil) {
		_user = user;
		_jdbcTemplate = jdbcTemplate;
		_queryResultUtil = queryResultUtil;
	}

/*	public Map<String,List<XnatAbstractresourceI>> select(XnatProjectdata project, ProjectSyncConfiguration projectSyncConfiguration) throws Exception {
		List<XnatAbstractresourceI> resources = project.getResources_resource();
		List<XnatAbstractresourceI> resourcesToBeSynced = new ArrayList<XnatAbstractresourceI>();

		List<XnatAbstractresourceI> resourcesModified = new ArrayList<XnatAbstractresourceI>();
		List<XnatAbstractresourceI> resourcesDeleted = new ArrayList<XnatAbstractresourceI>();
		List<XnatAbstractresourceI> resourcesAdded = new ArrayList<XnatAbstractresourceI>();
		
		Map<String,List<XnatAbstractresourceI>> filteredResources = new HashMap<String,List<XnatAbstractresourceI>>();
		MapSqlParameterSource parameters = new MapSqlParameterSource();
		parameters.addValue(QueryResultUtil.PROJECT_QUERY_PARAMETER_NAME, projectSyncConfiguration.getSynchronizationConfiguration().getSource_project_id());
		QueryResultUtil queryUtil = new QueryResultUtil();

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
*/
	
	public Map<String,List<XnatAbstractresourceI>> select(XnatSubjectdata subject, String localSubjectId, ProjectSyncConfiguration projectSyncConfiguration) throws Exception {
		List<XnatAbstractresourceI> resources = subject.getResources_resource();
		List<XnatAbstractresourceI> resourcesToBeSynced = new ArrayList<XnatAbstractresourceI>();

		List<XnatAbstractresourceI> resourcesModified = new ArrayList<XnatAbstractresourceI>();
		List<XnatAbstractresourceI> resourcesDeleted = new ArrayList<XnatAbstractresourceI>();
		List<XnatAbstractresourceI> resourcesAdded = new ArrayList<XnatAbstractresourceI>();

		Map<String,List<XnatAbstractresourceI>> filteredResources = new HashMap<String,List<XnatAbstractresourceI>>();
		MapSqlParameterSource parameters = new MapSqlParameterSource();
		parameters.addValue(QueryResultUtil.PROJECT_QUERY_PARAMETER_NAME, projectSyncConfiguration.getSynchronizationConfiguration().getSource_project_id());
		parameters.addValue(QueryResultUtil.SUBJECT_QUERY_PARAMETER_NAME, localSubjectId);
		//Look for any of the subject resources which are configured to be synced
		//Which have been deleted.
		String query = _queryResultUtil.getParametrizedQueryForFetchingConfiguredSubjectResourcesDeletedSinceLastSync();
		//Columns
		//a.xnat_abstractresource_id,a.label, p.id, am.status, am.last_modified,xsi.sync_end_time,am.insert_date
		List<Map<String,Object>> deletedResources = _jdbcTemplate.queryForList(query, parameters);
		if (deletedResources != null && deletedResources.size() > 0) {
			for(Map<String,Object> row:deletedResources) {
				String deletedResourceLabel = (String)row.get("label");
				if (projectSyncConfiguration.isSubjectResourceToBeSynced(deletedResourceLabel)) {
					resourcesDeleted.add(createNewResource(deletedResourceLabel)) ;
				}
			}
		}
		
		
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
		query = _queryResultUtil.getParametrizedQueryForFetchingConfiguredSubjectResourcesChangedSinceLastSync();
		List<Map<String,Object>> changedResources = null;
		if (resourcesToBeSynced.size() > 0) {
			//Have the existing resources been modified
			List<String> resourceLabels = new ArrayList<>();
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
				changedResources = _jdbcTemplate.queryForList(query, parameters);
			}
			if (changedResources!=null && changedResources.size() > 0) {
				Date syncEndDate = (Date)projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getSyncEndTime();
				SyncConfigurationResource resource = projectSyncConfiguration.getSynchronizationConfiguration().getSubject_resources();
				resourcesAdded = getAbstractResourceItems(changedResources,QueryResultUtil.NEW_STATUS,syncEndDate, resource);
				resourcesModified = getAbstractResourceItems(toHash(resourcesAdded),changedResources,QueryResultUtil.ACTIVE_STATUS,syncEndDate, resource);
			}
		}
		filteredResources.put(QueryResultUtil.ACTIVE_STATUS,resourcesModified);
		filteredResources.put(QueryResultUtil.DELETE_STATUS,resourcesDeleted);
		filteredResources.put(QueryResultUtil.NEW_STATUS,resourcesAdded);
		return filteredResources;
	}
	
	private Hashtable<String,String> toHash(List<XnatAbstractresourceI> rscs) {
		Hashtable<String,String> labelHash = new Hashtable<>();
		if (rscs != null && rscs.size() > 0) {
			for (XnatAbstractresourceI rsc: rscs) {
				labelHash.put(rsc.getLabel(),rsc.getLabel());
			}
		}
		return labelHash;
	}
	
	private XnatResource createNewResource(String label) {
		Class c = BaseElement.GetGeneratedClass(XnatResource.SCHEMA_ELEMENT_NAME);
		ItemI o = null;
		try {
            o = (ItemI) c.newInstance();
            o.setProperty("label", label);        
        }catch(Exception e) {
        	_log.debug("Could not instantiate the Abstract resource " + label);
        }
        return new XnatResource(o);
	}
	
	private List<XnatAbstractresourceI> getAbstractResourceItems(Hashtable<String,String> accountedResources,List<Map<String,Object>> rows, String status, Date syncEndDate,SyncConfigurationResource resource) throws Exception {
		List<XnatAbstractresourceI> absResources = new ArrayList<XnatAbstractresourceI>();
		List<Map<String,Object>> unaccountedRows = new ArrayList<Map<String,Object>>(); 
		if (rows != null && rows.size() > 0) {
			for (Map<String,Object> row: rows) {
				 String label = (String)row.get("label");
				 if (!accountedResources.containsKey(label)) {
					 unaccountedRows.add(row);
				 }
			}	
			absResources = getAbstractResourceItems(unaccountedRows, status, syncEndDate, resource);
		}
		return absResources;
	}
	
	private List<XnatAbstractresourceI> getAbstractResourceItems(List<Map<String,Object>> rows, String status, Date syncEndDate,SyncConfigurationResource resource) throws Exception {
		List<XnatAbstractresourceI> absResources = new ArrayList<XnatAbstractresourceI>();
		if (rows != null && rows.size() > 0) {
			if (QueryResultUtil.NEW_STATUS.equals(status)) {
				//Look only for resources added since last sync (end_time)
				for (Map<String,Object> row: rows) {
					Date insertDate = (Date)row.get("insert_date");
					boolean isActive = QueryResultUtil.ACTIVE_STATUS.equals((String)row.get("status"));
					int dateComparison = insertDate.compareTo(syncEndDate);
					if (dateComparison >= 0 && isActive) {
						//If no resource rule is specified, defaults to push all
						if (resource == null || resource.isAllowedToSync((String)row.get("label"))) { //Inserted at endTime or After endTime
							XnatAbstractresource aRsc = XnatAbstractresource.getXnatAbstractresourcesByXnatAbstractresourceId(row.get("xnat_abstractresource_id"), _user, true);
							absResources.add(aRsc);
						}
					}
				}
			}else {
				for (Map<String,Object> row: rows) {
					Date lastModifiedDate = (Date)row.get("last_modified");
					if (lastModifiedDate == null) {
						lastModifiedDate = (Date)row.get("row_last_modified");
					}
					boolean hasRequiredStatus = status.equals((String)row.get("status"));
					int dateComparison = lastModifiedDate.compareTo(syncEndDate);
					if (dateComparison >= 0 && hasRequiredStatus) {
						//If no resource rule is specified, defaults to push all
						if (resource == null || resource.isAllowedToSync((String)row.get("label"))) { //Inserted at endTime or After endTime
							XnatAbstractresource aRsc = XnatAbstractresource.getXnatAbstractresourcesByXnatAbstractresourceId(row.get("xnat_abstractresource_id"), _user, true);
							absResources.add(aRsc);
						}
					}
				}
			}
		}
		return absResources;
	}
	
	public boolean hasResourceBeenModified(XnatAbstractresource resource, Date syncEndDate) throws Exception {
		boolean modified = false;
		XFTItem item = resource.getCurrentDBVersion(); 
		String metaFieldName = item.getGenericSchemaElement().getMetaDataFieldName();
        Object v = item.getProperty(metaFieldName);
        XFTItem itemMeta = ItemSearch.GetItem(item.getXSIType() + "_meta_data/meta_data_id",v,null,false);
		//Is this new or updated?
		Date experimentInsertDate = (Date)itemMeta.getProperty("insert_date");
		Date experimentModifiedDate = (Date)itemMeta.getProperty("last_modified");
		int dateComparison = experimentInsertDate.compareTo(syncEndDate);
		if (dateComparison >= 0) { //Inserted at endTime or After endTime
			modified = true;
		}else {
			dateComparison = experimentModifiedDate.compareTo(syncEndDate);
			if (dateComparison >= 0) { //Modified at endTime or After endTime
				modified = true;
			}
		}
		return modified;
	}

}
