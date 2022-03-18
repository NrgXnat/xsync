package org.nrg.xsync.local;

import org.nrg.config.services.ConfigService;
import org.nrg.framework.services.SerializerService;
import org.nrg.mail.services.MailService;
import org.nrg.xdat.model.XnatAbstractresourceI;
import org.nrg.xdat.om.XnatProjectdata;
import org.nrg.xdat.om.XnatSubjectdata;
import org.nrg.xft.event.EventMetaI;
import org.nrg.xft.event.EventUtils;
import org.nrg.xft.security.UserI;
import org.nrg.xnat.services.archive.CatalogService;
import org.nrg.xsync.components.SyncStatusHolder;
import org.nrg.xsync.configuration.ProjectSyncConfiguration;
import org.nrg.xsync.connection.RemoteConnectionManager;
import org.nrg.xsync.exception.XsyncNotConfiguredException;
import org.nrg.xsync.manager.SynchronizationManager;
import org.nrg.xsync.remote.alias.services.SyncStatusService;
import org.nrg.xsync.tools.XsyncObserver;
import org.nrg.xsync.tools.XsyncXnatInfo;
import org.nrg.xsync.utils.QueryResultUtil;
import org.nrg.xsync.utils.XSyncFailureHandler;
import org.nrg.xsync.utils.XsyncFileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * @author Mohana Ramaratnam
 *
 * Added to facilitate manual syncing of a single subject.
 * The Subject Meta-data, Subject Resources would be sent
 *
 * If the subject has already been partially synced, use the same remote id
 * If the any of the descendants of the subject are being synced at the moment
 * wait until send completes to re-sync the subject.
 */

public class SingleSubjectTransfer implements Callable<Void> {

    private static final Logger log = LoggerFactory.getLogger(SingleSubjectTransfer.class);
    private final RemoteConnectionManager manager;
    private final MailService mailService;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final QueryResultUtil queryResultUtil;
    private final XsyncXnatInfo xnatInfo;
    private final SerializerService serializer;
    private final CatalogService catalogService;
    private final SyncStatusService syncStatusService;
    private final String                     projectId;
    private final UserI user;
    private MapSqlParameterSource parameters;
    private final ProjectSyncConfiguration projectSyncConfiguration;
    private final boolean                    syncAll;
    private XsyncObserver observer;
    private       String					 subjectId;

    public SingleSubjectTransfer(final RemoteConnectionManager manager, final ConfigService configService, final SerializerService serializer,
                                    final QueryResultUtil queryResultUtil, final NamedParameterJdbcTemplate jdbcTemplate, final MailService mailService,
                                    final CatalogService catalogService, final XsyncXnatInfo xnatInfo, SyncStatusService syncStatusService,
                                    final String projectId, final UserI user, final String subjectId) throws XsyncNotConfiguredException {
        this.manager = manager;
        this.mailService = mailService;
        this.queryResultUtil = queryResultUtil;
        this.jdbcTemplate = jdbcTemplate;
        this.xnatInfo = xnatInfo;
        this.serializer = serializer;
        this.catalogService = catalogService;
        this.syncStatusService = syncStatusService;
        this.projectId = projectId;
        this.user = user;
        this.parameters = new MapSqlParameterSource("project", projectId);
        projectSyncConfiguration = new ProjectSyncConfiguration(configService, serializer, (JdbcTemplate) jdbcTemplate.getJdbcOperations(), projectId, user);
        this.syncAll = !projectSyncConfiguration.isSetToSyncNewOnly();
        this.subjectId = subjectId;
    }


    public Void call() throws Exception {
        sync();
        return null;
    }

