package org.nrg.xsync.xapi;

import java.util.List;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.extern.slf4j.Slf4j;
import org.nrg.framework.annotations.XapiRestController;
import org.nrg.xapi.exceptions.DataFormatException;
import org.nrg.xapi.exceptions.NotFoundException;
import org.nrg.xapi.rest.AbstractXapiProjectRestController;
import org.nrg.xapi.rest.AuthDelegate;
import org.nrg.xapi.rest.XapiRequestMapping;
import org.nrg.xdat.security.helpers.AccessLevel;
import org.nrg.xdat.security.services.RoleHolder;
import org.nrg.xdat.security.services.UserManagementServiceI;
import org.nrg.xsync.globus.upload.GlobusUploadPermissionManager;
import org.nrg.xsync.pojo.GlobusUploadGrantPojo;
import org.nrg.xsync.security.XsyncEditProjectUserAuthority;
import org.nrg.xsync.security.XsyncReadProjectUserAuthority;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Project-scoped XAPI for managing per-user Globus upload permissions — the
 * backend for the project's "Globus Upload" management tab.
 *
 * <p>Listing requires project read access; add/remove requires project edit
 * access (the project-edit authority XSync already defines, which can be
 * tightened to owner-level later). All Globus ACL changes run under the node
 * service account, so XNAT admins do not need Globus identities.</p>
 *
 * @author XSync
 */
@Slf4j
@XapiRestController
@RequestMapping(value = "/xsync/globus")
@Api("XSync Globus Upload Permissions API")
public class GlobusUploadPermissionsController extends AbstractXapiProjectRestController {

    private final GlobusUploadPermissionManager _manager;

    @Autowired
    public GlobusUploadPermissionsController(final UserManagementServiceI userManagementService,
                                             final RoleHolder roleHolder,
                                             final GlobusUploadPermissionManager manager) {
        super(userManagementService, roleHolder);
        _manager = manager;
    }

    @AuthDelegate(XsyncReadProjectUserAuthority.class)
    @XapiRequestMapping(value = "/projects/{projectId}/upload-grants", method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE, restrictTo = AccessLevel.Authorizer)
    @ApiOperation(value = "List a project's Globus upload permissions, reconciled with the live Globus ACLs "
            + "(ACTIVE/PENDING/ORPHANED) when reachable.")
    @ApiResponses({@ApiResponse(code = 200, message = "Grants returned."),
            @ApiResponse(code = 403, message = "Not authorized."),
            @ApiResponse(code = 500, message = "Unexpected error")})
    public List<GlobusUploadGrantPojo> getUploadGrants(@PathVariable("projectId") final String projectId) {
        return _manager.list(projectId);
    }

    @AuthDelegate(XsyncEditProjectUserAuthority.class)
    @XapiRequestMapping(value = "/projects/{projectId}/eligible-users", method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE, restrictTo = AccessLevel.Authorizer)
    @ApiOperation(value = "List the project's edit-capable users (owners and members) — the candidates who may be "
            + "granted Globus upload permission.")
    @ApiResponses({@ApiResponse(code = 200, message = "Usernames returned."),
            @ApiResponse(code = 403, message = "Not authorized."),
            @ApiResponse(code = 500, message = "Unexpected error")})
    public List<String> getEligibleUsers(@PathVariable("projectId") final String projectId) {
        return _manager.eligibleUsers(projectId);
    }

    @AuthDelegate(XsyncEditProjectUserAuthority.class)
    @XapiRequestMapping(value = "/projects/{projectId}/upload-grants", method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE,
            restrictTo = AccessLevel.Authorizer)
    @ApiOperation(value = "Grant a Globus identity upload access as an XNAT user: resolves the identity, creates the "
            + "collection ACL, and records the binding.")
    @ApiResponses({@ApiResponse(code = 200, message = "Grant created."),
            @ApiResponse(code = 400, message = "Missing fields, unresolvable identity, or duplicate grant."),
            @ApiResponse(code = 403, message = "Not authorized."),
            @ApiResponse(code = 404, message = "Globus not configured for this node."),
            @ApiResponse(code = 500, message = "Unexpected error")})
    public GlobusUploadGrantPojo addUploadGrant(@PathVariable("projectId") final String projectId,
                                                @RequestBody final GlobusUploadGrantPojo grant)
            throws NotFoundException, DataFormatException {
        return _manager.add(projectId, grant.getGlobusUsername(), grant.getXnatUsername());
    }

    @AuthDelegate(XsyncEditProjectUserAuthority.class)
    @XapiRequestMapping(value = "/projects/{projectId}/upload-grants/{identityId}", method = RequestMethod.DELETE,
            restrictTo = AccessLevel.Authorizer)
    @ApiOperation(value = "Revoke an upload grant: deletes the Globus ACL and removes the record. Does not affect "
            + "already-imported data.")
    @ApiResponses({@ApiResponse(code = 200, message = "Grant removed."),
            @ApiResponse(code = 403, message = "Not authorized."),
            @ApiResponse(code = 404, message = "No such grant, or Globus not configured."),
            @ApiResponse(code = 500, message = "Unexpected error")})
    public void removeUploadGrant(@PathVariable("projectId") final String projectId,
                                  @PathVariable("identityId") final String identityId)
            throws NotFoundException, DataFormatException {
        _manager.remove(projectId, identityId);
    }

    @ResponseStatus(value = HttpStatus.NOT_FOUND)
    @ExceptionHandler(value = {NotFoundException.class})
    public String handleNotFound(final Exception e) {
        return e.getMessage();
    }

    @ResponseStatus(value = HttpStatus.BAD_REQUEST)
    @ExceptionHandler(value = {DataFormatException.class})
    public String handleDataFormat(final Exception e) {
        return "Incorrect data format: " + e.getMessage();
    }
}
