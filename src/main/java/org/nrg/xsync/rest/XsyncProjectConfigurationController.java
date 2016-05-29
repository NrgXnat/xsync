package org.nrg.xsync.rest;

import io.swagger.annotations.Api;
import org.nrg.framework.annotations.XapiRestController;
import org.nrg.xdat.rest.AbstractXnatRestApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * @author Mohana Ramaratnam
 *
 */

@Api(description = "Xsync Project Configuration")
@XapiRestController
@RequestMapping(value = "/xsync/setup")

public class XsyncProjectConfigurationController extends AbstractXnatRestApi {

    @RequestMapping(value = "{project}", produces = {MediaType.APPLICATION_JSON_VALUE}, method = RequestMethod.PUT)
    public ResponseEntity<Void> updateEntity(@PathVariable final String id, @RequestBody final SampleEntity entity) {
        final SampleEntity existing = _service.findBySampleId(id);
        existing.setSampleId(entity.getSampleId());
        existing.setMessage(entity.getMessage());
        _service.update(existing);
        return new ResponseEntity<>(HttpStatus.OK);
    }

}
