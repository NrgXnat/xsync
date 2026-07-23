package org.nrg.xsync.xapi;

import java.util.List;
import java.util.Properties;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.lang3.StringUtils;
import org.nrg.xapi.exceptions.DataFormatException;
import org.nrg.xapi.exceptions.NotFoundException;
import org.nrg.xapi.rest.AuthDelegate;
import org.nrg.xft.exception.InvalidValueException;
import org.nrg.xsync.pojo.WhitelistSitePojo;
import org.nrg.xsync.pojo.XsyncSitePreferencesPojo;
import org.nrg.xsync.security.XsyncAdministratorUserAuthorization;
import org.nrg.xsync.services.local.WhitelistXsyncSiteService;
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
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestMethod;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * The Class XsyncPreferencesController.
 *
 * @author Mike Hodge
 */

@Slf4j
@XapiRestController
@Api("XSync Preferences API")
@SuppressWarnings("unused")
public class XsyncPreferencesController extends AbstractXapiRestController {

	@Autowired
	public XsyncPreferencesController(final XsyncSitePreferencesBean prefs, final AsperaSitePrefs asperaSitePrefs,
									  final AsperaProjectPrefs asperaProjectPrefs, final UserManagementServiceI userManagementService,
									  final RoleHolder roleHolder, final WhitelistXsyncSiteService whitelistXsyncSiteService) {
		super(userManagementService, roleHolder);
		this.prefs = prefs;
		this.asperaSitePrefs = asperaSitePrefs;
		this.asperaProjectPrefs = asperaProjectPrefs;
		this.whitelistXsyncSiteService = whitelistXsyncSiteService;
	}

	@AuthDelegate(XsyncAdministratorUserAuthorization.class)
	@XapiRequestMapping(value = "xsyncSitePreferences", method = RequestMethod.POST,
			consumes = MediaType.APPLICATION_JSON_VALUE, restrictTo = AccessLevel.Authorizer)
	@ApiOperation(value = "Sets the XSync site preferences")
	@ApiResponses({ @ApiResponse(code = 200, message = "XSync site preferences set."),
			@ApiResponse(code = 500, message = "Unexpected error") })
	public void setPreferences(@RequestBody XsyncSitePreferencesPojo xsyncSitePreferencesPojo)
			throws InvalidValueException {
		prefs.update(xsyncSitePreferencesPojo);
	}

	@AuthDelegate(XsyncAdministratorUserAuthorization.class)
	@XapiRequestMapping(value = "xsyncSitePreferences", method = RequestMethod.GET, produces = {
			MediaType.APPLICATION_JSON_VALUE }, restrictTo = AccessLevel.Authorizer)
	@ApiOperation(value = "Gets the XSync site preferences", response = XsyncSitePreferencesPojo.class)
	@ApiResponses({ @ApiResponse(code = 200, message = "XSync site preferences retrieved."),
			@ApiResponse(code = 500, message = "Unexpected error") })
	public XsyncSitePreferencesPojo getPreferences()  {
		return prefs.toPojo();
	}

	@XapiRequestMapping(value = "xsyncSitePreferences/aspera", method = RequestMethod.POST,
			consumes = MediaType.APPLICATION_JSON_VALUE, restrictTo = AccessLevel.Admin)
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

	@XapiRequestMapping(value = "xsyncSitePreferences/aspera", method = RequestMethod.GET, produces = {
			MediaType.APPLICATION_JSON_VALUE })
	@ApiOperation(value = "Gets the XSync site preferences", response = Properties.class)
	@ApiResponses({ @ApiResponse(code = 200, message = "XSync site Aspera preferences retrieved."),
			@ApiResponse(code = 500, message = "Unexpected error") })
	public ResponseEntity<AsperaSitePrefsInfo> getAsperaPreferences() {
		return new ResponseEntity<>(new AsperaSitePrefsInfo(asperaSitePrefs), HttpStatus.OK);
	}

