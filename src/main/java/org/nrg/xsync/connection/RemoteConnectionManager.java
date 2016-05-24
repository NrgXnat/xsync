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

	static Hashtable<RemoteConnection, String> remoteJsessions = new Hashtable<RemoteConnection, String>();
	
	
	public static String getJsession(String url){
		String jSession = remoteJsessions.get(url);
		return jSession;
	}
	
	public static void setJsession(String url, String jSession){
		remoteJsessions.put(url, jSession);
	}
	
	public RemoteConnection getConnection(String remoteUrl, String projectId) {
		RemoteConnection connection = new RemoteConnection();
		String jSession = getJsession(remoteUrl);
		if (jSession != null) {
			//Is connection alive?
			//If yes, use it
			//If no, refresh it
			connection.setUrl(remoteUrl);
		}else {
			//Get a new JSession
			HttpEntity<String> request = new HttpEntity<String>(getAuthHeaders(connection));
			SimpleClientHttpRequestFactory requestFactory =new SimpleClientHttpRequestFactory();
			RestTemplate template = new RestTemplate(requestFactory);
			ResponseEntity<String> response = template.exchange(connection.getUrl()+"/data/JSESSIONID", HttpMethod.POST, request, String.class);
			connection.setJsessionid(response.getBody());
		}
		return connection;
	}
	
	
	/**
	 * Sets the alias token.
	 *
	 * @param connection the connection
	 * @return the string
	 */
	public RemoteConnection getConnection1(RemoteConnection connection){
		HttpEntity<String> request = new HttpEntity<String>(RemoteConnectionManager.getAuthHeaders(connection));
		ResponseEntity<String> response = getResttemplate().exchange(connection.getUrl()+"/data/services/tokens/issue", HttpMethod.GET, request, String.class);
		logger.info(response.getBody());
		System.out.println(response.getBody());
		try {
			AliasToken aliasToken = (AliasToken) new ObjectMapper().readValue(response.getBody(), AliasToken.class);
			connection.setUsername(aliasToken.getAlias());
			connection.setPassword(""+aliasToken.getSecret());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new RuntimeException("Failed to get remote connection.");
		}
		return connection;
	}

	/**
	 * Gets the auth headers.
	 *
	 * @param connection the connection
	 * @return the auth headers
	 */
	protected static HttpHeaders getAuthHeaders(RemoteConnection connection){
		HttpHeaders headers = new HttpHeaders();
		if(connection.getJsessionid()==null){
			headers.add("Authorization", "Basic " + getBase64Credentials(connection));
			//connection.setJsessionid(this.getSessionId(connection);
		}else{
			headers.add("Cookie", "JSESSIONID=" + connection.getJsessionid());
		}
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
		//if(conn.getAlias()!=null){
		//	plainCreds = conn.getAlias().getAlias()+":"+conn.getAlias().getToken();
		//}else{
			plainCreds = conn.getUsername()+":"+conn.getPassword();
		//}
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
