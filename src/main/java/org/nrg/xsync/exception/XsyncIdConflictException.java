package org.nrg.xsync.exception;

public class XsyncIdConflictException extends Exception{
	private static final long serialVersionUID = -8396042885800987483L;
	String _cause;
	public XsyncIdConflictException(String cause) {
		_cause = cause;
	}
	
	public String getMessage() {
		return _cause;
	}
}