    private synchronized void sync() {
        XnatProjectdata project = null;
        XnatAbstractresourceI synchronizationResource = null;

        XnatSubjectdata localSubject = XnatSubjectdata.getXnatSubjectdatasById(subjectId, user, false);
        if (localSubject == null) {
            return;
        }
        final String _subjectLabel = localSubject.getLabel();
        try {
            Boolean isSyncEnabled = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncEnabled();
            if (!isSyncEnabled) {
                return;
            }
            Boolean isSyncBlocked = syncStatusService.isCurrentlySyncing(projectId);
            if (isSyncBlocked != null && isSyncBlocked) {
                try {
                    mailService.sendHtmlMessage(xnatInfo.getAdminEmail(), user.getEmail(), "Project " + projectId + " sync skipped ",
                            "<html><body>"
                                    + "<p>Sync was skipped.  See information below:</p>"
                                    + "<p>PROJECT: " + projectId + "</p>"
                                    + "<p>SITE: " + manager.getSiteId() + "</p>"
                                    + "<p>USER: " + user.getLogin() + "</p>"
                                    + "</body></html>");
                    log.debug("Sync Blocked");
                } catch (Exception e) {
                    log.error("Failed to send email.", e);
                }
                return;
            }
            project = projectSyncConfiguration.getProject();
            String remoteProjectId = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteProjectId();
            String remoteHost = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl();

            SynchronizationManager.BEGIN_SYNC(manager.getSyncManifestService(), xnatInfo, project.getId(), remoteProjectId, remoteHost, user, mailService, jdbcTemplate);
            syncStatusService.registerSyncStart(projectId, SyncStatusHolder.SyncType.SUBJECT_SYNC, SynchronizationManager.getProjectManifest(project.getId()));
            observer  = new XsyncObserver(projectId);
            synchronizationResource = XsyncFileUtils.createSynchronizationLogResource(project,user);
            boolean syncAllStates = !projectSyncConfiguration.isSetToSyncNewOnly();
            SubjectDataSync subjectDataSync = new SubjectDataSync(
                    manager, xnatInfo, queryResultUtil,
                    jdbcTemplate, localSubject, projectSyncConfiguration,
                    user, syncAllStates, observer,  serializer, syncStatusService);
            //Only Subject Meta Data and the Subject Resources are to be synced. None of the experiments are synced
            subjectDataSync.sync(false);

            SynchronizationManager.END_SYNC(serializer, project.getId(), jdbcTemplate, false);
            syncStatusService.registerSyncEnd(projectId,SynchronizationManager.getProjectManifest(project.getId()));
        } catch (Exception e) {
            log.error(e.getMessage(),e);
            try {
                if (_subjectLabel!=null) {
                    syncStatusService.registerFailedSubject(projectId, _subjectLabel);
                }
                //Roll back the syncBlocked flag
                log.debug(e.getLocalizedMessage());
                //syncStatusUpdater.saveSyncBlockStatus(Boolean.FALSE);
                XSyncFailureHandler.handle(mailService, xnatInfo.getAdminEmail(), manager.getSiteId(), projectId, e, "Sync failed");
            } catch (Throwable t) {
                throw t;
            } finally {
                syncStatusService.registerSyncEnd(projectId,SynchronizationManager.getProjectManifest(project.getId()));
            }
        }finally{
            observer.close(synchronizationResource);
            if (synchronizationResource != null && project != null) {
                //RefreshCatalog
                EventMetaI now = EventUtils.DEFAULT_EVENT(user, "Synchronization Log Added");
                try  {
                    final List<CatalogService.Operation> _operations  = new ArrayList<>();
                    final String                   _resource   = "/data/archive/projects/"+projectId+"/resources/"+synchronizationResource.getLabel();
                    _operations.addAll(CatalogService.Operation.ALL);
                    catalogService.refreshResourceCatalog(user, _resource, _operations.toArray(new CatalogService.Operation[_operations.size()]));
                    //ResourceUtils.refreshResourceCatalog((XnatAbstractresource)synchronizationResource, project.getArchiveRootPath(), true, true, true, true, _user, now);
                }catch(Exception e) {log.debug("Unable to refresh catalog");}
            }
        }

    }

}
