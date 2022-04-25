package org.nrg.xsync.services.local;

import org.nrg.config.services.ConfigService;
import org.nrg.framework.services.SerializerService;
import org.nrg.framework.task.XnatTask;
import org.nrg.framework.task.services.XnatTaskService;
import org.nrg.mail.services.MailService;
import org.nrg.xdat.security.helpers.Users;
import org.nrg.xft.security.UserI;
import org.nrg.xnat.services.archive.CatalogService;
import org.nrg.xnat.task.AbstractXnatTask;
import org.nrg.xsync.connection.RemoteConnectionManager;
import org.nrg.xsync.discoverer.ProjectChangeDiscoverer;
import org.nrg.xsync.exception.XsyncNotConfiguredException;
import org.nrg.xsync.remote.alias.services.SyncStatusService;
import org.nrg.xsync.tools.XsyncXnatInfo;
import org.nrg.xsync.utils.QueryResultUtil;
import org.nrg.xsync.utils.XSyncFailureHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolExecutorFactoryBean;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

@XnatTask(taskId = "XsyncScheduledSync", description = "XSync Scheduled Sync Operations", 
defaultExecutionResolver = "SingleNodeExecutionResolver", executionResolverConfigurable = true)
public abstract class AbstractSyncService extends AbstractXnatTask {
    private final RemoteConnectionManager       _manager;
    private final ConfigService                 _configService;
    private final MailService                   _mailService;
    private final SerializerService             _serializer;
    private final CatalogService 			    _catalogService;
    private final NamedParameterJdbcTemplate    _jdbcTemplate;
    private final QueryResultUtil               _queryResultUtil;
    private final XsyncXnatInfo                 _xnatInfo;
    private final ThreadPoolExecutorFactoryBean _executorFactoryBean;
    private final SyncStatusService				_syncStatusService;
    protected static final Logger logger = LoggerFactory.getLogger(AbstractSyncService.class);

    protected AbstractSyncService(final RemoteConnectionManager manager, final ConfigService configService, final MailService mailService,
    		final CatalogService catalogService, final SerializerService serializer, final JdbcTemplate jdbcTemplate, final QueryResultUtil queryResultUtil,
    		final XsyncXnatInfo xnatInfo, final ThreadPoolExecutorFactoryBean executorFactoryBean, final SyncStatusService syncStatusService,
    		final XnatTaskService taskService) {
    	super(taskService, false, null, jdbcTemplate);
        _manager = manager;
        _configService = configService;
        _mailService = mailService;
        _catalogService = catalogService;
        _serializer = serializer;
        _jdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
        _queryResultUtil = queryResultUtil;
        _xnatInfo = xnatInfo;
        _executorFactoryBean = executorFactoryBean;
        _syncStatusService = syncStatusService;
    }

    protected ProjectChangeDiscoverer getProjectChangeDiscoverer(final String projectId, final UserI user) throws XsyncNotConfiguredException {
        return new ProjectChangeDiscoverer(_manager, _configService, _serializer, _queryResultUtil, _jdbcTemplate, _mailService, _catalogService,_xnatInfo, _syncStatusService, projectId, user);
    }

    protected RemoteConnectionManager getManager() {
        return _manager;
    }

    protected MailService getMailService() {
        return _mailService;
    }

    protected QueryResultUtil getQueryResultUtil() {
        return _queryResultUtil;
    }

    protected ExecutorService getExecutor() {
        return _executorFactoryBean.getObject();
    }

    protected XsyncXnatInfo getXnatInfo() {
        return _xnatInfo;
    }
    
    protected void doSync(List<Map<String, Object>> queryResultsRows) {
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
                        XSyncFailureHandler.handle(getMailService(), getXnatInfo().getAdminEmail(), getManager().getSiteId(), projectId, e, "Monthly sync failed");
                    }
                }
            }catch(Exception e) {
                logger.debug(e.getMessage());
                XSyncFailureHandler.handle(getMailService(), getXnatInfo().getAdminEmail(), getManager().getSiteId(), "", e, "Monthly sync failed");
            }finally{
                //executor.shutdown();
            }
        }
    }
    
}
