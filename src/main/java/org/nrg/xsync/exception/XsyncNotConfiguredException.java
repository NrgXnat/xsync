package org.nrg.xsync.exception;

/**
 * @author Mohana Ramaratnam
 *
 */
public class XsyncNotConfiguredException extends Exception{
	
	String _cause;
	public XsyncNotConfiguredException(String cause) {
		_cause = cause;
	}
	
	public String getMessage() {
		return _cause;
	}
}
