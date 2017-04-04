package org.nrg.xsync.local;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.nrg.config.services.ConfigService;
import org.nrg.framework.services.SerializerService;
import org.nrg.mail.services.MailService;
import org.nrg.xdat.base.BaseElement;
import org.nrg.xdat.bean.CatCatalogBean;
import org.nrg.xdat.model.XnatAbstractresourceI;
import org.nrg.xdat.om.XnatAbstractresource;
import org.nrg.xdat.om.XnatProjectdata;
import org.nrg.xdat.om.XnatResourcecatalog;
import org.nrg.xdat.om.XnatSubjectdata;
import org.nrg.xdat.om.XnatExperimentdata;
import org.nrg.xdat.om.XnatSubjectassessordata;
import org.nrg.xft.ItemI;
import org.nrg.xft.event.EventMetaI;
import org.nrg.xft.event.EventUtils;
import org.nrg.xft.security.UserI;
import org.nrg.xft.utils.FileUtils;
import org.nrg.xnat.services.archive.CatalogService;
import org.nrg.xsync.configuration.ProjectSyncConfiguration;
import org.nrg.xsync.connection.RemoteConnection;
import org.nrg.xsync.connection.RemoteConnectionHandler;
import org.nrg.xsync.connection.RemoteConnectionManager;
import org.nrg.xsync.connection.RemoteConnectionResponse;
import org.nrg.xsync.exception.XsyncNotConfiguredException;
import org.nrg.xsync.local.IdMapper;
import org.nrg.xsync.local.RemoteSubject;
import org.nrg.xsync.manager.SynchronizationManager;
import org.nrg.xsync.manifest.ResourceSyncItem;
import org.nrg.xsync.manifest.SubjectSyncItem;
import org.nrg.xsync.tools.XSyncTools;
import org.nrg.xsync.tools.XsyncObserver;
import org.nrg.xsync.tools.XsyncXnatInfo;
import org.nrg.xsync.utils.QueryResultUtil;
import org.nrg.xsync.utils.XSyncFailureHandler;
import org.nrg.xsync.utils.XsyncFileUtils;
import org.nrg.xsync.utils.XsyncUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.nrg.xsync.utils.SyncStatusUpdater;
import com.google.common.collect.Lists;


/**
 * @author Mohana Ramaratnam
 *
 */
public class SingleExperimentTransfer implements Callable<Void> {

    private static final Logger _log = LoggerFactory.getLogger(SingleExperimentTransfer.class);

    //When created entry is in MetaData;
    //status field tells about the status of the entity
    //When updated entry is in History
    private final RemoteConnectionManager    _manager;
    private final MailService                _mailService;
    private final NamedParameterJdbcTemplate _jdbcTemplate;
    private final QueryResultUtil            _queryResultUtil;
    private final XsyncXnatInfo              _xnatInfo;
    private final SerializerService          _serializer;
    private final CatalogService 			 _catalogService;
    private final String                     _projectId;
    private final UserI                      _user;
    private       MapSqlParameterSource      _parameters;
    private final ProjectSyncConfiguration   _projectSyncConfiguration;
    private final boolean                    _syncAll;
    private 	  XsyncObserver				 _observer;
    private       String					 _exptId;
    
    public SingleExperimentTransfer(final RemoteConnectionManager manager, final ConfigService configService, final SerializerService serializer, final QueryResultUtil queryResultUtil, final NamedParameterJdbcTemplate jdbcTemplate, final MailService mailService, final CatalogService catalogService, final XsyncXnatInfo xnatInfo, final String projectId, final UserI user, final String exptId) throws XsyncNotConfiguredException {
        _manager = manager;
        _mailService = mailService;
        _queryResultUtil = queryResultUtil;
        _jdbcTemplate = jdbcTemplate;
        _xnatInfo = xnatInfo;
        _serializer = serializer;
        _catalogService = catalogService;
        _projectId = projectId;
        _user = user;
        _parameters = new MapSqlParameterSource("project", _projectId);
        _projectSyncConfiguration = new ProjectSyncConfiguration(configService, serializer, (JdbcTemplate) jdbcTemplate.getJdbcOperations(), _projectId, _user);
        _syncAll = !_projectSyncConfiguration.isSetToSyncNewOnly();
        _exptId = exptId;
    }
    
