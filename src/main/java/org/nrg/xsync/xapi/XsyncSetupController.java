package org.nrg.xsync.xapi;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.lang3.StringUtils;
import org.nrg.config.entities.Configuration;
import org.nrg.config.services.ConfigService;
import org.nrg.framework.annotations.XapiRestController;
import org.nrg.framework.constants.Scope;
import org.nrg.framework.services.SerializerService;
import org.nrg.xapi.rest.AbstractXapiProjectRestController;
import org.nrg.xapi.rest.XapiRequestMapping;
import org.nrg.xdat.om.XnatProjectdata;
import org.nrg.xdat.security.services.RoleHolder;
import org.nrg.xdat.security.services.UserManagementServiceI;
import org.nrg.xft.security.UserI;
import org.nrg.xsync.components.XsyncSitePreferencesBean;
import org.nrg.xsync.pojo.WhitelistSitePojo;
import org.nrg.xsync.pojo.XsyncSitePreferencesPojo;
import org.nrg.xsync.services.local.WhitelistXsyncSiteService;
import org.nrg.xsync.services.local.impl.WhitelistXsyncSiteServiceImpl;
import org.nrg.xsync.utils.XsyncUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Mohana Ramaratnam
 *
 */

@XapiRestController
@RequestMapping(value = "/xsync/setup")
@Api(description = "XSync Management API")
public class XsyncSetupController extends AbstractXapiProjectRestController {
	@Autowired
	public XsyncSetupController(final UserManagementServiceI userManagementService, final RoleHolder roleHolder, final ConfigService configService, XsyncSitePreferencesBean prefs, WhitelistXsyncSiteService whitelistXsyncSiteService, final SerializerService serializer, final JdbcTemplate jdbcTemplate) {
		super(userManagementService, roleHolder);
		_configService = configService;
        _prefs = prefs;
        this.whitelistXsyncSiteService = whitelistXsyncSiteService;
        _serializer = serializer;
		_jdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
	}

