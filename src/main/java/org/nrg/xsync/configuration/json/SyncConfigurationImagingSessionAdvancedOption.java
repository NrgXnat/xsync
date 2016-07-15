package org.nrg.xsync.configuration.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * @author Mohana Ramaratnam
 *
 */
@JsonIgnoreProperties(ignoreUnknown = true)

public class SyncConfigurationImagingSessionAdvancedOption extends SyncConfigurationAdvancedOption{
	Boolean anonymize;
	SyncConfigurationScanTypes scan_types;
	SyncConfigurationResource scan_resources;
	SyncConfigurationSessionAssessor session_assessors;
	/**
	 * @return the anonymize
	 */
	public Boolean getAnonymize() {
		return anonymize;
	}
	/**
	 * @param anonymize the anonymize to set
	 */
	public void setAnonymize(Boolean anonymize) {
		this.anonymize = anonymize;
	}
	/**
	 * @return the scan_types
	 */
	public SyncConfigurationScanTypes getScan_types() {
		return scan_types;
	}
	/**
	 * @param scan_types the scan_types to set
	 */
	public void setScan_types(SyncConfigurationScanTypes scan_types) {
		this.scan_types = scan_types;
	}
	/**
	 * @return the scan_resources
	 */
	public SyncConfigurationResource getScan_resources() {
		return scan_resources;
	}
	/**
	 * @param scan_resources the scan_resources to set
	 */
	public void setScan_resources(SyncConfigurationResource scan_resources) {
		this.scan_resources = scan_resources;
	}
	/**
	 * @return the session_assessors
	 */
	public SyncConfigurationSessionAssessor getSession_assessors() {
		return session_assessors;
	}
	/**
	 * @param session_assessors the session_assessors to set
	 */
	public void setSession_assessors(SyncConfigurationSessionAssessor session_assessors) {
		this.session_assessors = session_assessors;
	}
	
	public static  SyncConfigurationImagingSessionAdvancedOption GetDefaultImagingSessionSyncConfigurationAdvancedOption(String xsiType) {
		SyncConfigurationImagingSessionAdvancedOption advOption = new SyncConfigurationImagingSessionAdvancedOption();
		advOption.setXsi_type(xsiType);
		advOption.setNeeds_ok_to_sync(false);
		advOption.setResources(SyncConfigurationResource.GetDefaultSyncConfigurationResource());
		advOption.setAnonymize(false);
		advOption.setScan_types(SyncConfigurationScanTypes.GetDefaultSyncConfigurationScanTypes());
		advOption.setResources(SyncConfigurationResource.GetDefaultSyncConfigurationResource());
		return advOption;
	}
	
	public boolean isAllowedToSyncScan(String scanType) {
		boolean isAllowed = false;
		if (scan_types == null) {
			isAllowed = true;
		}else {
			isAllowed = scan_types.isAllowedToSync(scanType);
		}
		return isAllowed;
	}

	public boolean isAllowedToSyncScanResource(String scanResourceLabel) {
		boolean isAllowed = false;
		if (scan_resources == null) {
			isAllowed = true;
		}else {
			isAllowed = scan_resources.isAllowedToSync(scanResourceLabel);
		}
		return isAllowed;
	}

}
