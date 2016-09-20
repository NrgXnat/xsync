package org.nrg.xsync.xapi;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.apache.commons.lang3.StringUtils;
import org.nrg.framework.annotations.XapiRestController;
import org.nrg.xdat.rest.AbstractXapiRestController;
import org.nrg.xdat.security.services.RoleHolder;
import org.nrg.xdat.security.services.UserManagementServiceI;
import org.nrg.xsync.discoverer.ProjectInformation;
import org.nrg.xsync.exception.XsyncNoProjectEntitiesSpecifiedException;
import org.nrg.xsync.exception.XsyncNoProjectSpecifiedException;
import org.nrg.xsync.utils.QueryResultUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.*;

@Api(description = "XSync Entity State API")
@XapiRestController
@RequestMapping(value = "/xsync/information")
public class XsyncEntityStateController extends AbstractXapiRestController {
    @Autowired
    public XsyncEntityStateController(final UserManagementServiceI userManagementService, final RoleHolder roleHolder, final QueryResultUtil queryResultUtil, final JdbcTemplate jdbcTemplate) {
        super(userManagementService, roleHolder);
        _queryResultUtil = queryResultUtil;
        _jdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    @ApiOperation(value = "Retrieves the information specified in the list parameter for the indicated project.", notes = "Returns the project information if available.", response = ObjectNode.class)
    @ApiResponses({@ApiResponse(code = 200, message = "The project information was successfully retrieved."), @ApiResponse(code = 401, message = "Must be authenticated to access the XNAT REST API."), @ApiResponse(code = 403, message = "User not authorized to access indicated project."), @ApiResponse(code = 500, message = "Unexpected error")})
    @RequestMapping(value = "/projects/{projectId}", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<ObjectNode> getProjectInformation(@PathVariable("projectId") final String projectId, @RequestParam("listChoices") final String listChoices) throws XsyncNoProjectSpecifiedException, XsyncNoProjectEntitiesSpecifiedException {
        if (StringUtils.isBlank(projectId)) {
            throw new XsyncNoProjectSpecifiedException();
        }
        if (StringUtils.isBlank(listChoices)) {
            throw new XsyncNoProjectEntitiesSpecifiedException(projectId);
        }

        if (_log.isDebugEnabled()) {
            _log.debug("Returning project information details for project " + projectId + ": " + listChoices);
        }
        final ObjectNode objectNode = (new ProjectInformation(_queryResultUtil, _jdbcTemplate, projectId)).getInformation(listChoices);
        return new ResponseEntity<>(objectNode, HttpStatus.OK);
    }

    private static final Logger _log = LoggerFactory.getLogger(XsyncEntityStateController.class);

    private final QueryResultUtil            _queryResultUtil;
    private final NamedParameterJdbcTemplate _jdbcTemplate;
}