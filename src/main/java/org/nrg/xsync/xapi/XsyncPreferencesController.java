package org.nrg.xsync.xapi;

import java.util.Properties;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.nrg.xft.exception.InvalidValueException;
import org.nrg.xsync.pojo.XsyncSitePreferencesPojo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.nrg.framework.annotations.XapiRestController;
import org.nrg.framework.exceptions.NrgServiceException;
import org.nrg.xapi.rest.AbstractXapiRestController;
import org.nrg.xapi.rest.XapiRequestMapping;
import org.nrg.xdat.security.helpers.AccessLevel;
import org.nrg.xdat.security.services.RoleHolder;
import org.nrg.xdat.security.services.UserManagementServiceI;
import org.nrg.xsync.aspera.AsperaProjectPrefs;
import org.nrg.xsync.aspera.AsperaProjectPrefsInfo;
import org.nrg.xsync.aspera.AsperaSitePrefs;
import org.nrg.xsync.aspera.AsperaSitePrefsInfo;
import org.nrg.xsync.components.XsyncSitePreferencesBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMethod;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * The Class XsyncPreferencesController.
 *
 * @author Mike Hodge
 */

@XapiRestController
@Api(description = "XSync Preferences API")
@SuppressWarnings("unused")
public class XsyncPreferencesController extends AbstractXapiRestController {

	@Autowired
	public XsyncPreferencesController(final XsyncSitePreferencesBean prefs, final AsperaSitePrefs asperaSitePrefs,
			final AsperaProjectPrefs asperaProjectPrefs, final UserManagementServiceI userManagementService,
			final RoleHolder roleHolder, final ObjectMapper objectMapper) {
		super(userManagementService, roleHolder);
		this.prefs = prefs;
		this.asperaSitePrefs = asperaSitePrefs;
		this.asperaProjectPrefs = asperaProjectPrefs;
		this.objectMapper = objectMapper;
	}

	/**
	 * Sets the preferences.
	 *
	 * @param jsonbody
	 *            the jsonbody
	 * @return the response entity
	 */
	@SuppressWarnings("deprecation")
	@XapiRequestMapping(value = "xsyncSitePreferences", method = RequestMethod.POST, consumes = MediaType.TEXT_PLAIN_VALUE, restrictTo = AccessLevel.Admin)
	@ApiOperation(value = "Sets the XSync site preferences")
	@ApiResponses({ @ApiResponse(code = 200, message = "XSync site preferences set."),
			@ApiResponse(code = 500, message = "Unexpected error") })
	public ResponseEntity<String> setPreferences(@RequestBody String jsonbody) {
		try {
			final XsyncSitePreferencesPojo xsyncSitePreferencesPojo = objectMapper.readValue(jsonbody, XsyncSitePreferencesPojo.class);
			prefs.update(xsyncSitePreferencesPojo);
		} catch (Exception exception) {
			return new ResponseEntity<>("XSync preferences assignment failed ", HttpStatus.INTERNAL_SERVER_ERROR);
		}
		return new ResponseEntity<>("XSync preferences set", HttpStatus.OK);
	}

	/**
	 * Gets the preferences.
	 *
	 * @return the preferences
	 */
	@SuppressWarnings("deprecation")
	@XapiRequestMapping(value = "xsyncSitePreferences", method = RequestMethod.GET, produces = {
			MediaType.APPLICATION_JSON_VALUE }, restrictTo = AccessLevel.Admin)
	@ApiOperation(value = "Gets the XSync site preferences", response = XsyncSitePreferencesPojo.class)
	@ApiResponses({ @ApiResponse(code = 200, message = "XSync site preferences retrieved."),
			@ApiResponse(code = 500, message = "Unexpected error") })
	public XsyncSitePreferencesPojo getPreferences()  {
		return prefs.toPojo();
	}

	/**
	 * Sets the preferences.
	 *
	 * @return the response entity
	 */
	@XapiRequestMapping(value = "xsyncSitePreferences/aspera", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, restrictTo = AccessLevel.Admin)
	@ApiOperation(value = "Sets the XSync site aspera preferences")
	@ApiResponses({ @ApiResponse(code = 200, message = "XSync site preferences set."),
			@ApiResponse(code = 500, message = "Unexpected error") })
	public ResponseEntity<String> setAsperaPreferences(@RequestBody AsperaSitePrefsInfo asperaPrefs) {
		try {
			asperaSitePrefs.setAsperaNodeUrl(asperaPrefs.getAsperaNodeUrl());
			asperaSitePrefs.setAsperaNodeUser(asperaPrefs.getAsperaNodeUser());
			asperaSitePrefs.setPrivateKey(asperaPrefs.getPrivateKey());
			asperaSitePrefs.setPrivateKey(asperaPrefs.getPrivateKey());
			asperaSitePrefs.setDestinationDirectory(asperaPrefs.getDestinationDirectory());
			asperaSitePrefs.setLogDirectory(asperaPrefs.getLogDirectory());
			asperaSitePrefs.setSshPort(asperaPrefs.getSshPort());
			asperaSitePrefs.setUdpPort(asperaPrefs.getUdpPort());
		} catch (Exception exception) {
			return new ResponseEntity<>("XSync preferences assignment failed ", HttpStatus.INTERNAL_SERVER_ERROR);
		}
		return new ResponseEntity<>("XSync preferences set", HttpStatus.OK);
	}

