package org.nrg.xsync.manifest;

import java.util.ArrayList;

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


}
