package org.nrg.xsync.configuration.json;

import java.util.ArrayList;

import org.nrg.xsync.utils.XsyncUtils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * @author Mohana Ramaratnam
 *
 */
@JsonIgnoreProperties(ignoreUnknown = true)

public class SyncConfigurationScanTypes {

    String sync_type;
	ArrayList<String> scan_type_list;
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
	 * @return the scan_type_list
	 */
	public ArrayList<String> getScan_type_list() {
		return scan_type_list;
	}
	/**
	 * @param scan_type_list the scan_type_list to set
	 */
	public void setScan_type_list(ArrayList<String> scan_type_list) {
		this.scan_type_list = scan_type_list;
	}

	public static SyncConfigurationScanTypes GetDefaultSyncConfigurationScanTypes() {
		SyncConfigurationScanTypes scan = new SyncConfigurationScanTypes();
		scan.setSync_type(XsyncUtils.SYNC_TYPE_ALL);
		scan.setScan_type_list(new ArrayList<String>());
		return scan;
	}
	
	public boolean isAllowedToSync(String scanType) {
		boolean isAllowed = false;
		if (sync_type.equals(XsyncUtils.SYNC_TYPE_ALL)) {
			isAllowed = true;
		}else if (sync_type.equals(XsyncUtils.SYNC_TYPE_NONE)) {
			return false;
		}else if (sync_type.equals(XsyncUtils.SYNC_TYPE_INCLUDE)) {
			if (doesScanTypeListContainScanType(scanType)) {
				isAllowed = true;
			}
		}else if (sync_type.equals(XsyncUtils.SYNC_TYPE_EXCLUDE)) {
			if (!doesScanTypeListContainScanType(scanType)) {
				isAllowed = true;
			}
		}
		return isAllowed;
	}
	
	private boolean doesScanTypeListContainScanType(String scanType) {
		boolean contains = false;
		for (String x:scan_type_list) {
			if (x.equals(scanType)) {
				contains = true;
				break;
			}
		}
		return contains;
	}
}
