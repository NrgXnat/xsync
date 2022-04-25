package org.nrg.xsync.xapi;

import java.util.Properties;

import org.apache.commons.lang.exception.ExceptionUtils;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.fasterxml.jackson.databind.JsonNode;
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
			final RoleHolder roleHolder) {
		super(userManagementService, roleHolder);
		_prefs = prefs;
		_asperaSitePrefs = asperaSitePrefs;
		_asperaProjectPrefs = asperaProjectPrefs;
	}

	/**
	 * Sets the preferences.
	 *
	 * @param jsonbody
	 *            the jsonbody
	 * @return the response entity
	 */
	@SuppressWarnings("deprecation")
	@XapiRequestMapping(value = "xsyncSitePreferences", method = RequestMethod.POST, consumes = MediaType.TEXT_PLAIN_VALUE)
	@ApiOperation(value = "Sets the XSync site preferences")
	@ApiResponses({ @ApiResponse(code = 200, message = "XSync site preferences set."),
			@ApiResponse(code = 500, message = "Unexpected error") })
	public ResponseEntity<String> setPreferences(@RequestBody String jsonbody) {
		try {
			final HttpStatus status = isPermitted();
			if (status != null) {
				return new ResponseEntity<>(status);
			}
			// Store the JSON to the Synchronization table
			final ObjectMapper objectMapper = new ObjectMapper();
			final JsonNode synchronizationJson = objectMapper.readValue(jsonbody, JsonNode.class);

			try {
				final String tokenRefreshInterval = synchronizationJson.get("tokenRefreshInterval").asText();
				_prefs.setTokenRefreshInterval(tokenRefreshInterval);
			} catch (NullPointerException e) {
				// Allow for blank field
			}
			try {
				final String syncRetryInterval = synchronizationJson.get("syncRetryInterval").asText();
				_prefs.setSyncRetryInterval(syncRetryInterval);
			} catch (NullPointerException e) {
				// Allow for blank field
			}
			try {
				final String syncRetryCount = synchronizationJson.get("syncRetryCount").asText();
				_prefs.setSyncRetryCount(syncRetryCount);
			} catch (NullPointerException e) {
				// Allow for blank field
			}
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
			MediaType.APPLICATION_JSON_VALUE })
	@ApiOperation(value = "Gets the XSync site preferences", response = Properties.class)
	@ApiResponses({ @ApiResponse(code = 200, message = "XSync site preferences retrieved."),
			@ApiResponse(code = 500, message = "Unexpected error") })
	public ResponseEntity<Properties> getPreferences() throws NrgServiceException {
		final HttpStatus status = isPermitted();
		if (status != null) {
			return new ResponseEntity<>(status);
		}
		try {
			final Properties preferences = new Properties();
			preferences.setProperty("tokenRefreshInterval", _prefs.getTokenRefreshInterval());
			preferences.setProperty("syncRetryInterval", _prefs.getSyncRetryInterval());
			preferences.setProperty("syncRetryCount", _prefs.getSyncRetryCount());
			return new ResponseEntity<>(preferences, HttpStatus.OK);
		} catch (Exception exception) {
			throw new NrgServiceException("XSync preferences assignment failed", exception);
		}
	}

	/**
	 * Sets the preferences.
	 *
	 * @param jsonbody
	 *            the jsonbody
	 * @return the response entity
	 */
	@XapiRequestMapping(value = "xsyncSitePreferences/aspera", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, restrictTo = AccessLevel.Admin)
	@ApiOperation(value = "Sets the XSync site aspera preferences")
	@ApiResponses({ @ApiResponse(code = 200, message = "XSync site preferences set."),
			@ApiResponse(code = 500, message = "Unexpected error") })
	public ResponseEntity<String> setAsperaPreferences(@RequestBody AsperaSitePrefsInfo asperaPrefs) {
		try {
			_asperaSitePrefs.setAsperaNodeUrl(asperaPrefs.getAsperaNodeUrl());
			_asperaSitePrefs.setAsperaNodeUser(asperaPrefs.getAsperaNodeUser());
			_asperaSitePrefs.setPrivateKey(asperaPrefs.getPrivateKey());
			_asperaSitePrefs.setPrivateKey(asperaPrefs.getPrivateKey());
			_asperaSitePrefs.setDestinationDirectory(asperaPrefs.getDestinationDirectory());
			_asperaSitePrefs.setLogDirectory(asperaPrefs.getLogDirectory());
			_asperaSitePrefs.setSshPort(asperaPrefs.getSshPort());
			_asperaSitePrefs.setUdpPort(asperaPrefs.getUdpPort());
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
		return new ResponseEntity<>(new AsperaSitePrefsInfo(_asperaSitePrefs), HttpStatus.OK);
	}

	/**
	 * Sets the preferences.
	 *
	 * @param jsonbody
	 *            the jsonbody
	 * @return the response entity
	 */
	@XapiRequestMapping(value = "xsyncProjectPreferences/project/{projectId}/aspera", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, restrictTo = AccessLevel.Owner)
	@ApiOperation(value = "Sets the XSync project aspera preferences")
	@ApiResponses({ @ApiResponse(code = 200, message = "XSync site preferences set."),
			@ApiResponse(code = 500, message = "Unexpected error") })
	public ResponseEntity<String> setAsperaProjectPreferences(@PathVariable("projectId") final String projectId,
			@RequestBody AsperaProjectPrefsInfo asperaPrefs) {
		try {
			_asperaProjectPrefs.setAsperaEnabled(projectId, asperaPrefs.getAsperaEnabled());
			_asperaProjectPrefs.setAsperaNodeUrl(projectId, asperaPrefs.getAsperaNodeUrl());
			_asperaProjectPrefs.setAsperaNodeUser(projectId, asperaPrefs.getAsperaNodeUser());
			_asperaProjectPrefs.setPrivateKey(projectId, asperaPrefs.getPrivateKey());
			_asperaProjectPrefs.setPrivateKey(projectId, asperaPrefs.getPrivateKey());
			_asperaProjectPrefs.setDestinationDirectory(projectId, asperaPrefs.getDestinationDirectory());
			_asperaProjectPrefs.setLogDirectory(projectId, asperaPrefs.getLogDirectory());
			_asperaProjectPrefs.setSshPort(projectId, asperaPrefs.getSshPort());
			_asperaProjectPrefs.setUdpPort(projectId, asperaPrefs.getUdpPort());
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
		final AsperaProjectPrefsInfo prefsInfo = new AsperaProjectPrefsInfo(_asperaProjectPrefs, projectId);
		// Get site defaults, if project settings have not been configured
		if ((prefsInfo.getAsperaNodeUrl() == null || prefsInfo.getAsperaNodeUrl().length() < 1)
				&& (prefsInfo.getAsperaNodeUser() == null || prefsInfo.getAsperaNodeUser().length() < 1)
				&& (_asperaSitePrefs.getAsperaNodeUrl() != null || _asperaSitePrefs.getAsperaNodeUrl().length() > 0)
				&& (_asperaSitePrefs.getAsperaNodeUser() != null || _asperaSitePrefs.getAsperaNodeUser().length() > 0)) {
			_logger.warn("WARNING: Project Aspera preferences not found for project " + projectId + 
					".  Returning site preferences instead for project preference call.");
			prefsInfo.setAsperaEnabled(false);
			prefsInfo.setAsperaNodeUrl(_asperaSitePrefs.getAsperaNodeUrl());
			prefsInfo.setAsperaNodeUser(_asperaSitePrefs.getAsperaNodeUser());
			prefsInfo.setPrivateKey(_asperaSitePrefs.getPrivateKey());
			prefsInfo.setPrivateKey(_asperaSitePrefs.getPrivateKey());
			prefsInfo.setDestinationDirectory(_asperaSitePrefs.getDestinationDirectory());
			prefsInfo.setLogDirectory(_asperaSitePrefs.getLogDirectory());
			prefsInfo.setSshPort(_asperaSitePrefs.getSshPort());
			prefsInfo.setUdpPort(_asperaSitePrefs.getUdpPort());
		}
		return new ResponseEntity<>(prefsInfo, HttpStatus.OK);
	}

	private final XsyncSitePreferencesBean _prefs;
	private final AsperaSitePrefs _asperaSitePrefs;
	private final AsperaProjectPrefs _asperaProjectPrefs;
	private static Logger _logger = LoggerFactory.getLogger(XsyncPreferencesController.class);
	

}
