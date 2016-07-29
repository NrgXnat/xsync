package org.nrg.xsync.xapi;

import org.nrg.xsync.exception.XsyncNoProjectEntitiesSpecifiedException;
import org.nrg.xsync.exception.XsyncNoProjectSpecifiedException;
import org.nrg.xsync.exception.XsyncNotConfiguredException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class XsyncControllerAdvice {
    @SuppressWarnings("UnusedParameters")
    @ExceptionHandler(XsyncNotConfiguredException.class)
    public ResponseEntity<String> handleXsyncNotConfiguredException(final XsyncNotConfiguredException exception) {
        return new ResponseEntity<>("Xsync is not configured on this server.", HttpStatus.SERVICE_UNAVAILABLE);
    }

    @SuppressWarnings("UnusedParameters")
    @ExceptionHandler(XsyncNoProjectSpecifiedException.class)
    public ResponseEntity<String> handleXsyncNoProjectSpecifiedException(final XsyncNoProjectSpecifiedException exception) {
        return new ResponseEntity<>("No project was specified in your REST call.", HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(XsyncNoProjectEntitiesSpecifiedException.class)
    public ResponseEntity<String> handleXsyncNoProjectEntitiesSpecifiedException(final XsyncNoProjectEntitiesSpecifiedException exception) {
        return new ResponseEntity<>("No entity specified for project " + exception.getProjectId() + " for which project specific information is required", HttpStatus.BAD_REQUEST);
    }
}
