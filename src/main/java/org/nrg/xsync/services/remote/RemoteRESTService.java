package org.nrg.xsync.services.remote;

import java.io.File;

import org.nrg.xdat.om.XnatExperimentdata;
import org.nrg.xdat.om.XnatSubjectassessordata;
import org.nrg.xdat.om.XnatSubjectdata;
import org.nrg.xsync.connection.RemoteConnection;
import org.nrg.xsync.connection.RemoteConnectionResponse;

/**
 * @author Mohana Ramaratnam
 *
 */
public interface RemoteRESTService {
	public RemoteConnectionResponse deleteProjectResource(RemoteConnection connection, String projectId, String resourceLabel) throws Exception;
	public RemoteConnectionResponse importProjectResource(RemoteConnection connection, String projectId, String resourceLabel, File zipFile) throws Exception;
	
	public RemoteConnectionResponse importSubject(RemoteConnection connection, XnatSubjectdata subject) throws Exception;
	public RemoteConnectionResponse deleteSubject(RemoteConnection connection, XnatSubjectdata subject) throws Exception;
	public RemoteConnectionResponse deleteSubjectResource(RemoteConnection connection, XnatSubjectdata subject, String resourceLabel) throws Exception;
	public RemoteConnectionResponse importSubjectResource(RemoteConnection connection, XnatSubjectdata subject, String resourceLabel, File zipFile) throws Exception;

	public RemoteConnectionResponse deleteExperiment(RemoteConnection connection, XnatExperimentdata experiment) throws Exception;
	public RemoteConnectionResponse importSubjectAssessor(RemoteConnection connection,XnatSubjectdata subject,XnatSubjectassessordata assessor ) throws Exception;
	public RemoteConnectionResponse importSubjectAssessorResource(RemoteConnection connection,XnatSubjectdata subject,XnatSubjectassessordata assessor, String resourceLabel, File zipFile ) throws Exception;

	public RemoteConnectionResponse importXar(RemoteConnection connection,File xar) throws Exception;
    public RemoteConnectionResponse getResult(RemoteConnection connection, String uri) throws Exception;

}
