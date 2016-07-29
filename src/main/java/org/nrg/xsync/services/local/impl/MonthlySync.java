package org.nrg.xsync.services.local.impl;

import org.nrg.xsync.services.local.MonthlySyncService;

/**
 * @author Mohana Ramaratnam
 *
 */
public class MonthlySync implements Runnable {
	
    public MonthlySync(final MonthlySyncService service) {
        _service = service;
    }

    @Override
    public void run() {
        if (_service != null) {
            _service.syncMonthly();
        }
    }

    private final MonthlySyncService _service;
}
