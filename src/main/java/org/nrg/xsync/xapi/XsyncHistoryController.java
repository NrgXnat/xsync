package org.nrg.xsync.xapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.nrg.framework.annotations.XapiRestController;
import org.nrg.xdat.rest.AbstractXapiRestController;
import org.nrg.xdat.security.services.RoleHolder;
import org.nrg.xdat.security.services.UserManagementServiceI;
import org.nrg.xsync.manifest.XsyncProjectHistory;
import org.nrg.xsync.services.local.SyncManifestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Michael Hileman on 2016/07/05.
 *
 */


@Api(description="Xsync History API")
@XapiRestController
@RequestMapping(value="/xsync/history")
@JsonIgnoreProperties(value = { "created" })
public class XsyncHistoryController extends AbstractXapiRestController {
	@Autowired
	public XsyncHistoryController(final SyncManifestService service, final UserManagementServiceI userManagementService, final RoleHolder roleHolder) {
        super(userManagementService, roleHolder);
        _service = service;
	}

	
    @ApiOperation(value="History of Xsync transactions", response=String.class)
    @ApiResponses({
            @ApiResponse(code=200, message="OK"),
            @ApiResponse(code=401, message="Not Found")
    })

    @RequestMapping(method=RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<List<XsyncProjectHistory>> getAllSyncHistory() {
        return new ResponseEntity<>(_service.getAll(), HttpStatus.OK);
    }

    @RequestMapping(method=RequestMethod.GET, value="{id}")
    @ResponseBody
    public ResponseEntity<XsyncProjectHistory> getSyncHistoryById(@PathVariable final long id) {
        return new ResponseEntity<>(_service.retrieve(id), HttpStatus.OK);
    }

    @RequestMapping(value="/projects/{projectId}", method=RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<List<XsyncProjectHistory>> getSyncHistoryByProject(@PathVariable("projectId") String projectId) {
        List<XsyncProjectHistory> allHistory = _service.getAll();
        List<XsyncProjectHistory> filteredHistory = new ArrayList<>();

        for (XsyncProjectHistory history : allHistory) {
            if (history.getLocalProject().equals(projectId)) {
                filteredHistory.add(history);
            }
        }
        return new ResponseEntity<>(filteredHistory, HttpStatus.OK);
    }

    private final SyncManifestService _service;
}