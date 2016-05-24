package org.nrg.xsync.exception;

/**
 * @author Mohana Ramaratnam
 *
 */
public class XsyncStoreException extends Exception{
	String _cause;
	public XsyncStoreException(String cause) {
		_cause = cause;
	}
	
	public String getMessage() {
		return _cause;
	}
}
