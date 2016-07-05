package org.nrg.xsync.xapi;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.nrg.framework.annotations.XapiRestController;
import org.nrg.xdat.bean.CatCatalogBean;
import org.nrg.xdat.model.XnatAbstractresourceI;
import org.nrg.xdat.om.XnatProjectdata;
import org.nrg.xdat.om.XnatResourcecatalog;
import org.nrg.xdat.om.XsyncXsyncinfodata;
import org.nrg.xdat.om.XsyncXsyncprojectdata;
import org.nrg.xdat.rest.AbstractXnatRestApi;
import org.nrg.xft.XFTItem;
import org.nrg.xft.event.EventDetails;
import org.nrg.xft.event.EventMetaI;
import org.nrg.xft.event.EventUtils;
import org.nrg.xft.event.persist.PersistentWorkflowI;
import org.nrg.xft.event.persist.PersistentWorkflowUtils;
import org.nrg.xft.security.UserI;
import org.nrg.xft.utils.FileUtils;
import org.nrg.xft.utils.SaveItemHelper;
import org.nrg.xft.utils.ValidationUtils.ValidationResults;
import org.nrg.xnat.helpers.uri.URIManager;
import org.nrg.xnat.helpers.uri.URIManager.ArchiveItemURI;
import org.nrg.xnat.helpers.uri.UriParserUtils;
import org.nrg.xnat.utils.ResourceUtils;
import org.nrg.xnat.utils.WorkflowUtils;
import org.nrg.xsync.manager.SynchronizationManager;
import org.nrg.xsync.remote.alias.RemoteAliasEntity;
import org.nrg.xsync.remote.alias.services.RemoteAliasService;
import org.nrg.xsync.utils.XsyncFileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * @author Mohana Ramaratnam
 *
 */

@XapiRestController
@RequestMapping(value = "/xsync")
@Api(description = "XSync Management API")

public class XsyncSetupController extends AbstractXnatRestApi {
	XsyncXsyncprojectdata existing = null;
	String projectId = null;
	JsonNode synchronizationJson = null;
	static final String PROJECT_ELEMENT_JSON_NAME = "project"; 
	static final String REMOTE_HOST_URL = "remote_url";
	static final String USER_REMOTE_TOKEN = "remote_token"; 
	static final String USER_REMOTE_SECRET = "remote_secret"; 

	@Autowired
	private RemoteAliasService _remoteAliasService;

	@RequestMapping(path="/projects/{projectId}", method = RequestMethod.POST, consumes = "application/json")
    @ApiOperation(value = "Sets up the Xsync project configuration",  response = String.class)
    @ApiResponses({@ApiResponse(code = 200, message = "XSync configuration successfully configured."),  @ApiResponse(code = 500, message = "Unexpected error")})
	
