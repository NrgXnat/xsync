package org.nrg.xsync.remote.alias.services;

import java.util.Date;
import java.util.List;

import org.nrg.xsync.components.SyncStatusHolder;
import org.nrg.xsync.components.SyncStatusHolder.SyncType;
import org.nrg.xsync.components.elements.ProjectSyncStatus;
import org.nrg.xsync.manifest.SyncManifest;
import org.nrg.xsync.manifest.history.XsyncProjectHistory;
import org.nrg.xsync.services.local.SyncManifestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SyncStatusService {
	
	SyncStatusHolder _statusHolder;
	private SyncManifestService _manifestService;
	
	@Autowired
	public SyncStatusService(SyncStatusHolder statusHolder, SyncManifestService manifestService) {
		_statusHolder = statusHolder;
		_manifestService = manifestService;
	}
	
	public void registerSyncStart(String projectId, SyncType syncType, SyncManifest syncManifest) {
		final ProjectSyncStatus projectSyncStatus = _statusHolder.getProjectSyncStatus(projectId);
		projectSyncStatus.setSyncing(true);
		if (syncManifest!=null) {
			projectSyncStatus.setSyncStartTime(syncManifest.getSync_start_time());
		} else {
			projectSyncStatus.setSyncStartTime(new Date());
		}
		projectSyncStatus.setSyncEndTime(null);
		projectSyncStatus.setSyncType(syncType);
		projectSyncStatus.setCurrentSubject(null);
		projectSyncStatus.setCurrentExperiment(null);
		projectSyncStatus.setCurrentExperimentType(null);
		projectSyncStatus.getInitialSubjectList().clear();
		projectSyncStatus.getCompletedSubjects().clear();
		projectSyncStatus.getFailedSubjects().clear();
		projectSyncStatus.getCompletedExperiments().clear();
		projectSyncStatus.getFailedExperiments().clear();
	}

	public void registerSyncEnd(String projectId, SyncManifest syncManifest) {
		final ProjectSyncStatus projectSyncStatus = _statusHolder.getProjectSyncStatus(projectId);
		projectSyncStatus.setSyncing(false);
		if (syncManifest!=null) {
			projectSyncStatus.setWasSyncSuccessful(syncManifest.wasSyncSuccessful());
			projectSyncStatus.setSyncEndTime(syncManifest.getSync_end_time());
		} else {
			projectSyncStatus.setSyncEndTime(new Date());
		}
		projectSyncStatus.setCurrentSubject(null);
		projectSyncStatus.setCurrentExperiment(null);
		final XsyncProjectHistory history = _manifestService.findByStartDate(syncManifest.getSync_start_time());
		if (history!=null) {
			projectSyncStatus.setHistoryId(history.getId());
		}
	}
	
	public void registerCurrentSubject(String projectId, String subjectId) {
		_statusHolder.getProjectSyncStatus(projectId).setCurrentSubject(subjectId);
	}
	
	public void registerCurrentExperiment(String projectId, String expId, String expType) {
		final ProjectSyncStatus projectSyncStatus = _statusHolder.getProjectSyncStatus(projectId);
		projectSyncStatus.setCurrentExperiment(expId);
		projectSyncStatus.setCurrentExperimentType(expType);
	}
	
	public void registerCompletedSubject(String projectId, String subjectId) {
		final ProjectSyncStatus projectSyncStatus = _statusHolder.getProjectSyncStatus(projectId);
		projectSyncStatus.getCompletedSubjects().add(subjectId);
		projectSyncStatus.setCurrentSubject(null);
	}

	public void registerFailedSubject(String projectId, String subjectId) {
		final ProjectSyncStatus projectSyncStatus = _statusHolder.getProjectSyncStatus(projectId);
		projectSyncStatus.getFailedSubjects().add(subjectId);
		projectSyncStatus.setCurrentSubject(null);
	}
	
	public void registerCompletedExperiment(String projectId, String expId, String expType) {
		final ProjectSyncStatus projectSyncStatus = _statusHolder.getProjectSyncStatus(projectId);
		projectSyncStatus.getCompletedExperiments().put(expId,  expType);
		projectSyncStatus.setCurrentExperiment(null);
		projectSyncStatus.setCurrentExperimentType(null);
	}

	public void registerFailedExperiment(String projectId, String expId, String expType) {
		final ProjectSyncStatus projectSyncStatus = _statusHolder.getProjectSyncStatus(projectId);
		projectSyncStatus.getFailedExperiments().put(expId, expType);
		projectSyncStatus.setCurrentExperiment(null);
		projectSyncStatus.setCurrentExperimentType(null);
	}
	
	public boolean isCurrentlySyncing(String projectId) {
		return _statusHolder.getProjectSyncStatus(projectId).isSyncing();
	}
	
	public ProjectSyncStatus getProjectSyncStatus(String projectId) {
		return _statusHolder.getProjectSyncStatus(projectId);
	}

	public void registerInitialSubjectList(String projectId, List<String> subjectIds) {
		final ProjectSyncStatus projectSyncStatus = _statusHolder.getProjectSyncStatus(projectId);
		projectSyncStatus.getInitialSubjectList().clear();
		projectSyncStatus.getInitialSubjectList().addAll(subjectIds);
		
	}

}
