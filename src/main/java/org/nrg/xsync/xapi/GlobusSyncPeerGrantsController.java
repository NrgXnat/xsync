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
import org.nrg.xapi.rest.AbstractXapiRestController;
import org.nrg.xapi.rest.AuthDelegate;
import org.nrg.xapi.rest.XapiRequestMapping;
import org.nrg.xdat.security.helpers.AccessLevel;
import org.nrg.xdat.security.services.RoleHolder;
import org.nrg.xdat.security.services.UserManagementServiceI;
import org.nrg.xsync.globus.sync.GlobusSyncPeerGrantManager;
import org.nrg.xsync.pojo.GlobusSyncPeerGrantPojo;
import org.nrg.xsync.security.XsyncAdministratorUserAuthorization;
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
 * Site-level XAPI for managing the per-peer ACLs on this node's <em>sync</em>
 * inbox — the backend for the site-admin "Sync inbox access" panel. The per-peer,
 * site-scoped counterpart to the project-scoped
 * {@link GlobusUploadPermissionsController}.
 *
 * <p>All operations require the XSync administrator role (or site admin), via
 * {@link XsyncAdministratorUserAuthorization} — the same gate the site-level
 * Globus endpoint controller uses. ACL changes run under the node service
 * account, which must hold {@code access_manager} on the sync inbox collection.</p>
 *
 * @author XSync
 */
@Slf4j
@XapiRestController
@RequestMapping(value = "/xsync/globus")
@Api("XSync Globus Sync-Inbox Peer ACL API")
public class GlobusSyncPeerGrantsController extends AbstractXapiRestController {

    private final GlobusSyncPeerGrantManager _manager;

    @Autowired
    public GlobusSyncPeerGrantsController(final UserManagementServiceI userManagementService,
                                          final RoleHolder roleHolder,
                                          final GlobusSyncPeerGrantManager manager) {
        super(userManagementService, roleHolder);
        _manager = manager;
    }

    @AuthDelegate(XsyncAdministratorUserAuthorization.class)
    @XapiRequestMapping(value = "/sync-inbox-grants", method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE, restrictTo = AccessLevel.Authorizer)
    @ApiOperation(value = "List the peer grants on this node's sync inbox, reconciled with the live Globus ACLs "
            + "(ACTIVE/PENDING/ORPHANED) when reachable.")
    @ApiResponses({@ApiResponse(code = 200, message = "Grants returned."),
            @ApiResponse(code = 403, message = "Not authorized."),
            @ApiResponse(code = 500, message = "Unexpected error")})
    public List<GlobusSyncPeerGrantPojo> getSyncPeerGrants() {
        return _manager.list();
    }

    @AuthDelegate(XsyncAdministratorUserAuthorization.class)
    @XapiRequestMapping(value = "/sync-inbox-grants", method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE,
            restrictTo = AccessLevel.Authorizer)
    @ApiOperation(value = "Grant a peer client write access to this node's sync inbox: validates the client identity, "
            + "creates the collection ACL on the peer's subpath, and records the binding.")
    @ApiResponses({@ApiResponse(code = 200, message = "Grant created."),
            @ApiResponse(code = 400, message = "Missing fields, invalid or unresolvable client id, or duplicate grant."),
            @ApiResponse(code = 403, message = "Not authorized."),
            @ApiResponse(code = 404, message = "Globus not configured for this node."),
            @ApiResponse(code = 500, message = "Unexpected error")})
    public GlobusSyncPeerGrantPojo addSyncPeerGrant(@RequestBody final GlobusSyncPeerGrantPojo grant)
            throws NotFoundException, DataFormatException {
        return _manager.add(grant.getPeerLabel(), grant.getPeerClientId());
    }

    @AuthDelegate(XsyncAdministratorUserAuthorization.class)
    @XapiRequestMapping(value = "/sync-inbox-grants/{peerIdentityId}", method = RequestMethod.DELETE,
            restrictTo = AccessLevel.Authorizer)
    @ApiOperation(value = "Revoke a peer grant: deletes the Globus ACL and removes the record.")
    @ApiResponses({@ApiResponse(code = 200, message = "Grant removed."),
            @ApiResponse(code = 403, message = "Not authorized."),
            @ApiResponse(code = 404, message = "No such grant, or Globus not configured."),
            @ApiResponse(code = 500, message = "Unexpected error")})
    public void removeSyncPeerGrant(@PathVariable("peerIdentityId") final String peerIdentityId)
            throws NotFoundException, DataFormatException {
        _manager.remove(peerIdentityId);
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
