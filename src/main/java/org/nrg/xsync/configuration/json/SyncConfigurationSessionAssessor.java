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

public class SyncConfigurationSessionAssessor   extends BaseSyncConfiguration{
	List<SyncConfigurationXsiType> xsi_types = new ArrayList<SyncConfigurationXsiType>();
	/**
	 * @return the xsi_types
	 */
	public List<SyncConfigurationXsiType> getXsi_types() {
		return xsi_types;
	}

	/**
	 * @param xsi_types the xsi_types to set
	 */
	public void setXsi_types(List<SyncConfigurationXsiType> xsi_types) {
		this.xsi_types = xsi_types;
	}
	
	public SyncConfigurationXsiType getXsiType(String type) {
		SyncConfigurationXsiType match = null;
		for (SyncConfigurationXsiType x:xsi_types) {
			if (type.equals(x.getXsi_type())) {
				match = x;
				break;
			}
		}
		return match;
	}
	
	public boolean isAllowedToSync(String assessorXsiType) {
		boolean isAllowed = false;
		if (sync_type.equals(XsyncUtils.SYNC_TYPE_ALL)) {
			isAllowed = true;
		}else if (sync_type.equals(XsyncUtils.SYNC_TYPE_NONE)) {
			return false;
		}else if (sync_type.equals(XsyncUtils.SYNC_TYPE_INCLUDE)) {
			if (isIncludedInXsiTypeList(assessorXsiType)) {
				isAllowed = true;
			}
		}else if (sync_type.equals(XsyncUtils.SYNC_TYPE_EXCLUDE)) {
			if (!isIncludedInXsiTypeList(assessorXsiType)) {
				isAllowed = true;
			}
		}
		return isAllowed;
	}
	
	protected boolean isIncludedInXsiTypeList(String assessorXsiType) {
		boolean contains = false;
		for (SyncConfigurationXsiType x:xsi_types) {
			if (x.equals(assessorXsiType)) {
				contains = true;
				break;
			}
		}
		return contains;
	}	
	
	public static SyncConfigurationSessionAssessor GetDefaultSyncConfigurationSessionAssessor() {
		SyncConfigurationSessionAssessor ass = new SyncConfigurationSessionAssessor();
		ass.setSync_type(XsyncUtils.SYNC_TYPE_ALL);
		ass.setXsi_types(new ArrayList<SyncConfigurationXsiType>());
		return ass;
	}
}
