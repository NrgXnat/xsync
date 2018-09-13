package org.nrg.xsync.services.local.impl;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.nrg.config.services.ConfigService;
import org.nrg.framework.services.SerializerService;
import org.nrg.framework.task.services.XnatTaskService;
import org.nrg.mail.services.MailService;
import org.nrg.xnat.services.archive.CatalogService;
import org.nrg.xsync.connection.RemoteConnectionManager;
import org.nrg.xsync.remote.alias.services.SyncStatusService;
import org.nrg.xsync.services.local.AbstractSyncService;
import org.nrg.xsync.services.local.WeeklySyncService;
import org.nrg.xsync.tools.XsyncXnatInfo;
import org.nrg.xsync.utils.QueryResultUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolExecutorFactoryBean;
import org.springframework.stereotype.Service;

/**
 * @author Mohana Ramaratnam
 */
@Service
public class DefaultWeeklySyncService extends AbstractSyncService implements WeeklySyncService {
    @Autowired
    public DefaultWeeklySyncService(final RemoteConnectionManager manager, final ConfigService configService, final MailService mailService, final CatalogService catalogService,
    		final SerializerService serializer, final JdbcTemplate jdbcTemplate, final QueryResultUtil queryResultUtil,
    		final XsyncXnatInfo xnatInfo, final ThreadPoolExecutorFactoryBean executorFactoryBean,
    		final SyncStatusService syncStatusService, final XnatTaskService taskService) {
        super(manager, configService, mailService, catalogService, serializer, jdbcTemplate, queryResultUtil,
        		xnatInfo, executorFactoryBean, syncStatusService, taskService);
    }

	@Override
	protected void runTask() {
		syncWeekly();
	}

    private void syncWeekly() {
        logger.info("Weekly Sync Triggered - " + new Date());
        final List<Map<String, Object>> queryResultsRows = getQueryResultUtil().getProjectsTobeSyncedWeekly();
        doSync(queryResultsRows);
        logger.info("Weekly Sync Completed - " + new Date());
    }

}
