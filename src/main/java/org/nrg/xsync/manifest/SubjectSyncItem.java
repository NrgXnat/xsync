package org.nrg.xsync.manifest;

import java.util.ArrayList;

import lombok.Getter;
import lombok.Setter;
import org.nrg.xsync.tools.XSyncTools;
import org.nrg.xsync.utils.XsyncUtils;

/**
 * @author Mohana Ramaratnam
 *
 */
@Setter
@Getter
public class SubjectSyncItem extends SyncedItem {

    ArrayList<ExperimentSyncItem> experiments;
    ArrayList<ResourceSyncItem> resources;

	public SubjectSyncItem(String localId, String localLabel) {
		super(localId, localLabel);
		resources = new ArrayList<>();
		experiments = new ArrayList<>();
	}

    public void addResources(ResourceSyncItem resource) {
		resources.add(resource);
		//stateChanged();
	}

    public void addExperiment(ExperimentSyncItem experiment) {
		experiments.add(experiment);
		//stateChanged();
	}

    public void updateSyncStatus(String status, String msg) {
		boolean someSyncFailed = false;
		String childStatus = null;
		String message = msg;
		if (resources != null && !resources.isEmpty()) {
			for (ResourceSyncItem r: resources) {
				if (r.getSyncStatus()!=null) {
					if (r.getSyncStatus().equals(XsyncUtils.SYNC_STATUS_FAILED)) {
						someSyncFailed = true;
						message += " Resource " + r.getLocalLabel() + " failed to sync. ";
					}else if (!r.getSyncStatus().equals(XsyncUtils.SYNC_STATUS_SYNCED_AND_VERIFIED)) {
						childStatus = r.getSyncStatus();
						message += " Resource " + r.getLocalLabel() + " sync could not be verified. ";
					}
				}
			}
		}
		if (experiments != null && !experiments.isEmpty()) {
			for (ExperimentSyncItem e: experiments) {
				if (e.getSyncStatus().equals(XsyncUtils.SYNC_STATUS_FAILED)) {
					someSyncFailed = true;
					message += " Experiment " + e.getLocalLabel() + " failed to sync. ";
				}else if (!e.getSyncStatus().equals(XsyncUtils.SYNC_STATUS_SYNCED_AND_VERIFIED)) {
					childStatus = e.getSyncStatus();
					message += " Experiment " + e.getLocalLabel() + " sync could not be verified. ";
				}
			}			
		}
		if (someSyncFailed) {
			setSyncStatus(XsyncUtils.SYNC_STATUS_FAILED);
			setMessage("Sync failed. " + message);
		}else {
			if (childStatus == null) {
				setSyncStatus(XsyncUtils.SYNC_STATUS_SYNCED_AND_VERIFIED);
				setMessage(msg + " synced");
			}else {
				setSyncStatus(childStatus);
				setMessage(message);
			}
		}
	}
	
	public String toString() {
		String str = super.toString();
		final  String newline = XSyncTools.NEWLINE;
		str += "Resources:" + newline;
		for (int i=0;i<resources.size();i++) {
			str += resources.get(i).toString() + " " + newline;
		}
		str += "Experiments:" + newline;
		for (int i=0;i<experiments.size();i++) {
			str += experiments.get(i).toString() + " " + newline;
		}
		return str;
	}
}