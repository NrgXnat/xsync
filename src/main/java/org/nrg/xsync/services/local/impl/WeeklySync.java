package org.nrg.xsync.services.local.impl;

import org.nrg.xsync.services.local.WeeklySyncService;

/**
 * @author Mohana Ramaratnam
 *
 */
public class WeeklySync implements Runnable {
    public WeeklySync(final WeeklySyncService service) {
        _service = service;
    }

    @Override
    public void run() {
        if (_service != null) {
            _service.syncWeekly();
        }
    }

    private final WeeklySyncService _service;

}
