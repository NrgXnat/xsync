package org.nrg.xsync.connection;

import java.io.File;
import java.util.List;

import org.apache.commons.codec.binary.Base64;
import org.apache.log4j.Logger;
import org.nrg.framework.annotations.XnatPlugin;
import org.nrg.xdat.XDAT;
import org.nrg.xdat.om.XnatExperimentdata;
import org.nrg.xdat.om.XnatSubjectassessordata;
import org.nrg.xdat.om.XnatSubjectdata;
import org.nrg.xsync.exception.XsyncRemoteConnectionException;
import org.nrg.xsync.remote.alias.RemoteAliasEntity;
import org.nrg.xsync.remote.alias.services.RemoteAliasService;
import org.nrg.xsync.services.remote.RemoteRESTService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.HttpHeaders;

/**
 * @author Mohana Ramaratnam
 *
 */

public class RemoteConnectionManager {
	/** The logger. */
	public static Logger logger = Logger.getLogger(RemoteConnectionManager.class);

	private final RemoteRESTService remoteRESTService = XDAT.getContextService().getBean(RemoteRESTService.class);



	/**
	 * Gets the auth headers.
	 *
	 * @param connection the connection
	 * @return the auth headers
	 */
	public static HttpHeaders GetAuthHeaders(RemoteConnection connection){
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
