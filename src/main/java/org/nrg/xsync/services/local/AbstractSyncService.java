package org.nrg.xsync.services.local;

import org.nrg.config.services.ConfigService;
import org.nrg.framework.services.SerializerService;
import org.nrg.mail.services.MailService;
import org.nrg.xft.security.UserI;
import org.nrg.xnat.services.archive.CatalogService;
import org.nrg.xsync.connection.RemoteConnectionManager;
import org.nrg.xsync.discoverer.ProjectChangeDiscoverer;
import org.nrg.xsync.exception.XsyncNotConfiguredException;
import org.nrg.xsync.remote.alias.services.SyncStatusService;
import org.nrg.xsync.tools.XsyncXnatInfo;
import org.nrg.xsync.utils.QueryResultUtil;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolExecutorFactoryBean;

import java.util.concurrent.ExecutorService;

public abstract class AbstractSyncService {
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

    protected AbstractSyncService(final RemoteConnectionManager manager, final ConfigService configService, final MailService mailService,
    		final CatalogService catalogService, final SerializerService serializer, final JdbcTemplate jdbcTemplate, final QueryResultUtil queryResultUtil,
    		final XsyncXnatInfo xnatInfo, final ThreadPoolExecutorFactoryBean executorFactoryBean, SyncStatusService syncStatusService) {
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
}
