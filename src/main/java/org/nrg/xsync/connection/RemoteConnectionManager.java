package org.nrg.xsync.connection;

import java.io.File;
import java.io.IOException;
import java.util.Hashtable;

import org.apache.commons.codec.binary.Base64;
import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;
import org.nrg.xdat.XDAT;
import org.nrg.xdat.entities.AliasToken;
import org.nrg.xdat.om.XnatExperimentdata;
import org.nrg.xdat.om.XnatSubjectassessordata;
import org.nrg.xdat.om.XnatSubjectdata;
import org.nrg.xnat.services.xsync.remote.RemoteRESTService;
import org.nrg.xsync.exception.XsyncRemoteConnectionException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * @author Mohana Ramaratnam
 *
 */
public class RemoteConnectionManager {
	/** The logger. */
	public static Logger logger = Logger.getLogger(RemoteConnectionManager.class);

	private final RemoteRESTService remoteRESTService = XDAT.getContextService().getBean(RemoteRESTService.class);

	static Hashtable<String,RemoteConnection> remoteConnections = new Hashtable<String, RemoteConnection>();

	
	public static Hashtable<String,RemoteConnection> GetAllConnections() {
		return remoteConnections;
	}

	public static RemoteConnection getConnection(String projectId) throws XsyncRemoteConnectionException{
		RemoteConnection conn = remoteConnections.get(projectId);
		if (conn.isLocked()) {
			//Scheduler may be acquiring a token
			//Wait for a minute?
			try {
			    Thread.sleep(60000);                 //1000 milliseconds is one second.
			} catch(InterruptedException ex) {
			    Thread.currentThread().interrupt();
			}
		}
		if (conn.isLocked()) {
			throw new XsyncRemoteConnectionException("Unable to clear lock for connection " + conn.getUrl() + " Project: " + projectId);
		}
		//Hopefully by now the aliasToken has been acquired
		return conn;
	}

	public static void setConnection(String projectId, RemoteConnection conn){
		remoteConnections.put(projectId, conn);
	}

	public void saveConnection(String projectId, String remoteUrl, String userName, String password) {
		RemoteConnection conn = new RemoteConnection();
		conn.setUrl(remoteUrl);
		try {
			RemoteConnection tempConn = new RemoteConnection();
			tempConn.setUsername(userName);
			tempConn.setPassword(password);
			HttpEntity<String> request = new HttpEntity<String>(getAuthHeaders(tempConn));
			SimpleClientHttpRequestFactory requestFactory =new SimpleClientHttpRequestFactory();
			RestTemplate template = new RestTemplate(requestFactory);
			ResponseEntity<String> response = template.exchange(conn.getUrl()+"/data/services/tokens/issue", HttpMethod.GET, request, String.class);
			AliasToken aliasToken = (AliasToken) new ObjectMapper().readValue(response.getBody(), AliasToken.class);		
			conn.setUsername(aliasToken.getAlias());
			conn.setPassword(aliasToken.getSecret());
			conn.setAcquiredDate();
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException("Failed to get remote connection.");
		}
	}



	/**
	 * Gets the auth headers.
	 *
	 * @param connection the connection
	 * @return the auth headers
	 */
	public static HttpHeaders getAuthHeaders(RemoteConnection connection){
		HttpHeaders headers = new HttpHeaders();
		headers.add("Authorization", "Basic " + getBase64Credentials(connection));
		return headers;
	}

	/**
	 * Gets the base64 credentials.
	 *
	 * @param conn the conn
	 * @return the base64 credentials
	 */
	protected static String getBase64Credentials(RemoteConnection conn) {
		String plainCreds;
		plainCreds = conn.getUsername()+":"+conn.getPassword();
		byte[] plainCredsBytes = plainCreds.getBytes();
		byte[] base64CredsBytes = Base64.encodeBase64(plainCredsBytes);
		String base64Creds = new String(base64CredsBytes);
		return base64Creds;
	}


	public RemoteConnectionResponse importSubject(RemoteConnection connection, XnatSubjectdata subject) {
		return remoteRESTService.importSubject(connection, subject);
	}

	public RemoteConnectionResponse deleteSubject(RemoteConnection connection, XnatSubjectdata subject) {
		return remoteRESTService.deleteSubject(connection, subject);
	}

	public RemoteConnectionResponse deleteSubjectResource(RemoteConnection connection, XnatSubjectdata subject, String resourceLabel) {
		return remoteRESTService.deleteSubjectResource(connection, subject, resourceLabel);
	}
	public RemoteConnectionResponse deleteProjectResource(RemoteConnection connection, String projectId, String resourceLabel) {
		return remoteRESTService.deleteProjectResource(connection, projectId, resourceLabel);
	}
	public RemoteConnectionResponse importProjectResource(RemoteConnection connection, String projectId, String resourceLabel, File zipFile) {
		return remoteRESTService.importProjectResource(connection, projectId, resourceLabel, zipFile);
	}

	public RemoteConnectionResponse importSubjectResource(RemoteConnection connection, XnatSubjectdata subject, String resourceLabel, File zipFile) {
		return remoteRESTService.importSubjectResource(connection, subject, resourceLabel, zipFile);
	}

	public RemoteConnectionResponse deleteExperiment(RemoteConnection connection, XnatExperimentdata experiment) {
		return remoteRESTService.deleteExperiment(connection, experiment);
	}

	public RemoteConnectionResponse importSubjectAssessor(RemoteConnection connection,XnatSubjectdata subject,XnatSubjectassessordata assessor ) {
		return remoteRESTService.importSubjectAssessor(connection, subject,assessor);
	}
	public RemoteConnectionResponse importSubjectAssessorResource(RemoteConnection connection,XnatSubjectdata subject,XnatSubjectassessordata assessor, String resourceLabel, File zipFile ) {
		return remoteRESTService.importSubjectAssessorResource(connection, subject,assessor, resourceLabel, zipFile);
	}

	public RemoteConnectionResponse importXar(RemoteConnection connection,File xar) {
		return remoteRESTService.importXar(connection, xar);
	}

	public RemoteConnectionResponse getResult(RemoteConnection connection,String uri) {
		return remoteRESTService.getResult(connection,uri);
	}

}
