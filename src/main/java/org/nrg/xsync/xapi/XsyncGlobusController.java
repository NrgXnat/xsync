package org.nrg.xsync.xapi;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.nrg.framework.annotations.XapiRestController;
import org.nrg.xapi.exceptions.DataFormatException;
import org.nrg.xapi.exceptions.NotFoundException;
import org.nrg.xapi.rest.AbstractXapiRestController;
import org.nrg.xapi.rest.AuthDelegate;
import org.nrg.xapi.rest.XapiRequestMapping;
import org.nrg.xdat.security.helpers.AccessLevel;
import org.nrg.xdat.security.services.RoleHolder;
import org.nrg.xdat.security.services.UserManagementServiceI;
import org.nrg.xsync.globus.GlobusAuthException;
import org.nrg.xsync.globus.GlobusAuthService;
import org.nrg.xsync.globus.GlobusClient;
import org.nrg.xsync.globus.GlobusCredentials;
import org.nrg.xsync.globus.entities.GlobusEndpoint;
import org.nrg.xsync.globus.entities.GlobusNodeConfig;
import org.nrg.xsync.globus.services.GlobusEndpointService;
import org.nrg.xsync.globus.services.GlobusNodeConfigService;
import org.nrg.xsync.pojo.GlobusEndpointPojo;
import org.nrg.xsync.pojo.GlobusNodeConfigPojo;
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
 * Site-level management of Globus endpoints (WS2 of the XNAT Data Import &amp;
 * Synchronization proposal): configure the endpoints XSync may transfer to/from,
 * with their confidential-client credentials, and test connectivity.
 *
 * <p>All operations require the Xsync Administrator role (or site admin), via
 * {@link XsyncAdministratorUserAuthorization}. Client secrets are write-only:
 * they are accepted on create/update but never returned.</p>
 *
 * @author XSync
 */
@Slf4j
@XapiRestController
@RequestMapping(value = "/xsync/globus")
@Api("XSync Globus Endpoint Management API")
public class XsyncGlobusController extends AbstractXapiRestController {

    private final GlobusEndpointService   _endpointService;
    private final GlobusNodeConfigService _nodeConfigService;
    private final GlobusAuthService       _authService;
    private final GlobusClient            _globusClient;

    @Autowired
    public XsyncGlobusController(final UserManagementServiceI userManagementService, final RoleHolder roleHolder,
                                 final GlobusEndpointService endpointService,
                                 final GlobusNodeConfigService nodeConfigService,
                                 final GlobusAuthService authService, final GlobusClient globusClient) {
        super(userManagementService, roleHolder);
        _endpointService = endpointService;
        _nodeConfigService = nodeConfigService;
        _authService = authService;
        _globusClient = globusClient;
    }

    @AuthDelegate(XsyncAdministratorUserAuthorization.class)
    @XapiRequestMapping(value = "/endpoints", method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE, restrictTo = AccessLevel.Authorizer)
    @ApiOperation(value = "List all configured Globus endpoints (secrets omitted).")
    @ApiResponses({@ApiResponse(code = 200, message = "Endpoints returned."),
            @ApiResponse(code = 403, message = "Not authorized."),
            @ApiResponse(code = 500, message = "Unexpected error")})
    public List<GlobusEndpointPojo> getEndpoints() {
        return _endpointService.getAllEndpoints().stream().map(XsyncGlobusController::toPojo).collect(Collectors.toList());
    }

    @AuthDelegate(XsyncAdministratorUserAuthorization.class)
    @XapiRequestMapping(value = "/endpoints/{name}", method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE, restrictTo = AccessLevel.Authorizer)
    @ApiOperation(value = "Get a configured Globus endpoint by name (secret omitted).")
    @ApiResponses({@ApiResponse(code = 200, message = "Endpoint returned."),
            @ApiResponse(code = 403, message = "Not authorized."),
            @ApiResponse(code = 404, message = "Endpoint not found."),
            @ApiResponse(code = 500, message = "Unexpected error")})
    public GlobusEndpointPojo getEndpoint(@PathVariable("name") final String name) throws NotFoundException {
        return toPojo(_endpointService.getByName(name));
    }

    @AuthDelegate(XsyncAdministratorUserAuthorization.class)
    @XapiRequestMapping(value = "/endpoints", method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE,
            restrictTo = AccessLevel.Authorizer)
    @ApiOperation(value = "Create a Globus destination, or update the one with the same name.")
    @ApiResponses({@ApiResponse(code = 200, message = "Endpoint saved."),
            @ApiResponse(code = 400, message = "Missing required fields."),
            @ApiResponse(code = 403, message = "Not authorized."),
            @ApiResponse(code = 500, message = "Unexpected error")})
    public GlobusEndpointPojo saveEndpoint(@RequestBody final GlobusEndpointPojo pojo) throws DataFormatException {
        return toPojo(_endpointService.createOrUpdate(toEntity(pojo)));
    }

    @AuthDelegate(XsyncAdministratorUserAuthorization.class)
    @XapiRequestMapping(value = "/endpoints/{name}", method = RequestMethod.DELETE,
            restrictTo = AccessLevel.Authorizer)
    @ApiOperation(value = "Delete a configured Globus endpoint.")
    @ApiResponses({@ApiResponse(code = 200, message = "Endpoint deleted."),
            @ApiResponse(code = 403, message = "Not authorized."),
            @ApiResponse(code = 404, message = "Endpoint not found."),
            @ApiResponse(code = 500, message = "Unexpected error")})
    public void deleteEndpoint(@PathVariable("name") final String name) throws NotFoundException {
        _endpointService.delete(name);
    }

