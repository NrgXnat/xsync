package org.nrg.xsync.local;

import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.nrg.xdat.XDAT;
import org.nrg.xdat.model.XnatExperimentdataShareI;
import org.nrg.xdat.model.XnatSubjectdataI;
import org.nrg.xdat.om.*;
import org.nrg.xft.XFTItem;
import org.nrg.xft.security.UserI;
import org.nrg.xnat.xsync.generator.XsyncLabelGeneratorI;
import org.nrg.xsync.configuration.ProjectSyncConfiguration;
import org.nrg.xsync.connection.RemoteConnection;
import org.nrg.xsync.connection.RemoteConnectionHandler;
import org.nrg.xsync.connection.RemoteConnectionManager;
import org.nrg.xsync.connection.RemoteConnectionResponse;
import org.nrg.xsync.exception.XsyncRemoteConnectionException;
import org.nrg.xsync.utils.ConflictCheckUtil;
import org.nrg.xsync.utils.QueryResultUtil;
import org.nrg.xsync.utils.XsyncRESTUtils;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.annotation.Nullable;

/**
 * @author Mohana Ramaratnam
 */
@Slf4j
public class IdMapper {
    public static final String USE_LOCAL = "use_local";
    public static final String USE_CUSTOM = "use_custom";
    UserI _user;
    ProjectSyncConfiguration projectSyncConfiguration;
    private final RemoteConnectionManager _manager;
    private final QueryResultUtil _queryResultUtil;
    private final NamedParameterJdbcTemplate _jdbcTemplate;
    private final Map<String, XsyncLabelGeneratorI> idGenerators;

    public IdMapper(final RemoteConnectionManager manager, final QueryResultUtil queryResultUtil, final NamedParameterJdbcTemplate jdbcTemplate, final UserI user, final ProjectSyncConfiguration projectSyncConfiguration) {
        _manager = manager;
        _queryResultUtil = queryResultUtil;
        _jdbcTemplate = jdbcTemplate;
        _user = user;
        this.projectSyncConfiguration = projectSyncConfiguration;
        this.idGenerators = XDAT.getContextService().getBeansOfType(XsyncLabelGeneratorI.class);
    }


    private String assignRemoteLabel(XFTItem item) throws Exception {
        XsyncXsyncinfodata syncInfo = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo();
        String beanId = USE_CUSTOM.equals(syncInfo.getIdentifiers()) ? syncInfo.getCustomIdentifierClass() : null;
        String remoteLabel = null;
        final XsyncLabelGeneratorI idGenerator;
        if (StringUtils.isNotBlank(beanId) && (idGenerator = idGenerators.get(beanId)) != null) {
            remoteLabel = idGenerator.generateId(_user, item, projectSyncConfiguration);
        }

        if (StringUtils.isBlank(remoteLabel)) {
            try {
                remoteLabel = item.getStringProperty("label");
            } catch (Exception e) {
                log.error("Issue reading label field", e);
            }
        }
        return remoteLabel;
    }

    private String getAssignedRemoteId(String localXnatId) {
        String remoteId = null;
        String remoteProjectId = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteProjectId();
        String remoteUrl = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl();
        String query = "select remote_xnat_id from xsync_xsyncremotemapdata";
        query += " where local_xnat_id=:localXnatId and remote_project_id=:remoteProjectId and remote_host_url=:remoteUrl";
        final MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("localXnatId", localXnatId);
        parameters.addValue("remoteProjectId", remoteProjectId);
        parameters.addValue("remoteUrl", remoteUrl);
        List<String> results = _jdbcTemplate.queryForList(query, parameters, String.class);
        if (results != null && results.size() >= 1) {
            remoteId = results.get(0);
        }
        return remoteId;
    }

    /**
     * Correct id and label.
     *
     * @param newSubject the new subject
     * @throws Exception the exception
     */
    public void correctIDandLabel(XnatSubjectdata newSubject) throws Exception {
        // Update label
        String remoteLabel = assignRemoteLabel(newSubject.getItem());
        newSubject.setLabel(remoteLabel);

        // Correct ID
        String remoteId = this.getAssignedRemoteId(newSubject.getId());
        if (remoteId != null) { //Subject has already been synced and so we have a remote id
            newSubject.setId(remoteId);
            ConflictCheckUtil.checkForConflict(newSubject, remoteId, projectSyncConfiguration,
                    _jdbcTemplate, _queryResultUtil, _manager);
        } else {
            // Let the remote site assign the ID
            newSubject.setId("");
            // correct shared projects
        }
        List<XnatProjectparticipant> sharedProjects = newSubject.getSharing_share();
        if (sharedProjects != null && sharedProjects.size() > 0) {
            int i = 0;
            int total_projects_shared_into = sharedProjects.size();
            while (total_projects_shared_into > 0) {
                newSubject.removeSharing_share(i);
                total_projects_shared_into = newSubject.getSharing_share().size();
            }
        }
    }

