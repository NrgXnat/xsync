package org.nrg.xsync.xapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.nrg.config.services.ConfigService;
import org.nrg.framework.annotations.XapiRestController;
import org.nrg.framework.constants.Scope;
import org.nrg.xdat.model.XnatAbstractresourceI;
import org.nrg.xdat.om.XnatProjectdata;
import org.nrg.xdat.om.XsyncXsyncprojectdata;
import org.nrg.xdat.rest.AbstractXapiRestController;
import org.nrg.xdat.security.services.RoleHolder;
import org.nrg.xdat.security.services.UserManagementServiceI;
import org.nrg.xft.event.EventDetails;
import org.nrg.xft.event.EventUtils;
import org.nrg.xft.security.UserI;
import org.nrg.xft.utils.FileUtils;
import org.nrg.xnat.helpers.uri.URIManager;
import org.nrg.xnat.helpers.uri.URIManager.ArchiveItemURI;
import org.nrg.xnat.helpers.uri.UriParserUtils;
import org.nrg.xnat.utils.ResourceUtils;
import org.nrg.xsync.utils.XsyncFileUtils;
import org.nrg.xsync.utils.XsyncUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.inject.Inject;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * @author Mohana Ramaratnam
 *
 */

@XapiRestController
@RequestMapping(value = "/xsync")
@Api(description = "XSync Management API")
public class XsyncSetupController extends AbstractXapiRestController {
	@Autowired
	public XsyncSetupController(final UserManagementServiceI userManagementService, final RoleHolder roleHolder) {
		super(userManagementService, roleHolder);
	}

	@RequestMapping(path="/projects/{projectId}", method = RequestMethod.POST, consumes = "application/json")
    @ApiOperation(value = "Sets up the Xsync project configuration",  response = String.class)
    @ApiResponses({@ApiResponse(code = 200, message = "XSync configuration successfully configured."),  @ApiResponse(code = 500, message = "Unexpected error")})
	public ResponseEntity<String> setup(@PathVariable("projectId") String projectId, @RequestBody String jsonbody) {
		//curl -H "Content-Type: application/json" -X POST -d '{  "project":"TEST1ID",  "sync_frequency":"daily",  "auto_sync":"false",  "identifiers":"use_local",  "remote_url":"http://localhost:8080/xnat",  "remote_project_id":"SyncProjectId"}' -u admin  "http://localhost:8080/xnat/xapi/xsync/setup?project=TEST1ID"
		try {
			UserI user = getSessionUser();
			this.projectId = projectId;
			//Store the JSON to the Synchronization table
			ObjectMapper objectMapper = new ObjectMapper();
			final JsonNode synchronizationJson = objectMapper.readValue(jsonbody, JsonNode.class);
	        projectId = synchronizationJson.get(XsyncUtils.PROJECT_ELEMENT_JSON_NAME).asText();
	        XnatProjectdata project = XnatProjectdata.getProjectByIDorAlias(projectId, user, false);
            if (project == null) {
                //this.getResponse().setStatus(Status.CLIENT_ERROR_NOT_FOUND, "Unable to identify project");
	        	return new ResponseEntity<>(" Project ID not provided ",HttpStatus.BAD_REQUEST);
            }else {
            	projectId = project.getId();
            }
            //TODO validate the JSON
            XsyncUtils xsyncUtils = new XsyncUtils(user);
            xsyncUtils.loadConfigurationToDB(synchronizationJson);
//            save_resource(project,jsonbody);
			saveConfig(project, jsonbody);
        	return new ResponseEntity<>(projectId + " Xsync Setup complete",  HttpStatus.OK);

		}catch (Exception  exception) {
        	return new ResponseEntity<>(projectId + " Xsync Setup failed ", HttpStatus.INTERNAL_SERVER_ERROR );
		}
	}

	@RequestMapping(path="/projects/{projectId}", method = RequestMethod.GET)
	@ApiOperation(value = "Gets the Xsync project configuration",  response = String.class)
	@ApiResponses({@ApiResponse(code = 500, message = "Unexpected error")})
	public ResponseEntity<String> setup(@PathVariable("projectId") String projectId) {
		String config = _configService.getConfig("xsync", "json", Scope.Project, projectId).getContents();
		return new ResponseEntity<>(config, HttpStatus.OK);
	}

	
	private void saveConfig(XnatProjectdata project, String xsyncConfigJson) throws Exception {
//		Configuration config = _configService.getConfig("xsync", project.getId());
		_configService.replaceConfig(getSessionUser().getUsername(), "", "xsync", "json", xsyncConfigJson, Scope.Project, projectId);
	}

	private void saveDicomAnonymizationToConfig(XnatProjectdata project, String anonymizationScript) throws Exception {
		_configService.replaceConfig(getSessionUser().getUsername(), "", "xsync", "presyncanonymization", anonymizationScript, Scope.Project, project.getId());
	}

	@RequestMapping(path="/projects/{projectId}/presyncanonymization", method = RequestMethod.PUT)
    @ApiOperation(value = "Adds Pre-Sync project specific DICOM Anonyzation",  response = String.class)
    @ApiResponses({@ApiResponse(code = 200, message = "Pre-Sync DICOM anonymization successfully configured."),  @ApiResponse(code = 500, message = "Unexpected error")})
	public ResponseEntity<String> addDICOMAnonymization(@PathVariable("projectId") String projectId, @RequestBody(required=false) String anonymizationScript) {
		UserI user = getSessionUser();
		try {
	        XnatProjectdata project = XnatProjectdata.getProjectByIDorAlias(projectId, user, false);
            if (project == null) {
	        	return new ResponseEntity<>(" Project ID " +  projectId +"  does not exist ",HttpStatus.BAD_REQUEST);
            }
	        this.projectId = project.getId();
			saveDicomAnonymizationToConfig(project,anonymizationScript);
		}catch(Exception e) {
        	return new ResponseEntity<>(projectId + " Pre-Sync DICOM Anonymization script could not be saved. ", HttpStatus.INTERNAL_SERVER_ERROR );
		}
    	return new ResponseEntity<>(projectId + " Pre-Sync anonymization saved",  HttpStatus.OK);
	}

	@RequestMapping(path="/projects/{projectId}/presyncanonymization", method = RequestMethod.GET)
    @ApiOperation(value = "GETs Pre-Sync project specific DICOM Anonyzation",  response = String.class)
    @ApiResponses({@ApiResponse(code = 200, message = "Pre-Sync DICOM anonymization."),
			       @ApiResponse(code = 204, message = "No DICOM anonymization found."),
			       @ApiResponse(code = 500, message = "Unexpected error")})
	public ResponseEntity<String> getDICOMAnonymization(@PathVariable("projectId") String projectId) {
		try {
			String config = _configService.getConfig("xsync", "presyncanonymization", Scope.Project, projectId).getContents();
			return new ResponseEntity<>(config, HttpStatus.OK);
		} catch(NullPointerException e) {
			return new ResponseEntity<>("", HttpStatus.NO_CONTENT);
		}
	}


	private String                projectId           = null;

	@Inject
	private ConfigService _configService;
}