	@ApiOperation(value = "Sets up the Xsync project configuration",  response = String.class)
	@ApiResponses({@ApiResponse(code = 200, message = "XSync configuration successfully configured."),  @ApiResponse(code = 500, message = "Unexpected error")})
    @XapiRequestMapping(value = "/projects/{projectId}", method = RequestMethod.POST, consumes = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<String> setup(@PathVariable("projectId") String projectId, @RequestBody String jsonbody) {
		//curl -H "Content-Type: application/json" -X POST -d '{  "project":"TEST1ID",  "sync_frequency":"daily",  "auto_sync":"false",  "identifiers":"use_local",  "remote_url":"http://localhost:8080/xnat",  "remote_project_id":"SyncProjectId"}' -u admin  "http://localhost:8080/xnat/xapi/xsync/setup?project=TEST1ID"
		try {
			final UserI user = getSessionUser();
	    	final HttpStatus status = canDeleteProject(projectId);
	        if (status != null) {
	            return new ResponseEntity<>(status);
	        }
			
			//Store the JSON to the Synchronization table
			final JsonNode synchronizationJson = _serializer.deserializeJson(jsonbody, JsonNode.class);
			projectId = synchronizationJson.get(XsyncUtils.PROJECT_ELEMENT_JSON_NAME).asText();
			if (projectId == null) {
				//this.getResponse().setStatus(Status.CLIENT_ERROR_NOT_FOUND, "Unable to identify project");
				return new ResponseEntity<>(" Project ID not provided ",HttpStatus.BAD_REQUEST);
			}else {
				XnatProjectdata project = XnatProjectdata.getProjectByIDorAlias(projectId, user, false);
				if (project == null) {
					return new ResponseEntity<>(" Project  " + projectId + " not found. ",HttpStatus.BAD_REQUEST);
				} else {
					XsyncSitePreferencesPojo sitePreferencesPojo = _prefs.toPojo();
					if (sitePreferencesPojo.getXsyncWhitelistEnabled()) {
						boolean siteUrlPresentInWhitelist = false;
						List<WhitelistSitePojo> whitelistSitePojoList = whitelistXsyncSiteService.getAllWhitelistedSites();
						String inputUrl = (String) new ObjectMapper().readValue(jsonbody, HashMap.class).get("remote_url");
						for (WhitelistSitePojo whitelistSite : whitelistSitePojoList) {
							if (whitelistSite.getSiteUrl().equalsIgnoreCase(inputUrl)) {
								siteUrlPresentInWhitelist = true;
								break;
							}
						}
						if (!siteUrlPresentInWhitelist) {
							return new ResponseEntity<>(" Site URL " + inputUrl + " is not an allowed option to receive data. ",HttpStatus.BAD_REQUEST);
						}
					}
					
					//TODO validate the JSON
					XsyncUtils xsyncUtils = new XsyncUtils(_serializer, _jdbcTemplate, user);
					xsyncUtils.loadConfigurationToDB(synchronizationJson);
					saveConfig(project, jsonbody, projectId);
					return new ResponseEntity<>(projectId + " Xsync Setup complete",  HttpStatus.OK);
				}
			}
		}catch (Exception  exception) {
            _logger.error("ERROR:  Xsync Setup Threw an Exception:  {}", ExceptionUtils.getFullStackTrace(exception));
			return new ResponseEntity<>(projectId + " Xsync Setup failed ", HttpStatus.INTERNAL_SERVER_ERROR );
		}
	}

	
	@ApiOperation(value = "Gets the Xsync project configuration" )
	@ApiResponses({@ApiResponse(code = 500, message = "Unexpected error")})
    @XapiRequestMapping(value = "/projects/{projectId}", method = RequestMethod.GET, produces = {MediaType.APPLICATION_JSON_VALUE})
	public ResponseEntity<JsonNode> setup(@PathVariable("projectId") final String projectId) throws Exception{
    	final HttpStatus status = canReadProject(projectId);
        if (status != null) {
            return new ResponseEntity<>(status);
        }		
		final Configuration conf = _configService.getConfig("xsync", "json", Scope.Project, projectId);
		final String config = conf != null ? conf.getContents() : null;
		//HACK - to avoid escaped String being sent as response
		ObjectMapper objectMapper = new ObjectMapper();
		if (StringUtils.isNotBlank(config)) {
			try {
				JsonNode node = objectMapper.readTree(config);
				return new ResponseEntity<>(node,  HttpStatus.OK);
			}catch(Exception e) {
				_logger.error("ERROR:  Error retrieving project setup:  " + ExceptionUtils.getFullStackTrace(e));
				return new ResponseEntity<JsonNode>(HttpStatus.INTERNAL_SERVER_ERROR);
			}
		}else {
			return new ResponseEntity<JsonNode>(HttpStatus.NOT_FOUND);
		}
	}

	private void saveConfig(XnatProjectdata project, String xsyncConfigJson, String projectId) throws Exception {
//		Configuration config = _configService.getConfig("xsync", project.getId());
		_configService.replaceConfig(getSessionUser().getUsername(), "", "xsync", "json", xsyncConfigJson, Scope.Project, projectId);
	}

	private void saveDicomAnonymizationToConfig(XnatProjectdata project, String anonymizationScript) throws Exception {
		_configService.replaceConfig(getSessionUser().getUsername(), "", "xsync", "presyncanonymization", anonymizationScript, Scope.Project, project.getId());
	}

    @XapiRequestMapping(path="/presyncanonymization/projects/{projectId}", method = RequestMethod.PUT)
	@ApiOperation(value = "Adds Pre-Sync project specific DICOM Anonyzation",  response = String.class)
	@ApiResponses({@ApiResponse(code = 200, message = "Pre-Sync DICOM anonymization successfully configured."),  @ApiResponse(code = 500, message = "Unexpected error")})
	public ResponseEntity<String> addDICOMAnonymization(@PathVariable("projectId") String projectId, @RequestBody(required=false) String anonymizationScript) throws Exception{
		final UserI user = getSessionUser();
    	final HttpStatus status = canDeleteProject(projectId);
        if (status != null) {
            return new ResponseEntity<>(status);
        }		
		try {
	        XnatProjectdata project = XnatProjectdata.getProjectByIDorAlias(projectId, user, false);
            if (project == null) {
	        	return new ResponseEntity<>(" Project ID " +  projectId +"  does not exist ",HttpStatus.BAD_REQUEST);
            }
			saveDicomAnonymizationToConfig(project,anonymizationScript);
		}catch(Exception e) {
			_logger.error("ERROR:  Error saving pre-sync DICOM anonymization script:  " + ExceptionUtils.getFullStackTrace(e));
        	return new ResponseEntity<>(projectId + " Pre-Sync DICOM Anonymization script could not be saved. ", HttpStatus.INTERNAL_SERVER_ERROR );
		}
    	return new ResponseEntity<>(projectId + " Pre-Sync anonymization saved",  HttpStatus.OK);
	}

    @XapiRequestMapping(path="/presyncanonymization/projects/{projectId}", method = RequestMethod.GET)
	@ApiOperation(value = "GETs Pre-Sync project specific DICOM Anonyzation",  response = String.class)
	@ApiResponses({@ApiResponse(code = 200, message = "Pre-Sync DICOM anonymization."),
			       @ApiResponse(code = 204, message = "No DICOM anonymization found."),
			       @ApiResponse(code = 500, message = "Unexpected error")})
	public ResponseEntity<String> getDICOMAnonymization(@PathVariable("projectId") String projectId) {
		try {
	    	final HttpStatus status = canReadProject(projectId);
	        if (status != null) {
	            return new ResponseEntity<>(status);
	        }			
			Configuration config = _configService.getConfig("xsync", "presyncanonymization", Scope.Project, projectId);
			return new ResponseEntity<>(config == null ? "" : config.getContents(), HttpStatus.OK);
		} catch(Exception e) {
			_logger.error("ERROR:  Error returning DICOM anonymization script:  " + ExceptionUtils.getFullStackTrace(e));
			return new ResponseEntity<>("", HttpStatus.NO_CONTENT);
		}
	}


	private final ConfigService              _configService;
	private final XsyncSitePreferencesBean   _prefs;
	private final WhitelistXsyncSiteService whitelistXsyncSiteService;
	private final SerializerService          _serializer;
	private final NamedParameterJdbcTemplate _jdbcTemplate;
	public static Logger _logger = LoggerFactory.getLogger(XsyncSetupController.class);
}