    @AuthDelegate(XsyncAdministratorUserAuthorization.class)
    @XapiRequestMapping(value = "/endpoints/{name}/test", method = RequestMethod.POST,
            produces = MediaType.APPLICATION_JSON_VALUE, restrictTo = AccessLevel.Authorizer)
    @ApiOperation(value = "Test a destination: obtain a Globus token with the node service account and probe the "
            + "destination's inbox collection. Returns true if the token is obtained and the collection resolves.")
    @ApiResponses({@ApiResponse(code = 200, message = "Test performed; body is the boolean result."),
            @ApiResponse(code = 403, message = "Not authorized."),
            @ApiResponse(code = 404, message = "Endpoint not found, or Globus not configured for this node."),
            @ApiResponse(code = 500, message = "Unexpected error")})
    public boolean testEndpoint(@PathVariable("name") final String name) throws NotFoundException {
        final GlobusEndpoint endpoint = _endpointService.getByName(name);
        final GlobusCredentials credentials = _nodeConfigService.getServiceCredentials();
        return testConnection(credentials, endpoint.getInboxCollectionId());
    }

    @AuthDelegate(XsyncAdministratorUserAuthorization.class)
    @XapiRequestMapping(value = "/node", method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE, restrictTo = AccessLevel.Authorizer)
    @ApiOperation(value = "Get this node's Globus configuration (service account + outbox); secret omitted. "
            + "Returns an empty configuration if none is set yet.")
    @ApiResponses({@ApiResponse(code = 200, message = "Node configuration returned."),
            @ApiResponse(code = 403, message = "Not authorized."),
            @ApiResponse(code = 500, message = "Unexpected error")})
    public GlobusNodeConfigPojo getNodeConfig() {
        return _nodeConfigService.getConfig().map(XsyncGlobusController::toNodePojo).orElseGet(GlobusNodeConfigPojo::new);
    }

    @AuthDelegate(XsyncAdministratorUserAuthorization.class)
    @XapiRequestMapping(value = "/node", method = {RequestMethod.POST, RequestMethod.PUT},
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE,
            restrictTo = AccessLevel.Authorizer)
    @ApiOperation(value = "Set this node's Globus configuration. A blank secret on update leaves the stored one unchanged.")
    @ApiResponses({@ApiResponse(code = 200, message = "Node configuration saved."),
            @ApiResponse(code = 400, message = "Missing required fields."),
            @ApiResponse(code = 403, message = "Not authorized."),
            @ApiResponse(code = 500, message = "Unexpected error")})
    public GlobusNodeConfigPojo saveNodeConfig(@RequestBody final GlobusNodeConfigPojo pojo) throws DataFormatException {
        return toNodePojo(_nodeConfigService.save(toNodeEntity(pojo)));
    }

    /**
     * Test a Globus connection: obtain a fresh Transfer token with the node
     * service credentials, then probe each supplied collection for reachability.
     *
     * <p>Returns {@code true} only if the token is obtained and no collection is
     * {@link GlobusClient.Reachability#NOT_FOUND} (a bad UUID) or
     * {@link GlobusClient.Reachability#ERROR}. A {@code FORBIDDEN} probe is
     * tolerated: a collection reached over a per-peer subpath ACL legitimately
     * denies a root listing. So a green result shows the account authenticates
     * and the UUIDs resolve, not that every ACL is correct.</p>
     *
     * @param credentials   the node service-account credentials
     * @param collectionIds collection UUIDs to probe (blanks ignored)
     * @return {@code true} if the connection test passes
     */
    private boolean testConnection(final GlobusCredentials credentials, final String... collectionIds) {
        final String token;
        try {
            token = _authService.getTransferToken(credentials, true);
        } catch (GlobusAuthException e) {
            log.info("Globus connection test failed for client {}: {}", credentials.clientId(), e.getMessage());
            return false;
        }
        return Stream.of(collectionIds)
                .filter(StringUtils::isNotBlank)
                .map(id -> _globusClient.probeCollection(token, id))
                .noneMatch(r -> r == GlobusClient.Reachability.NOT_FOUND || r == GlobusClient.Reachability.ERROR);
    }

    private static GlobusEndpointPojo toPojo(final GlobusEndpoint endpoint) {
        return new GlobusEndpointPojo(endpoint.getName(), endpoint.getInboxCollectionId(),
                endpoint.getRemoteInboxPath(), endpoint.getRemoteInboxServerDirectory());
    }

    private static GlobusEndpoint toEntity(final GlobusEndpointPojo pojo) {
        return new GlobusEndpoint(pojo.getName(), pojo.getInboxCollectionId(),
                pojo.getRemoteInboxPath(), pojo.getRemoteInboxServerDirectory());
    }

    private static GlobusNodeConfigPojo toNodePojo(final GlobusNodeConfig config) {
        return new GlobusNodeConfigPojo(config.getClientId(), null, config.getOutboxCollectionId(),
                config.getOutboxDirectory(), config.getInboxCollectionId());
    }

    private static GlobusNodeConfig toNodeEntity(final GlobusNodeConfigPojo pojo) {
        return new GlobusNodeConfig(pojo.getClientId(), pojo.getClientSecret(), pojo.getOutboxCollectionId(),
                pojo.getOutboxDirectory(), pojo.getInboxCollectionId());
    }

    @ResponseStatus(value = HttpStatus.NOT_FOUND)
    @ExceptionHandler(value = {NotFoundException.class})
    public String handleNotFound(final Exception e) {
        return "Globus endpoint not found: " + e.getMessage();
    }

    @ResponseStatus(value = HttpStatus.BAD_REQUEST)
    @ExceptionHandler(value = {DataFormatException.class})
    public String handleDataFormat(final Exception e) {
        return "Incorrect data format: " + e.getMessage();
    }
}
