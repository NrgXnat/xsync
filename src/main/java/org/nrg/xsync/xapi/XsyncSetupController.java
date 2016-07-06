package org.nrg.xsync.xapi;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import org.nrg.framework.annotations.XapiRestController;
import org.nrg.xdat.bean.CatCatalogBean;
import org.nrg.xdat.model.XnatAbstractresourceI;
import org.nrg.xdat.om.XnatProjectdata;
import org.nrg.xdat.om.XnatResourcecatalog;
import org.nrg.xdat.om.XsyncXsyncprojectdata;
import org.nrg.xdat.rest.AbstractXnatRestApi;
import org.nrg.xft.event.EventDetails;
import org.nrg.xft.event.EventMetaI;
import org.nrg.xft.event.EventUtils;
import org.nrg.xft.event.persist.PersistentWorkflowI;
import org.nrg.xft.event.persist.PersistentWorkflowUtils;
import org.nrg.xft.security.UserI;
import org.nrg.xft.utils.FileUtils;
import org.nrg.xft.utils.SaveItemHelper;
import org.nrg.xnat.helpers.uri.URIManager;
import org.nrg.xnat.helpers.uri.URIManager.ArchiveItemURI;
import org.nrg.xnat.helpers.uri.UriParserUtils;
import org.nrg.xnat.utils.ResourceUtils;
import org.nrg.xsync.remote.alias.services.RemoteAliasService;
import org.nrg.xsync.utils.XsyncFileUtils;
import org.nrg.xsync.utils.XsyncUtils;
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
	        projectId = synchronizationJson.get(XsyncUtils.PROJECT_ELEMENT_JSON_NAME).asText();
	    	
	        XnatProjectdata project = XnatProjectdata.getProjectByIDorAlias(projectId, user, false);
            if (project == null) {
                //this.getResponse().setStatus(Status.CLIENT_ERROR_NOT_FOUND, "Unable to identify project");
	        	return new ResponseEntity<>(" Project ID not provided ",HttpStatus.BAD_REQUEST);
            }else {
            	projectId = project.getId();
            }
            XsyncUtils xsyncUtils = new XsyncUtils(user);
            xsyncUtils.loadConfigurationToDB(synchronizationJson);
            save_resource(project,jsonbody);
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
			String jsonPath = dest_path + File.separator + XsyncFileUtils.SYNCHRONIZATION_LABEL + File.separator + "sync_config.json";
			try {
				Files.write( Paths.get(jsonPath), xsyncCfgJSON.getBytes());
			}catch(IOException e) {
				throw e;
			}
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
	
	


}
