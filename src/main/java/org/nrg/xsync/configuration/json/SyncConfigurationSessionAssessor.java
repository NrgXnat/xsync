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

public class SyncConfigurationSessionAssessor  {
	SyncConfigurationXsiType xsi_types;
	List<SyncConfigurationAdvancedOption> advanced_options;

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
	
	public SyncConfigurationAdvancedOption getAdvancedOption(String asessorXsiType) {
		SyncConfigurationAdvancedOption advOption = null;
		List<SyncConfigurationAdvancedOption> advOptions = getAdvanced_options();
		if (advOptions == null) return null;
		for (SyncConfigurationAdvancedOption aOption : advOptions) {
			if (aOption.getXsi_type().equals(asessorXsiType)) {
				advOption = aOption;
				break;
			}
		}
		return advOption;
	}
	
	public static SyncConfigurationSessionAssessor GetDefaultSyncConfigurationSessionAssessor() {
		SyncConfigurationSessionAssessor ass = new SyncConfigurationSessionAssessor();
		ass.setXsi_types(SyncConfigurationXsiType.GetDefaultSyncConfiguration());
		ass.setAdvanced_options(new ArrayList<SyncConfigurationAdvancedOption>());
		return ass;
	}
}
