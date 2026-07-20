package org.nrg.xsync.configuration;

import java.nio.file.Paths;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import lombok.extern.slf4j.Slf4j;
import lombok.Getter;
import org.nrg.config.entities.Configuration;
import org.nrg.config.services.ConfigService;
import org.nrg.framework.constants.Scope;
import org.nrg.framework.services.SerializerService;
import org.nrg.xdat.model.XnatImagesessiondataI;
import org.nrg.xdat.model.XnatSubjectassessordataI;
import org.nrg.xdat.om.XnatProjectdata;
import org.nrg.xdat.om.XsyncXsyncinfodata;
import org.nrg.xdat.om.XsyncXsyncprojectdata;
import org.nrg.xft.event.EventMetaI;
import org.nrg.xft.event.EventUtils;
import org.nrg.xft.security.UserI;
import org.nrg.xsync.configuration.json.SyncConfiguration;
import org.nrg.xsync.configuration.json.SyncConfigurationImagingSessionXsiType;
import org.nrg.xsync.configuration.json.SyncConfigurationXsiType;
import org.nrg.xsync.exception.XsyncNotConfiguredException;
import org.nrg.xsync.utils.XsyncUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * @author Mohana Ramaratnam
 */
@SuppressWarnings("unused")
@Slf4j
public class ProjectSyncConfiguration {
    public ProjectSyncConfiguration(final ConfigService configService, final SerializerService serializer, final JdbcTemplate jdbcTemplate,
    		final String projectId, final UserI user) throws XsyncNotConfiguredException {
        _user = user;
        project = XnatProjectdata.getProjectByIDorAlias(projectId, user, false);
        _configService = configService;
        _serializer = serializer;
        _jdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
        synchronizationConfiguration = setSyncConfigurationFromService(projectId);
        syncProjectConfiguration = setProjectSyncConfiguration();
    }

    public XsyncXsyncprojectdata getProjectSyncConfigurationFromDB() {
        return syncProjectConfiguration;
    }

    public boolean isResourceToBeSynced(String resourceLabel) {
        return synchronizationConfiguration != null && synchronizationConfiguration.isProjectResourceAllowedToSync(resourceLabel);
    }

    public boolean isSubjectResourceToBeSynced(String resourceLabel) {
        return synchronizationConfiguration != null && synchronizationConfiguration.isSubjectResourceAllowedToSync(resourceLabel);
    }

    public boolean isSubjectAssessorToBeSynced(String assessorXsiType) {
        return synchronizationConfiguration != null && synchronizationConfiguration.isSubjectAssessorAllowedToSync(assessorXsiType);
    }

    public boolean isSubjectAssessorResourceToBeSynced(String assessorXsiType, String resourceLabel) {
    	boolean allowedToSync = true;
        if (synchronizationConfiguration.hasSubjectAssessorConfigurationDefinition()) {
            SyncConfigurationXsiType advOption = synchronizationConfiguration.getSubjectAssessor(assessorXsiType);
            allowedToSync = advOption.isResourceAllowedToSync(resourceLabel);
        } 
        return allowedToSync;
    }

    public boolean subjectAssessorNeedsOkToSync(String assessorXsiType) {
        if (synchronizationConfiguration.hasSubjectAssessorConfigurationDefinition()) {
            SyncConfigurationXsiType advOption = synchronizationConfiguration.getSubjectAssessor(assessorXsiType);
            return advOption.getNeeds_ok_to_sync();
        } else {
            return false;
        }
    }

    public boolean imagingSessionNeedsOkToSync(String xsiType) {
        if (synchronizationConfiguration.hasImagingSessionConfigurationDefinition()) {
            SyncConfigurationImagingSessionXsiType advOption = synchronizationConfiguration.getImagingSession(xsiType);
            try {
            	return advOption.getNeeds_ok_to_sync();
            }catch(Exception e) {
            	return false;
            }
        } else {
            return false;
        }
    }

    public boolean imagingAssessorNeedsOkToSync(String xsiType, String assessorXsiType) {
        if (synchronizationConfiguration.hasImagingSessionConfigurationDefinition()) {
            SyncConfigurationImagingSessionXsiType advOption = synchronizationConfiguration.getImagingSession(xsiType);
            try {
                SyncConfigurationXsiType advSessionAssessorOption = advOption.getSession_assessors().getXsiType(assessorXsiType);
                return advSessionAssessorOption.getNeeds_ok_to_sync();
            } catch (NullPointerException npe) {
                return false; // Not present defaults to ok not required
            }
        } else {
            return false;
        }
    }

    public boolean isOnlyASubjectAssessor(XnatSubjectassessordataI experiment) {
        return !(experiment instanceof XnatImagesessiondataI);
    }

    public boolean isImagingSessionToBeSynced(String imagingSessionXsiType) {
        return synchronizationConfiguration != null && synchronizationConfiguration.isImagingSessionAllowedToSync(imagingSessionXsiType);
    }

    public boolean isImagingSessionScanToBeSynced(String imagingSessionXsiType, String imagingScanType) {
        if (synchronizationConfiguration == null) {
            return false;
        } else {
            SyncConfigurationImagingSessionXsiType imgSessionAdvOption = synchronizationConfiguration.getImagingSession(imagingSessionXsiType);
            return imgSessionAdvOption.isAllowedToSyncScan(imagingScanType);
        }
    }

