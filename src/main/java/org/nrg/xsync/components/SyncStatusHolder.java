package org.nrg.xsync.components;

import java.util.HashMap;
import java.util.Map;

import org.nrg.xsync.components.elements.ProjectSyncStatus;
import org.springframework.stereotype.Component;

@Component
public class SyncStatusHolder {
	
	public enum SyncType { PROJECT_SYNC, EXPERIMENT_SYNC, NONE_SINCE_STARTUP }; 
	private final Map<String,ProjectSyncStatus> statusMap = new HashMap<>();
	
	public ProjectSyncStatus getProjectSyncStatus(String projectId) {
		if (!statusMap.containsKey(projectId)) {
			statusMap.put(projectId, new ProjectSyncStatus());
		}
		return statusMap.get(projectId);
		
	}

}
