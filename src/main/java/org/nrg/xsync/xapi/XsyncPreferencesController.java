package org.nrg.xsync.xapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.json.JSONObject;
import org.nrg.framework.annotations.XapiRestController;
import org.nrg.xdat.rest.AbstractXapiRestController;
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

/**
 * The Class XsyncPreferencesController.
 *
 * @author Mike Hodge
 */

@XapiRestController
@RequestMapping(value = "/xsyncSitePreferences")
@Api(description = "XSync Preferences API")
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
	@RequestMapping(method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "Sets the XSync site preferences")
    @ApiResponses({@ApiResponse(code = 200, message = "XSync site preferences set."), @ApiResponse(code = 500, message = "Unexpected error")})
	public ResponseEntity<String> setPreferences(@RequestBody String jsonbody) {
		try {
			//Store the JSON to the Synchronization table
			final ObjectMapper objectMapper = new ObjectMapper();
			final JsonNode synchronizationJson = objectMapper.readValue(jsonbody, JsonNode.class);
	        final String tokenRefreshInterval = synchronizationJson.get("tokenRefreshInterval").asText();
	        _prefs.setTokenRefreshInterval(tokenRefreshInterval);
		}catch (Exception  exception) {
        	return new ResponseEntity<>("XSync preferences assignment failed ", HttpStatus.INTERNAL_SERVER_ERROR );
		}
       	return new ResponseEntity<>("XSync preferences set", HttpStatus.OK );
	}

	/**
	 * Gets the preferences.
	 *
	 * @return the preferences
	 */
	@RequestMapping(method = RequestMethod.GET, produces = {MediaType.APPLICATION_JSON_VALUE})
    @ApiOperation(value = "Gets the XSync site preferences")
    @ApiResponses({@ApiResponse(code = 200, message = "XSync site preferences retrieved."),  @ApiResponse(code = 500, message = "Unexpected error")})
	public ResponseEntity<String> getPreferences() {
		try {
			final JSONObject prefsJson = new JSONObject();
			prefsJson.put("tokenRefreshInterval", _prefs.getTokenRefreshInterval());
        	return new ResponseEntity<>(prefsJson.toString(), HttpStatus.OK );
		}catch (Exception  exception) {
        	return new ResponseEntity<>("XSync preferences assignment failed ", HttpStatus.INTERNAL_SERVER_ERROR );
		}
	}

	private final XsyncSitePreferencesBean _prefs;
}
