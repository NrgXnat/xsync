package org.nrg.xsync.configuration.json;

import java.util.ArrayList;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.nrg.xsync.utils.XsyncUtils;

/**
 * The Class SyncConfigurationFilter.
 *
 * @author Atul Kaushal
 */

@Setter
@Getter
public class SyncConfigurationFilter extends BaseSyncConfiguration {

	private String xml_path;
	private String filter_type;
	private ArrayList<String> filter_values;

    /**
	 * Gets the default sync configuration filter.
	 *
	 * @return the default sync configuration filter
	 */
	public static SyncConfigurationFilter getDefaultSyncConfigurationFilter() {
		SyncConfigurationFilter syncfilter = new SyncConfigurationFilter();
		syncfilter.setSync_type(XsyncUtils.SYNC_TYPE_ALL);
		syncfilter.setXml_path(StringUtils.EMPTY);
		syncfilter.setFilter_type(StringUtils.EMPTY);
		syncfilter.setFilter_values(new ArrayList<String>());
		return syncfilter;
	}

	/**
	 * Checks if is allowed to sync.
	 *
	 * @param assessorXsiType
	 *            the assessor xsi type
	 * @return true, if is allowed to sync
	 */
	public boolean isAllowedToSync(String assessorXsiType) {
		boolean isAllowed = false;
		if (XsyncUtils.SYNC_TYPE_ALL.equals(sync_type)) {
			isAllowed = true;
		} else if (XsyncUtils.SYNC_TYPE_NONE.equals(sync_type)) {
			return false;
		} else if (XsyncUtils.SYNC_TYPE_INCLUDE.equals(sync_type)) {
			isAllowed = true;
		} else if (XsyncUtils.SYNC_TYPE_EXCLUDE.equals(sync_type)) {
			isAllowed = true;
		}
		return isAllowed;
	}
}
