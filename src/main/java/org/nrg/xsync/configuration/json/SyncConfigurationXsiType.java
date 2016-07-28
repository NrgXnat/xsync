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
	String sync_type;
	List<String> types_list;
	
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
	 * @return the types_list
	 */
	public List<String> getTypes_list() {
		return types_list;
	}
	/**
	 * @param types_list the types_list to set
	 */
	public void setTypes_list(List<String> types_list) {
		this.types_list = types_list;
	}
	
	public boolean isAllowedToSync(String xsiType) {
		boolean isAllowed = false;
		if (sync_type.equals(XsyncUtils.SYNC_TYPE_ALL)) {
			isAllowed = true;
		}else if (sync_type.equals(XsyncUtils.SYNC_TYPE_NONE)) {
			return false;
		}else if (sync_type.equals(XsyncUtils.SYNC_TYPE_INCLUDE)) {
			if (doesTypesListContainXsiType(xsiType)) {
				isAllowed = true;
			}
		}else if (sync_type.equals(XsyncUtils.SYNC_TYPE_EXCLUDE)) {
			if (!doesTypesListContainXsiType(xsiType)) {
				isAllowed = true;
			}
		}
		return isAllowed;
	}

	
	private boolean doesTypesListContainXsiType(String xsiType) {
		boolean contains = false;
		for (String x:types_list) {
			if (x.equals(xsiType)) {
				contains = true;
				break;
			}
		}
		return contains;
	}
	
	public static SyncConfigurationXsiType GetDefaultSyncConfiguration() {
		SyncConfigurationXsiType cfg = new SyncConfigurationXsiType();
		cfg.setSync_type(XsyncUtils.SYNC_TYPE_ALL);
		cfg.setTypes_list(new ArrayList<String>());
		return cfg;
	}
}
