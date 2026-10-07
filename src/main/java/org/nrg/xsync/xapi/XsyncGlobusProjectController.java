package org.nrg.xsync.xapi;

import java.util.List;
import java.util.stream.Collectors;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.nrg.framework.annotations.XapiRestController;
import org.nrg.xapi.rest.AbstractXapiProjectRestController;
import org.nrg.xapi.rest.AuthDelegate;
import org.nrg.xapi.rest.XapiRequestMapping;
import org.nrg.xdat.security.helpers.AccessLevel;
import org.nrg.xdat.security.services.RoleHolder;
import org.nrg.xdat.security.services.UserManagementServiceI;
import org.nrg.xsync.globus.GlobusProjectPrefs;
import org.nrg.xsync.globus.entities.GlobusEndpoint;
import org.nrg.xsync.globus.services.GlobusEndpointService;
import org.nrg.xsync.pojo.GlobusProjectConfigPojo;
import org.nrg.xsync.security.XsyncDeleteProjectUserAuthority;
import org.nrg.xsync.security.XsyncReadProjectUserAuthority;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * Minimal project-scoped XAPI to read and set a project's Globus transfer
 * configuration ({@link GlobusProjectPrefs}). This is the configuration seam
 * for the first-pass Globus send path; a project UI is a later increment.
 *
 * <p>Read requires project read access; write requires project edit access
 * (the same authorities XSync setup uses).</p>
 *
 * @author XSync
 */
@Slf4j
@XapiRestController
@RequestMapping(value = "/xsync/globus")
@Api("XSync Globus Project Configuration API")
public class XsyncGlobusProjectController extends AbstractXapiProjectRestController {

    private final GlobusProjectPrefs _prefs;
    private final GlobusEndpointService _endpointService;

    @Autowired
    public XsyncGlobusProjectController(final UserManagementServiceI userManagementService, final RoleHolder roleHolder,
                                        final GlobusProjectPrefs prefs, final GlobusEndpointService endpointService) {
        super(userManagementService, roleHolder);
        _prefs = prefs;
        _endpointService = endpointService;
    }

    @AuthDelegate(XsyncReadProjectUserAuthority.class)
    @XapiRequestMapping(value = "/projects/{projectId}/config", method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE, restrictTo = AccessLevel.Authorizer)
    @ApiOperation(value = "Get a project's Globus transfer configuration.")
    @ApiResponses({@ApiResponse(code = 200, message = "Configuration returned."),
            @ApiResponse(code = 403, message = "Not authorized."),
            @ApiResponse(code = 500, message = "Unexpected error")})
    public ResponseEntity<GlobusProjectConfigPojo> getConfig(@PathVariable("projectId") final String projectId) {
        return new ResponseEntity<>(toPojo(projectId), HttpStatus.OK);
    }

    @AuthDelegate(XsyncReadProjectUserAuthority.class)
    @XapiRequestMapping(value = "/projects/{projectId}/endpointNames", method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE, restrictTo = AccessLevel.Authorizer)
    @ApiOperation(value = "List the names of registered Globus endpoints, for a project owner to choose one. "
            + "Returns names only (no client credentials).")
    @ApiResponses({@ApiResponse(code = 200, message = "Endpoint names returned."),
            @ApiResponse(code = 403, message = "Not authorized."),
            @ApiResponse(code = 500, message = "Unexpected error")})
    public List<String> getEndpointNames(@PathVariable("projectId") final String projectId) {
        return _endpointService.getAllEndpoints().stream()
                .map(GlobusEndpoint::getName)
                .collect(Collectors.toList());
    }

    @AuthDelegate(XsyncDeleteProjectUserAuthority.class)
    @XapiRequestMapping(value = "/projects/{projectId}/config", method = {RequestMethod.POST, RequestMethod.PUT},
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE,
            restrictTo = AccessLevel.Authorizer)
    @ApiOperation(value = "Set a project's Globus transfer configuration; returns the saved configuration.")
    @ApiResponses({@ApiResponse(code = 200, message = "Configuration saved."),
            @ApiResponse(code = 403, message = "Not authorized."),
            @ApiResponse(code = 500, message = "Unexpected error")})
    public ResponseEntity<GlobusProjectConfigPojo> setConfig(@PathVariable("projectId") final String projectId,
                                                             @RequestBody final GlobusProjectConfigPojo config) {
        _prefs.setGlobusEnabled(projectId, Boolean.TRUE.equals(config.getGlobusEnabled()));
        _prefs.setGlobusEndpointName(projectId, StringUtils.defaultString(config.getGlobusEndpointName()));
        return new ResponseEntity<>(toPojo(projectId), HttpStatus.OK);
    }

    private GlobusProjectConfigPojo toPojo(final String projectId) {
        return new GlobusProjectConfigPojo(
                _prefs.getGlobusEnabled(projectId),
                _prefs.getGlobusEndpointName(projectId));
    }
}
