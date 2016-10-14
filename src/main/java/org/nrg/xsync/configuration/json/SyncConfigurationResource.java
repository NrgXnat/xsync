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
public class SyncConfigurationResource extends BaseSyncConfigurationWithItems{

	public static SyncConfigurationResource GetDefaultSyncConfigurationResource() {
		SyncConfigurationResource resource = new SyncConfigurationResource();
		resource.setSync_type(XsyncUtils.SYNC_TYPE_ALL);
		resource.setItems(new ArrayList<String>());
		return resource;
	}
	
	
}