	public ResponseEntity<String> setup(@PathVariable("projectId") String projectId,@RequestBody String jsonbody) {
		//curl -H "Content-Type: application/json" -X POST -d '{  "project":"TEST1ID",  "sync_frequency":"daily",  "auto_sync":"false",  "identifiers":"use_local",  "remote_url":"http://localhost:8080/xnat",  "remote_project_id":"SyncProjectId"}' -u admin  "http://localhost:8080/xnat/xapi/xsync/setup?project=TEST1ID"
		try {
			UserI user = getSessionUser();
			this.projectId = projectId;
			//Store the JSON to the Synchronization table
			ObjectMapper objectMapper = new ObjectMapper();
			synchronizationJson = objectMapper.readValue(jsonbody, JsonNode.class);
	        projectId = synchronizationJson.get(PROJECT_ELEMENT_JSON_NAME).asText();
	    	
	        XnatProjectdata project = XnatProjectdata.getProjectByIDorAlias(projectId, user, false);
            if (project == null) {
                //this.getResponse().setStatus(Status.CLIENT_ERROR_NOT_FOUND, "Unable to identify project");
	        	return new ResponseEntity<>(" Project ID not provided ",HttpStatus.BAD_REQUEST);
            }else {
            	projectId = project.getId();
            }
            
			XFTItem item = XFTItem.NewItem(XsyncXsyncprojectdata.SCHEMA_ELEMENT_NAME, user);
            XsyncXsyncprojectdata syncProject = new XsyncXsyncprojectdata(item);

            ArrayList<XsyncXsyncprojectdata> list = XsyncXsyncprojectdata.getXsyncXsyncprojectdatasByField(XsyncXsyncprojectdata.SCHEMA_ELEMENT_NAME+"/project_id", projectId, user, true);
            if (list != null && list.size() > 0) {
            	existing = list.get(0);
            	syncProject.setItem(existing.getItem());
            }
            populate(syncProject,synchronizationJson);
            save_resource(project,jsonbody);
            final ValidationResults vr = syncProject.validate();
            if (vr != null && !vr.isValid()) {
	        	return new ResponseEntity<>(projectId + " Xsync Setup failed. Invalid JSON: " + vr.isValid(),HttpStatus.BAD_REQUEST);
            }
            EventMetaI c = EventUtils.DEFAULT_EVENT(user, "Added Synchronization");
            
            if (SaveItemHelper.authorizedSave(syncProject, user, false, true, c)) {
                EventDetails details = EventUtils.newEventInstance(EventUtils.CATEGORY.DATA, EventUtils.TYPE.WEB_SERVICE,  EventUtils.getAddModifyAction(syncProject.getXSIType(), (existing == null)), "", "");
            	PersistentWorkflowI wrk = PersistentWorkflowUtils.buildOpenWorkflow(user, syncProject.getXSIType(),syncProject.getXsyncXsyncprojectdataId()+"",syncProject.getProjectId(), details);
            	WorkflowUtils.complete(wrk, c);
            }
        	return new ResponseEntity<>(projectId + " Xsync Setup complete", (existing == null) ? HttpStatus.CREATED : HttpStatus.OK);

		}catch (Exception  exception) {
        	return new ResponseEntity<>(projectId + " Xsync Setup failed ", HttpStatus.INTERNAL_SERVER_ERROR );
		}
	}
	
	private void save_resource(XnatProjectdata project, String xsyncCfgJSON) throws Exception {
		String dest_path = FileUtils.AppendRootPath(project.getRootArchivePath(),
				"resources/");
		UserI user = getSessionUser();
		
		List<XnatAbstractresourceI> resources = project.getResources_resource();
		XnatAbstractresourceI synchronizationResource = null;
		for (XnatAbstractresourceI res: resources) {
			if (res.getLabel().equalsIgnoreCase(XsyncFileUtils.SYNCHRONIZATION_LABEL)) {
				//Existing file possibly, update it
				synchronizationResource = res;
				break;
			}
		}
		if (synchronizationResource == null) {
			// Create one
			//Create a resource and hence a catalog
			XnatResourcecatalog syncResource = new XnatResourcecatalog();
			syncResource.setLabel(XsyncFileUtils.SYNCHRONIZATION_LABEL);
			String resourceFolder=syncResource.getLabel();


			CatCatalogBean cat = new CatCatalogBean();
			cat.setId(XsyncFileUtils.SYNCHRONIZATION_LABEL);
			File dest=null;
			if(resourceFolder==null){
				dest = new File(new File(dest_path),cat.getId() + "_catalog.xml");
			}else{
				dest = new File(new File(dest_path,resourceFolder),cat.getId() + "_catalog.xml");
			}
			dest.getParentFile().mkdirs();
			

			try {
				FileWriter fw = new FileWriter(dest);
				cat.toXML(fw, true);
				fw.close();

				String path = dest_path + File.separator + XsyncFileUtils.SYNCHRONIZATION_LABEL + File.separator + "sync_config.json";

				Files.write( Paths.get(path), xsyncCfgJSON.getBytes());

			} catch (IOException e) {
				throw e;
			}
			syncResource.setUri(dest.getAbsolutePath());
			try {
                EventDetails details = EventUtils.newEventInstance(EventUtils.CATEGORY.DATA, EventUtils.TYPE.WEB_SERVICE,  EventUtils.CREATE_RESOURCE, "", "");
				PersistentWorkflowI wrk=PersistentWorkflowUtils.buildOpenWorkflow(user, project.getItem(), details);
				EventMetaI ci=wrk.buildEvent();
				project.setResources_resource(syncResource.getItem());
				SaveItemHelper.authorizedSave(project,user, false, false,ci);
			}catch(Exception e) {
				throw e;
			}
		}else {
			//Existing file possibly, update it
			//Possible Configuration Change
			//Implies all data must be resynced - reset the last sync time
			//If a sync operation is running - wait for it to complete.
			//After its done - reset the last sync time.
			String jsonPath = dest_path + File.separator + XsyncFileUtils.SYNCHRONIZATION_LABEL + File.separator + "sync_config.json";
			try {
				Files.write( Paths.get(jsonPath), xsyncCfgJSON.getBytes());
			}catch(IOException e) {
				throw e;
			}
			SynchronizationManager.notifySyncConfigurationChange(projectId);
		}
		refreshCatalog(user);
	}
	
