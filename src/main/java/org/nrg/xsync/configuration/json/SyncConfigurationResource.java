package org.nrg.xsync.configuration.json;

import java.util.ArrayList;
import java.util.List;

import org.nrg.xsync.utils.XsyncUtils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * @author Mohana Ramaratnam
 *
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SyncConfigurationResource {
	String sync_type;
	List<String> resource_list;

	/**
	 * @return the sync_type
	 */
	public String getSync_type() {
		return sync_type;
	}
	/**
	 * @param sync_type the sync_type to set
	 */
	public void setSync_type(String sync_type) {
		this.sync_type = sync_type;
	}
	/**
	 * @return the resources
	 */
	public List<String> getResource_list() {
		return resource_list;
	}
	/**
	 * @param resources the resources to set
	 */
	public void setResource_list(List<String> resources) {
		this.resource_list = resources;
	}

	public static SyncConfigurationResource GetDefaultSyncConfigurationResource() {
		SyncConfigurationResource resource = new SyncConfigurationResource();
		resource.setSync_type(XsyncUtils.SYNC_TYPE_ALL);
		resource.setResource_list(new ArrayList<String>());
		return resource;
	}
	
	public boolean isAllowedToSync(String resourceLabel) {
		boolean isAllowed = false;
		if (sync_type.equals(XsyncUtils.SYNC_TYPE_ALL)) {
			isAllowed = true;
		}else if (sync_type.equals(XsyncUtils.SYNC_TYPE_NONE)) {
			return false;
		}else if (sync_type.equals(XsyncUtils.SYNC_TYPE_INCLUDE)) {
			if (resource_list.contains(resourceLabel)) {
				isAllowed = true;
			}
		}else if (sync_type.equals(XsyncUtils.SYNC_TYPE_EXCLUDE)) {
			if (!resource_list.contains(resourceLabel)) {
				isAllowed = true;
			}
		}
		return isAllowed;
	}
	
}
