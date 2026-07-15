package org.nrg.xsync.configuration.json;

import java.util.ArrayList;

import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 *
 * @author Mohana Ramaratnam
 * @author Atul Kaushal
 */
@Setter
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)

public class SyncConfigurationImagingSessionXsiType extends SyncConfigurationXsiType {

	/** The Constant logger. */
	public static final Logger logger = LoggerFactory.getLogger(SyncConfigurationImagingSessionXsiType.class);

    SyncConfigurationScanTypes scan_types;
    SyncConfigurationResource scan_resources;
    SyncConfigurationSessionAssessor session_assessors;

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
