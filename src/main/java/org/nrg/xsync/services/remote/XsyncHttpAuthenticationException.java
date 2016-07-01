package org.nrg.xsync.services.remote;

import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpStatusCodeException;

/**
 * The Class XsyncHttpAuthenticationException.
 * 
 * @author Mike Hodge
 */
public class XsyncHttpAuthenticationException extends HttpStatusCodeException {

	/** The Constant serialVersionUID. */
	private static final long serialVersionUID = 3521166371492029388L;

	/**
	 * Instantiates a new xsync http authentication exception.
	 *
	 * @param statusCode the status code
	 * @param statusText the status text
	 */
	protected XsyncHttpAuthenticationException(HttpStatus statusCode, String statusText) {
		super(statusCode, statusText);
	}

}
