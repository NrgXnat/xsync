package org.nrg.xsync.configuration.json;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

/**
 * @author Mohana Ramaratnam
 *
 */
@Setter
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class SyncConfiguration implements Serializable {

    Boolean enabled;
    String sync_frequency;
    Boolean sync_new_only;
    String source_project_id;
    String remote_project_id;
    String remote_url;
    String identifiers;
	String customIdentifierClass;
    Boolean anonymize;
    SyncConfigurationResource project_resources;
    SyncConfigurationResource subject_resources;
    SyncConfigurationSubjectAssessor subject_assessors;
    SyncConfigurationImagingSessions imaging_sessions;
	private String notification_emails;
	private Integer no_of_retry_days=3;

	public boolean isProjectResourceAllowedToSync(String resourceLabel) {
		if (hasProjectResourceConfigurationDefinition()) {
			return project_resources.isAllowedToSync(resourceLabel);
		}else {
			return true; //Anything not configured defaults to sync
		}
	}

	public boolean isSubjectResourceAllowedToSync(String resourceLabel) {
		if (hasSubjectResourceConfigurationDefinition()) {
			return subject_resources.isAllowedToSync(resourceLabel);
		}else {
			return true; //Anything not configured defaults to sync
		}
	}

	public boolean isSubjectAssessorAllowedToSync(String xsiType) {
		try {
			if (hasSubjectAssessorConfigurationDefinition()) {
				return subject_assessors.isAllowedToSync(xsiType);
			}else {
				return true; //Anything not configured defaults to sync
			}
		} catch(NullPointerException ignored) {}
		return false;
	}

	public boolean hasProjectResourceConfigurationDefinition() {
        return project_resources != null;
	}

	public boolean hasSubjectResourceConfigurationDefinition() {
        return subject_resources != null;
	}

	public boolean hasSubjectAssessorConfigurationDefinition() {
        return subject_assessors != null;
	}

	public boolean hasImagingSessionConfigurationDefinition() {
        return imaging_sessions != null;
	}

	public boolean isImagingSessionAllowedToSync(String xsiType) {
		try {
			if (hasImagingSessionConfigurationDefinition()) {
				return imaging_sessions.isAllowedToSync(xsiType);
			}else {
				return true; //Anything not configured defaults to sync
			}
		} catch(NullPointerException ignored) {}
		return false;
	}

	public SyncConfigurationXsiType getSubjectAssessor(String xsiType) {
		SyncConfigurationXsiType advOption = SyncConfigurationXsiType.GetDefaultSyncConfiguration(xsiType);
		if (hasSubjectAssessorConfigurationDefinition()) {
			List<SyncConfigurationXsiType> advOptions = subject_assessors.getXsi_types();
			if (advOptions == null || advOptions.isEmpty()) {
				return advOption;
			}
			for (SyncConfigurationXsiType aOption : advOptions) {
				if (xsiType.equals(aOption.getXsi_type())) {
					advOption = aOption;
					break;
				}
			}
		}
		return advOption;
	}

	public SyncConfigurationImagingSessionXsiType getImagingSession(String xsiType) {
		SyncConfigurationImagingSessionXsiType advOption = SyncConfigurationImagingSessionXsiType.GetDefaultImagingSessionSyncConfigurationAdvancedOption(xsiType);
		if (hasImagingSessionConfigurationDefinition()) {
			//if (isImagingSessionAllowedToSync(xsiType)) {
				List<SyncConfigurationImagingSessionXsiType> advOptions = imaging_sessions.getXsi_types();
				if (advOptions == null || advOptions.isEmpty()) {
					return advOption;
				}
				for (SyncConfigurationImagingSessionXsiType aOption : advOptions) {
					if (xsiType.equals(aOption.getXsi_type())) {
						advOption = aOption;
						break;
					}
				}
			//}
		}
		return advOption;
	}
}
