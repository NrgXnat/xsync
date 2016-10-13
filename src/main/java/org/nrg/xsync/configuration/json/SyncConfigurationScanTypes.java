package org.nrg.xsync.configuration.json;

import java.util.ArrayList;

import org.nrg.xsync.utils.XsyncUtils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * @author Mohana Ramaratnam
 *
 */
@JsonIgnoreProperties(ignoreUnknown = true)

public class SyncConfigurationScanTypes extends BaseSyncConfigurationWithItems {
 
	public static SyncConfigurationScanTypes GetDefaultSyncConfigurationScanTypes() {
		SyncConfigurationScanTypes scan = new SyncConfigurationScanTypes();
		scan.setSync_type(XsyncUtils.SYNC_TYPE_ALL);
		scan.setItem_list(new ArrayList<String>());
		return scan;
	}
	

}
