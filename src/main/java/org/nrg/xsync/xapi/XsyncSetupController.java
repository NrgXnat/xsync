package org.nrg.xsync.xapi;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.lang3.StringUtils;
import org.nrg.config.entities.Configuration;
import org.nrg.config.services.ConfigService;
import org.nrg.framework.annotations.XapiRestController;
import org.nrg.framework.constants.Scope;
import org.nrg.framework.orm.hibernate.AbstractHibernateEntity;
import org.nrg.xapi.rest.AbstractXapiProjectRestController;
import org.nrg.xapi.rest.XapiRequestMapping;
import org.nrg.xdat.om.XnatProjectdata;
import org.nrg.xdat.security.helpers.AccessLevel;
import org.nrg.xdat.security.services.RoleHolder;
import org.nrg.xdat.security.services.UserManagementServiceI;
import org.nrg.xft.security.UserI;
import org.nrg.xsync.components.XsyncSitePreferencesBean;
import org.nrg.xsync.pojo.configuration.SyncConfigurationPojo;
import org.nrg.xsync.pojo.WhitelistSitePojo;
import org.nrg.xsync.pojo.XsyncSitePreferencesPojo;
import org.nrg.xsync.services.local.WhitelistXsyncSiteService;
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

import java.util.ArrayList;
import java.util.List;

/**
 * @author Mohana Ramaratnam
 *
 */

@XapiRestController
@RequestMapping(value = "/xsync/setup")
@Api("XSync Management API")
public class XsyncSetupController extends AbstractXapiProjectRestController {
	@Autowired
	public XsyncSetupController(final UserManagementServiceI userManagementService,
								final RoleHolder roleHolder,
								final ConfigService configService,
								final XsyncSitePreferencesBean prefs,
								final WhitelistXsyncSiteService whitelistXsyncSiteService,
								final JdbcTemplate jdbcTemplate) {
		super(userManagementService, roleHolder);
		_configService = configService;
        _prefs = prefs;
        _whitelistXsyncSiteService = whitelistXsyncSiteService;
		_jdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
	}

	@ApiOperation(value = "Get all xsync configs" )
	@ApiResponses({@ApiResponse(code = 500, message = "Unexpected error")})
	@XapiRequestMapping(method = RequestMethod.GET, produces = {MediaType.APPLICATION_JSON_VALUE},
			restrictTo = AccessLevel.Admin)
	public ResponseEntity<List<JsonNode>> getAllXsyncConfigs() {
		List<Configuration> configurations =
				_configService.getAll().stream().filter(c -> c.getTool().equalsIgnoreCase("xsync")).filter(AbstractHibernateEntity::isEnabled).toList();
		try {
			ObjectMapper objectMapper = new ObjectMapper();
			List<JsonNode> configurationNodes = new ArrayList<>();
			for (Configuration configuration : configurations) {
				final String configurationString = configuration != null ? configuration.getContents() : null;
				JsonNode configurationNode = objectMapper.readTree(configurationString);
				configurationNodes.add(configurationNode);
			}

			return new ResponseEntity<>(configurationNodes,  HttpStatus.OK);
			} catch(Exception e) {
				_logger.error("ERROR:  Error retrieving system xsync configurations:  {}",
							  ExceptionUtils.getFullStackTrace(e));
				return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
			}
	}

