package org.nrg.xsync.xapi;

import java.util.Properties;

import org.nrg.framework.annotations.XapiRestController;
import org.nrg.framework.exceptions.NrgServiceException;
import org.nrg.xapi.rest.AbstractXapiRestController;
import org.nrg.xapi.rest.XapiRequestMapping;
import org.nrg.xdat.security.services.RoleHolder;
import org.nrg.xdat.security.services.UserManagementServiceI;
import org.nrg.xsync.configuration.XsyncSitePreferencesBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
@RequestMapping(value = "/xsyncSitePreferences")
@Api(description = "XSync Preferences API")
@SuppressWarnings("unused")
public class XsyncPreferencesController extends AbstractXapiRestController {
	@Autowired
	public XsyncPreferencesController(final  XsyncSitePreferencesBean prefs,final UserManagementServiceI userManagementService, final RoleHolder roleHolder) {
        super(userManagementService, roleHolder);
        _prefs = prefs;
	}

	/**
	 * Sets the preferences.
	 *
	 * @param jsonbody the jsonbody
	 * @return the response entity
	 */
    @XapiRequestMapping(method = RequestMethod.POST, consumes = MediaType.TEXT_PLAIN_VALUE)
    @ApiOperation(value = "Sets the XSync site preferences")
    @ApiResponses({@ApiResponse(code = 200, message = "XSync site preferences set."), @ApiResponse(code = 500, message = "Unexpected error")})
	public ResponseEntity<String> setPreferences(@RequestBody String jsonbody) {
		try {
			final HttpStatus status = isPermitted();
	        if (status != null) {
	            return new ResponseEntity<>(status);
	        }    	
			//Store the JSON to the Synchronization table
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
		}catch (Exception exception) {
        	return new ResponseEntity<>("XSync preferences assignment failed ", HttpStatus.INTERNAL_SERVER_ERROR );
		}
       	return new ResponseEntity<>("XSync preferences set", HttpStatus.OK );
	}

	/**
	 * Gets the preferences.
	 *
	 * @return the preferences
	 */
    @XapiRequestMapping(method = RequestMethod.GET, produces = {MediaType.APPLICATION_JSON_VALUE})
    @ApiOperation(value = "Gets the XSync site preferences", response = Properties.class)
    @ApiResponses({@ApiResponse(code = 200, message = "XSync site preferences retrieved."),
				   @ApiResponse(code = 500, message = "Unexpected error")})
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

	private final XsyncSitePreferencesBean _prefs;
}
