package org.nrg.xsync.configuration.json;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;
import org.nrg.xsync.utils.XsyncUtils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * @author Mohana Ramaratnam
 *
 */
@Setter
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)

public class SyncConfigurationSubjectAssessor extends BaseSyncConfiguration {
    List<SyncConfigurationXsiType> xsi_types = new ArrayList<SyncConfigurationXsiType>();

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
