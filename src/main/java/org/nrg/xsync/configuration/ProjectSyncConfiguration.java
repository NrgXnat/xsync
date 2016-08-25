package org.nrg.xsync.configuration;

import java.nio.file.Paths;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

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
import org.nrg.xsync.configuration.json.SyncConfigurationAdvancedOption;
import org.nrg.xsync.configuration.json.SyncConfigurationImagingSessionAdvancedOption;
import org.nrg.xsync.exception.XsyncNotConfiguredException;
import org.nrg.xsync.utils.XsyncUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * @author Mohana Ramaratnam
 */
@SuppressWarnings("unused")
public class ProjectSyncConfiguration {
    public ProjectSyncConfiguration(final ConfigService configService, final SerializerService serializer, final JdbcTemplate jdbcTemplate, final String projectId, final UserI user) throws XsyncNotConfiguredException {
        _user = user;
        _project = XnatProjectdata.getProjectByIDorAlias(projectId, user, false);
        _configService = configService;
        _serializer = serializer;
        _jdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
        _syncConfiguration = setSyncConfigurationFromService(projectId);
        _syncProjectConfiguration = setProjectSyncConfiguration();
    }

    public XnatProjectdata getProject() {
        return _project;
    }

    public XsyncXsyncprojectdata getProjectSyncConfigurationFromDB() {
        return _syncProjectConfiguration;
    }

    public SyncConfiguration getSynchronizationConfiguration() {
        return _syncConfiguration;
    }

    public boolean isResourceToBeSynced(String resourceLabel) {
        return _syncConfiguration != null && _syncConfiguration.isProjectResourceAllowedToSync(resourceLabel);
    }

    public boolean isSubjectResourceToBeSynced(String resourceLabel) {
        return _syncConfiguration != null && _syncConfiguration.isSubjectResourceAllowedToSync(resourceLabel);
    }

    public boolean isSubjectAssessorToBeSynced(String assessorXsiType) {
        return _syncConfiguration != null && _syncConfiguration.isSubjectAssessorAllowedToSync(assessorXsiType);
    }

    public boolean isSubjectAssessorResourceToBeSynced(String assessorXsiType, String resourceLabel) {
        if (_syncConfiguration.hasSubjectAssessorConfigurationDefinition()) {
            SyncConfigurationAdvancedOption advOption = _syncConfiguration.getSubjectAssessorAdvancedOptions(assessorXsiType);
            return advOption.isResourceAllowedToSync(resourceLabel);
        } else {
            return true; //Default - when not specified all is pushed
        }
    }

    public boolean subjectAssessorNeedsOkToSync(String assessorXsiType) {
        if (_syncConfiguration.hasSubjectAssessorConfigurationDefinition()) {
            SyncConfigurationAdvancedOption advOption = _syncConfiguration.getSubjectAssessorAdvancedOptions(assessorXsiType);
            return advOption.getNeeds_ok_to_sync();
        } else {
            return false;
        }
    }

    public boolean imagingSessionNeedsOkToSync(String xsiType) {
        if (_syncConfiguration.hasImagingSessionConfigurationDefinition()) {
            SyncConfigurationImagingSessionAdvancedOption advOption = _syncConfiguration.getImagingSessionAdvancedOptions(xsiType);
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
        if (_syncConfiguration.hasImagingSessionConfigurationDefinition()) {
            SyncConfigurationImagingSessionAdvancedOption advOption = _syncConfiguration.getImagingSessionAdvancedOptions(xsiType);
            try {
                SyncConfigurationAdvancedOption advSessionAssessorOption = advOption.getSession_assessors().getAdvancedOption(assessorXsiType);
                return advSessionAssessorOption.getNeeds_ok_to_sync();
            } catch (NullPointerException npe) {
                return false; // Not present defaults to ok not required
            }
        } else {
            return false;
        }
    }

    public boolean isOnlyASubjectAssessor(XnatSubjectassessordataI experiment) {
        boolean isOnlyASubjectAssessor = true;
        if (experiment instanceof XnatImagesessiondataI) {
            isOnlyASubjectAssessor = false;
        }
        return isOnlyASubjectAssessor;
    }

    public boolean isImagingSessionToBeSynced(String imagingSessionXsiType) {
        return _syncConfiguration != null && _syncConfiguration.isImagingSessionAllowedToSync(imagingSessionXsiType);
    }

    public boolean isImagingSessionScanToBeSynced(String imagingSessionXsiType, String imagingScanType) {
        if (_syncConfiguration == null) {
            return false;
        } else {
            SyncConfigurationImagingSessionAdvancedOption imgSessionAdvOption = _syncConfiguration.getImagingSessionAdvancedOptions(imagingSessionXsiType);
            return imgSessionAdvOption.isAllowedToSyncScan(imagingScanType);
        }

    }

    public boolean isImagingSessionScanResourceToBeSynced(String imagingSessionXsiType, String imagingScanType, String imagingScanResourceName) {
        if (_syncConfiguration == null) {
            return false;
        }
        SyncConfigurationImagingSessionAdvancedOption imgSessionAdvOption = _syncConfiguration.getImagingSessionAdvancedOptions(imagingSessionXsiType);
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
        self += " Project : " + _project + "\n";
        self += "SyncConfiguration: " + _syncConfiguration + "\n";
        self += " DB SyncInfo:  " + "\n";
        self += "Remote Project: " + _syncProjectConfiguration.getSyncinfo().getRemoteProjectId() + "\n";
        self += "Remote URL: " + _syncProjectConfiguration.getSyncinfo().getRemoteUrl();
        self += "Sync_Blocked: " + _syncProjectConfiguration.getSyncBlocked();
        return self;
    }

    private static String getAnonymizationFilePath(final String projectArchiveRootPath, final String fileType) {
        return Paths.get(projectArchiveRootPath, "resources", "synchronization", fileType + "_anon.das").toString();
    }

    private XsyncXsyncprojectdata setProjectSyncConfiguration() throws XsyncNotConfiguredException {
        final XsyncUtils xsyncUtils = new XsyncUtils(_serializer, _jdbcTemplate, _user);
        final XsyncXsyncprojectdata syncProjectConfiguration = xsyncUtils.getSyncDetailsForProject(_project.getId());

        if (syncProjectConfiguration == null) {
            _log.error("Could not find sync data for _project " + _project.getId());
            throw new XsyncNotConfiguredException("Could not find sync data for _project " + _project.getId());
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
                _log.debug("Unable to save synchronization  start time: " + " Cause:" + e.getMessage());
                throw new XsyncNotConfiguredException("Unable to save synchronization  start time: " + " Cause:" + e.getMessage());
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
                throw new XsyncNotConfiguredException("Synchronization Configuration does not exist for " + _project.getId());
            }
        } else {
            throw new XsyncNotConfiguredException("Synchronization Configuration does not exist for " + _project.getId());
        }
    }

    private static final Logger   _log         = LoggerFactory.getLogger(ProjectSyncConfiguration.class);
    private static final Calendar OLD_CALENDAR = new GregorianCalendar(1970, 1, 1);

    private final ConfigService              _configService;
    private final SerializerService          _serializer;
    private final NamedParameterJdbcTemplate _jdbcTemplate;
    private final UserI                      _user;
    private final XsyncXsyncprojectdata      _syncProjectConfiguration;
    private final XnatProjectdata            _project;
    private final SyncConfiguration          _syncConfiguration;
}
