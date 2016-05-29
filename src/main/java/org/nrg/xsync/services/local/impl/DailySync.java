package org.nrg.xsync.services.local.impl;

import org.nrg.xsync.services.local.DailySyncService;

/**
 * @author Mohana Ramaratnam
 *
 */
public class DailySync implements Runnable {
    public DailySync(final DailySyncService service) {
        _service = service;
    }

    @Override
    public void run() {
        if (_service != null) {
            _service.syncDaily();
        }
    }

    private final DailySyncService _service;


}
