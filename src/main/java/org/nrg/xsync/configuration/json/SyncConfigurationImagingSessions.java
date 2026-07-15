package org.nrg.xsync.configuration.json;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;
import org.nrg.xsync.utils.XsyncUtils;

/**
 * @author Mohana Ramaratnam
 *
 */
@Setter
@Getter
public class SyncConfigurationImagingSessions extends BaseSyncConfiguration{
    List<SyncConfigurationImagingSessionXsiType> xsi_types = new ArrayList<SyncConfigurationImagingSessionXsiType>();

    public SyncConfigurationImagingSessionXsiType getXsiType(String type) {
		SyncConfigurationImagingSessionXsiType match = null;
		for (SyncConfigurationImagingSessionXsiType x:xsi_types) {
			if (type.equals(x.getXsi_type())) {
				match = x;
				break;
			}
		}
		return match;
	}

    public boolean isAllowedToSync(String xsiType) {
		boolean isAllowed = false;
		if (sync_type.equals(XsyncUtils.SYNC_TYPE_ALL)) {
			isAllowed = true;
		}else if (sync_type.equals(XsyncUtils.SYNC_TYPE_NONE)) {
			return false;
		}else if (sync_type.equals(XsyncUtils.SYNC_TYPE_INCLUDE)) {
			if (isIncludedInXsiTypeList(xsiType)) {
				isAllowed = true;
			}
		}else if (sync_type.equals(XsyncUtils.SYNC_TYPE_EXCLUDE)) {
			if (!isIncludedInXsiTypeList(xsiType)) {
				isAllowed = true;
			}
		}
		return isAllowed;
	}
	
	protected boolean isIncludedInXsiTypeList(String xsiType) {
		boolean contains = false;
		for (SyncConfigurationImagingSessionXsiType x:xsi_types) {
			if (x.xsi_type.equals(xsiType)) {
				contains = true;
				break;
			}
		}
		return contains;
	}	
	
	

}
