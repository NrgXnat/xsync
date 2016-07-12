package org.nrg.xsync.xapi;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.nrg.framework.annotations.XapiRestController;
import org.nrg.xdat.rest.AbstractXapiRestController;
import org.nrg.xsync.manifest.SyncManifestHistory;
import org.nrg.xsync.services.local.SyncManifestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Created by Michael Hileman on 2016/07/05.
 */


@Api(description="Xsync Manifest API")
@XapiRestController
@RequestMapping(value="/xsync/manifest")
public class XsyncManifestController extends AbstractXapiRestController {

    @ApiOperation(value="Gets the Xsync Manifest", response=String.class)
    @ApiResponses({
            @ApiResponse(code=200, message="OK"),
            @ApiResponse(code=401, message="Not Found")
    })

    @RequestMapping(method=RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<List<SyncManifestHistory>> getAllSyncHistory() {
        return new ResponseEntity<>(_service.getAll(), HttpStatus.OK);
    }

//    @RequestMapping(method=RequestMethod.GET, value="{date}")
//    @ResponseBody
//    public ResponseEntity<SyncManifestHistory> getSyncHistoryByDate(@PathVariable final long date) {
//        return new ResponseEntity<>(_service.retrieve(date), HttpStatus.OK);
//    }

    @RequestMapping(method=RequestMethod.GET, value="{id}")
    @ResponseBody
    public ResponseEntity<SyncManifestHistory> getSyncHistoryById(@PathVariable final long id) {
        return new ResponseEntity<>(_service.retrieve(id), HttpStatus.OK);
    }

//    @RequestMapping(produces = {MediaType.APPLICATION_JSON_VALUE}, method = RequestMethod.POST)
//    public ResponseEntity<SyncManifestHistory> createEntity(@RequestBody final SyncManifestHistory entity) {
//    public ResponseEntity<SyncManifestHistory> createHistoryRecord() {
//        SyncManifestHistory syncRecord = new SyncManifestHistory();
//
//        syncRecord.setStartDate(new Date());
//        syncRecord.setCompleteDate(new Date());
//        syncRecord.setRemoteProject("test 2");
//        syncRecord.setSubjectCount(20);
//        syncRecord.setResourcesCount(100);
//
//        _service.create(syncRecord);
//
//        return new ResponseEntity<>(syncRecord, HttpStatus.OK);
//    }

    @Autowired
    public SyncManifestService _service;
}