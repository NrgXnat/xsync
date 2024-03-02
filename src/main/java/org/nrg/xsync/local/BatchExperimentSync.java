package org.nrg.xsync.local;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import org.nrg.framework.services.SerializerService;
import org.nrg.xdat.model.XnatExperimentdataI;
import org.nrg.xdat.model.XnatSubjectdataI;
import org.nrg.xdat.om.XnatAbstractresource;
import org.nrg.xdat.om.XnatExperimentdata;
import org.nrg.xdat.om.XnatProjectdata;
import org.nrg.xft.security.UserI;
import org.nrg.xnat.xsync.remote.verify.XsyncProjectVerifier;
import org.nrg.xsync.configuration.ProjectSyncConfiguration;
import org.nrg.xsync.connection.RemoteConnection;
import org.nrg.xsync.connection.RemoteConnectionHandler;
import org.nrg.xsync.connection.RemoteConnectionManager;
import org.nrg.xsync.connection.RemoteConnectionResponse;
import org.nrg.xsync.exception.XsyncRemoteConnectionException;
import org.nrg.xsync.manifest.ExperimentSyncItem;
import org.nrg.xsync.manifest.SubjectSyncItem;
import org.nrg.xsync.remote.alias.services.SyncStatusService;
import org.nrg.xsync.tools.XSyncTools;
import org.nrg.xsync.tools.XsyncXnatInfo;
import org.nrg.xsync.utils.ConflictCheckUtil;
import org.nrg.xsync.utils.QueryResultUtil;
import org.nrg.xsync.utils.XsyncUtils;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public class BatchExperimentSync {

    boolean syncAllStates;
    XnatSubjectdataI localSubject;
    ProjectSyncConfiguration projectSyncConfiguration;
    UserI user;
    SubjectSyncItem subjectSyncInfo;
    private final XsyncXnatInfo xnatInfo;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final RemoteConnectionManager manager;
    private final QueryResultUtil queryResultUtil;
    private List<XnatAbstractresource> subjectResourcesToBeVerified = new ArrayList<XnatAbstractresource>();
    private XnatProjectdata localProject;
    private final SerializerService serializer;
    private final SyncStatusService syncStatusService;


    public BatchExperimentSync(final RemoteConnectionManager manager, final XsyncXnatInfo xnatInfo, final QueryResultUtil queryResultUtil,
                               final NamedParameterJdbcTemplate jdbcTemplate, XnatSubjectdataI localSubject, final ProjectSyncConfiguration projectSyncConfiguration,
                               final UserI user, final boolean syncAll, final SubjectSyncItem subjectSyncInfo, final SerializerService serializer, final SyncStatusService syncStatusService) {
        this.localSubject = localSubject;
        this.user = user;
        this.projectSyncConfiguration = projectSyncConfiguration;
        this.syncAllStates = syncAll;
        this.subjectSyncInfo = subjectSyncInfo;
        this.jdbcTemplate = jdbcTemplate;
        this.manager = manager;
        this.xnatInfo = xnatInfo;
        this.queryResultUtil = queryResultUtil;
        localProject = XnatProjectdata.getXnatProjectdatasById(localSubject.getProject(), user, false);
        this.serializer = serializer;
        this.syncStatusService = syncStatusService;
    }

    /**
     * For a given subject, whose metadata is already synced, this method will sync the experiments
     *
     * @param remoteSubject         The subject on the destination XNAT to which the experiments need to be associated with
     * @param experimentsToBeSynced The list of experiments to be synced
     * @throws Exception
     */
    public void syncExperiments(XnatSubjectdataI remoteSubject, Map<String, List<XnatExperimentdataI>> experimentsToBeSynced) throws Exception {
        final String remoteProjectId = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteProjectId();
        final List<XnatExperimentdataI> deletedExperiments = experimentsToBeSynced.get(QueryResultUtil.DELETE_STATUS);
        final List<XnatExperimentdataI> updatedExperiments = experimentsToBeSynced.get(QueryResultUtil.ACTIVE_STATUS);
        final List<XnatExperimentdataI> newExperiments = experimentsToBeSynced.get(QueryResultUtil.NEW_STATUS);
        final List<XnatExperimentdataI> okToSyncExperiments = experimentsToBeSynced.get(QueryResultUtil.OK_TO_SYNC_STATUS);
        final List<XnatExperimentdataI> failedSyncExperiments = experimentsToBeSynced.get(QueryResultUtil.FAILED_STATUS);
        final List<XnatExperimentdataI> syncCalledExperiments = new ArrayList<>();

        if (syncAllStates) {
            //Delete experiments
            if (deletedExperiments != null && deletedExperiments.size() > 0) {
                //Remove each of these resources from the Remote site
                for (final XnatExperimentdataI experiment : deletedExperiments) {
                    if (experiment.getXSIType().startsWith("xsync:")) {
                        continue;
                    }
                    try {
                        final XnatExperimentdata exp = (XnatExperimentdata) experiment;
                        exp.setProject(remoteProjectId);
                        exp.getItem().setProperty("subject_ID", remoteSubject.getId());
                        this.deleteExperiment(exp);
                    } catch (Exception e) {
                        log.error("Could not delete experiment {} for subject {}", experiment.getId(), remoteSubject.getId(), e);
                    }
                }
            }
            //Update the modified experiments
            if (updatedExperiments != null && updatedExperiments.size() > 0) {
                for (final XnatExperimentdataI experiment : updatedExperiments) {
                    pushExperiment(experiment, remoteSubject, false);
                    syncCalledExperiments.add(experiment);
                }
            }
        }
        //Push the new experiments
        if (newExperiments != null && newExperiments.size() > 0) {
            for (final XnatExperimentdataI experiment : newExperiments) {
                if (!experimentExistsAtRemoteSiteWhenPushingNew(experiment)) {
                    pushExperiment(experiment, remoteSubject, true);
                    syncCalledExperiments.add(experiment);
                } else {
                    final ExperimentSyncItem expSyncItem = new ExperimentSyncItem(experiment.getId(), experiment.getLabel());
                    expSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_SKIPPED);
                    // TODO:  Should probably separate out the last case.  Maybe report on the last status.
                    expSyncItem.setMessage("Appears that the session was transferred without using XSync, that the configured " +
                            "XSync remote ID has changed since this session was sent or that this session was " +
                            "sent as part of a failed sync process.");
                    subjectSyncInfo.addExperiment(expSyncItem);
                }
            }
        }
        //Irrespective of the syncOnlyNwew Flag, the OK to Sync experiments must be pushed
        if (okToSyncExperiments != null && okToSyncExperiments.size() > 0) {
            for (final XnatExperimentdataI experiment : okToSyncExperiments) {
                pushExperiment(experiment, remoteSubject, false);
                syncCalledExperiments.add(experiment);
            }
        }
        // If it hasn't already been synced, sync any experiments with a failed sync status
        for (final XnatExperimentdataI experiment : failedSyncExperiments) {
            if (!syncCalledExperiments.contains(experiment)) {
                pushExperiment(experiment, remoteSubject, false);
            }
        }
    }

    /**
     * Sync an experiment
     *
     * @param assess                - The experiment to sync
     * @param remoteSubject         - The subject on the destination XNAT to associate the experiment to
     * @param syncIfNotSyncedInPast - Flag to decide if the experiment is to be synced only if its not synced in the past
     * @throws Exception
     */

    public void pushExperiment(XnatExperimentdataI assess, XnatSubjectdataI remoteSubject, boolean syncIfNotSyncedInPast) throws Exception {
        XsyncExperimentTransfer syncExptransfer = new XsyncExperimentTransfer(manager, xnatInfo, queryResultUtil, jdbcTemplate, projectSyncConfiguration, user,
                subjectSyncInfo, localSubject, serializer, syncStatusService, localProject, syncIfNotSyncedInPast);
        syncExptransfer.syncExperiment(assess, remoteSubject);
    }


    private RemoteConnectionResponse deleteExperiment(XnatExperimentdata experiment) throws Exception {
        //If the experiment was already stored, we have the remote id
        //Use that id to delete the experiment
        //If not, the experiment was never synced. So ignore.
        IdMapper idMapper = new IdMapper(manager, queryResultUtil, jdbcTemplate, user, projectSyncConfiguration);
        String remoteId = idMapper.getRemoteAccessionId(experiment.getId());
        String localId = experiment.getId();
        String localLabel = experiment.getLabel();
        ExperimentSyncItem expSyncItem = new ExperimentSyncItem(experiment.getId(), experiment.getLabel());
        expSyncItem.setRemoteId(remoteId);
        if (remoteId != null) {
            experiment.setId(remoteId);
            ConflictCheckUtil.checkForConflict(experiment, remoteId, projectSyncConfiguration,
                    jdbcTemplate, queryResultUtil, manager);
            try {
                RemoteConnectionHandler remoteConnectionHandler = new RemoteConnectionHandler(jdbcTemplate, queryResultUtil);
                RemoteConnection connection = remoteConnectionHandler.getConnection(projectSyncConfiguration.getProject().getId(), projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl());
                RemoteConnectionResponse response = manager.deleteExperiment(connection, experiment);
                if (response.wasSuccessful()) {
                    expSyncItem.setMessage("Subject " + localSubject.getLabel() + " experiment " + localLabel + " deleted. ");
                    expSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_DELETED);
                    XSyncTools xsyncTools = new XSyncTools(user, jdbcTemplate, queryResultUtil);
                    xsyncTools.deleteXsyncRemoteEntry(this.localSubject.getProject(), localId);
                } else {
                    expSyncItem.setMessage("Subject " + localSubject.getLabel() + " experiment " + localLabel + " could not be deleted. " + response.getResponseBody());
                    expSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_FAILED);
                }
                subjectSyncInfo.addExperiment(expSyncItem);
                return response;
            } catch (Exception e) {
                log.error("Error deleting experiment {}", experiment.getLabel(), e);
                expSyncItem.setMessage("Subject " + localSubject.getLabel() + " experiment " + localLabel + " could not be deleted. " + e.getMessage());
                expSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_FAILED);
                subjectSyncInfo.addExperiment(expSyncItem);
                throw e;
            }
        } else {
            return null;
        }
    }


    private boolean experimentExistsAtRemoteSiteWhenPushingNew(XnatExperimentdataI experiment) throws Exception {
        final String remoteUrl = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl();
        final String remoteProjectId = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteProjectId();
        final String uri = remoteUrl + "/data/archive/experiments?xnat:imagesessiondata/project=" + remoteProjectId + "&xnat:mrsessiondata/label=" + experiment.getLabel() + "&format=json&offset=*";
        final XsyncProjectVerifier projectVerifier = new XsyncProjectVerifier(manager, queryResultUtil, jdbcTemplate, projectSyncConfiguration, serializer);
        final RemoteConnectionResponse response = projectVerifier.get(uri);
        if (!response.wasSuccessful()) {
            throw new XsyncRemoteConnectionException("ERROR:  Could not check remote site for experiment");
        }
        final Type type = new TypeToken<Map<String, Map<String, Object>>>() {}.getType();
        final Map<String, Map<String, Object>> configMap = new Gson().fromJson(response.getResponseBody(), type);
        if (configMap.containsKey("ResultSet") && configMap.get("ResultSet").containsKey("totalRecords")) {
            final Integer totalRecords = Integer.valueOf(configMap.get("ResultSet").get("totalRecords").toString());
            if (totalRecords >= 1) {
                return true;
            }
        } else {
            throw new XsyncRemoteConnectionException("ERROR:  Could not check remote site for experiment (unexpected results)");
        }
        return false;
    }

}
