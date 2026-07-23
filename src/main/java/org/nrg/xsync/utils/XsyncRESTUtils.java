/**
 * 
 */
package org.nrg.xsync.utils;

import org.nrg.xdat.om.XsyncXsyncinfodata;
import org.nrg.xsync.configuration.ProjectSyncConfiguration;
import org.nrg.xsync.connection.RemoteConnection;
import org.nrg.xsync.connection.RemoteConnectionHandler;
import org.nrg.xsync.connection.RemoteConnectionManager;
import org.nrg.xsync.connection.RemoteConnectionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

// TODO: Auto-generated Javadoc
/**
 * The Class XsyncRESTUtils.
 *
 * @author Atul
 */
public class XsyncRESTUtils {

	/** The Constant _log. */
	private static final Logger _log = LoggerFactory.getLogger(XsyncRESTUtils.class);

	/** The project sync configuration. */
	private ProjectSyncConfiguration projectSyncConfiguration;

	/** The jdbc template. */
	private final NamedParameterJdbcTemplate _jdbcTemplate;

	/** The manager. */
	private final RemoteConnectionManager _manager;

	/** The query result util. */
	private final QueryResultUtil _queryResultUtil;

	/**
	 * Instantiates a new xsync REST utils.
	 *
	 * @param manager
	 *            the manager
	 * @param queryResultUtil
	 *            the query result util
	 * @param jdbcTemplate
	 *            the jdbc template
	 * @param projectSyncConfiguration
	 *            the project sync configuration
	 */
	public XsyncRESTUtils(final RemoteConnectionManager manager, final QueryResultUtil queryResultUtil,
			final NamedParameterJdbcTemplate jdbcTemplate, ProjectSyncConfiguration projectSyncConfiguration) {
		this.projectSyncConfiguration = projectSyncConfiguration;
		this._manager = manager;
		this._queryResultUtil = queryResultUtil;
		_jdbcTemplate = jdbcTemplate;
	}

	/**
	 * Gets the remote experiment id.
	 *
	 * @param remoteSubjectLabel
	 *            the remote subject label
	 * @param remoteExperimentLabel
	 *            the remote experiment label
	 * @param xsiType
	 *            the xsi type
	 * @return the remote experiment id or null if none found
	 */
	public String getRemoteExperimentId(String remoteSubjectLabel, String remoteExperimentLabel, String xsiType) {
		String expId = null;
		XsyncXsyncinfodata syncInfo = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo();
		String remoteProjectId = syncInfo.getRemoteProjectId();
		try {
			RemoteConnectionHandler remoteConnectionHandler = new RemoteConnectionHandler(_jdbcTemplate,
					_queryResultUtil);
			RemoteConnection connection = remoteConnectionHandler.getConnection(
					projectSyncConfiguration.getProject().getId(),
					syncInfo.getRemoteUrl());
			String uri = projectSyncConfiguration.getSynchronizationConfiguration().getRemote_url()
					+ "/data/archive/projects/" + remoteProjectId + "/subjects/" + remoteSubjectLabel + "/experiments/"
					+ remoteExperimentLabel + "?format=json&columns=ID,label";
			RemoteConnectionResponse connectionResponse = _manager.getResult(connection, uri);
			if (connectionResponse.wasSuccessful()) {
				String jsonStr = connectionResponse.getResponseBody();
				ObjectMapper mapper = new ObjectMapper();

				JsonNode userSelectionAsPOJO = mapper.readValue(jsonStr, JsonNode.class);
				JsonNode resultSet = userSelectionAsPOJO.get("items");
				for (int i = 0; i < resultSet.size(); i++) {
					JsonNode resultElt = resultSet.get(i);
					JsonNode dataFields = resultElt.get("data_fields");
					JsonNode meta = resultElt.get("meta");
					String remoteXsiType = meta.get("xsi:type").asText();
					String label = dataFields.get("label").asText();
					String ID = dataFields.get("ID").asText();
					if (label.equals(remoteExperimentLabel) && xsiType.equals(remoteXsiType)) {
						expId = ID;
						break;
					}
				}
			}
		} catch (Exception e) {
			_log.error("Experiment not available on remote subject.");
			_log.debug(e.getLocalizedMessage());
		}
		return expId;
	}
}
