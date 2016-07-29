package org.nrg.xsync.discoverer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.nrg.xdat.XDAT;
import org.nrg.xsync.utils.QueryResultUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * @author Mohana Ramaratnam
 *
 */
public class ProjectInformation {
    private static final Logger _log = LoggerFactory.getLogger(ProjectInformation.class);

	String _projectId;
	MapSqlParameterSource parameters;
	private final NamedParameterJdbcTemplate _jdbcTemplate;
	private final QueryResultUtil _queryResultUtil;

	public static final String SUBJECT_RESOURCES="subjectresources";
	public static final String SUBJECT_ASSESSORS="subjectassessors";
	public static final String IMAGING_SESSION_TYPES="imagingsessiontypes";
	public static final String IMAGING_ASSESSOR_TYPES="imagingassessortypes";
	
	
	public ProjectInformation(final QueryResultUtil queryResultUtil, final NamedParameterJdbcTemplate jdbcTemplate, String projectId) {
		_queryResultUtil = queryResultUtil;
		_jdbcTemplate = jdbcTemplate;
		_projectId = projectId;
		parameters = new MapSqlParameterSource();
		parameters.addValue(QueryResultUtil.PROJECT_QUERY_PARAMETER_NAME, _projectId);

	}
	
	public ObjectNode getInformation(String informationRequired) {
		ObjectMapper objectMapper = new ObjectMapper();
		ObjectNode jsonObj = objectMapper.createObjectNode();
		jsonObj.put("project", _projectId);
	    String[] informationSnippets = informationRequired.split(",");
	    for (String informationItem: informationSnippets) {
		    Map<String,JsonNode> objMap = getInformationSnippet(informationItem);
	    	if (objMap != null) {
	    		Set<String> keys = objMap.keySet();
	    		for (String key : keys) {
		    		jsonObj.put(key, objMap.get(key));
	    		}
	    	}
	    }
		return jsonObj;
	}
	
	public Map<String,JsonNode>  getInformationSnippet(String informationItem) {
		Map<String,JsonNode> obj = null;
		if (SUBJECT_RESOURCES.equalsIgnoreCase(informationItem)) {
			obj = getSubjectResources();
		}else if (SUBJECT_ASSESSORS.equalsIgnoreCase(informationItem)) {
			obj = getSubjectAssessors();
		}else if (IMAGING_SESSION_TYPES.equalsIgnoreCase(informationItem)) {
			obj = getImagingSessionTypes();
		}else if (IMAGING_ASSESSOR_TYPES.equalsIgnoreCase(informationItem)) {
			obj = getImagingAssessorTypes();
		}
		return obj;
	}
	
	private Map<String, JsonNode>  getSubjectResources() {
		List<String> uniqueSubjectResourcesAcrossSubjects = new ArrayList<String>();
		Map<String,JsonNode> jsonWithTag = new HashMap<String, JsonNode>();
		String query = "select distinct ar.label from xnat_abstractresource ar ";
		query += " left join xnat_subjectdata_resource sr ON ar.xnat_abstractresource_id=sr.xnat_abstractresource_xnat_abstractresource_id";
		query += "	left join xnat_subjectdata s ON s.id = sr.xnat_subjectdata_id";
		query += " where s.project=:"+QueryResultUtil.PROJECT_QUERY_PARAMETER_NAME; 
		uniqueSubjectResourcesAcrossSubjects = _jdbcTemplate.queryForList(query, parameters, String.class);
		JsonUtils jsonUtils = new JsonUtils();
		JsonNode obj =  jsonUtils.toJSONFromList(uniqueSubjectResourcesAcrossSubjects);
		jsonWithTag.put("subjectresources", obj);
		return jsonWithTag;
	}
	
	private Map<String,JsonNode>  getSubjectAssessors() {
		//No imaging sessions - only "pure" Subject Assessors
		Map<String,JsonNode> jsonWithTag = new HashMap<String, JsonNode>();
		String query =" select  xes.element_name as xsiType, xes.singular as singularName from xnat_experimentdata e ";
		query +=" LEFT JOIN xdat_meta_element xme ON e.extension = xme.xdat_meta_element_id ";
		query +=" LEFT JOIN xdat_element_security xes ON xes.element_name=xme.element_name "; 
		query +=" LEFT JOIN xnat_subjectassessordata sa ON sa.id=e.id ";
		query +=" LEFT JOIN xnat_subjectdata s ON s.id=sa.subject_id where e.project=:"+QueryResultUtil.PROJECT_QUERY_PARAMETER_NAME+" and e.id not in (select exp.id from xnat_experimentdata exp, xnat_imagesessiondata i where exp.id=i.id and exp.project=:"+QueryResultUtil.PROJECT_QUERY_PARAMETER_NAME+")";
		query += " and e.id not in (select exp.id from xnat_experimentdata exp, xnat_imageassessordata ia where exp.id=ia.id and exp.project=:"+QueryResultUtil.PROJECT_QUERY_PARAMETER_NAME+")";
		 List<Map<String,Object>> results = _jdbcTemplate.queryForList(query, parameters);
		 JsonUtils jsonUtils = new JsonUtils();
		 JsonNode obj = jsonUtils.toJSONFromMap(results);
		 jsonWithTag.put("subjectassessors", obj);
		 return jsonWithTag;
	}

