package org.nrg.xsync.connection;

import lombok.Getter;
import lombok.Setter;

/**
 * Defines a discrete remote REST operation.
 */
@Setter
@Getter
public class RemoteOperation {

    private String username;
    private String password;
    private String proxy;
    private String url;
    private String method;
}
