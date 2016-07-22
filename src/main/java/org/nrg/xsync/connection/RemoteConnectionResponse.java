package org.nrg.xsync.connection;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * @author Mohana Ramaratnam
 *
 */
public class RemoteConnectionResponse {

	ResponseEntity<String> _response;
	
	public RemoteConnectionResponse(ResponseEntity<String> response) {
		_response = response;
	}
	
	public boolean wasSuccessful() {
		return 	((_response.getStatusCode().value()==HttpStatus.OK.value()) || (_response.getStatusCode().value()==HttpStatus.CREATED.value()))?true:false;
	}
	
	public String getResponseBody() {
		return _response.getBody();
	}
	
	public List<String> getResponseHeader(String name) {
		return _response.getHeaders().get(name);
	}
	
	public ResponseEntity<String> getResponse() {
		return _response;
	}
}
