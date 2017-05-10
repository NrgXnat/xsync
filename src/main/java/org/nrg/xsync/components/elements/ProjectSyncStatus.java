package org.nrg.xsync.components.elements;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.nrg.xsync.components.SyncStatusHolder.SyncType;

public class ProjectSyncStatus {

	private boolean isSyncing;
	private SyncType syncType; 
	private Date syncStartTime;
	private Date syncEndTime;
	private String currentSubject;
	private String currentExperiment;
	private String currentExperimentType;
	private long historyId;
	private Boolean wasSyncSuccessful;
	private final List<String> initialSubjectList = new ArrayList<>();
	private final List<String> completedSubjects = new ArrayList<>();
	private final List<String> failedSubjects = new ArrayList<>();
	private final Map<String,String> completedExperiments = new HashMap<>();
	private final Map<String,String> failedExperiments = new HashMap<>();
	private static final SimpleDateFormat datef = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss"); 
	
	public ProjectSyncStatus() {
		isSyncing = false;
		syncType = SyncType.NONE_SINCE_STARTUP;
	}
	
	public boolean isSyncing() {
		return isSyncing;
	}
	
	public void setSyncing(boolean isSyncing) {
		this.isSyncing = isSyncing;
	}
	
	public SyncType getSyncType() {
		return syncType;
	}
	
	public void setSyncType(SyncType syncType) {
		this.syncType = syncType;
	}
	
	public String getSyncStartTime() {
		return (syncStartTime!=null) ? datef.format(syncStartTime) : null;
	}

	public void setSyncStartTime(Date syncStartTime) {
		this.syncStartTime = syncStartTime;
	}

	public String getSyncEndTime() {
		return (syncEndTime!=null) ? datef.format(syncEndTime) : null;
	}

	public void setSyncEndTime(Date syncEndTime) {
		this.syncEndTime = syncEndTime;
	}

	public String getCurrentSubject() {
		return currentSubject;
	}
	
	public void setCurrentSubject(String currentSubject) {
		this.currentSubject = currentSubject;
	}

	public String getCurrentExperiment() {
		return currentExperiment;
	}
	
	public void setCurrentExperiment(String currentExperiment) {
		this.currentExperiment = currentExperiment;
	}

	public String getCurrentExperimentType() {
		return currentExperimentType;
	}
	
	public void setCurrentExperimentType(String currentExperimentType) {
		this.currentExperimentType = currentExperimentType;
	}
	
	public List<String> getInitialSubjectList() {
		return initialSubjectList;
	}
	
	public List<String> getCompletedSubjects() {
		return completedSubjects;
	}
	
	public List<String> getFailedSubjects() {
		return failedSubjects;
	}
	
	public Map<String,String> getCompletedExperiments() {
		return completedExperiments;
	}
	
	public Map<String,String> getFailedExperiments() {
		return failedExperiments;
	}

	public long getHistoryId() {
		return historyId;
	}

	public void setHistoryId(long historyId) {
		this.historyId = historyId;
	}

	public Boolean getWasSyncSuccessful() {
		return this.wasSyncSuccessful;
	}

	public void setWasSyncSuccessful(boolean wasSyncSuccessful) {
		this.wasSyncSuccessful=wasSyncSuccessful;
	}
	
}
