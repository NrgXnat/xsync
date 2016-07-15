package org.nrg.xsync.configuration.json;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * @author Mohana Ramaratnam
 *
 */
@JsonIgnoreProperties(ignoreUnknown = true)

public class SyncConfigurationSubjectAssessor {
	SyncConfigurationXsiType xsi_types;
	List<SyncConfigurationAdvancedOption> advanced_options;
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
	/**
	 * @return the advanced_options
	 */
	public List<SyncConfigurationAdvancedOption> getAdvanced_options() {
		return advanced_options;
	}
	/**
	 * @param advanced_options the advanced_options to set
	 */
	public void setAdvanced_options(List<SyncConfigurationAdvancedOption> advanced_options) {
		this.advanced_options = advanced_options;
	}
	
	public boolean isAllowedToSync(String xsiType) {
		boolean isAllowedToSync = xsi_types.isAllowedToSync(xsiType);
		return isAllowedToSync;
	}
}
