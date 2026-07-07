package org.nrg.xsync.connection;

import java.nio.charset.StandardCharsets;
import java.util.List;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * @author Mohana Ramaratnam
 *
 */
@Slf4j
public class RemoteConnectionResponse {

	HttpStatus status = null;
	@Getter
    String responseBody;

	public RemoteConnectionResponse(final CloseableHttpResponse  response) {
		if (response != null) {
			status = HttpStatus.valueOf(response.getStatusLine().getStatusCode());
			try {
				responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
			} catch(Exception e) {
				log.error("Could not extract responseBody {}", e.getMessage());
			}
		}
	}

	public RemoteConnectionResponse(final ResponseEntity<String> response) {
		if (response != null) {
			status = response.getStatusCode();
			responseBody = response.getBody();
		}
	}

	public boolean wasSuccessful() {
		if (null == status)
			return false;
		return 	((status.value() == HttpStatus.OK.value()) || (status.value() == HttpStatus.CREATED.value())) ? true : false;
	}

    public ResponseEntity<String> getResponse() {
		return new ResponseEntity<>(responseBody, status);
	}
}
