package org.nrg.xsync.configuration.json;

import java.util.ArrayList;

import org.apache.log4j.Logger;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 *
 * @author Mohana Ramaratnam
 * @author Atul Kaushal
 */
@JsonIgnoreProperties(ignoreUnknown = true)

public class SyncConfigurationImagingSessionXsiType extends SyncConfigurationXsiType {

	/** The Constant logger. */
	public static final Logger logger = Logger.getLogger(SyncConfigurationImagingSessionXsiType.class);

	SyncConfigurationScanTypes scan_types;
	SyncConfigurationResource scan_resources;
	SyncConfigurationSessionAssessor session_assessors;

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

	/**
	 * Default configuration.
	 * 
	 * @param xsiType
	 *            the xsiType to set
	 * @return the SyncConfigurationImagingSessionXsiType object with default
	 *         configuration values.
	 */
	public static  SyncConfigurationImagingSessionXsiType GetDefaultImagingSessionSyncConfigurationAdvancedOption(String xsiType) {
		SyncConfigurationImagingSessionXsiType advOption = new SyncConfigurationImagingSessionXsiType();
		advOption.setXsi_type(xsiType);
		advOption.setNeeds_ok_to_sync(false);
		advOption.setResources(SyncConfigurationResource.GetDefaultSyncConfigurationResource());
		advOption.setScan_types(SyncConfigurationScanTypes.GetDefaultSyncConfigurationScanTypes());
		advOption.setScan_resources(SyncConfigurationResource.GetDefaultSyncConfigurationResource());
		advOption.setSession_assessors(SyncConfigurationSessionAssessor.GetDefaultSyncConfigurationSessionAssessor());
		advOption.setScan_filters(new ArrayList<SyncConfigurationFilter>());
		advOption.setFilters(new ArrayList<SyncConfigurationFilter>());
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

	public boolean isAllowedToSyncAssessor(String assessorXsiType) {
		boolean isAllowed = false;
		if (session_assessors == null) {
			isAllowed = true;
		} else {
			isAllowed = session_assessors.isAllowedToSync(assessorXsiType);
		}
		return isAllowed;
	}
}
