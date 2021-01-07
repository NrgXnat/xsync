package org.nrg.xsync.services.local.impl;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.nrg.config.services.ConfigService;
import org.nrg.framework.services.SerializerService;
import org.nrg.framework.task.XnatTask;
import org.nrg.framework.task.services.XnatTaskService;
import org.nrg.mail.services.MailService;
import org.nrg.xnat.services.archive.CatalogService;
import org.nrg.xsync.connection.RemoteConnectionManager;
import org.nrg.xsync.remote.alias.services.SyncStatusService;
import org.nrg.xsync.services.local.AbstractSyncService;
import org.nrg.xsync.services.local.HourlySyncService;
import org.nrg.xsync.tools.XsyncXnatInfo;
import org.nrg.xsync.utils.QueryResultUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolExecutorFactoryBean;
import org.springframework.stereotype.Service;

/**
 * @author Mohana Ramaratnam
 */
@Service
@XnatTask(taskId = "XsyncDefaultHourlySyncService", description = "Xsync autosync", defaultExecutionResolver = "SingleNodeExecutionResolver")
public class DefaultHourlySyncService extends AbstractSyncService implements HourlySyncService {
	@Autowired
	public DefaultHourlySyncService(final RemoteConnectionManager manager, final ConfigService configService, final MailService mailService,
			final CatalogService catalogService,final SerializerService serializer, final JdbcTemplate jdbcTemplate,
			final QueryResultUtil queryResultUtil, final XsyncXnatInfo xnatInfo,
			@Qualifier("xsyncThreadPoolExecutorFactoryBean") final ThreadPoolExecutorFactoryBean xsyncThreadPoolExecutorFactoryBean,
			final SyncStatusService syncStatusService, final XnatTaskService taskService) {
		super(manager, configService, mailService, catalogService,serializer, jdbcTemplate, queryResultUtil,
				xnatInfo, xsyncThreadPoolExecutorFactoryBean, syncStatusService, taskService);
	}

	@Override
	public void runTask() {
		syncHourly();
	}

	private void syncHourly() {
        logger.info("Hourly Sync Triggered - " + new Date());
		//Get all projects with their sync schedules marked daily
		final List<Map<String,Object>> queryResultsRows = getQueryResultUtil().getProjectsTobeSyncedHourly();
		doSync(queryResultsRows);
        logger.info("Hourly Sync Completed - " + new Date());
	}
	
}
