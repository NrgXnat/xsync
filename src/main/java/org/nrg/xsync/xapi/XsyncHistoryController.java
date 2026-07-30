package org.nrg.xsync.xapi;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.annotations.ApiParam;
import org.nrg.framework.annotations.XapiRestController;
import org.nrg.xapi.exceptions.NoContentException;
import org.nrg.xapi.exceptions.NotFoundException;
import org.nrg.xapi.rest.AbstractXapiProjectRestController;
import org.nrg.xapi.rest.AuthDelegate;
import org.nrg.xapi.rest.XapiRequestMapping;
import org.nrg.xdat.om.XnatSubjectdata;
import org.nrg.xdat.security.helpers.AccessLevel;
import org.nrg.xdat.security.services.RoleHolder;
import org.nrg.xdat.security.services.UserManagementServiceI;
import org.nrg.xft.security.UserI;
import org.nrg.xsync.manifest.history.XsyncProjectHistory;
import org.nrg.xsync.pojo.history.XsyncProjectHistoryPojo;
import org.nrg.xsync.security.XsyncAdministratorUserAuthorization;
import org.nrg.xsync.services.local.SyncManifestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * The Class XsyncHistoryController.
 * Created by Michael Hileman on 2016/07/05.
 * @author Atul
 */
@Api("Xsync History API")
@XapiRestController
@RequestMapping(value="/xsync/history")
@JsonIgnoreProperties(value = { "created" })
public class XsyncHistoryController extends AbstractXapiProjectRestController {
	@Autowired
	public XsyncHistoryController(final SyncManifestService syncManifestService,
                                  final UserManagementServiceI userManagementService,
                                  final RoleHolder roleHolder) {
        super(userManagementService, roleHolder);
        this.syncManifestService = syncManifestService;
    }
	
    @ApiOperation(value="Get the complete list of history elements.")
    @ApiResponses({
            @ApiResponse(code=200, message="Returned history elements."),
            @ApiResponse(code=401, message="History elements not found."),
            @ApiResponse(code=403, message="Insufficient permissions to obtain all history data."),
            @ApiResponse(code=500, message="Unexpected error")
    })
    @AuthDelegate(XsyncAdministratorUserAuthorization.class)
    @XapiRequestMapping(method=RequestMethod.GET, restrictTo = AccessLevel.Authorizer, produces = {MediaType.APPLICATION_JSON_VALUE})
    public List<XsyncProjectHistoryPojo> getAllSyncHistory() {
        List<XsyncProjectHistoryPojo> allHistoryPojos = new ArrayList<>();
        for (XsyncProjectHistory history : syncManifestService.getAll()) {
            allHistoryPojos.add(mapper.convertValue(history, XsyncProjectHistoryPojo.class));
        }
        return allHistoryPojos;
    }

    @ApiOperation(value="Get a specific history element from a project's history record.")
    @ApiResponses({
            @ApiResponse(code=200, message="Returned history element."),
            @ApiResponse(code=401, message="History element not found."),
            @ApiResponse(code=403, message="Insufficient permissions to obtain history element."),
            @ApiResponse(code=500, message="Unexpected error")
    })
    @XapiRequestMapping(method=RequestMethod.GET, value="/projects/{projectId}/{id}", restrictTo = AccessLevel.Read,
            produces = {MediaType.APPLICATION_JSON_VALUE})
    public XsyncProjectHistoryPojo getSyncHistoryById(
            @ApiParam(value = "Project id.", required = true) @PathVariable("projectId") final String projectId,
            @ApiParam(value = "Id of requested history item.", required = true)@PathVariable("id") final long id) {
        return mapper.convertValue(syncManifestService.retrieve(id), XsyncProjectHistoryPojo.class);
    }

    @ApiOperation(value="Get xsync history for project.")
    @ApiResponses({
            @ApiResponse(code=200, message="Returned history elements."),
            @ApiResponse(code=401, message="History data not found."),
            @ApiResponse(code=403, message="Insufficient permissions to obtain history elements."),
            @ApiResponse(code=500, message="Unexpected error")
    })
    @XapiRequestMapping(value="/projects/{projectId}", method=RequestMethod.GET, restrictTo = AccessLevel.Read,
            produces = {MediaType.APPLICATION_JSON_VALUE})
    public List<XsyncProjectHistoryPojo> getSyncHistoryByProject(
            @ApiParam(value = "Project id.", required = true) @PathVariable("projectId") String projectId) {
    	List<XsyncProjectHistory> allHistory = syncManifestService.getAll();
        List<XsyncProjectHistoryPojo> filteredHistory = new ArrayList<>();

        for (XsyncProjectHistory history : allHistory) {
            if (history.getLocalProject().equals(projectId)) {
                filteredHistory.add(mapper.convertValue(history, XsyncProjectHistoryPojo.class));
            }
        }
        return filteredHistory;
    }

