package org.nrg.xsync.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class XsyncCredentialsRequiredException extends Exception {
    public XsyncCredentialsRequiredException(final String username, final String url, final boolean isUsernameBlank, final boolean isPasswordBlank) {
        super(String.format("The user %s requested a remote connection to the URL %s but provided no %s.", username, url, isUsernameBlank && isPasswordBlank ? "password or username" : (isUsernameBlank ? "username" : "password")));
        _username = username;
        _url = url;
    }

    public String getUsername() {
        return _username;
    }

    public String getUrl() {
        return _url;
    }

    private final String _username;
    private final String _url;
}
