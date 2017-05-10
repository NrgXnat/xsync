package org.nrg.xsync.services.local.impl;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.nrg.config.services.ConfigService;
import org.nrg.framework.services.SerializerService;
import org.nrg.mail.services.MailService;
import org.nrg.xdat.security.helpers.Users;
import org.nrg.xnat.services.archive.CatalogService;
import org.nrg.xsync.connection.RemoteConnectionManager;
import org.nrg.xsync.discoverer.ProjectChangeDiscoverer;
import org.nrg.xsync.remote.alias.services.SyncStatusService;
import org.nrg.xsync.services.local.AbstractSyncService;
import org.nrg.xsync.services.local.DailySyncService;
import org.nrg.xsync.tools.XsyncXnatInfo;
import org.nrg.xsync.utils.QueryResultUtil;
import org.nrg.xsync.utils.XSyncFailureHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolExecutorFactoryBean;
import org.springframework.stereotype.Service;

/**
 * @author Mohana Ramaratnam
 *
 */
@Service
public class DefaultDailySyncService extends AbstractSyncService implements DailySyncService {
	@Autowired
	public DefaultDailySyncService(final RemoteConnectionManager manager, final ConfigService configService, final MailService mailService,
			final CatalogService catalogService,final SerializerService serializer, final JdbcTemplate jdbcTemplate, final QueryResultUtil queryResultUtil,
			final XsyncXnatInfo xnatInfo, final ThreadPoolExecutorFactoryBean executorFactoryBean, SyncStatusService syncStatusService) {
		super(manager, configService, mailService, catalogService,serializer, jdbcTemplate, queryResultUtil, xnatInfo, executorFactoryBean, syncStatusService);
	}

	@Override
	public void syncDaily() {
		//Get all projects with their sync schedules marked daily
		final List<Map<String,Object>> queryResultsRows = getQueryResultUtil().getProjectsTobeSyncedDaily();
		//TODO
		//The user who sets up the sync will 
		//All project access will be done by the admin user
		if (queryResultsRows != null && queryResultsRows.size() > 0) {
			final ExecutorService executor = getExecutor();
			try {
				for (final Map<String,Object> row : queryResultsRows) {
					final String projectId =(String)row.get("source_project_id");
					final String userId = (String)row.get("sync_scheduled_by");
					final ProjectChangeDiscoverer projectChange = getProjectChangeDiscoverer(projectId, Users.getUser(userId));
					try {
						executor.submit(projectChange);
					}catch(Exception e) {
						logger.debug(e.getMessage(), e);
						XSyncFailureHandler.handle(getMailService(), getXnatInfo().getAdminEmail(), getManager().getSiteId(), projectId, e, "Daily sync failed");
					}
				}
			}catch(Exception e) {
				logger.debug(e.getMessage(), e);
				XSyncFailureHandler.handle(getMailService(), getXnatInfo().getAdminEmail(), getManager().getSiteId(), "Project", e, "Daily sync failed");
			} finally {
				//executor.shutdown();
			}
		}
	}
	
	private final static Logger logger = LoggerFactory.getLogger(DefaultDailySyncService.class);
}
