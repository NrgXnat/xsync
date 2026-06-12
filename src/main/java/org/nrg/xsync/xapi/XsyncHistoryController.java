package org.nrg.xsync.xapi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.nrg.framework.annotations.XapiRestController;
import org.nrg.xapi.rest.AbstractXapiProjectRestController;
import org.nrg.xapi.rest.XapiRequestMapping;
import org.nrg.xdat.om.XnatSubjectdata;
import org.nrg.xdat.security.helpers.AccessLevel;
import org.nrg.xdat.security.services.RoleHolder;
import org.nrg.xdat.security.services.UserManagementServiceI;
import org.nrg.xft.security.UserI;
import org.nrg.xsync.components.XsyncSitePreferencesBean;
import org.nrg.xsync.manifest.XsyncProjectHistory;
import org.nrg.xsync.pojo.XsyncRemoteUrlDetailsPojo;
import org.nrg.xsync.services.local.SyncManifestService;
import org.nrg.xsync.services.local.WhitelistXsyncSiteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

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
                                  final RoleHolder roleHolder,
                                  final WhitelistXsyncSiteService whitelistXsyncSiteService,
                                  final XsyncSitePreferencesBean xsyncSitePreferencesBean) {
        super(userManagementService, roleHolder);
        this.syncManifestService = syncManifestService;
        this.whitelistXsyncSiteService = whitelistXsyncSiteService;
        this.xsyncSitePreferencesBean = xsyncSitePreferencesBean;
    }
	
    /**
     * Gets the all sync history.
     *
     * @return the all sync history
     */
    @ApiOperation(value="History of Xsync transactions", response=String.class)
    @ApiResponses({
            @ApiResponse(code=200, message="OK"),
            @ApiResponse(code=401, message="Not Found")
    })
    @XapiRequestMapping(method=RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<List<XsyncProjectHistory>> getAllSyncHistory() {
    	final HttpStatus status = isPermitted();
        if (status != null) {
            return new ResponseEntity<>(status);
        }    	
        return new ResponseEntity<>(syncManifestService.getAll(), HttpStatus.OK);
    }

    /**
     * Gets all sync history elements filtered by the remote url they are sending data to..
     *
     * @return the sync history
     */
    @ApiOperation(value="History of xsync transactions filtered by remote url.", response=String.class)
    @ApiResponses({
            @ApiResponse(code=200, message="Obtained the history elements."),
            @ApiResponse(code=401, message="History data not found"),
            @ApiResponse(code=500, message="Unexpected error")
    })
    @XapiRequestMapping(method=RequestMethod.GET,  value="/remoteUrls", produces = {MediaType.APPLICATION_JSON_VALUE},
            restrictTo = AccessLevel.Admin)
    public ResponseEntity<List<XsyncRemoteUrlDetailsPojo>> getAllHistoryByRemoteUrl() {
        if (xsyncSitePreferencesBean.getXsyncWhitelistEnabled()) {
            return new ResponseEntity<>(syncManifestService.findRemoteUrlDetails(true,
                                         whitelistXsyncSiteService.getAllWhitelistedSites()), HttpStatus.OK);
        } else {
            return new ResponseEntity<>(syncManifestService.findRemoteUrlDetails(false,
                                         Collections.emptyList()), HttpStatus.OK);
        }

    }

    /**
     * Gets the sync history by id.
     *
     * @param projectId the project id
     * @param id the id
     * @return the sync history by id
     * @throws Exception the exception
     */
    @XapiRequestMapping(method=RequestMethod.GET, value="/projects/{projectId}/{id}")
    @ResponseBody
    public ResponseEntity<XsyncProjectHistory> getSyncHistoryById(@PathVariable("projectId") final String projectId,@PathVariable("id") final long id) throws Exception {
    	final HttpStatus status = canReadProject(projectId);
        if (status != null) {
            return new ResponseEntity<>(status);
        }
        return new ResponseEntity<>(syncManifestService.retrieve(id), HttpStatus.OK);
    }

    /**
     * Gets the sync history by project.
     *
     * @param projectId the project id
     * @return the sync history by project
     * @throws Exception the exception
     */
    @XapiRequestMapping(value="/projects/{projectId}", method=RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<List<XsyncProjectHistory>> getSyncHistoryByProject(@PathVariable("projectId") String projectId) throws Exception {
    	final UserI user = getSessionUser();
    	final HttpStatus status = canReadProject(projectId);
        if (status != null) {
            return new ResponseEntity<>(status);
        }
    	List<XsyncProjectHistory> allHistory = syncManifestService.getAll();
        List<XsyncProjectHistory> filteredHistory = new ArrayList<>();

        for (XsyncProjectHistory history : allHistory) {
            if (history.getLocalProject().equals(projectId)) {
                filteredHistory.add(history);
            }
        }
        return new ResponseEntity<>(filteredHistory, HttpStatus.OK);
    }

    /**
     * Gets the most recent sync history by project and subject label.
     *
     * @param projectId the project id
     * @param subjectLabel the subject label
     * @return the most recent sync history by project
     * @throws Exception the exception
     */
    @XapiRequestMapping(value="/latest/projects/{projectId}/subjects/{subjectLabel}", method=RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<XsyncProjectHistory> getMostRecentSyncHistoryByProject(@PathVariable("projectId") String projectId, @PathVariable("subjectLabel") String subjectLabel) throws Exception {
        final UserI user = getSessionUser();
        final HttpStatus status = canReadProject(projectId);
        if (status != null) {
            return new ResponseEntity<>(status);
        }

        XnatSubjectdata subject  = XnatSubjectdata.GetSubjectByProjectIdentifier(projectId, subjectLabel, user, false);
        if (subject != null) {
            XsyncProjectHistory latest = syncManifestService.findMostRecentBySubject(projectId, subjectLabel);
            if (latest == null) {
              return new ResponseEntity("{}",HttpStatus.OK);
            }
            return new ResponseEntity(latest, HttpStatus.OK);
        }
        return new ResponseEntity("Subject identified by " + subjectLabel + " does not exist", HttpStatus.BAD_REQUEST);
    }

    /** The service. */
    private final SyncManifestService syncManifestService;
    private final WhitelistXsyncSiteService whitelistXsyncSiteService;
    private final XsyncSitePreferencesBean xsyncSitePreferencesBean;
}
