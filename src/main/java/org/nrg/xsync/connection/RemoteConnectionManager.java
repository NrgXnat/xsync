package org.nrg.xsync.connection;

import java.io.File;
import java.util.Map;

import org.apache.commons.codec.binary.Base64;
import org.apache.log4j.Logger;
import org.nrg.xdat.om.XnatExperimentdata;
import org.nrg.xdat.om.XnatSubjectassessordata;
import org.nrg.xdat.om.XnatSubjectdata;
import org.nrg.xdat.preferences.SiteConfigPreferences;
import org.nrg.xsync.services.local.SyncManifestService;
import org.nrg.xsync.services.remote.RemoteRESTService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.google.common.collect.Maps;

/**
 * The Class RemoteConnectionManager.
 *
 * @author Mohana Ramaratnam
 */
@Service
public class RemoteConnectionManager {

	/** The logger. */
	private static Logger logger = Logger.getLogger(RemoteConnectionManager.class);

	/** The remote rest service. */
	private final RemoteRESTService     _remoteRESTService;
	private final SyncManifestService   _syncManifestService;
	private final SiteConfigPreferences _preferences;

	/** The Constant cache. */
	private static final Map<String,String> sessionCache = Maps.newHashMap();

	@Autowired
	public RemoteConnectionManager(final RemoteRESTService remoteRESTService, final SyncManifestService syncManifestService, final SiteConfigPreferences preferences) {
		_remoteRESTService = remoteRESTService;
		_syncManifestService = syncManifestService;
		_preferences = preferences;
	}

	/**
	 * Gets the auth headers.
	 *
	 * @param connection the connection
	 * @param useJSESSIONID the use jsessionid
	 * @param refreshCache the refresh cache
	 * @return the auth headers
	 */
	public static HttpHeaders GetAuthHeaders(RemoteConnection connection, boolean useJSESSIONID, boolean refreshCache){
		final HttpHeaders headers = new HttpHeaders();
		if (useJSESSIONID) {
			final String JSESSIONID = getJsessionId(connection);
			if (JSESSIONID!=null && JSESSIONID.length()>0) {
				headers.add(HttpHeaders.COOKIE, "JSESSIONID=" + JSESSIONID);
				return headers;
			}
		} 
		if (refreshCache) {
			getJsessionId(connection, true);
		}
		headers.add("Authorization", "Basic " + getBase64Credentials(connection));
		return headers;
	}

	/**
	 * Gets the auth headers.
	 *
	 * @param connection the connection
	 * @param useJSESSIONID the use jsessionid
	 * @return the http headers
	 */
	public static HttpHeaders GetAuthHeaders(RemoteConnection connection, boolean useJSESSIONID) {
		// We won't refresh the JSESSSIONID cache unless we're told to
		return GetAuthHeaders(connection, useJSESSIONID, false);
	}
	
	/**
	 * Gets the auth headers (sends credentials.  does not use the cache version).
	 *
	 * @param connection the connection
	 * @return the http headers
	 */
	public static HttpHeaders GetAuthHeaders(RemoteConnection connection) {
		// For safety, let's not use JSESSIONID unless we're told to. 
		return GetAuthHeaders(connection, false, false);
	}

	/**
	 * Gets the jsession id (option to refresh the cache).
	 *
	 * @param connection the connection
	 * @param refreshCache refresh the cache, or used cached version?
	 * @return the jsession id
	 */
	private static String getJsessionId(RemoteConnection connection, boolean refreshCache) {
		final String cacheKey = connectionToCacheKey(connection);
		if (!refreshCache && sessionCache.containsKey(cacheKey)) {
			return sessionCache.get(cacheKey);
		}
		final HttpEntity<?> httpEntity = new HttpEntity<Object>(GetAuthHeaders(connection, false));
		final SimpleClientHttpRequestFactory requestFactory =new SimpleClientHttpRequestFactory();
		final RestTemplate restTemplate = new RestTemplate(requestFactory);
		final ResponseEntity<String> response = restTemplate.exchange(connection.getUrl() + "/data/JSESSIONID", HttpMethod.GET, httpEntity, String.class);
		if (response.getStatusCode().equals(HttpStatus.OK)) {
			final String responseBody = response.getBody();
			if (responseBody!=null && responseBody.length()>0) {
				sessionCache.put(cacheKey, responseBody);
			}
			return response.getBody();
		}
		return null;
	}
	
	/**
	 * Gets the jsession id (uses cached version if available (does not refresh the cache)).
	 *
	 * @param connection the connection
	 * @return the jsession id
	 */
	private static String getJsessionId(RemoteConnection connection) {
		return getJsessionId(connection, false);
	}

	/**
	 * Connection to cache key.
	 *
	 * @param connection the connection
	 * @return the string
	 */
	private static String connectionToCacheKey(RemoteConnection connection) {
		return connection.getUrl() + "::" + connection.getLocalProject();
	}

	/**
	 * Gets the base64 credentials.
	 *
	 * @param conn the conn
	 * @return the base64 credentials
	 */
	private static String getBase64Credentials(RemoteConnection conn) {
		final String plainCreds;
		plainCreds = conn.getUsername()+":"+conn.getPassword();
		final byte[] plainCredsBytes = plainCreds.getBytes();
		final byte[] base64CredsBytes = Base64.encodeBase64(plainCredsBytes);
		final String base64Creds = new String(base64CredsBytes);
		return base64Creds;
	}