    public boolean isImagingSessionScanResourceToBeSynced(String imagingSessionXsiType, String imagingScanType, String imagingScanResourceName) {
        if (synchronizationConfiguration == null) {
            return false;
        }
        SyncConfigurationImagingSessionXsiType imgSessionAdvOption = synchronizationConfiguration.getImagingSession(imagingSessionXsiType);
        return imgSessionAdvOption.isAllowedToSyncScan(imagingScanType) && imgSessionAdvOption.isAllowedToSyncScanResource(imagingScanResourceName);
    }

    public boolean isSetToSyncNewOnly() {
        return getSynchronizationConfiguration().getSync_new_only();
    }

    @SuppressWarnings("deprecation")
    public boolean isThisProjectBeingSyncedForTheFirstTime() {
        boolean beingSyncedForTheFirstTime = false;
        XsyncXsyncinfodata syncInfo = getProjectSyncConfigurationFromDB().getSyncinfo();
        if (syncInfo.getSyncEndTime() == null && syncInfo.getSyncStatus() == null) {
            beingSyncedForTheFirstTime = true;
        } else {
            Object date = syncInfo.getSyncEndTime();
            if (date != null) {
                if ((((Date) date).getYear() == OLD_CALENDAR.get(Calendar.YEAR)) && syncInfo.getSyncStatus() == null) {
                    beingSyncedForTheFirstTime = true;
                }
            }
        }
        return beingSyncedForTheFirstTime;
    }

    @Override
    public String toString() {
        String self = "";
        self += " Project : " + project + "\n";
        self += "SyncConfiguration: " + synchronizationConfiguration + "\n";
        self += " DB SyncInfo:  " + "\n";
        self += "Remote Project: " + syncProjectConfiguration.getSyncinfo().getRemoteProjectId() + "\n";
        self += "Remote URL: " + syncProjectConfiguration.getSyncinfo().getRemoteUrl();
        self += "Sync_Blocked: " + syncProjectConfiguration.getSyncBlocked();
        return self;
    }

    private static String getAnonymizationFilePath(final String projectArchiveRootPath, final String fileType) {
        return Paths.get(projectArchiveRootPath, "resources", "synchronization", fileType + "_anon.das").toString();
    }

    private XsyncXsyncprojectdata setProjectSyncConfiguration() throws XsyncNotConfiguredException {
        final XsyncUtils xsyncUtils = new XsyncUtils(_jdbcTemplate, _user);
        final XsyncXsyncprojectdata syncProjectConfiguration = xsyncUtils.getSyncDetailsForProject(project.getId());

        if (syncProjectConfiguration == null) {
            log.error("Could not find sync data for project {}", project.getId());
            throw new XsyncNotConfiguredException("Could not find sync data for project " + project.getId());
        }
        boolean save = false;
        //No sync has been done so far. Set a dummy date and then start
        //If this is the first time that the sync is taking place
        if (syncProjectConfiguration.getSyncinfo().getSyncStartTime() == null) {
            syncProjectConfiguration.getSyncinfo().setSyncStartTime(OLD_CALENDAR.getTime());
            save = true;
        }
        if (syncProjectConfiguration.getSyncinfo().getSyncEndTime() == null) {
            syncProjectConfiguration.getSyncinfo().setSyncEndTime(OLD_CALENDAR.getTime());
            save = true;
        }
        if (save) {
            try {
                //Backward compatible XNAT 1.6.5 does not have ADMIN_EVENT method
                EventMetaI c = EventUtils.DEFAULT_EVENT(_user, "ADMIN_EVENT occurred");
                syncProjectConfiguration.save(_user, false, true, c);
            } catch (Exception e) {
                log.debug("Unable to save synchronization  start time:  Cause:{}", e.getMessage());
                throw new XsyncNotConfiguredException("Unable to save synchronization start time: Cause:" + e.getMessage());
            }
        }
        return syncProjectConfiguration;
    }

    private SyncConfiguration setSyncConfigurationFromService(final String projectId) throws XsyncNotConfiguredException {
        final Configuration conf  = _configService.getConfig("xsync", "json", Scope.Project, projectId);
        final String config	= conf != null ? conf.getContents() : null;
        if (config != null) {
            try {
                return _serializer.deserializeJson(config, SyncConfiguration.class);
            } catch (Exception e) {
                throw new XsyncNotConfiguredException("Synchronization Configuration does not exist for " + project.getId());
            }
        } else {
            throw new XsyncNotConfiguredException("Synchronization Configuration does not exist for " + project.getId());
        }
    }

    private static final Calendar OLD_CALENDAR = new GregorianCalendar(1970, 1, 1);

    private final ConfigService              _configService;
    private final SerializerService          _serializer;
    private final NamedParameterJdbcTemplate _jdbcTemplate;
    private final UserI                      _user;
    private final XsyncXsyncprojectdata syncProjectConfiguration;
    @Getter
    private final XnatProjectdata project;
    @Getter
    private final SyncConfiguration synchronizationConfiguration;
}
