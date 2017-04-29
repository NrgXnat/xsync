package org.nrg.xsync.manifest;

import java.util.ArrayList;
import org.nrg.xdat.om.XnatImagescandata;
import org.nrg.xsync.utils.XsyncUtils;
import org.nrg.xdat.model.XnatAbstractresourceI;


/**
 * @author Mohana Ramaratnam
 *
 */
public class ScanSyncItem extends SyncedItem{
	
	ArrayList<ResourceSyncItem> resources;

	public ScanSyncItem(String localId, String localLabel) {
		super(localId, localLabel);
		resources = new ArrayList<ResourceSyncItem>();
	}

	/**
	 * @return the resources
	 */
	public ArrayList<ResourceSyncItem> getResources() {
		return resources;
	}

	public void addResources(ResourceSyncItem resource) {
		resources.add(resource);
	}

	/**
	 * @param resources the resources to set
	 */
	public void setResources(ArrayList<ResourceSyncItem> resources) {
		this.resources = resources;
	}

	public void extractDetails(XnatImagescandata scan) {
		if (scan.getFile() != null && scan.getFile().size() > 0) {
			for (XnatAbstractresourceI r: scan.getFile()) {
				ResourceSyncItem rSync = getResourceSyncItem(r);
				addResources(rSync);
			}
		}

	}
	
	public  void updateResourceStatus(String status) {
		if (resources != null && resources.size() > 0) {
			for (ResourceSyncItem r: resources) {
				if (r.getSyncStatus() == null) {
					r.setSyncStatus(status);
				}
			}
		}
	}
	
	public void updateSyncStatus() {
		boolean someSyncFailed = false;
		String childStatus = null;
		String message = this.getMessage();
		if (resources != null && resources.size() > 0) {
			for (ResourceSyncItem r: resources) {
				if (r.getSyncStatus()!=null) {
					if (r.getSyncStatus().equals(XsyncUtils.SYNC_STATUS_FAILED)) {
						someSyncFailed = true;
						message += " Resource " + r.getLocalLabel() + " failed to sync. ";
					}else if (!r.getSyncStatus().equals(XsyncUtils.SYNC_STATUS_SYNCED_AND_VERIFIED)) {
						childStatus = r.getSyncStatus();
						message += "Resource " + r.getLocalLabel() + " sync could not be verified. ";
					}
				}
			}
		}
		if (someSyncFailed) {
			setSyncStatus(XsyncUtils.SYNC_STATUS_FAILED);
			setMessage("Sync failed. " + message);
		}else {
			if (childStatus == null) {
				setSyncStatus(XsyncUtils.SYNC_STATUS_SYNCED_AND_VERIFIED);
				setMessage(this.getMessage() + " synced");
			}else {
				setSyncStatus(childStatus);
				setMessage(message);
			}
		}
		return;

	}

}
