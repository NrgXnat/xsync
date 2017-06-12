package org.nrg.xsync.exception;

/**
 * The Class XsyncConfigurationException.
 *
 * @author Atul Kaushal
 */
public class XsyncConfigurationException extends Exception{
	

	/** The Constant serialVersionUID. */
	private static final long serialVersionUID = 7981307002637808309L;
	
	/** The cause. */
	String _cause;
	
	/**
	 * Instantiates a new xsync configuration exception.
	 *
	 * @param cause the cause
	 */
	public XsyncConfigurationException(String cause) {
		_cause = cause;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Throwable#getMessage()
	 */
	public String getMessage() {
		return _cause;
	}
}
