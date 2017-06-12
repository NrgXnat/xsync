package org.nrg.xsync.configuration.json;

import java.util.ArrayList;

import org.apache.commons.lang3.StringUtils;
import org.nrg.xsync.utils.XsyncUtils;

/**
 * The Class SyncConfigurationFilter.
 *
 * @author Atul Kaushal
 */

public class SyncConfigurationFilter extends BaseSyncConfiguration {

	/** The xml path. */
	private String xml_path;

	/** The filter type. */
	private String filter_type;

	/** The filter values. */
	private ArrayList<String> filter_values;

	/**
	 * Gets the xml path.
	 *
	 * @return the xml path
	 */
	public String getXml_path() {
		return xml_path;
	}

	/**
	 * Sets the xml path.
	 *
	 * @param xml_path
	 *            the new xml path
	 */
	public void setXml_path(String xml_path) {
		this.xml_path = xml_path;
	}

	/**
	 * Gets the filter type.
	 *
	 * @return the filter type
	 */
	public String getFilter_type() {
		return filter_type;
	}

	/**
	 * Sets the filter type.
	 *
	 * @param filter_type
	 *            the new filter type
	 */
	public void setFilter_type(String filter_type) {
		this.filter_type = filter_type;
	}

	/**
	 * Gets the filter values.
	 *
	 * @return the filter values
	 */
	public ArrayList<String> getFilter_values() {
		return filter_values;
	}

	/**
	 * Sets the filter values.
	 *
	 * @param filter_values
	 *            the new filter values
	 */
	public void setFilter_values(ArrayList<String> filter_values) {
		this.filter_values = filter_values;
	}

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
