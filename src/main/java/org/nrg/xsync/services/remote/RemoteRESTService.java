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
	public RemoteConnectionResponse deleteProjectResource(RemoteConnection connection, String projectId, String resourceLabel);
	public RemoteConnectionResponse importProjectResource(RemoteConnection connection, String projectId, String resourceLabel, File zipFile);
	
	public RemoteConnectionResponse importSubject(RemoteConnection connection, XnatSubjectdata subject);
	public RemoteConnectionResponse deleteSubject(RemoteConnection connection, XnatSubjectdata subject);
	public RemoteConnectionResponse deleteSubjectResource(RemoteConnection connection, XnatSubjectdata subject, String resourceLabel);
	public RemoteConnectionResponse importSubjectResource(RemoteConnection connection, XnatSubjectdata subject, String resourceLabel, File zipFile);

	public RemoteConnectionResponse deleteExperiment(RemoteConnection connection, XnatExperimentdata experiment);
	public RemoteConnectionResponse importSubjectAssessor(RemoteConnection connection,XnatSubjectdata subject,XnatSubjectassessordata assessor );
	public RemoteConnectionResponse importSubjectAssessorResource(RemoteConnection connection,XnatSubjectdata subject,XnatSubjectassessordata assessor, String resourceLabel, File zipFile );

	public RemoteConnectionResponse importXar(RemoteConnection connection,File xar);
    public RemoteConnectionResponse getResult(RemoteConnection connection, String uri);

}
