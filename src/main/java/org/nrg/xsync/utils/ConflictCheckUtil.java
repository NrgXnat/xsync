package org.nrg.xsync.utils;

import java.util.List;
import java.util.Map;

import org.apache.commons.collections.CollectionUtils;
import org.nrg.xdat.model.XnatExperimentdataI;
import org.nrg.xdat.model.XnatSubjectdataI;
import org.nrg.xsync.configuration.ProjectSyncConfiguration;
import org.nrg.xsync.connection.RemoteConnection;
import org.nrg.xsync.connection.RemoteConnectionHandler;
import org.nrg.xsync.connection.RemoteConnectionManager;
import org.nrg.xsync.connection.RemoteConnectionResponse;
import org.nrg.xsync.exception.XsyncConflictCheckFailureException;
import org.nrg.xsync.exception.XsyncIdConflictException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class ConflictCheckUtil {
	
	private static Gson _gson = new Gson();
	
	public static void checkForConflict(XnatSubjectdataI subject, String remoteId,
			ProjectSyncConfiguration projectSyncConfiguration, NamedParameterJdbcTemplate jdbcTemplate,
			QueryResultUtil queryResultUtil, RemoteConnectionManager manager
			) throws Exception {
        final String remoteProjectId = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteProjectId();
        final String remoteUrl = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl();
		final String uri = remoteUrl +"/data/subjects?format=json&ID="+ remoteId + "&columns=ID,project,label,URI&offset=*";
		final RemoteConnectionHandler remoteConnectionHandler = new RemoteConnectionHandler(jdbcTemplate, queryResultUtil);
		final RemoteConnection connection = remoteConnectionHandler.getConnection(projectSyncConfiguration.getProject().getId(), projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl());
		final RemoteConnectionResponse connectionResponse = manager.getResult(connection,uri);
		if (connectionResponse.wasSuccessful()) {
			final List<Map<String, String>> result = getResultList(connectionResponse);;
			if (!CollectionUtils.isEmpty(result)) {
				final String remoteSessionLabel = result.getFirst().get("label");
				final String remoteSessionProject = result.getFirst().get("project");
				if (remoteSessionLabel != null && remoteSessionProject != null) {
					if (subject.getLabel().equals(remoteSessionLabel) && remoteProjectId.equals(remoteSessionProject)) {
						return;
					} else {
						throw new XsyncIdConflictException("Subject " + remoteId + 
								" exists at destination with a different label or in a different project.");
					}
				}
			} else {
				return;
			}
		}
		throw new XsyncConflictCheckFailureException("Could not check for subject label conflict (SUBJECT=" + subject.getLabel() + ")");
	}
	
	public static void checkForConflict(XnatExperimentdataI exp, String remoteId,
			ProjectSyncConfiguration projectSyncConfiguration, NamedParameterJdbcTemplate jdbcTemplate,
			QueryResultUtil queryResultUtil, RemoteConnectionManager manager
			) throws Exception {
        final String remoteProjectId = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteProjectId();
        final String remoteUrl = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl();
		final String uri = remoteUrl +"/data/experiments?format=json&ID="+ remoteId + "&columns=ID,project,label,URI&offset=*";
		final RemoteConnectionHandler remoteConnectionHandler = new RemoteConnectionHandler(jdbcTemplate, queryResultUtil);
		final RemoteConnection connection = remoteConnectionHandler.getConnection(projectSyncConfiguration.getProject().getId(), projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl());
		final RemoteConnectionResponse connectionResponse = manager.getResult(connection,uri);
		if (connectionResponse.wasSuccessful()) {
			final List<Map<String, String>> result = getResultList(connectionResponse);
			if (result == null || result.isEmpty()) {
				return;
			}
			final String remoteSessionLabel = result.getFirst().get("label");
			final String remoteSessionProject = result.getFirst().get("project");
			if (exp.getLabel().equals(remoteSessionLabel) && remoteProjectId.equals(remoteSessionProject)) {
				return;
			} else {
				throw new XsyncIdConflictException("Experiment " + remoteId +
						" exists at destination with a different label or in a different project.");
			}
		}
		throw new XsyncConflictCheckFailureException("Could not check for experiment label conflict (EXPERIMENT=" + exp.getLabel() + ")");
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, String>> getResultList(RemoteConnectionResponse connectionResponse) throws XsyncConflictCheckFailureException {
		final String responseBody = connectionResponse.getResponseBody();
		final Map<String,Map<String,Object>> responseObj =
				_gson.fromJson(responseBody, new TypeToken<Map<String,Map<String,Object>>>(){}.getType());
		if (responseObj == null) {
			return null;
		}
		final Map<String, Object> resultSet = responseObj.get("ResultSet");
		if (resultSet == null) {
			return null;
		}
		final Object resultObj = resultSet.get("Result");
		if (resultObj instanceof List) {
			return (List<Map<String,String>>)resultObj;
		}
		throw new XsyncConflictCheckFailureException("Failed to get results list");
	}

}
