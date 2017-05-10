package org.nrg.xsync.xapi;

import org.apache.commons.lang3.StringUtils;
import org.nrg.framework.annotations.XapiRestController;
import org.nrg.xapi.rest.AbstractXapiProjectRestController;
import org.nrg.xdat.security.services.RoleHolder;
import org.nrg.xdat.security.services.UserManagementServiceI;
import org.nrg.xsync.components.elements.ProjectSyncStatus;
import org.nrg.xsync.exception.XsyncNoProjectEntitiesSpecifiedException;
import org.nrg.xsync.exception.XsyncNoProjectSpecifiedException;
import org.nrg.xsync.remote.alias.services.SyncStatusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

@Api(description = "XSync Entity State API")
@XapiRestController
@RequestMapping(value = "/xsync/syncStatus")
public class XsyncStatusController extends AbstractXapiProjectRestController {
	@Autowired
    public XsyncStatusController(final UserManagementServiceI userManagementService, final RoleHolder roleHolder, final SyncStatusService syncStatusService) {
        super(userManagementService, roleHolder);
        _syncStatusService = syncStatusService;
    }
	
    @ApiOperation(value = "Retrieves sync status information.", notes = "Returns sync status information for a project.", response = ProjectSyncStatus.class)
    @ApiResponses({@ApiResponse(code = 200, message = "OK"), @ApiResponse(code = 401, message = "Must be authenticated to access the XNAT REST API."), @ApiResponse(code = 403, message = "User not authorized to access indicated project."), @ApiResponse(code = 500, message = "Unexpected error")})
    @RequestMapping(value = "/projects/{projectId}", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<ProjectSyncStatus> getProjectInformation(@PathVariable("projectId") final String projectId) throws XsyncNoProjectSpecifiedException, XsyncNoProjectEntitiesSpecifiedException, Exception {
    	//final UserI user = getSessionUser();
    	final HttpStatus status = canReadProject(projectId);
        if (status != null) {
            return new ResponseEntity<>(status);
        }
    	if (StringUtils.isBlank(projectId)) {
            throw new XsyncNoProjectSpecifiedException();
        }
        return new ResponseEntity<>(_syncStatusService.getProjectSyncStatus(projectId), HttpStatus.OK);
    }

    //private static final Logger _log = LoggerFactory.getLogger(XsyncStatusController.class);
    private SyncStatusService _syncStatusService;

}