package org.nrg.xsync.services.remote;

import java.io.File;

import org.nrg.xdat.om.WrkWorkflowdata;
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
	public RemoteConnectionResponse deleteProjectResource(final RemoteConnection connection, final String projectId, final String resourceLabel) throws Exception;
	public RemoteConnectionResponse importProjectResource(final RemoteConnection connection, final String projectId, final String resourceLabel, final File zipFile, final boolean updateStats) throws Exception;
	public RemoteConnectionResponse createWorkflow(final RemoteConnection connection, final WrkWorkflowdata wrk) throws Exception;

	
	public RemoteConnectionResponse importSubject(final RemoteConnection connection, final XnatSubjectdata subject) throws Exception;
	public RemoteConnectionResponse deleteSubject(final RemoteConnection connection, final XnatSubjectdata subject) throws Exception;
	public RemoteConnectionResponse deleteSubjectResource(final RemoteConnection connection, final XnatSubjectdata subject, final String resourceLabel) throws Exception;
	public RemoteConnectionResponse importSubjectResource(final RemoteConnection connection, final XnatSubjectdata subject, final String resourceLabel, final File zipFile, final boolean updateStats) throws Exception;
	public RemoteConnectionResponse importImageSessionResource(final RemoteConnection connection, final XnatExperimentdata experiment, final String resourceLabel, final File zipFile, final boolean updateStats) throws Exception;

	public RemoteConnectionResponse deleteExperiment(final RemoteConnection connection, final XnatExperimentdata experiment) throws Exception;
	public RemoteConnectionResponse importSubjectAssessor(final RemoteConnection connection, final XnatSubjectdata subject, final XnatSubjectassessordata assessor ) throws Exception;
	public RemoteConnectionResponse importSubjectAssessorResource(final RemoteConnection connection, final XnatSubjectdata subject, final XnatSubjectassessordata assessor, final String resourceLabel, final File zipFile, final boolean updateStats ) throws Exception;

	public RemoteConnectionResponse importXar(final RemoteConnection connection, final File xar) throws Exception;
	public RemoteConnectionResponse importXar(final RemoteConnection connection, final String xarPath) throws Exception;
    public RemoteConnectionResponse getResult(final RemoteConnection connection, final String uri) throws Exception;

}