	/**
	 * Gets the preferences.
	 *
	 * @return the preferences
	 */
	@XapiRequestMapping(value = "xsyncSitePreferences/aspera", method = RequestMethod.GET, produces = {
			MediaType.APPLICATION_JSON_VALUE })
	@ApiOperation(value = "Gets the XSync site preferences", response = Properties.class)
	@ApiResponses({ @ApiResponse(code = 200, message = "XSync site Aspera preferences retrieved."),
			@ApiResponse(code = 500, message = "Unexpected error") })
	public ResponseEntity<AsperaSitePrefsInfo> getAsperaPreferences() throws NrgServiceException {
		return new ResponseEntity<>(new AsperaSitePrefsInfo(asperaSitePrefs), HttpStatus.OK);
	}

	/**
	 * Sets the preferences.
	 *
	 * @return the response entity
	 */
	@XapiRequestMapping(value = "xsyncProjectPreferences/project/{projectId}/aspera", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, restrictTo = AccessLevel.Owner)
	@ApiOperation(value = "Sets the XSync project aspera preferences")
	@ApiResponses({ @ApiResponse(code = 200, message = "XSync site preferences set."),
			@ApiResponse(code = 500, message = "Unexpected error") })
	public ResponseEntity<String> setAsperaProjectPreferences(@PathVariable("projectId") final String projectId,
			@RequestBody AsperaProjectPrefsInfo asperaPrefs) {
		try {
			asperaProjectPrefs.setAsperaEnabled(projectId, asperaPrefs.getAsperaEnabled());
			asperaProjectPrefs.setAsperaNodeUrl(projectId, asperaPrefs.getAsperaNodeUrl());
			asperaProjectPrefs.setAsperaNodeUser(projectId, asperaPrefs.getAsperaNodeUser());
			asperaProjectPrefs.setPrivateKey(projectId, asperaPrefs.getPrivateKey());
			asperaProjectPrefs.setPrivateKey(projectId, asperaPrefs.getPrivateKey());
			asperaProjectPrefs.setDestinationDirectory(projectId, asperaPrefs.getDestinationDirectory());
			asperaProjectPrefs.setLogDirectory(projectId, asperaPrefs.getLogDirectory());
			asperaProjectPrefs.setSshPort(projectId, asperaPrefs.getSshPort());
			asperaProjectPrefs.setUdpPort(projectId, asperaPrefs.getUdpPort());
		} catch (Exception exception) {
			_logger.error("ERROR:  Error setting preferences:  " + ExceptionUtils.getFullStackTrace(exception));
			return new ResponseEntity<>("XSync preferences assignment failed ", HttpStatus.INTERNAL_SERVER_ERROR);
		}
		return new ResponseEntity<>("XSync preferences set", HttpStatus.OK);
	}

	/**
	 * Gets the preferences.
	 *
	 * @return the preferences
	 */
	@XapiRequestMapping(value = "xsyncProjectPreferences/project/{projectId}/aspera", method = RequestMethod.GET, produces = {
			MediaType.APPLICATION_JSON_VALUE }, restrictTo = AccessLevel.Read)
	@ApiOperation(value = "Gets the XSync project preferences", response = Properties.class)
	@ApiResponses({ @ApiResponse(code = 200, message = "XSync site Aspera preferences retrieved."),
			@ApiResponse(code = 500, message = "Unexpected error") })
	public ResponseEntity<AsperaProjectPrefsInfo> getAsperaProjectPreferences(
			@PathVariable("projectId") final String projectId) throws NrgServiceException {
		final AsperaProjectPrefsInfo prefsInfo = new AsperaProjectPrefsInfo(asperaProjectPrefs, projectId);
		// Get site defaults, if project settings have not been configured
		if ((prefsInfo.getAsperaNodeUrl() == null || prefsInfo.getAsperaNodeUrl().length() < 1)
				&& (prefsInfo.getAsperaNodeUser() == null || prefsInfo.getAsperaNodeUser().length() < 1)
				&& (asperaSitePrefs.getAsperaNodeUrl() != null || asperaSitePrefs.getAsperaNodeUrl().length() > 0)
				&& (asperaSitePrefs.getAsperaNodeUser() != null || asperaSitePrefs.getAsperaNodeUser().length() > 0)) {
			_logger.warn("WARNING: Project Aspera preferences not found for project " + projectId + 
					".  Returning site preferences instead for project preference call.");
			prefsInfo.setAsperaEnabled(false);
			prefsInfo.setAsperaNodeUrl(asperaSitePrefs.getAsperaNodeUrl());
			prefsInfo.setAsperaNodeUser(asperaSitePrefs.getAsperaNodeUser());
			prefsInfo.setPrivateKey(asperaSitePrefs.getPrivateKey());
			prefsInfo.setPrivateKey(asperaSitePrefs.getPrivateKey());
			prefsInfo.setDestinationDirectory(asperaSitePrefs.getDestinationDirectory());
			prefsInfo.setLogDirectory(asperaSitePrefs.getLogDirectory());
			prefsInfo.setSshPort(asperaSitePrefs.getSshPort());
			prefsInfo.setUdpPort(asperaSitePrefs.getUdpPort());
		}
		return new ResponseEntity<>(prefsInfo, HttpStatus.OK);
	}

	private final XsyncSitePreferencesBean prefs;
	private final AsperaSitePrefs asperaSitePrefs;
	private final AsperaProjectPrefs asperaProjectPrefs;
	private final ObjectMapper   objectMapper;
	private static Logger _logger = LoggerFactory.getLogger(XsyncPreferencesController.class);
	

}