	private Map<String,JsonNode>  getImagingSessionTypes() {
		//Imaging sessions only - not "pure" Subject Assessors
		 Map<String,JsonNode> jsonWithTag = new HashMap<String, JsonNode>();
		 JsonUtils jsonUtils = new JsonUtils();

		 List<Map<String,Object>> mergedRows = new ArrayList<Map<String,Object>>();

		 String queryForImagingSessions = " select  distinct  xes.element_name as xsitype, xes.singular as singularName  from xnat_imagesessiondata i ";
		queryForImagingSessions += " LEFT JOIN xnat_experimentdata e ON e.id=i.id";
		queryForImagingSessions += " LEFT JOIN xdat_meta_element xme ON e.extension = xme.xdat_meta_element_id"; 
		queryForImagingSessions += " LEFT JOIN xdat_element_security xes ON xes.element_name=xme.element_name ";
		queryForImagingSessions += " where e.project=:"+ QueryResultUtil.PROJECT_QUERY_PARAMETER_NAME+ " order by xsitype";
		
		 List<Map<String,Object>> imagingsessions = _jdbcTemplate.queryForList(queryForImagingSessions, parameters);

		 
		String queryForScanTypes = " select  distinct scan.type as scantypes, xes.element_name as xsitype, xes.singular as singularName  from xnat_imagesessiondata i ";
		queryForScanTypes += " LEFT JOIN xnat_experimentdata e ON e.id=i.id";
		queryForScanTypes += " LEFT JOIN xdat_meta_element xme ON e.extension = xme.xdat_meta_element_id"; 
		queryForScanTypes += " LEFT JOIN xdat_element_security xes ON xes.element_name=xme.element_name ";
		queryForScanTypes += " INNER JOIN xnat_imagescandata scan ON scan.image_session_id  = i.id";
		queryForScanTypes += " LEFT JOIN xnat_subjectassessordata sa ON sa.id=i.id";
		queryForScanTypes += " LEFT JOIN xnat_subjectdata s ON s.id=sa.subject_id ";
		queryForScanTypes += " where e.project=:"+QueryResultUtil.PROJECT_QUERY_PARAMETER_NAME + " group by type,xsitype order by xsitype";
		
		//Rows looks like
		/*
		 "scantypes";"xsitype";"singularname"
		 "MEGTypeScan1";"xnat:megSessionData";"MEGSession"
		"3D FLAIR";"xnat:mrSessionData";"MRSession"
		"3D T1";"xnat:mrSessionData";"MRSession"
		"3D T2";"xnat:mrSessionData";"MRSession"
		"BLA";"xnat:mrSessionData";"MRSession"
		"BLA1";"xnat:mrSessionData";"MRSession"
		"Resting StatefMRI";"xnat:mrSessionData";"MRSession"
		"AC";"xnat:petSessionData";"PETSession"
		"FC";"xnat:petSessionData";"PETSession"
		"Map";"xnat:petSessionData";"PETSession"
		"NAC";"xnat:petSessionData";"PETSession"
		*/
		//After reorganization they look like
		/*
		 * 
		    singularname;xsitype;scantypes
			MRSession;xnat:mrSessionData;[3D FLAIR, 3D T1, 3D T2, BLA, BLA1, Resting StatefMRI]
		 	PETSession;xnat:petSessionData;[AC, NAC, FC, Map]
		 	MEGSession;xnat:megSessionData;[MEGTypeScan1]
		 */

		 List<Map<String,Object>> scan_types_results = _jdbcTemplate.queryForList(queryForScanTypes, parameters);
		 jsonUtils.debug("scan_type_results",scan_types_results);

		 List<Map<String,Object>> reOrganizedImageSessionScans = _queryResultUtil.reorganizeAsPivotColumnArray(scan_types_results, "xsitype", "scantypes");
		 jsonUtils.debug("reOrganizedImageSessionScans",reOrganizedImageSessionScans);

		 String queryForResources = " select  distinct resource.label as resources, xes.element_name as xsitype, xes.singular as singularName  from xnat_imagesessiondata i ";
		 queryForResources += " LEFT JOIN xnat_experimentdata e ON e.id=i.id ";
		 queryForResources += " LEFT JOIN xdat_meta_element xme ON e.extension = xme.xdat_meta_element_id "; 
		 queryForResources += " LEFT JOIN xdat_element_security xes ON xes.element_name=xme.element_name ";
		 queryForResources += " LEFT JOIN xnat_experimentdata_resource er ON er.xnat_experimentdata_id = i.id ";  
		 queryForResources += " INNER JOIN xnat_abstractresource resource ON er.xnat_abstractresource_xnat_abstractresource_id  = resource.xnat_abstractresource_id ";
		 queryForResources += " LEFT JOIN xnat_subjectassessordata sa ON sa.id=i.id ";
		 queryForResources += " LEFT JOIN xnat_subjectdata s ON s.id=sa.subject_id ";
		 queryForResources += " where e.project=:"+QueryResultUtil.PROJECT_QUERY_PARAMETER_NAME + " group by resources,xsitype order by xsitype";
		 List<Map<String,Object>> resources_results = _jdbcTemplate.queryForList(queryForResources, parameters);

		 List<Map<String,Object>> reOrganizedImageSessionResources = _queryResultUtil.reorganizeAsPivotColumnArray(resources_results, "xsitype", "resources");
		 
		 String queryForScanResources = " select distinct a.label as scanresources,s.type as scantypes, xes.element_name as xsitype,xes.singular as singularName  from xnat_abstractresource a";
		 queryForScanResources += " LEFT JOIN xnat_imagescandata s ON a.xnat_imagescandata_xnat_imagescandata_id=s.xnat_imagescandata_id";
		 queryForScanResources += " LEFT JOIN xnat_imagesessiondata i on i.id=s.image_session_id";
		 queryForScanResources += " LEFT JOIN xnat_experimentdata e ON e.id=i.id";
		 queryForScanResources += " LEFT JOIN xdat_meta_element xme ON e.extension = xme.xdat_meta_element_id ";
		 queryForScanResources += " LEFT JOIN xdat_element_security xes ON xes.element_name=xme.element_name ";
		 queryForScanResources += " where a.xnat_imagescandata_xnat_imagescandata_id in (";
		 queryForScanResources += " select xnat_imagescandata_id from xnat_imagescandata";
		 queryForScanResources += " LEFT JOIN xnat_imagesessiondata i ON i.id=xnat_imagescandata.image_session_id";
		 queryForScanResources += " LEFT JOIN xnat_experimentdata e ON i.id = e.id";
		 queryForScanResources += "  where e.project=:" + QueryResultUtil.PROJECT_QUERY_PARAMETER_NAME;
		 queryForScanResources += " ) group by xsitype, scantypes, a.label order by scantypes";
		 List<Map<String,Object>> scan_resources_results = _jdbcTemplate.queryForList(queryForScanResources, parameters);
		 Map<Object,List<Map<String,Object>>> reOrganizedImageSessionScanResources = _queryResultUtil.separateByColumn(scan_resources_results, "xsitype");
		 //Merge all the pieces by the column xsitype
	 
		 List<Object> distinctColumnValues = _queryResultUtil.getValuesInColumn(imagingsessions,"xsitype");
		 for (Object dcv : distinctColumnValues) {
		    //singularname;xsitype;type
			//MRSession;xnat:mrSessionData;type=[3D FLAIR, 3D T1, 3D T2, BLA, BLA1, Resting StatefMRI]
			 Map<String,Object> xsitypeInformation = new HashMap<>();
			 List<Map<String,Object>> resourceRows = _queryResultUtil.getRows(reOrganizedImageSessionResources,"xsitype",dcv,true);
			 Object singularname = _queryResultUtil.getValueInColumnInRowWithColumnValue(imagingsessions, "singularname","xsitype",dcv);
			 Map<String,Object> resourceInfo = new HashMap<>();
			 if (resourceRows.size() == 1) {
				 resourceInfo = resourceRows.get(0);
			 }else {
				 ArrayList noResources = new ArrayList();
				 resourceInfo.put("resources", noResources);
			 }
			 _queryResultUtil.append(resourceInfo, xsitypeInformation);

			 List<Map<String,Object>> scanRows = _queryResultUtil.getRows(reOrganizedImageSessionScans,"xsitype",dcv,true);
			 List<Map<String,Object>> xsiTypeScanTypeResources = reOrganizedImageSessionScanResources.get(dcv);
			 List<Map<String,Object>> xsiTypeScanTypeResourcesGroupedByScanTypes = null;
			 if (xsiTypeScanTypeResources != null) {
				 xsiTypeScanTypeResourcesGroupedByScanTypes  = _queryResultUtil.reorganizeAsPivotColumnArray(xsiTypeScanTypeResources, "scantypes", "scanresources");
			 }	 
			 Map<String,Object> scans = new HashMap<>();
			 if (scanRows != null && scanRows.size() == 1 ) {
				 Map<String,Object> scanTypeInfo = scanRows.get(0);
				 List<Map<String,Object>> scanTypeInformation = new ArrayList<Map<String, Object>>();
				 Object scanTypes = scanTypeInfo.get("scantypes"); //Is an Array of types
				 for (Object scantype: (ArrayList)scanTypes) {
				     Map<String,Object> scanDetails = new HashMap<String,Object>();
				     scanDetails.put("type", scantype);
				     List<Map<String,Object>> scanResourceRows = null;
				     if (xsiTypeScanTypeResourcesGroupedByScanTypes != null) {
					    scanResourceRows = _queryResultUtil.getRows(xsiTypeScanTypeResourcesGroupedByScanTypes,"scantypes",scantype,true);
				     }
					 if (scanResourceRows != null && scanResourceRows.size()==1) {
						 Object resourcesOfScan = scanResourceRows.get(0).get("scanresources");
						 scanDetails.put("resources", resourcesOfScan);
						 scanTypeInformation.add(scanDetails);
					 }else { // No resources for this scan
						 ArrayList noResources = new ArrayList();
						 scanDetails.put("resources", noResources);
						 scanTypeInformation.add(scanDetails);
					 }
				 }
				 scans.put("scans", scanTypeInformation);
				 scans.put("xsitype",dcv);
				 scans.put("singularname", singularname);
				 _queryResultUtil.append(scans, xsitypeInformation);
			  }else {
					 ArrayList noScans = new ArrayList();
					 scans.put("scans", noScans);
			  }
			   _queryResultUtil.append(scans, xsitypeInformation);
			   xsitypeInformation.put("xsitype", dcv);
			   xsitypeInformation.put("singularname", singularname);
			   mergedRows.add(xsitypeInformation);
		 }
		 JsonNode obj = jsonUtils.toJSONFromMap(mergedRows);
		 jsonWithTag.put("imagingsessions", obj);
		 return jsonWithTag;
	}
	