    public void correctIDandLabel(XnatExperimentdata targetExperiment, XnatSubjectdataI targetSubject)
            throws Exception {
        // Update label
        String sourceLabel = targetExperiment.getLabel();
        String remoteLabel = assignRemoteLabel(targetExperiment.getItem());
        targetExperiment.setLabel(remoteLabel);

        // Correct ID
        String alreadyAssignedRemoteId = getRemoteAccessionIdFromDbOrRest(targetExperiment, sourceLabel,
                targetSubject, new XsyncRESTUtils(_manager, _queryResultUtil, _jdbcTemplate, projectSyncConfiguration));

        String newId = "";
        if (alreadyAssignedRemoteId != null) {
            ConflictCheckUtil.checkForConflict(targetExperiment, alreadyAssignedRemoteId, projectSyncConfiguration,
                    _jdbcTemplate, _queryResultUtil, _manager);
            newId = alreadyAssignedRemoteId;
        }
        targetExperiment.setId(newId);

        if (targetExperiment instanceof XnatSubjectassessordata) {
            ((XnatSubjectassessordata) targetExperiment).setSubjectId(targetSubject.getLabel());
        }

        // correct shared projects
        for (XnatExperimentdataShareI share : targetExperiment.getSharing_share()) {
            if (share.getLabel() != null) {
                share.setLabel("");
            }
        }

    }

    @Nullable
    private String getRemoteAccessionIdFromDbOrRest(XnatExperimentdata targetExperiment, String sourceLabel,
                                                    XnatSubjectdataI targetSubject, XsyncRESTUtils restUtil) {
        String alreadyAssignedRemoteId = getAssignedRemoteId(targetExperiment.getId());
        log.debug("correctIDandLabel (experiment=" + sourceLabel +
                "): returned alreadyAssignedRemoteID(idMapper.getRemoteAssessionId)=" + alreadyAssignedRemoteId);
        if (alreadyAssignedRemoteId == null) {
            alreadyAssignedRemoteId = restUtil.getRemoteExperimentId(targetSubject.getId(),
                    targetExperiment.getLabel(), targetExperiment.getXSIType());
            log.debug("correctIDandLabel (experiment=" + sourceLabel +
                    "): returned alreadyAssignedRemoteID(restUtil.getRemoteExperimentId)=" + alreadyAssignedRemoteId);
        }

        // WORKAROUND (TEMPORARY???):  We've had some cases where experiments have been assigned the accession number of
        // the subject. It's not clear why that is happening, but it causes a lot of problems when it does (one session overwrites
        // the other). Let's set the id to null if it looks like a subject accession number
        if (alreadyAssignedRemoteId != null && alreadyAssignedRemoteId.matches("^.*_S[0-9]*$")) {
            log.error("ERROR: Experiment appears to have been assigned a subject accession number. Setting it to null" +
                    " so a new one is assigned");
            alreadyAssignedRemoteId = null;
        }
        return alreadyAssignedRemoteId;
    }

    public String getRemoteAccessionId(String localAccessionId) {
        return getAssignedRemoteId(localAccessionId);
    }

    public String getRemoteId(String remoteUrl, String remoteProjectId, String remoteSubjectLabel, String remoteEntityLabel, String xsiType) throws XsyncRemoteConnectionException, Exception {
        String remote_id = null;
        String uri = remoteUrl + "/data/archive/projects/" + remoteProjectId + "/subjects/" + remoteSubjectLabel + "/experiments?format=json&columns=ID,label&xsiType=" + xsiType;
        final RemoteConnectionHandler remoteConnectionHandler = new RemoteConnectionHandler(_jdbcTemplate, _queryResultUtil);
        RemoteConnection connection = remoteConnectionHandler.getConnection(projectSyncConfiguration.getProject().getId(), projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl());
        RemoteConnectionResponse connectionResponse = _manager.getResult(connection, uri);
        if (connectionResponse.wasSuccessful()) {
            //Parse the returned JSON
            // {"ResultSet":{"Result":[{"ID":"XNAT_E00059","label":"MR1S1","URI":"/data/experiments/XNAT_E00059","xnat:mrsessiondata/id":"XNAT_E00059"}], "totalRecords": "1"}}
            String jsonStr = connectionResponse.getResponseBody();
            ObjectMapper mapper = new ObjectMapper();
            try {
                JsonNode userSelectionAsPOJO = mapper.readValue(jsonStr, JsonNode.class);
                JsonNode resultSet = userSelectionAsPOJO.get("ResultSet");
                JsonNode resultArray = resultSet.get("Result");
                for (int i = 0; i < resultArray.size(); i++) {
                    JsonNode resultElt = resultArray.get(i);
                    JsonNode label = resultElt.get("label");
                    if (label != null) {
                        if (label.asText().equals(remoteEntityLabel)) {
                            remote_id = resultElt.get("ID").asText();
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                log.debug(e.getLocalizedMessage());
            }
        }
        return remote_id;
    }

}
