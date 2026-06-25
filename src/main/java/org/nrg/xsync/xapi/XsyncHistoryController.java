package org.nrg.xsync.xapi;

import java.util.ArrayList;
import java.util.List;

import io.swagger.annotations.ApiParam;
import org.nrg.framework.annotations.XapiRestController;
import org.nrg.xapi.rest.AbstractXapiProjectRestController;
import org.nrg.xapi.rest.XapiRequestMapping;
import org.nrg.xdat.om.XnatSubjectdata;
import org.nrg.xdat.security.helpers.AccessLevel;
import org.nrg.xdat.security.services.RoleHolder;
import org.nrg.xdat.security.services.UserManagementServiceI;
import org.nrg.xft.security.UserI;
import org.nrg.xsync.manifest.XsyncProjectHistory;
import org.nrg.xsync.services.local.SyncManifestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;


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
    @XapiRequestMapping(method=RequestMethod.GET, restrictTo = AccessLevel.Admin, produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<List<XsyncProjectHistory>> getAllSyncHistory() {
        return new ResponseEntity<>(syncManifestService.getAll(), HttpStatus.OK);
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
    public ResponseEntity<XsyncProjectHistory> getSyncHistoryById(
            @ApiParam(value = "Project id.", required = true) @PathVariable("projectId") final String projectId,
            @ApiParam(value = "Id of requested history item.", required = true)@PathVariable("id") final long id) {
        return new ResponseEntity<>(syncManifestService.retrieve(id), HttpStatus.OK);
    }

    @ApiOperation(value="Get xsync history for project.")
    @ApiResponses({
            @ApiResponse(code=200, message="Returned history elements."),
            @ApiResponse(code=401, message="History element not found."),
            @ApiResponse(code=403, message="Insufficient permissions to obtain history elements."),
            @ApiResponse(code=500, message="Unexpected error")
    })
    @XapiRequestMapping(value="/projects/{projectId}", method=RequestMethod.GET, restrictTo = AccessLevel.Read,
            produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<List<XsyncProjectHistory>> getSyncHistoryByProject(
            @ApiParam(value = "Project id.", required = true) @PathVariable("projectId") String projectId) {
    	List<XsyncProjectHistory> allHistory = syncManifestService.getAll();
        List<XsyncProjectHistory> filteredHistory = new ArrayList<>();

        for (XsyncProjectHistory history : allHistory) {
            if (history.getLocalProject().equals(projectId)) {
                filteredHistory.add(history);
            }
        }
        return new ResponseEntity<>(filteredHistory, HttpStatus.OK);
    }

    @ApiOperation(value="Get xsync history for specific subject.")
    @ApiResponses({
            @ApiResponse(code=200, message="Returned the history item"),
            @ApiResponse(code=401, message="The input subject has no history entries."),
            @ApiResponse(code=403, message="Insufficient permissions to obtain history data."),
            @ApiResponse(code=404, message="Input subject label does not exist within this project."),
            @ApiResponse(code=500, message="Unexpected error")
    })
    @XapiRequestMapping(value="/latest/projects/{projectId}/subjects/{subjectLabel}", method=RequestMethod.GET,
            restrictTo = AccessLevel.Read, produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<XsyncProjectHistory> getMostRecentSyncHistoryByProject(
            @ApiParam(value = "Project id.", required = true) @PathVariable("projectId") String projectId,
            @ApiParam(value = "Subject label.", required = true)@PathVariable("subjectLabel") String subjectLabel)  {
        final UserI user = getSessionUser();

        XnatSubjectdata subject  = XnatSubjectdata.GetSubjectByProjectIdentifier(projectId, subjectLabel, user, false);
        if (subject != null) {
            XsyncProjectHistory latest = syncManifestService.findMostRecentBySubject(projectId, subjectLabel);
            if (latest == null) {
              return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }
            return new ResponseEntity<>(latest, HttpStatus.OK);
        }
        return new ResponseEntity<>( HttpStatus.BAD_REQUEST);
    }

    /** The service. */
    private final SyncManifestService syncManifestService;
}
