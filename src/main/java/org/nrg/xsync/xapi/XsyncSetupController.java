package org.nrg.xsync.xapi;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.nrg.config.entities.Configuration;
import org.nrg.framework.annotations.XapiRestController;
import org.nrg.xapi.exceptions.NotFoundException;
import org.nrg.xapi.rest.AbstractXapiProjectRestController;
import org.nrg.xapi.rest.XapiRequestMapping;
import org.nrg.xdat.om.XnatProjectdata;
import org.nrg.xdat.security.helpers.AccessLevel;
import org.nrg.xdat.security.services.RoleHolder;
import org.nrg.xdat.security.services.UserManagementServiceI;
import org.nrg.xsync.components.XsyncSitePreferencesBean;
import org.nrg.xsync.pojo.configuration.SyncConfigurationPojo;
import org.nrg.xsync.services.local.WhitelistXsyncSiteService;
import org.nrg.xsync.services.local.XsyncConfigurationService;
import org.nrg.xsync.utils.XsyncUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;
import java.util.zip.DataFormatException;

/**
 * @author Mohana Ramaratnam
 *
 */

@XapiRestController
@RequestMapping(value = "/xsync/setup")
@Api("XSync Management API")
@Slf4j
public class XsyncSetupController extends AbstractXapiProjectRestController {
	@Autowired
	public XsyncSetupController(final UserManagementServiceI userManagementService,
                                final RoleHolder roleHolder,
                                final XsyncSitePreferencesBean prefs, XsyncConfigurationService xsyncConfigService,
                                final WhitelistXsyncSiteService whitelistXsyncSiteService,
                                final JdbcTemplate jdbcTemplate) {
		super(userManagementService, roleHolder);
        _prefs = prefs;
        _xsyncConfigService = xsyncConfigService;
        _whitelistXsyncSiteService = whitelistXsyncSiteService;
		_jdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

	@ApiOperation(value = "Sets up the Xsync project configuration",  response = String.class)
	@ApiResponses({@ApiResponse(code = 200, message = "XSync configuration successfully configured."),
			@ApiResponse(code = 401, message = "User does not have required credentials to set project configuration."),
			@ApiResponse(code = 500, message = "Unexpected error")})
    @XapiRequestMapping(value = "/projects/{projectId}", method = RequestMethod.POST, consumes =
			MediaType.APPLICATION_JSON_VALUE, restrictTo = AccessLevel.Delete)
	public String setup(@PathVariable("projectId") String projectId,
										@RequestBody SyncConfigurationPojo configurationPojo) throws Exception {
		try {
			if (configurationPojo.getSource_project_id().isBlank()) {
				throw new DataFormatException(" Project ID not provided ");
			}  else if (!configurationPojo.getSource_project_id().equals(projectId)) {
				throw new DataFormatException(" Project ID values are inconsistent ");
			}
			XnatProjectdata project = XnatProjectdata.getProjectByIDorAlias(projectId, getSessionUser(), false);
			if (project == null) {
				throw new DataFormatException(" Project " + projectId + " not found. ");
			}
			if (!_xsyncConfigService.checkForWhitelistConformation(_prefs.toPojo().getXsyncWhitelistEnabled(),
																   _whitelistXsyncSiteService.getAllWhitelistedSites(), configurationPojo.getRemote_url())) {
				throw new DataFormatException(" Site URL " + configurationPojo.getRemote_url() +
													" is not an allowed option to receive data. ");
			}

			XsyncUtils xsyncUtils = new XsyncUtils(_jdbcTemplate, getSessionUser());
			xsyncUtils.loadConfigurationToDB(configurationPojo);
			_xsyncConfigService.saveConfig(getSessionUser(), configurationPojo, projectId);
			return projectId + " Xsync Setup complete";
		} catch (Exception  exception) {
			log.error("ERROR:  Xsync Setup Threw an Exception:  {}", ExceptionUtils.getFullStackTrace(exception));
			throw new Exception(projectId + " Xsync Setup failed.");
		}
	}

	@ApiOperation(value = "Gets the Xsync project configuration" )
	@ApiResponses({@ApiResponse(code = 200, message = "XSync configuration returned."),
			@ApiResponse(code = 401, message = "User does not have required credentials to get project configuration."),
			@ApiResponse(code = 500, message = "Unexpected error")})
    @XapiRequestMapping(value = "/projects/{projectId}", method = RequestMethod.GET, produces =
			{MediaType.APPLICATION_JSON_VALUE}, restrictTo = AccessLevel.Read)
	public ResponseEntity<SyncConfigurationPojo> getXsyncProjectConfiguration(@PathVariable("projectId") final String projectId) throws Exception{
		return new ResponseEntity<>(_xsyncConfigService.getSyncConfiguration(projectId),  HttpStatus.OK);
	}

	private void saveDicomAnonymizationToConfig(XnatProjectdata project, String anonymizationScript) throws Exception {
		_xsyncConfigService.replaceConfiguration(getSessionUser().getUsername(), "presyncanonymization",
												 anonymizationScript, project.getId());
	}

    @XapiRequestMapping(path="/presyncanonymization/projects/{projectId}", method = RequestMethod.PUT,
			restrictTo = AccessLevel.Delete)
	@ApiOperation(value = "Adds Pre-Sync project specific DICOM Anonymization",  response = String.class)
	@ApiResponses({
			@ApiResponse(code = 200, message = "Pre-Sync DICOM anonymization successfully configured."),
			@ApiResponse(code = 401, message = "User does not have required credentials to edit project anonymization."),
			@ApiResponse(code = 500, message = "Unexpected error")})
	public String addDICOMAnonymization(@PathVariable("projectId") String projectId,
														@RequestBody(required=false) String anonymizationScript) throws Exception {
		try {
	        XnatProjectdata project = XnatProjectdata.getProjectByIDorAlias(projectId, getSessionUser(), false);
            if (project == null) {
	        	return " Project ID " +  projectId +"  does not exist ";
            }
			saveDicomAnonymizationToConfig(project,anonymizationScript);
		} catch(Exception e) {
            log.error("ERROR:  Error saving pre-sync DICOM anonymization script:  {}", ExceptionUtils.getFullStackTrace(e));
        	throw new Exception(projectId + " Pre-Sync DICOM Anonymization script could not be saved. " );
		}
    	return projectId + " Pre-Sync anonymization saved";
	}

    @XapiRequestMapping(path="/presyncanonymization/projects/{projectId}", method = RequestMethod.GET,
			restrictTo = AccessLevel.Read)
	@ApiOperation(value = "GETs Pre-Sync project specific DICOM Anonymization",  response = String.class)
	@ApiResponses({@ApiResponse(code = 200, message = "Pre-Sync DICOM anonymization."),
			       @ApiResponse(code = 204, message = "No DICOM anonymization found."),
			       @ApiResponse(code = 401, message = "User does not have required credentials to access project."),
			       @ApiResponse(code = 500, message = "Unexpected error")})
	public String getDICOMAnonymization(@PathVariable("projectId") String projectId) throws Exception {
		try {
			Configuration config = _xsyncConfigService.getGenericXsyncConfiguration("presyncanonymization", projectId);
			return config == null ? "" : config.getContents();
		} catch(Exception e) {
            log.error("ERROR:  Error returning DICOM anonymization script:  {}", ExceptionUtils.getFullStackTrace(e));
			throw new Exception("Error obtaining DICOM anonymization script: " + ExceptionUtils.getFullStackTrace(e));
		}
	}

	private final XsyncSitePreferencesBean   _prefs;
	private final XsyncConfigurationService _xsyncConfigService;
	private final WhitelistXsyncSiteService  _whitelistXsyncSiteService;
	private final NamedParameterJdbcTemplate _jdbcTemplate;

	@ResponseStatus(value = HttpStatus.NOT_FOUND)
	@ExceptionHandler(value = {NotFoundException.class})
	public String handleElementNotFound(final Exception e) {
		return "Element not found: " + e.getMessage();
	}
}
