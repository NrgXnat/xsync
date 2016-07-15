package org.nrg.xsync.configuration.json;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Mohana Ramaratnam
 *
 */
public class SyncConfigurationImagingSessions {
	SyncConfigurationXsiType xsi_types;
	List<SyncConfigurationImagingSessionAdvancedOption> advanced_options;
	/**
	 * @return the advanced_options
	 */
	public List<SyncConfigurationImagingSessionAdvancedOption> getAdvanced_options() {
		return advanced_options;
	}
	/**
	 * @param advanced_options the advanced_options to set
	 */
	public void setAdvanced_options(ArrayList<SyncConfigurationImagingSessionAdvancedOption> advanced_options) {
		this.advanced_options = advanced_options;
	}
	/**
	 * @return the xsi_types
	 */
	public SyncConfigurationXsiType getXsi_types() {
		return xsi_types;
	}
	/**
	 * @param xsi_types the xsi_types to set
	 */
	public void setXsi_types(SyncConfigurationXsiType xsi_types) {
		this.xsi_types = xsi_types;
	}
	

}