	@XapiRequestMapping(value = "xsyncSitePreferences/httpsEnabled", method =
			RequestMethod.GET,	produces = MediaType.APPLICATION_JSON_VALUE)
	@ApiOperation(value = "Checks whether Https connection is enabled on the site level.")
	@ApiResponses({ @ApiResponse(code = 200, message = "Https enabled returned."),
			@ApiResponse(code = 500, message = "Unexpected error") })
	public Boolean getHttpsEnabled() {
		return prefs.getHttpsEnabled();
	}

	@XapiRequestMapping(value = "xsyncSitePreferences/asperaEnabled", method =
			RequestMethod.GET,	produces = MediaType.APPLICATION_JSON_VALUE)
	@ApiOperation(value = "Checks whether Aspera is enabled on the site level.")
	@ApiResponses({ @ApiResponse(code = 200, message = "Aspera enabled returned."),
			@ApiResponse(code = 500, message = "Unexpected error") })
	public Boolean getAsperaEnabled() {
		return prefs.getAsperaEnabled();
	}

	@XapiRequestMapping(value = "xsyncProjectPreferences/project/{projectId}/asperaEnabled", method =
			RequestMethod.GET,	produces = MediaType.APPLICATION_JSON_VALUE, restrictTo = AccessLevel.Read)
	@ApiOperation(value = "Checks whether Aspera is enabled for project.")
	@ApiResponses({ @ApiResponse(code = 200, message = "Aspera enabled returned."),
			@ApiResponse(code = 500, message = "Unexpected error") })
	public Boolean getProjectAsperaEnabled(@PathVariable("projectId") final String projectId) {
		final AsperaProjectPrefsInfo prefsInfo = new AsperaProjectPrefsInfo(asperaProjectPrefs, projectId);
		return prefsInfo.getAsperaEnabled();
	}

