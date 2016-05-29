package org.nrg.xsync.services.local.impl;

import org.nrg.xsync.services.local.XsyncAliasRefreshService;

/**
 * @author Mohana Ramaratnam
 *
 */
public class XSyncAliasTokenRefresh implements Runnable {
    public XSyncAliasTokenRefresh(final XsyncAliasRefreshService service) {
        _service = service;
    }

    @Override
    public void run() {
        if (_service != null) {
            _service.refreshToken();;
        }
    }

    private final XsyncAliasRefreshService _service;

}
