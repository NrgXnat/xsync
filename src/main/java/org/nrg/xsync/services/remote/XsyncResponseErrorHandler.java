package org.nrg.xsync.services.remote;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClientException;

/**
 * The Class XsyncResponseErrorHandler.
 * 
 * @author Mike Hodge
 */
public class XsyncResponseErrorHandler implements ResponseErrorHandler {

	@Override
	public boolean hasError(ClientHttpResponse response) throws IOException {
		final HttpStatus statusCode = HttpStatus.valueOf(response.getStatusCode().value());
		return (statusCode.is4xxClientError() || statusCode.is5xxServerError());
	}

	@Override
	public void handleError(ClientHttpResponse response) throws IOException {
		final HttpStatus statusCode = HttpStatus.valueOf(response.getStatusCode().value());
		switch (statusCode.series()) {
			case CLIENT_ERROR:
				if (statusCode.equals(HttpStatus.UNAUTHORIZED)) {
						throw new XsyncHttpAuthenticationException(statusCode, response.getStatusText());
				}
				throw new HttpClientErrorException(statusCode, response.getStatusText());
			case SERVER_ERROR:
				throw new HttpServerErrorException(statusCode, response.getStatusText());
			default:
				throw new RestClientException("Unknown status code [" + statusCode + "]");
		}
	}
	
}
