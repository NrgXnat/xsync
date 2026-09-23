package org.nrg.xsync.globus;

/**
 * Thrown when a Globus Transfer API operation fails: a request is rejected, a
 * response cannot be parsed or is missing an expected field, or a task cannot
 * be waited on to completion.
 *
 * @author XSync
 */
public class GlobusTransferException extends RuntimeException {

    public GlobusTransferException(final String message) {
        super(message);
    }

    public GlobusTransferException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
