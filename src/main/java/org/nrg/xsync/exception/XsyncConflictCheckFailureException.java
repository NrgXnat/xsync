package org.nrg.xsync.exception;

public class XsyncConflictCheckFailureException extends Exception{
	private static final long serialVersionUID = 3933639996442341604L;
	String _cause;
	public XsyncConflictCheckFailureException(String cause) {
		_cause = cause;
	}
	
	public String getMessage() {
		return _cause;
	}
}
