package org.nrg.xsync.globus;

/**
 * Thrown when Globus authentication fails: a token request is rejected, the
 * response cannot be parsed, or no usable Transfer token is present.
 *
 * @author XSync
 */
public class GlobusAuthException extends RuntimeException {

    public GlobusAuthException(final String message) {
        super(message);
    }

    public GlobusAuthException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
