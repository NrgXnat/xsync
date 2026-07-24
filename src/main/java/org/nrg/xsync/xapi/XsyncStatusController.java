package org.nrg.xsync.xapi;

import org.apache.commons.lang3.StringUtils;
import org.nrg.framework.annotations.XapiRestController;
import org.nrg.xapi.rest.AbstractXapiProjectRestController;
import org.nrg.xapi.rest.AuthDelegate;
import org.nrg.xapi.rest.XapiRequestMapping;
import org.nrg.xdat.security.helpers.AccessLevel;
import org.nrg.xdat.security.services.RoleHolder;
import org.nrg.xdat.security.services.UserManagementServiceI;
import org.nrg.xsync.components.elements.ProjectSyncStatus;
import org.nrg.xsync.exception.XsyncNoProjectSpecifiedException;
import org.nrg.xsync.remote.alias.services.SyncStatusService;
import org.nrg.xsync.security.XsyncReadProjectUserAuthority;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

@Api("XSync Entity State API")
@XapiRestController
@RequestMapping(value = "/xsync/syncStatus")
public class XsyncStatusController extends AbstractXapiProjectRestController {

	@Autowired
    public XsyncStatusController(final UserManagementServiceI userManagementService,
                                 final RoleHolder roleHolder,
                                 final SyncStatusService syncStatusService) {
        super(userManagementService, roleHolder);
        _syncStatusService = syncStatusService;
    }

    @AuthDelegate(XsyncReadProjectUserAuthority.class)
    @ApiOperation(value = "Retrieves sync status information.",
            notes = "Returns sync status information for a project.", response = ProjectSyncStatus.class)
    @ApiResponses({@ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 401, message = "Must be authenticated to access the XNAT REST API."),
            @ApiResponse(code = 403, message = "User not authorized to access indicated project."),
            @ApiResponse(code = 500, message = "Unexpected error")})
    @XapiRequestMapping(value = "/projects/{projectId}", produces = MediaType.APPLICATION_JSON_VALUE,
            method = RequestMethod.GET, restrictTo = AccessLevel.Authorizer)
    @ResponseBody
    public ProjectSyncStatus getProjectInformation(@PathVariable("projectId") final String projectId) throws Exception {
    	if (StringUtils.isBlank(projectId)) {
            throw new XsyncNoProjectSpecifiedException();
        }
        return _syncStatusService.getProjectSyncStatus(projectId);
    }

    private final SyncStatusService _syncStatusService;
}