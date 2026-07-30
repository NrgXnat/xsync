package org.nrg.xsync.components.elements;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;
import org.nrg.xsync.components.SyncStatusHolder.SyncType;

public class ProjectSyncStatus {

    private boolean isSyncing;
	@Setter
    @Getter
    private SyncType syncType;
	@Setter
    private Date syncStartTime;
	@Setter
    private Date syncEndTime;
	@Setter
    @Getter
    private String currentSubject;
	@Setter
    @Getter
    private String currentExperiment;
	@Setter
    @Getter
    private String currentExperimentType;
	@Setter
    @Getter
    private long historyId;
	@Getter
    private Boolean wasSyncSuccessful;
	@Getter
    private final List<String> initialSubjectList = new ArrayList<>();
	@Getter
    private final List<String> completedSubjects = new ArrayList<>();
	@Getter
    private final List<String> failedSubjects = new ArrayList<>();
	@Getter
    private final Map<String,String> completedExperiments = new HashMap<>();
	@Getter
    private final Map<String,String> failedExperiments = new HashMap<>();
	private static final SimpleDateFormat datef = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss"); 
	
	public ProjectSyncStatus() {
		isSyncing = false;
		syncType = SyncType.NONE_SINCE_STARTUP;
	}
	
	public boolean getIsSyncing() {
		return isSyncing;
	}

	public void setIsSyncing(boolean isSyncing) {
		this.isSyncing = isSyncing;
	}

    public String getSyncStartTime() {
		return (syncStartTime!=null) ? datef.format(syncStartTime) : null;
	}

    public String getSyncEndTime() {
		return (syncEndTime!=null) ? datef.format(syncEndTime) : null;
	}

    public void setWasSyncSuccessful(boolean wasSyncSuccessful) {
		this.wasSyncSuccessful=wasSyncSuccessful;
	}
}