	private synchronized void refreshCatalog(UserI user) throws Exception{
		String resource = "/archive/projects/"+projectId+"/resources/"+XsyncFileUtils.SYNCHRONIZATION_LABEL;

		URIManager.DataURIA uri=UriParserUtils.parseURI(resource);

		ArchiveItemURI resourceURI = (ArchiveItemURI) uri;
        EventDetails details = EventUtils.newEventInstance(EventUtils.CATEGORY.DATA, EventUtils.TYPE.WEB_SERVICE, "Catalog(s) Refreshed" , "", "");

		ResourceUtils.refreshResourceCatalog(resourceURI, user, details, true, true, false, true);
		
	}
	
	@RequestMapping(path="/projects/{projectId}/presyncanonymization", method = RequestMethod.PUT)
    @ApiOperation(value = "Adds Pre-Sync project specific DICOM Anonyzation",  response = String.class)
    @ApiResponses({@ApiResponse(code = 200, message = "Pre-Sync DICOM anonymization successfully configured."),  @ApiResponse(code = 500, message = "Unexpected error")})
	
	
	public ResponseEntity<String> addDICOMAnonymization(@PathVariable("projectId") String projectId,@RequestBody String anonymizationScript) {
		UserI user = getSessionUser();
        XnatProjectdata project = XnatProjectdata.getProjectByIDorAlias(projectId, user, false);
        this.projectId = project.getId();
		String dest_path = FileUtils.AppendRootPath(project.getRootArchivePath(),
				"resources/");
		
		List<XnatAbstractresourceI> resources = project.getResources_resource();
		XnatAbstractresourceI synchronizationResource = null;
		for (XnatAbstractresourceI res: resources) {
			if (res.getLabel().equalsIgnoreCase(XsyncFileUtils.SYNCHRONIZATION_LABEL)) {
				//Existing file possibly, update it
				synchronizationResource = res;
				break;
			}
		}
		if (synchronizationResource==null) {
        	return new ResponseEntity<>(projectId + " Xsync has not been configured. Please configure XSync before uploading anonymization script ", HttpStatus.INTERNAL_SERVER_ERROR );
		}
		String jsonPath = dest_path + File.separator + XsyncFileUtils.SYNCHRONIZATION_LABEL + File.separator + "DICOM_anon.das";
		try {
			Files.write( Paths.get(jsonPath), anonymizationScript.getBytes());
			refreshCatalog(user);
		}catch(Exception e) {
        	return new ResponseEntity<>(projectId + " Pre-Sync DICOM Anonymization script could not be saved. ", HttpStatus.INTERNAL_SERVER_ERROR );
		}
    	return new ResponseEntity<>(projectId + " Pre-Sync anonymization saved", (existing == null) ? HttpStatus.CREATED : HttpStatus.OK);
		
	}
	
	
	private void populate(XsyncXsyncprojectdata syncProject,JsonNode synchronizationJson) throws Exception {
		//Store
		/*{
			  project:"",
			  sync_frequency:"",
			  need_flag_to_synchronize:"",
			  auto_sync:"",
			  apply_anonymization:"",
			  identifiers:"",
			  remote_url:""
			}*/
		UserI user = getSessionUser();

		syncProject.setProjectId(synchronizationJson.get(PROJECT_ELEMENT_JSON_NAME).asText());
        XFTItem item = XFTItem.NewItem(XsyncXsyncinfodata.SCHEMA_ELEMENT_NAME, user);
        XsyncXsyncinfodata syncinfo = new XsyncXsyncinfodata(item);
        syncinfo.setSyncFrequency(synchronizationJson.get("sync_frequency").asText());
		syncinfo.setAutoSync(new Boolean(synchronizationJson.get("auto_sync").asBoolean()));
		syncinfo.setIdentifiers(synchronizationJson.get("identifiers").asText());
		syncinfo.setRemoteUrl(synchronizationJson.get("remote_url").asText());
		syncinfo.setRemoteProjectId(synchronizationJson.get("remote_project_id").asText());
		syncProject.setSyncinfo(syncinfo.getItem());
	}


}