	@XapiRequestMapping(value = "xsyncProjectPreferences/project/{projectId}/aspera",
			method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, restrictTo = AccessLevel.Delete)
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
            log.error("ERROR:  Error setting preferences:  {}", ExceptionUtils.getFullStackTrace(exception));
			return new ResponseEntity<>("XSync preferences assignment failed ", HttpStatus.INTERNAL_SERVER_ERROR);
		}
		return new ResponseEntity<>("XSync preferences set", HttpStatus.OK);
	}

	@XapiRequestMapping(value = "xsyncProjectPreferences/project/{projectId}/aspera", method = RequestMethod.GET,
			produces = {MediaType.APPLICATION_JSON_VALUE }, restrictTo = AccessLevel.Read)
	@ApiOperation(value = "Gets the XSync project preferences", response = Properties.class)
	@ApiResponses({ @ApiResponse(code = 200, message = "XSync site Aspera preferences retrieved."),
			@ApiResponse(code = 500, message = "Unexpected error") })
	public ResponseEntity<AsperaProjectPrefsInfo> getAsperaProjectPreferences(
			@PathVariable("projectId") final String projectId) throws NrgServiceException {
		final AsperaProjectPrefsInfo prefsInfo = new AsperaProjectPrefsInfo(asperaProjectPrefs, projectId);
		// Get site defaults, if project settings have not been configured
		if (StringUtils.isAllBlank(prefsInfo.getAsperaNodeUrl(), prefsInfo.getAsperaNodeUser(),
								   asperaSitePrefs.getAsperaNodeUrl(), asperaSitePrefs.getAsperaNodeUser())) {
            log.warn("WARNING: Project Aspera preferences not found for project {}. " +
								 "Returning site preferences instead for project preference call.", projectId);
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

	@XapiRequestMapping(value = "xsyncProjectPreferences/whitelistEnabled", method =
			RequestMethod.GET,	produces = MediaType.APPLICATION_JSON_VALUE)
	@ApiOperation(value = "Checks whether site whitelist is enabled on the site level.")
	@ApiResponses({ @ApiResponse(code = 200, message = "Site whitelist enabled returned."),
			@ApiResponse(code = 500, message = "Unexpected error") })
	public Boolean getWhitelistEnabled() {
		return prefs.getXsyncWhitelistEnabled();
	}

	@XapiRequestMapping(value = "xsyncSitePreferences/whitelistSites", method = RequestMethod.GET,
			produces = {MediaType.APPLICATION_JSON_VALUE }, restrictTo = AccessLevel.Read)
	@ApiOperation(value = "Get the whitelist of sites allowed for syncing")
	@ApiResponses({ @ApiResponse(code = 200, message = "Xsync whitelist sites retrieved."),
			@ApiResponse(code = 500, message = "Unexpected error") })
	public ResponseEntity<List<WhitelistSitePojo>> getAllWhitelistSites () {
		return new ResponseEntity<>(whitelistXsyncSiteService.getAllWhitelistedSites(), HttpStatus.OK);
	}

	@AuthDelegate(XsyncAdministratorUserAuthorization.class)
	@XapiRequestMapping(value = "xsyncSitePreferences/whitelistSites/add", method = RequestMethod.POST,
			produces = {MediaType.APPLICATION_JSON_VALUE }, restrictTo = AccessLevel.Authorizer)
	@ApiOperation(value = "Add an XNAT site to the xsync whitelist or update if a site with that id exists.")
	@ApiResponses({ @ApiResponse(code = 200, message = "Whitelist site added or updated."),
			@ApiResponse(code = 500, message = "Unexpected error") })
	public ResponseEntity<List<WhitelistSitePojo>> addNewWhitelistSiteOrUpdate (@RequestBody WhitelistSitePojo whitelistSitePojo) throws DataFormatException {
		return new ResponseEntity<>(whitelistXsyncSiteService.addOrUpdateWhitelistSiteFromSiteAdmin(whitelistSitePojo), HttpStatus.OK);
	}

	@AuthDelegate(XsyncAdministratorUserAuthorization.class)
	@XapiRequestMapping(value = "xsyncSitePreferences/whitelistSites/delete", method = RequestMethod.DELETE, produces = {
			MediaType.APPLICATION_JSON_VALUE }, restrictTo = AccessLevel.Authorizer)
	@ApiOperation(value = "Delete a site from the xsync whitelist.")
	@ApiResponses({ @ApiResponse(code = 200, message = "Whitelist site deleted."),
			@ApiResponse(code = 500, message = "Unexpected error") })
	public ResponseEntity<List<WhitelistSitePojo>> deleteWhitelistSite (@RequestBody WhitelistSitePojo whitelistSitePojo) throws DataFormatException, NotFoundException {
		return new ResponseEntity<>(whitelistXsyncSiteService.deleteWhitelistSiteFromSiteAdmin(whitelistSitePojo), HttpStatus.OK);
	}

	@XapiRequestMapping(value = "xsyncSitePreferences/blacklistSites", method = RequestMethod.GET, produces = {
			MediaType.APPLICATION_JSON_VALUE }, restrictTo = AccessLevel.Read)
	@ApiOperation(value = "Get the blacklist of sites not allowed for syncing")
	@ApiResponses({ @ApiResponse(code = 200, message = "Xsync blacklist sites retrieved."),
			@ApiResponse(code = 500, message = "Unexpected error") })
	public List<String> getAllBlacklistSites () {
		return prefs.getSitesBlacklist();
	}

	private final XsyncSitePreferencesBean prefs;
	private final AsperaSitePrefs asperaSitePrefs;
	private final AsperaProjectPrefs asperaProjectPrefs;
	private final WhitelistXsyncSiteService whitelistXsyncSiteService;

	@ResponseStatus(value = HttpStatus.BAD_REQUEST)
	@ExceptionHandler(value = {InvalidValueException.class})
	public String handleBadRequest(final Exception e) {
		return "Cannot set Xsync preference: " + e.getMessage();
	}

	@ResponseStatus(value = HttpStatus.BAD_REQUEST)
	@ExceptionHandler(value = {NotFoundException.class})
	public String handleDataNotFound(final Exception e) {
		return "Data not found: " + e.getMessage();
	}

	@ResponseStatus(value = HttpStatus.BAD_REQUEST)
	@ExceptionHandler(value = {DataFormatException.class})
	public String handleDataFormat(final Exception e) {
		return "Incorrect data format: " + e.getMessage();
	}
}
