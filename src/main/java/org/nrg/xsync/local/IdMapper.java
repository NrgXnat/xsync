package org.nrg.xsync.local;

import java.util.List;

import org.nrg.xdat.XDAT;
import org.nrg.xdat.model.XnatExperimentdataI;
import org.nrg.xdat.model.XnatExperimentdataShareI;
import org.nrg.xdat.om.XnatExperimentdata;
import org.nrg.xdat.om.XnatProjectparticipant;
import org.nrg.xdat.om.XnatSubjectdata;
import org.nrg.xft.XFTItem;
import org.nrg.xft.security.UserI;
import org.nrg.xsync.configuration.ProjectSyncConfiguration;
import org.nrg.xsync.connection.RemoteConnection;
import org.nrg.xsync.connection.RemoteConnectionManager;
import org.nrg.xsync.connection.RemoteConnectionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Mohana Ramaratnam
 *
 */
public class IdMapper {
	private static final Logger _log = LoggerFactory.getLogger(IdMapper.class);
	
	public static final String USE_LOCAL = "use_local";
	public static final String USE_REMOTE = "use_remote";
	public static final String USE_RANDOM = "use_random";
	public static final String USE_CUSTOM = "use_custom";
	UserI _user;
	ProjectSyncConfiguration syncProjectConfiguration;
	
	public IdMapper(UserI user,ProjectSyncConfiguration syncProjectConfiguration) {
		_user = user;
		this.syncProjectConfiguration = syncProjectConfiguration;
	}

	
	private String assignRemoteLabel(XFTItem item) {
		String remote_label = null;
		if (USE_LOCAL.equals(syncProjectConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getIdentifiers())) {
			try {
				remote_label = item.getStringProperty("label");
			}catch(Exception e) {
				
			}
		}
		//TODO other cases
		return remote_label;
	}
	
	private String getAssignedRemoteId(String localXnatId) {
		String remoteId = null;
		String query = "select remote_xnat_id from xsync_xsyncremotemapdata";
		query += " where local_xnat_id=:localXnatId";
		MapSqlParameterSource parameters = new MapSqlParameterSource();
		parameters = new MapSqlParameterSource();
		parameters.addValue("localXnatId", localXnatId);
		NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(
				new JdbcTemplate(XDAT.getDataSource()));
		 List<String> results = jdbcTemplate.queryForList(query, parameters,String.class);
		 if (results !=null && results.size()>=1) {
			 remoteId = results.get(0);
		 }
		return remoteId;
	}

	/**
	 * Correct id and label.
	 *
	 * @param newSubject
	 *            the new subject
	 * @return corrected id
	 * @throws Exception
	 *             the exception
	 */
	public void correctIDandLabel(XnatSubjectdata newSubject) throws Exception {

		// correct ID
		String remoteId = this.getAssignedRemoteId(newSubject.getId());
		if (remoteId != null) //Subject has already been synced and so we have a remote id
			newSubject.setId(remoteId);
		else
			// Let the remote site assign the ID
			newSubject.setId("");
			// correct shared projects
		String remoteLabel = assignRemoteLabel(newSubject.getItem());
		newSubject.setLabel(remoteLabel);
		List<XnatProjectparticipant> sharedProjects = newSubject.getSharing_share();
		if (sharedProjects != null && sharedProjects.size() >0) {
			int i = 0;
			int total_projects_shared_into = sharedProjects.size(); 
			while(total_projects_shared_into > 0) {
				XnatProjectparticipant sharedProject = sharedProjects.get(i);
				newSubject.removeSharing_share(i);;
				total_projects_shared_into = newSubject.getSharing_share().size();
			}
		}
	}

	public void correctIDandLabel(XnatExperimentdataI targetExperiment, String targetSubjectLabel) throws Exception {
		// correct ID
		String remoteId = this.getAssignedRemoteId(targetExperiment.getId());
		if (remoteId != null) //Subject has already been synced and so we have a remote id
			targetExperiment.setId(remoteId);
		else
			// Let the remote site assign the ID
			targetExperiment.setId("");
			// correct shared projects
		String remoteLabel = assignRemoteLabel(((XnatExperimentdata)targetExperiment).getItem());
		targetExperiment.setLabel(remoteLabel);
		// correct shared projects
		for (XnatExperimentdataShareI share : targetExperiment.getSharing_share()) {
				share.setLabel("");
		}
	}
	
	public String getRemoteAccessionId(String localAccessionId) {
		String remoteId = null;
		remoteId = this.getAssignedRemoteId(localAccessionId);
		return remoteId;
	}
	
	public String getRemoteId(String remoteUrl, String remoteProjectId, String remoteSubjectLabel, String remoteEntityLabel,String xsiType) {
		 String remote_id = null;
		 String uri = remoteUrl +"/data/archive/projects/" + remoteProjectId +"/subjects/"+ remoteSubjectLabel + "/experiments?format=json&columns=ID,label&xsiType="+xsiType;
		 RemoteConnectionManager remoteConnectionManager = new RemoteConnectionManager();
		 RemoteConnection connection = remoteConnectionManager.getConnection(remoteUrl, remoteProjectId);
		 RemoteConnectionResponse connectionResponse = remoteConnectionManager.getResult(connection,uri);
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
					if (label != null)  {
						if (label.asText().equals(remoteEntityLabel)){
							remote_id = resultElt.get("ID").asText();	 
							break;
						}
					}
				}
			}catch(Exception e) {
				_log.debug(e.getLocalizedMessage());
			}
		 }
		 return remote_id;
	}
	
}