	/**
	 * Gets the site ID.
	 * @return The site ID.
     */
	public String getSiteId() {
		return _preferences.getSiteId();
	}

	/**
	 * Gets the remote REST service.
	 * @return The remote REST service.
     */
	public RemoteRESTService getRemoteRESTService() {
		return _remoteRESTService;
	}

	public SyncManifestService getSyncManifestService() {
		return _syncManifestService;
	}

	/**
	 * Import subject.
	 *
	 * @param connection the connection
	 * @param subject the subject
	 * @return the remote connection response
	 * @throws Exception the exception
	 */
	public RemoteConnectionResponse importSubject(RemoteConnection connection, XnatSubjectdata subject) throws Exception {
		return _remoteRESTService.importSubject(connection, subject);
	}

	/**
	 * Delete subject.
	 *
	 * @param connection the connection
	 * @param subject the subject
	 * @return the remote connection response
	 * @throws Exception the exception
	 */
	public RemoteConnectionResponse deleteSubject(RemoteConnection connection, XnatSubjectdata subject) throws Exception {
		return _remoteRESTService.deleteSubject(connection, subject);
	}

	/**
	 * Delete subject resource.
	 *
	 * @param connection the connection
	 * @param subject the subject
	 * @param resourceLabel the resource label
	 * @return the remote connection response
	 * @throws Exception the exception
	 */
	public RemoteConnectionResponse deleteSubjectResource(RemoteConnection connection, XnatSubjectdata subject, String resourceLabel) throws Exception{
		return _remoteRESTService.deleteSubjectResource(connection, subject, resourceLabel);
	}
	
	/**
	 * Delete project resource.
	 *
	 * @param connection the connection
	 * @param projectId the project id
	 * @param resourceLabel the resource label
	 * @return the remote connection response
	 * @throws Exception the exception
	 */
	public RemoteConnectionResponse deleteProjectResource(RemoteConnection connection, String projectId, String resourceLabel) throws Exception{
		return _remoteRESTService.deleteProjectResource(connection, projectId, resourceLabel);
	}
	
	/**
	 * Import project resource.
	 *
	 * @param connection the connection
	 * @param projectId the project id
	 * @param resourceLabel the resource label
	 * @param zipFile the zip file
	 * @return the remote connection response
	 * @throws Exception the exception
	 */
	public RemoteConnectionResponse importProjectResource(RemoteConnection connection, String projectId, String resourceLabel, File zipFile) throws Exception {
		return _remoteRESTService.importProjectResource(connection, projectId, resourceLabel, zipFile);
	}

	/**
	 * Import subject resource.
	 *
	 * @param connection the connection
	 * @param subject the subject
	 * @param resourceLabel the resource label
	 * @param zipFile the zip file
	 * @return the remote connection response
	 * @throws Exception the exception
	 */
	public RemoteConnectionResponse importSubjectResource(RemoteConnection connection, XnatSubjectdata subject, String resourceLabel, File zipFile) throws Exception{
		return _remoteRESTService.importSubjectResource(connection, subject, resourceLabel, zipFile);
	}

	/**
	 * Delete experiment.
	 *
	 * @param connection the connection
	 * @param experiment the experiment
	 * @return the remote connection response
	 * @throws Exception the exception
	 */
	public RemoteConnectionResponse deleteExperiment(RemoteConnection connection, XnatExperimentdata experiment) throws Exception {
		return _remoteRESTService.deleteExperiment(connection, experiment);
	}

	/**
	 * Import subject assessor.
	 *
	 * @param connection the connection
	 * @param subject the subject
	 * @param assessor the assessor
	 * @return the remote connection response
	 * @throws Exception the exception
	 */
	public RemoteConnectionResponse importSubjectAssessor(RemoteConnection connection,XnatSubjectdata subject,XnatSubjectassessordata assessor ) throws Exception{
		return _remoteRESTService.importSubjectAssessor(connection, subject, assessor);
	}
	
	/**
	 * Import subject assessor resource.
	 *
	 * @param connection the connection
	 * @param subject the subject
	 * @param assessor the assessor
	 * @param resourceLabel the resource label
	 * @param zipFile the zip file
	 * @return the remote connection response
	 * @throws Exception the exception
	 */
	public RemoteConnectionResponse importSubjectAssessorResource(RemoteConnection connection,XnatSubjectdata subject,XnatSubjectassessordata assessor, String resourceLabel, File zipFile ) throws Exception {
		return _remoteRESTService.importSubjectAssessorResource(connection, subject, assessor, resourceLabel, zipFile);
	}

	/**
	 * Import xar.
	 *
	 * @param connection the connection
	 * @param xar the xar
	 * @return the remote connection response
	 * @throws Exception the exception
	 */
	public RemoteConnectionResponse importXar(RemoteConnection connection,File xar) throws Exception{
		return _remoteRESTService.importXar(connection, xar);
	}

	/**
	 * Gets the result.
	 *
	 * @param connection the connection
	 * @param uri the uri
	 * @return the result
	 * @throws Exception the exception
	 */
	public RemoteConnectionResponse getResult(RemoteConnection connection,String uri) throws Exception{
		return _remoteRESTService.getResult(connection, uri);
	}
}
