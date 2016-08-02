package org.nrg.xsync.connection;

/**
 * Defines a discrete remote REST operation.
 */
public class RemoteOperation {
    public String getUrl() {
        return _url;
    }

    public void setUrl(final String url) {
        _url = url;
    }

    public String getProxy() {
        return _proxy;
    }

    public void setProxy(final String proxy) {
        _proxy = proxy;
    }

    public String getMethod() {
        return _method;
    }

    public void setMethod(final String method) {
        _method = method;
    }

    public String getUsername() {
        return _username;
    }

    public void setUsername(final String username) {
        _username = username;
    }

    public String getPassword() {
        return _password;
    }

    public void setPassword(final String password) {
        _password = password;
    }

    private String _username;
    private String _password;
    private String _proxy;
    private String _url;
    private String _method;
}