	private Map<String,JsonNode> getImagingAssessorTypes() {
		Map<String,JsonNode> jsonWithTag = new HashMap<String, JsonNode>();
		String query = "select distinct xes1.element_name as imagingassessor_xsitype, xes1.singular as singularName,mr_types.imagingsession_xsitype  from xnat_imageassessordata ia ";
		query += " LEFT JOIN xnat_experimentdata e1 ON e1.id=ia.id";
		query += " LEFT JOIN xdat_meta_element xme1 ON e1.extension = xme1.xdat_meta_element_id"; 
		query += " LEFT JOIN xdat_element_security xes1 ON xes1.element_name=xme1.element_name";
		query += " left join (";
		query += " select xes.element_name as imagingsession_xsitype, i.id as imagingsession_id from xnat_imagesessiondata i";
		query += " LEFT JOIN xnat_experimentdata e ON e.id=i.id";
		query += " LEFT JOIN xdat_meta_element xme ON e.extension = xme.xdat_meta_element_id"; 
		query += " LEFT JOIN xdat_element_security xes ON xes.element_name=xme.element_name";
		query += " where e.project=:"+QueryResultUtil.PROJECT_QUERY_PARAMETER_NAME + " ) as mr_types ON mr_types.imagingsession_id=ia.imagesession_id";
		query += " where e1.project=:"+QueryResultUtil.PROJECT_QUERY_PARAMETER_NAME + " group by imagingsession_xsitype,imagingassessor_xsitype, singularName";
		 List<Map<String,Object>> results = _jdbcTemplate.queryForList(query, parameters);
		 Map<Object,List<Map<String,Object>>> groupedByImageSession = _queryResultUtil.separateByColumn(results, "imagingsession_xsitype");
		 List<Map<String,Object>> reOrganized = new ArrayList<>();
		 for (Object imagingSession : groupedByImageSession.keySet()) {
			 Map<String,Object> assessorsOfImagingSession = new HashMap<String,Object>();
			 assessorsOfImagingSession.put("imagingsession_xsitype", imagingSession);
			 List<Map<String,Object>> assessorsOfImageSession = groupedByImageSession.get(imagingSession);
			 if (assessorsOfImageSession!=null) {
				 List<Map<String,Object>> rows = new ArrayList<Map<String,Object>>();
				 for (Map<String,Object> row : assessorsOfImageSession) {
					 Map<String,Object> oneAssessor = new HashMap<String,Object>();
					 oneAssessor.put("xsitype",row.get("imagingassessor_xsitype"));
					 oneAssessor.put("singularname",row.get("singularname"));
					 rows.add(oneAssessor);
				 }
				 
				 assessorsOfImagingSession.put("assessors",rows);
				 reOrganized.add(assessorsOfImagingSession);
			 }
		 }
		JsonUtils jsonUtils = new JsonUtils();

		 JsonNode obj = jsonUtils.toJSONFromMap(reOrganized);
		 jsonWithTag.put("imageassessors", obj);
		 return jsonWithTag;

	}
	
	
}
