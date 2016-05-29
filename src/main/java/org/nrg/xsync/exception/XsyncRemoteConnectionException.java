package org.nrg.xsync.exception;

/**
 * @author Mohana Ramaratnam
 *
 */
public class XsyncRemoteConnectionException extends Exception{
	String _cause;
	public XsyncRemoteConnectionException(String cause) {
		_cause = cause;
	}
	
	public String getMessage() {
		return _cause;
	}
}