    @ApiOperation(value="Get history elements from last 3 months for project.")
    @ApiResponses({
            @ApiResponse(code=200, message="Returned history elements."),
            @ApiResponse(code=401, message="History data not found."),
            @ApiResponse(code=403, message="Insufficient permissions to obtain history elements."),
            @ApiResponse(code=500, message="Unexpected error")
    })
    @XapiRequestMapping(value="/projects/{projectId}/recentHistory", method=RequestMethod.GET, restrictTo =
            AccessLevel.Read, produces = {MediaType.APPLICATION_JSON_VALUE})
    public List<XsyncProjectHistoryPojo> getRecentSyncHistoryForProject(
            @ApiParam(value = "Project id.", required = true) @PathVariable("projectId") String projectId) {
        List<XsyncProjectHistory> allHistory = syncManifestService.getAll();
        List<XsyncProjectHistoryPojo> filteredHistory = new ArrayList<>();

        for (XsyncProjectHistory history : allHistory) {
            LocalDate startLocalDate = LocalDate.ofInstant(history.getStartDate().toInstant(), ZoneId.systemDefault());
            if (history.getLocalProject().equals(projectId) && startLocalDate.isAfter(LocalDate.now().minusMonths(3))) {
                filteredHistory.add(mapper.convertValue(history, XsyncProjectHistoryPojo.class));
            }
        }
        return filteredHistory;
    }

    @ApiOperation(value="Get xsync history for specific subject.")
    @ApiResponses({
            @ApiResponse(code=200, message="Returned the history item"),
            @ApiResponse(code=204, message="No history data found."),
            @ApiResponse(code=401, message="No such subject."),
            @ApiResponse(code=403, message="Insufficient permissions to obtain history data."),
            @ApiResponse(code=404, message="Input subject label does not exist within this project."),
            @ApiResponse(code=500, message="Unexpected error")
    })
    @XapiRequestMapping(value="/latest/projects/{projectId}/subjects/{subjectLabel}", method=RequestMethod.GET,
            restrictTo = AccessLevel.Read, produces = {MediaType.APPLICATION_JSON_VALUE})
    public XsyncProjectHistoryPojo getSubjectHistoryElement(
            @ApiParam(value = "Project id.", required = true) @PathVariable("projectId") String projectId,
            @ApiParam(value = "Subject label.", required = true)@PathVariable("subjectLabel") String subjectLabel) throws NotFoundException, NoContentException {
        final UserI user = getSessionUser();
        XnatSubjectdata subject  = XnatSubjectdata.GetSubjectByProjectIdentifier(projectId, subjectLabel, user, false);
        if (subject != null) {
            XsyncProjectHistory latest = syncManifestService.findMostRecentBySubject(projectId, subjectLabel);
            if (latest == null) {
                throw new NoContentException("No history elements found for subject: " + subjectLabel);
            }
            return mapper.convertValue(latest, XsyncProjectHistoryPojo.class);
        }
        throw new NotFoundException("Subject label {} not found.", subjectLabel);
    }

    @ApiOperation(value = "Get the stack trace for a failed sync." )
    @ApiResponses({
            @ApiResponse(code=200, message="Obtained stack trace."),
            @ApiResponse(code=403, message="User unauthorized to obtain failure information."),
            @ApiResponse(code=404, message="Configuration data not found."),
            @ApiResponse(code=500, message="Unexpected error")
    })
    @XapiRequestMapping(value = "{projectId}/failure", method = RequestMethod.GET,
            produces = {MediaType.TEXT_PLAIN_VALUE}, restrictTo = AccessLevel.Read)
    public String getFailureStackTrace(
            @ApiParam(value = "Project id.", required = true) @PathVariable("projectId") final String projectId,
            @ApiParam(value = "The input url.", required = true) @RequestParam String remoteUrl) throws NotFoundException {
        return syncManifestService.getStacktraceForFailedSync(remoteUrl, projectId);
    }

    private final SyncManifestService syncManifestService;
    private final ObjectMapper mapper = new ObjectMapper();

    @ResponseStatus(value = HttpStatus.NO_CONTENT)
    @ExceptionHandler(value = {NoContentException.class})
    public String handleNoContentException(final Exception e) {
        return "Element not found: " + e.getMessage();
    }
    @ResponseStatus(value = HttpStatus.NOT_FOUND)
    @ExceptionHandler(value = {NotFoundException.class})
    public String handleElementNotFound(final Exception e) {
        return "Element not found: " + e.getMessage();
    }
}