	@ApiOperation(value = "Get all xsync configs" )
	@ApiResponses({@ApiResponse(code = 500, message = "Unexpected error")})
	@XapiRequestMapping(value = "/number", method = RequestMethod.GET, produces = {MediaType.APPLICATION_JSON_VALUE},
			restrictTo = AccessLevel.Admin)
	public ResponseEntity<Integer> getNumberXsyncConfigs() {
		List<Configuration> configurations =
				_configService.getAll().stream().filter(c -> c.getTool().equalsIgnoreCase("xsync")).filter(AbstractHibernateEntity::isEnabled).toList();
		try {
			ObjectMapper objectMapper = new ObjectMapper();
			List<JsonNode> configurationNodes = new ArrayList<>();
			for (Configuration configuration : configurations) {
				final String configurationString = configuration != null ? configuration.getContents() : null;
				JsonNode configurationNode = objectMapper.readTree(configurationString);
				configurationNodes.add(configurationNode);
			}

			return new ResponseEntity<>(configurationNodes.size(),  HttpStatus.OK);
		} catch(Exception e) {
			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@ApiOperation(value = "Sets up the Xsync project configuration",  response = String.class)
	@ApiResponses({@ApiResponse(code = 200, message = "XSync configuration successfully configured."),  @ApiResponse(code = 500, message = "Unexpected error")})
    @XapiRequestMapping(value = "/projects/{projectId}", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> setup(@PathVariable("projectId") String projectId, @RequestBody SyncConfigurationPojo configurationPojo) {
		//curl -H "Content-Type: application/json" -X POST -d '{  "project":"TEST1ID",  "sync_frequency":"daily",  "auto_sync":"false",  "identifiers":"use_local",  "remote_url":"http://localhost:8080/xnat",  "remote_project_id":"SyncProjectId"}' -u admin  "http://localhost:8080/xnat/xapi/xsync/setup?project=TEST1ID"
		try {
			final UserI user = getSessionUser();
	    	final HttpStatus status = canDeleteProject(projectId);
	        if (status != null) {
	            return new ResponseEntity<>(status);
	        }
			
			//Store the JSON to the Synchronization table
			if (configurationPojo.getSource_project_id().isBlank()) {
				return new ResponseEntity<>(" Project ID not provided ",HttpStatus.BAD_REQUEST);
			}  else if (!configurationPojo.getSource_project_id().equals(projectId)) {
				return new ResponseEntity<>(" Project ID values are inconsistent ",HttpStatus.BAD_REQUEST);
			} else {
				XnatProjectdata project = XnatProjectdata.getProjectByIDorAlias(projectId, user, false);
				if (project == null) {
					return new ResponseEntity<>(" Project  " + projectId + " not found. ",HttpStatus.BAD_REQUEST);
				} else {
					XsyncSitePreferencesPojo sitePreferencesPojo = _prefs.toPojo();
					if (sitePreferencesPojo.getXsyncWhitelistEnabled()) {
						List<WhitelistSitePojo> whitelistSitePojoList =
								_whitelistXsyncSiteService.getAllWhitelistedSites();
						if (whitelistSitePojoList.stream().filter(wl -> wl.getSiteUrl().equalsIgnoreCase(configurationPojo.getRemote_url())).toList().isEmpty()) {
							return new ResponseEntity<>(" Site URL " + configurationPojo.getRemote_url() + " is not an allowed option to " +
																"receive data. ",HttpStatus.BAD_REQUEST);
						}
					}
					
					//TODO validate the JSON
					XsyncUtils xsyncUtils = new XsyncUtils(_jdbcTemplate, user);
					xsyncUtils.loadConfigurationToDB(configurationPojo);
					saveConfig(configurationPojo, projectId);
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
	public ResponseEntity<JsonNode> getXsyncProjectConfiguration(@PathVariable("projectId") final String projectId) throws Exception{
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
			} catch(Exception e) {
                _logger.error("ERROR:  Error retrieving project setup:  {}", ExceptionUtils.getFullStackTrace(e));
				return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
			}
		} else {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
	}

	private void saveConfig(SyncConfigurationPojo configurationPojo, String projectId) throws Exception {
		ObjectMapper mapper = new ObjectMapper();
		String serializedConfig = mapper.writeValueAsString(configurationPojo);
		_configService.replaceConfig(getSessionUser().getUsername(), "", "xsync", "json", serializedConfig, Scope.Project, projectId);
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
            _logger.error("ERROR:  Error saving pre-sync DICOM anonymization script:  {}", ExceptionUtils.getFullStackTrace(e));
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
            _logger.error("ERROR:  Error returning DICOM anonymization script:  {}", ExceptionUtils.getFullStackTrace(e));
			return new ResponseEntity<>("", HttpStatus.NO_CONTENT);
		}
	}


	private final ConfigService              _configService;
	private final XsyncSitePreferencesBean   _prefs;
	private final WhitelistXsyncSiteService _whitelistXsyncSiteService;
	private final NamedParameterJdbcTemplate _jdbcTemplate;
	public static Logger _logger = LoggerFactory.getLogger(XsyncSetupController.class);
}
