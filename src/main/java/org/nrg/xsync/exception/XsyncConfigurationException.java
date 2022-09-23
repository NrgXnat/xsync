package org.nrg.xsync.exception;

/**
 * The Class XsyncConfigurationException.
 *
 * @author Atul Kaushal
 */
public class XsyncConfigurationException extends Exception {
	private static final long serialVersionUID = -745838472566855025L;

	/**
	 * {@inheritDoc}
	 */
	public XsyncConfigurationException(final String message) {
		super(message);
	}

	/**
	 * {@inheritDoc}
	 */
	public XsyncConfigurationException(final String message, final Throwable cause) {
		super(message, cause);
	}
}
