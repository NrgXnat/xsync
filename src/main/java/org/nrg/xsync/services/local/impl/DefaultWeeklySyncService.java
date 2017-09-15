package org.nrg.xsync.services.local.impl;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.nrg.config.services.ConfigService;
import org.nrg.framework.services.SerializerService;
import org.nrg.framework.task.services.XnatTaskService;
import org.nrg.mail.services.MailService;
import org.nrg.xdat.security.helpers.Users;
import org.nrg.xnat.services.archive.CatalogService;
import org.nrg.xsync.connection.RemoteConnectionManager;
import org.nrg.xsync.discoverer.ProjectChangeDiscoverer;
import org.nrg.xsync.remote.alias.services.SyncStatusService;
import org.nrg.xsync.services.local.AbstractSyncService;
import org.nrg.xsync.services.local.WeeklySyncService;
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
    public void syncWeekly() {
		if (!shouldRunTask()) {
			logger.info("Process syncWeekly is not configured to run on this node.  Skipping.");
			return;
		}
        logger.info("Weekly Sync Triggered - BEGIN " + new Date());
        final List<Map<String, Object>> queryResultsRows = getQueryResultUtil().getProjectsTobeSyncedWeekly();

        //TODO
        //The user who sets up the sync will
        //All project access will be done by the admin user
        if (queryResultsRows != null && queryResultsRows.size() > 0) {
            ExecutorService executor = getExecutor();
            try {
            	for (Map<String, Object> row : queryResultsRows) {
                    String projectId = (String) row.get("source_project_id");
                    String userId = (String) row.get("sync_scheduled_by");
                    try {
                        final ProjectChangeDiscoverer projectChange = getProjectChangeDiscoverer(projectId, Users.getUser(userId));
                        executor.submit(projectChange);
                    } catch (Exception e) {
                        logger.debug(e.getMessage());
                        XSyncFailureHandler.handle(getMailService(), getXnatInfo().getAdminEmail(), getManager().getSiteId(), projectId, e, "Weekly sync failed");
                    }
                }
            }catch(Exception e) {
                logger.debug(e.getMessage());
                XSyncFailureHandler.handle(getMailService(), getXnatInfo().getAdminEmail(), getManager().getSiteId(), "", e, "Weekly sync failed");
            }finally {
                //executor.shutdown();
            }
        }
        logger.info("Weekly Sync Trigger - END " + new Date());
    }

    private final static Logger logger = LoggerFactory.getLogger(DefaultWeeklySyncService.class);
}