    public Void call() throws Exception {
        sync();
        return null;
    }

    private synchronized void sync() {
    	XnatProjectdata project = null;
    	SyncStatusUpdater syncStatusUpdater = new SyncStatusUpdater(_projectId, _projectSyncConfiguration, _user);
    	XnatAbstractresourceI synchronizationResource = null;

    	try {
            Boolean isSyncEnabled = _projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncEnabled();
            if (!isSyncEnabled) {
                return;
            }
            Boolean isSyncBlocked = _projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncBlocked();
            if (isSyncBlocked != null && isSyncBlocked) {
                try {
                    System.out.println("Sync is blocked ");
                    _mailService.sendHtmlMessage(_xnatInfo.getAdminEmail(), _user.getEmail(), "Project " + _projectId + " sync skipped ",
                                                          "<html><body><p>Project " + _projectId + " sync skipped </p></body></html>");
                    _log.debug("Sync Blocked");
                } catch (Exception e) {
                    _log.error("Failed to send email.", e);
                }
                return;
            }
            project = _projectSyncConfiguration.getProject();
            String remoteProjectId = _projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteProjectId();
            String remoteHost = _projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl();

            syncStatusUpdater.saveSyncBlockStatus(Boolean.TRUE);
            SynchronizationManager.BEGIN_SYNC(_manager.getSyncManifestService(), _xnatInfo, project.getId(), remoteProjectId, remoteHost, _user, _mailService);
            _observer  = new XsyncObserver(_projectId);
            synchronizationResource = XsyncFileUtils.createSynchronizationLogResource(project,_user);
            syncExperiment();
            
            syncStatusUpdater.saveSyncBlockStatus(Boolean.FALSE);
            SynchronizationManager.END_SYNC(_serializer, project.getId(), _jdbcTemplate, false);
        } catch (Exception e) {
            //Roll back the syncBlocked flag
            _log.debug(e.getLocalizedMessage());
            syncStatusUpdater.saveSyncBlockStatus(Boolean.FALSE);
            XSyncFailureHandler.handle(_mailService, _xnatInfo.getAdminEmail(), _manager.getSiteId(), _projectId, e, "Sync failed");
        }finally{
        	_observer.close(synchronizationResource);
        	if (synchronizationResource != null && project != null) {
           		//RefreshCatalog
        	    EventMetaI now = EventUtils.DEFAULT_EVENT(_user, "Synchronization Log Added");
        		try  {
        			 final List<CatalogService.Operation> _operations  = Lists.newArrayList();
        			 final String                   _resource   = "/data/archive/projects/"+_projectId+"/resources/"+synchronizationResource.getLabel();
        			 _operations.addAll(CatalogService.Operation.ALL);
        			 _catalogService.refreshResourceCatalog(_user, _resource, _operations.toArray(new CatalogService.Operation[_operations.size()]));  
        			//ResourceUtils.refreshResourceCatalog((XnatAbstractresource)synchronizationResource, project.getArchiveRootPath(), true, true, true, true, _user, now);
        		}catch(Exception e) {_log.debug("Unable to refresh catalog");}
        	}
        }

    }

    private void syncExperiment() throws Exception {
        XnatExperimentdata exp = XnatExperimentdata.getXnatExperimentdatasById(_exptId, _user, true);
        if (exp == null) {
           return;	
        }
        if (exp instanceof XnatSubjectassessordata) {
        	XnatSubjectdata localSubject = ((XnatSubjectassessordata)exp).getSubjectData();
            _log.debug("Exporting " + localSubject.getId());
            RemoteSubject remoteSubject = new RemoteSubject(_manager, _xnatInfo, _queryResultUtil, (JdbcTemplate) _jdbcTemplate.getJdbcOperations(), localSubject, _projectSyncConfiguration, _user, _syncAll, _observer);
            remoteSubject.syncExperiment(exp);
        }
    }
    
}
