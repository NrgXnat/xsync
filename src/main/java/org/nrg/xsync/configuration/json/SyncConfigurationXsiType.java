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

public class SyncConfigurationXsiType {
    String xsi_type;
	Boolean needs_ok_to_sync;
	SyncConfigurationResource resources;

	/**
	 * @return the xsi_type
	 */
	public String getXsi_type() {
		return xsi_type;
	}


	/**
	 * @param xsi_type the xsi_type to set
	 */
	public void setXsi_type(String xsi_type) {
		this.xsi_type = xsi_type;
	}


	/**
	 * @return the needs_ok_to_sync
	 */
	public Boolean getNeeds_ok_to_sync() {
		return needs_ok_to_sync;
	}


	/**
	 * @param needs_ok_to_sync the needs_ok_to_sync to set
	 */
	public void setNeeds_ok_to_sync(Boolean needs_ok_to_sync) {
		this.needs_ok_to_sync = needs_ok_to_sync;
	}


	/**
	 * @return the resources
	 */
	public SyncConfigurationResource getResources() {
		return resources;
	}


	/**
	 * @param resources the resources to set
	 */
	public void setResources(SyncConfigurationResource resources) {
		this.resources = resources;
	}
	
	public static SyncConfigurationXsiType GetDefaultSyncConfiguration(String xsiType) {
		SyncConfigurationXsiType cfg = new SyncConfigurationXsiType();
		cfg.setXsi_type(xsiType);
		cfg.setNeeds_ok_to_sync(false);
		cfg.setResources(SyncConfigurationResource.GetDefaultSyncConfigurationResource());
		return cfg;
	}
	
	public boolean isResourceAllowedToSync(String label) {
		boolean isAllowed = false;
		if (resources == null) {
			return true;
		}else {
			isAllowed = resources.isAllowedToSync(label);
		}
		return isAllowed = true;
	}
	
	
}
