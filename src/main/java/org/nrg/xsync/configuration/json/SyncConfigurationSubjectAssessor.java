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

public class SyncConfigurationSubjectAssessor extends BaseSyncConfiguration {
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
		for (SyncConfigurationXsiType x:xsi_types) {
			if (xsiType.equals(x.getXsi_type())) {
				contains = true;
				break;
			}
		}
		return contains;
	}

	

	
	public static SyncConfigurationSubjectAssessor GetDefaultSyncConfigurationSubjectAssessor() {
		SyncConfigurationSubjectAssessor ass = new SyncConfigurationSubjectAssessor();
		ass.setSync_type(XsyncUtils.SYNC_TYPE_ALL);
		ass.setXsi_types(new ArrayList<SyncConfigurationXsiType>());
		return ass;
	}
}
