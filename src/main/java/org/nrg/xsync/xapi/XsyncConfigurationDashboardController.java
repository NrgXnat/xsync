package org.nrg.xsync.xapi;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.nrg.framework.annotations.XapiRestController;
import org.nrg.xapi.exceptions.DataFormatException;
import org.nrg.xapi.exceptions.NotFoundException;
import org.nrg.xapi.rest.AbstractXapiRestController;
import org.nrg.xapi.rest.XapiRequestMapping;
import org.nrg.xdat.security.helpers.AccessLevel;
import org.nrg.xdat.security.services.RoleHolder;
import org.nrg.xdat.security.services.UserManagementServiceI;
import org.nrg.xft.security.UserI;
import org.nrg.xsync.components.XsyncSitePreferencesBean;
import org.nrg.xsync.manifest.history.XsyncProjectHistory;
import org.nrg.xsync.pojo.XsyncDashboardProjectConfigurationPojo;
import org.nrg.xsync.pojo.XsyncRemoteUrlDetailsPojo;
import org.nrg.xsync.services.local.ConfigurationDashboardService;
import org.nrg.xsync.services.local.SyncManifestService;
import org.nrg.xsync.services.local.WhitelistXsyncSiteService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.Collections;
import java.util.List;

@XapiRestController
@RequestMapping(value = "/xsync/dashboard")
@Api("API connection for admin configuration dashboard within XNAT")
public class XsyncConfigurationDashboardController extends AbstractXapiRestController {

    private final XsyncSitePreferencesBean _sitePreferences;
    private final SyncManifestService _syncManifestService;
    private final WhitelistXsyncSiteService _whitelistXsyncSiteService;
    private final ConfigurationDashboardService _configurationDashboardService;

    protected XsyncConfigurationDashboardController(final UserManagementServiceI userManagementService,
                                                    final RoleHolder roleHolder,
                                                    final XsyncSitePreferencesBean sitePreferences,
                                                    final SyncManifestService syncManifestService,
                                                    final WhitelistXsyncSiteService whitelistXsyncSiteService,
                                                    final ConfigurationDashboardService configurationDashboardService) {
        super(userManagementService, roleHolder);
        _sitePreferences = sitePreferences;
        _syncManifestService = syncManifestService;
        _whitelistXsyncSiteService = whitelistXsyncSiteService;
        _configurationDashboardService = configurationDashboardService;
    }

    @ApiOperation(value = "Get a report of all currently configured remote XNAT instances." )
    @ApiResponses({
            @ApiResponse(code=200, message="Obtained configuration data of remote XNAT instances."),
            @ApiResponse(code=403, message="User unauthorized to obtain configuration information."),
            @ApiResponse(code=404, message="Configuration data not found."),
            @ApiResponse(code=500, message="Unexpected error")
    })
    @XapiRequestMapping(method = RequestMethod.GET,
            produces = {MediaType.APPLICATION_JSON_VALUE}, restrictTo = AccessLevel.Admin)
    public List<XsyncRemoteUrlDetailsPojo> getAllXsyncConfigInformation() {
        final UserI user = getSessionUser();
        List<XsyncProjectHistory> allHistoryItems = _syncManifestService.getAll();
        if (_sitePreferences.getXsyncWhitelistEnabled()) {
            return _configurationDashboardService.createListOfRemoteDestinations(user,
              allHistoryItems, true, _whitelistXsyncSiteService.getAllWhitelistedSites());
        } else {
            return _configurationDashboardService.createListOfRemoteDestinations(user,
              allHistoryItems, false, Collections.emptyList());
        }
    }

    @ApiOperation(value = "Get a report of all xsync configurations that do not conform to whitelist." )
    @ApiResponses({
            @ApiResponse(code=200, message="Obtained configuration data of remote XNAT instances."),
            @ApiResponse(code=401, message="Whitelist not enabled."),
            @ApiResponse(code=403, message="User unauthorized to obtain configuration information."),
            @ApiResponse(code=404, message="Configuration data not found."),
            @ApiResponse(code=500, message="Unexpected error")
    })
    @XapiRequestMapping(value = "/whitelist", method = RequestMethod.GET,
            produces = {MediaType.APPLICATION_JSON_VALUE}, restrictTo = AccessLevel.Admin)
    public ResponseEntity<List<XsyncRemoteUrlDetailsPojo>> getAllNonConformingRemoteUrls() {
        if (!_sitePreferences.getXsyncWhitelistEnabled()) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        final UserI user = getSessionUser();
        List<XsyncProjectHistory> allHistoryItems = _syncManifestService.getAll();
        return new ResponseEntity<>(_configurationDashboardService.getAllNonWhitelistRemoteUrls(user,
            _whitelistXsyncSiteService.getAllWhitelistedSites(), allHistoryItems), HttpStatus.OK);
    }

    @ApiOperation(value = "Get a report of local projects connected to input remote url." )
    @ApiResponses({
            @ApiResponse(code=200, message="Obtained configuration data for input remote url."),
            @ApiResponse(code=403, message="User unauthorized to obtain configuration information."),
            @ApiResponse(code=404, message="Configuration data not found."),
            @ApiResponse(code=500, message="Unexpected error")
    })
    @XapiRequestMapping(value = "/remoteUrl", method = RequestMethod.GET,
            produces = {MediaType.APPLICATION_JSON_VALUE}, restrictTo = AccessLevel.Admin)
    public List<XsyncDashboardProjectConfigurationPojo> getSyncDetailsForRemoteUrl(
            @ApiParam(value = "The input url.", required = true) @RequestParam String remoteUrl) {
        return _configurationDashboardService.getAllProjectConnectionsForUrl(getSessionUser(),
                                                                             _syncManifestService.getAll(), remoteUrl);
    }

    @ResponseStatus(value = HttpStatus.NOT_FOUND)
    @ExceptionHandler(value = {NotFoundException.class})
    public String handleElementNotFound(final Exception e) {
        return "Element not found: " + e.getMessage();
    }
}
