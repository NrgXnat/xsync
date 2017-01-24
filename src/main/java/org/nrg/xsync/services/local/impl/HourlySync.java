package org.nrg.xsync.services.local.impl;

import org.nrg.xsync.services.local.HourlySyncService;

/**
 * @author Mohana Ramaratnam
 *
 */
public class HourlySync implements Runnable {
    public HourlySync(final HourlySyncService service) {
        _service = service;
    }

    @Override
    public void run() {
        if (_service != null) {
            _service.syncHourly();
        }
    }

    private final HourlySyncService _service;


}
