package org.nrg.xsync.local;

import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.nrg.framework.services.SerializerService;
import org.nrg.xdat.model.XnatExperimentdataI;
import org.nrg.xdat.model.XnatSubjectdataI;
import org.nrg.xdat.om.XnatAbstractresource;
import org.nrg.xdat.om.XnatExperimentdata;
import org.nrg.xdat.om.XnatProjectdata;
import org.nrg.xdat.om.XnatSubjectdata;
import org.nrg.xft.security.UserI;
import org.nrg.xsync.configuration.ProjectSyncConfiguration;
import org.nrg.xsync.connection.RemoteConnectionManager;
import org.nrg.xsync.manager.SynchronizationManager;
import org.nrg.xsync.manifest.SubjectSyncItem;
import org.nrg.xsync.remote.alias.services.SyncStatusService;
import org.nrg.xsync.tools.XSyncTools;
import org.nrg.xsync.tools.XsyncObserver;
import org.nrg.xsync.tools.XsyncXnatInfo;
import org.nrg.xsync.utils.QueryResultUtil;
import org.nrg.xsync.utils.XSyncFailureHandler;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * @author Mohana Ramaratnam
 *
 */
@Slf4j
public class RemoteSubject {
	boolean syncAllStates;
	XnatSubjectdataI localSubject;
	ProjectSyncConfiguration projectSyncConfiguration;
	UserI user;
	SubjectSyncItem subjectSyncInfo ;
	private final XsyncXnatInfo xnatInfo;
	private final NamedParameterJdbcTemplate jdbcTemplate;
	private final RemoteConnectionManager manager;
	private final QueryResultUtil queryResultUtil;
	private List<XnatAbstractresource> subjectResourcesToBeVerified = new ArrayList<XnatAbstractresource>();
	private XnatProjectdata localProject;
    private final SerializerService serializer;
	private final SyncStatusService syncStatusService;

	
	public RemoteSubject(final RemoteConnectionManager manager, final XsyncXnatInfo xnatInfo, final QueryResultUtil queryResultUtil,
			final JdbcTemplate jdbcTemplate, XnatSubjectdataI localSubject, ProjectSyncConfiguration projectSyncConfiguration,
			UserI user, boolean syncAll, XsyncObserver observer, SerializerService serializer, SyncStatusService syncStatusService) {
		this.localSubject = localSubject;
		this.user = user;
		this.projectSyncConfiguration = projectSyncConfiguration; 
		this.syncAllStates = syncAll;
		subjectSyncInfo = new SubjectSyncItem(localSubject.getId(), localSubject.getLabel());
		subjectSyncInfo.addObserver(observer);
		this.jdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
		this.manager = manager;
		this.xnatInfo = xnatInfo;
		this.queryResultUtil = queryResultUtil;
		localProject = XnatProjectdata.getXnatProjectdatasById(localSubject.getProject(), user, false);
		this.serializer = serializer;
		this.syncStatusService = syncStatusService;
	}

	/**
	 * Sync an experiment identified by the expt id
	 * @param exptId - The id of the experiment to be synced
	 * @throws Exception
	 */
	
	public void syncExperiment(String exptId) throws Exception{
		XnatExperimentdata exp = XnatExperimentdata.getXnatExperimentdatasById(exptId, user,true);
		syncExperiment(exp);
	}

	/**
	 * Sync an experiment identified by the expt id
	 * @param experiment - The experiment to be synced
	 * @throws Exception
	 */

	public void syncExperiment(XnatExperimentdata experiment) throws Exception {
		log.debug("Syncing remote experiment");
		//_syncStatusService.registerCurrentExperiment(localProject.getId(), experiment.getLabel(), experiment.getXSIType());
		IdMapper idMapper = new IdMapper(manager, queryResultUtil, jdbcTemplate, user, projectSyncConfiguration);

		try {
			SubjectDataSync subjectMetaDataSync = new SubjectDataSync(
					manager, xnatInfo, queryResultUtil,
					jdbcTemplate, localSubject, projectSyncConfiguration,
					user, syncAllStates, subjectSyncInfo, serializer, syncStatusService
			);
			XnatSubjectdata remoteSubject = subjectMetaDataSync.syncSubject();
			String subject_remote_id = remoteSubject.getId();
			log.debug("Remote subject id :: {}", subject_remote_id);
			if (subject_remote_id != null) {
				pushExperiment(experiment,remoteSubject, false);
				//_syncStatusService.registerCompletedExperiment(localProject.getId(), experiment.getLabel(), experiment.getXSIType());
				subjectSyncInfo.stateChanged();
			}	
		}catch(Exception e) {
			log.error("Error syncing experiment {}", experiment.getLabel(), e);
			//_syncStatusService.registerFailedExperiment(localProject.getId(), experiment.getLabel(), experiment.getXSIType());
			XSyncFailureHandler.handle(localSubject.getProject(),localSubject.getId(),localSubject.getXSIType(),idMapper.getRemoteAccessionId(this.localSubject.getId()), subjectSyncInfo, e);
			throw e;
		}
		SynchronizationManager.UPDATE_MANIFEST(localSubject.getProject(), subjectSyncInfo);
		log.debug("Syncing experiment END: {}", experiment.getLabel());
	}



	private void saveSyncDetails(String local_id, String remote_id, String remote_label, String syncStatus,String xsiType) {
		subjectSyncInfo.setSyncStatus(syncStatus);
		subjectSyncInfo.setRemoteId(remote_id);
		subjectSyncInfo.setXsiType(xsiType);
		subjectSyncInfo.setRemoteLabel(remote_label);
		String remoteProjectId = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteProjectId();
		String remoteUrl = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl();
		
		XSyncTools xsyncTools = new XSyncTools(user, jdbcTemplate, queryResultUtil);
		xsyncTools.saveSyncDetails(projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSourceProjectId(), local_id, remote_id,syncStatus,xsiType,remoteProjectId, remoteUrl);
	}

	private void pushExperiment(XnatExperimentdataI assess, XnatSubjectdataI remoteSubject, boolean syncIfNotSyncedInPast) throws Exception {
		BatchExperimentSync experimentSync = new BatchExperimentSync(manager, xnatInfo, queryResultUtil,
				jdbcTemplate, localSubject, projectSyncConfiguration,
				user, syncAllStates,  subjectSyncInfo, serializer, syncStatusService);
		experimentSync.pushExperiment(assess, remoteSubject, syncIfNotSyncedInPast);
	}






}
