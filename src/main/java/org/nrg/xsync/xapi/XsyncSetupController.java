package org.nrg.xsync.xapi;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.apache.commons.lang3.StringUtils;
import org.nrg.config.entities.Configuration;
import org.nrg.config.services.ConfigService;
import org.nrg.framework.annotations.XapiRestController;
import org.nrg.framework.constants.Scope;
import org.nrg.framework.services.SerializerService;
import org.nrg.xdat.om.XnatProjectdata;
import org.nrg.xdat.rest.AbstractXapiRestController;
import org.nrg.xdat.security.services.RoleHolder;
import org.nrg.xdat.security.services.UserManagementServiceI;
import org.nrg.xft.security.UserI;
import org.nrg.xsync.utils.XsyncUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * @author Mohana Ramaratnam
 *
 */

@XapiRestController
@RequestMapping(value = "/xsync")
@Api(description = "XSync Management API")
public class XsyncSetupController extends AbstractXapiRestController {
	@Autowired
	public XsyncSetupController(final UserManagementServiceI userManagementService, final RoleHolder roleHolder, final ConfigService configService, final SerializerService serializer, final JdbcTemplate jdbcTemplate) {
		super(userManagementService, roleHolder);
		_configService = configService;
		_serializer = serializer;
		_jdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
	}

	@ApiOperation(value = "Sets up the Xsync project configuration",  response = String.class)
	@ApiResponses({@ApiResponse(code = 200, message = "XSync configuration successfully configured."),  @ApiResponse(code = 500, message = "Unexpected error")})
	@RequestMapping(value = "/projects/{projectId}", method = RequestMethod.POST, consumes = "application/json")
	public ResponseEntity<String> setup(@PathVariable("projectId") String projectId, @RequestBody String jsonbody) {
		//curl -H "Content-Type: application/json" -X POST -d '{  "project":"TEST1ID",  "sync_frequency":"daily",  "auto_sync":"false",  "identifiers":"use_local",  "remote_url":"http://localhost:8080/xnat",  "remote_project_id":"SyncProjectId"}' -u admin  "http://localhost:8080/xnat/xapi/xsync/setup?project=TEST1ID"
		try {
			UserI user = getSessionUser();

			// TODO I don't know what's going on with the project ID here, but it's asking for something to go wrong. I think the project ID retrieved from the JSON is the target sync project, but one variable shouldn't do double duty like that.
			this.projectId = projectId;
			//Store the JSON to the Synchronization table
			final JsonNode synchronizationJson = _serializer.deserializeJson(jsonbody, JsonNode.class);
			projectId = synchronizationJson.get(XsyncUtils.PROJECT_ELEMENT_JSON_NAME).asText();
			XnatProjectdata project = XnatProjectdata.getProjectByIDorAlias(projectId, user, false);
			if (project == null) {
				//this.getResponse().setStatus(Status.CLIENT_ERROR_NOT_FOUND, "Unable to identify project");
				return new ResponseEntity<>(" Project ID not provided ",HttpStatus.BAD_REQUEST);
			}else {
				projectId = project.getId();
			}
			//TODO validate the JSON
			XsyncUtils xsyncUtils = new XsyncUtils(_serializer, _jdbcTemplate, user);
			xsyncUtils.loadConfigurationToDB(synchronizationJson);
//            save_resource(project,jsonbody);
			saveConfig(project, jsonbody);
			return new ResponseEntity<>(projectId + " Xsync Setup complete",  HttpStatus.OK);

		}catch (Exception  exception) {
			return new ResponseEntity<>(projectId + " Xsync Setup failed ", HttpStatus.INTERNAL_SERVER_ERROR );
		}
	}

	@ApiOperation(value = "Gets the Xsync project configuration",  response = String.class)
	@ApiResponses({@ApiResponse(code = 500, message = "Unexpected error")})
	@RequestMapping(value = "/projects/{projectId}", method = RequestMethod.GET)
	public ResponseEntity<String> setup(@PathVariable("projectId") final String projectId) {
		final Configuration conf = _configService.getConfig("xsync", "json", Scope.Project, projectId);
		final String config = conf != null ? conf.getContents() : null;
		return StringUtils.isNotBlank(config) ? new ResponseEntity<>(config, HttpStatus.OK) : new ResponseEntity<String>(HttpStatus.NOT_FOUND);
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

	private String projectId = null;

	private final ConfigService              _configService;
	private final SerializerService          _serializer;
	private final NamedParameterJdbcTemplate _jdbcTemplate;
}
