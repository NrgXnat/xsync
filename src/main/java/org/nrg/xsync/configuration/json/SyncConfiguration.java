package org.nrg.xsync.configuration.json;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * @author Mohana Ramaratnam
 *
 */
@JsonIgnoreProperties(ignoreUnknown = true)

public class SyncConfiguration implements Serializable{

	
	Boolean enabled;
	String sync_frequency;
	Boolean sync_new_only;
	String source_project_id;
	String remote_project_id;
	String remote_url;
	String identifiers;
	Boolean anonymize;
	SyncConfigurationResource project_resources;
	SyncConfigurationResource subject_resources;
	SyncConfigurationSubjectAssessor subject_assessors;
	SyncConfigurationImagingSessions imaging_sessions;
	
	
	public boolean isProjectResourceAllowedToSync(String resourceLabel) {
		boolean isAllowed = false;
		if (hasProjectResourceConfigurationDefinition()) {
			isAllowed = project_resources.isAllowedToSync(resourceLabel);
		}else {
			return true; //Anything not configured defaults to sync
		}
		return isAllowed;
	}

	public boolean isSubjectResourceAllowedToSync(String resourceLabel) {
		boolean isAllowed = false;
		if (hasSubjectResourceConfigurationDefinition()) {
			isAllowed = subject_resources.isAllowedToSync(resourceLabel);
		}else {
			return true; //Anything not configured defaults to sync
		}
		return isAllowed;
	}

	public boolean isSubjectAssessorAllowedToSync(String xsiType) {
		boolean isAllowed = false;
		try {
			if (hasSubjectAssessorConfigurationDefinition()) {
				isAllowed = subject_assessors.isAllowedToSync(xsiType);
			}else {
				return true; //Anything not configured defaults to sync
			}
		}catch(NullPointerException npe) {
			
		}
		return isAllowed;
	}

	public boolean hasProjectResourceConfigurationDefinition() {
		if (project_resources == null) {
			return false;
		}else {
			return true;
		}
	}

	public boolean hasSubjectResourceConfigurationDefinition() {
		if (subject_resources == null) {
			return false;
		}else {
			return true;
		}
	}


	public boolean hasSubjectAssessorConfigurationDefinition() {
		if (subject_assessors == null) {
			return false;
		}else {
			return true;
		}
	}

	public boolean hasImagingSessionConfigurationDefinition() {
		if (imaging_sessions == null) {
			return false;
		}else {
			return true;
		}
	}

	public boolean isImagingSessionAllowedToSync(String xsiType) {
		boolean isAllowed = false;
		try {
			if (hasImagingSessionConfigurationDefinition()) {
				isAllowed = imaging_sessions.isAllowedToSync(xsiType);
			}else {
				return true; //Anything not configured defaults to sync
			}
		}catch(NullPointerException npe) {}
		return isAllowed;
	}

	public SyncConfigurationXsiType getSubjectAssessor(String xsiType) {
		SyncConfigurationXsiType advOption = SyncConfigurationXsiType.GetDefaultSyncConfiguration(xsiType);
		if (hasSubjectAssessorConfigurationDefinition()) {
				List<SyncConfigurationXsiType> advOptions = subject_assessors.getXsi_types();
				if (advOptions == null || advOptions.size() < 1) {
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
				if (advOptions == null || advOptions.size() < 1){
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


	/**
	 * @return the source_project_id
	 */
	public String getSource_project_id() {
		return source_project_id;
	}

	/**
	 * @param source_project_id the source_project_id to set
	 */
	public void setSource_project_id(String source_project_id) {
		this.source_project_id = source_project_id;
	}

	/**
	 * @return the project_resources
	 */
	public SyncConfigurationResource getProject_resources() {
		return project_resources;
	}

	/**
	 * @param project_resources the project_resources to set
	 */
	public void setProject_resources(SyncConfigurationResource project_resources) {
		this.project_resources = project_resources;
	}

	/**
	 * @return the subject_resources
	 */
	public SyncConfigurationResource getSubject_resources() {
		return subject_resources;
	}

	/**
	 * @param subject_resources the subject_resources to set
	 */
	public void setSubject_resources(SyncConfigurationResource subject_resources) {
		this.subject_resources = subject_resources;
	}

	/**
	 * @return the subject_assessors
	 */
	public SyncConfigurationSubjectAssessor getSubject_assessors() {
		return subject_assessors;
	}

	/**
	 * @param subject_assessors the subject_assessors to set
	 */
	public void setSubject_assessors(SyncConfigurationSubjectAssessor subject_assessors) {
		this.subject_assessors = subject_assessors;
	}

	/**
	 * @return the imaging_sessions
	 */
	public SyncConfigurationImagingSessions getImaging_sessions() {
		return imaging_sessions;
	}

	/**
	 * @return the enabled
	 */
	public Boolean getEnabled() {
		return enabled;
	}

	/**
	 * @param enabled the enabled to set
	 */
	public void setEnabled(Boolean enabled) {
		this.enabled = enabled;
	}

	/**
	 * @return the sync_frequency
	 */
	public String getSync_frequency() {
		return sync_frequency;
	}

	/**
	 * @param sync_frequency the sync_frequency to set
	 */
	public void setSync_frequency(String sync_frequency) {
		this.sync_frequency = sync_frequency;
	}

	/**
	 * @return the sync_new_only
	 */
	public Boolean getSync_new_only() {
		return sync_new_only;
	}

	/**
	 * @param sync_new_only the sync_new_only to set
	 */
	public void setSync_new_only(Boolean sync_new_only) {
		this.sync_new_only = sync_new_only;
	}

	/**
	 * @return the remote_project_id
	 */
	public String getRemote_project_id() {
		return remote_project_id;
	}

	/**
	 * @param remote_project_id the remote_project_id to set
	 */
	public void setRemote_project_id(String remote_project_id) {
		this.remote_project_id = remote_project_id;
	}

	/**
	 * @return the remote_url
	 */
	public String getRemote_url() {
		return remote_url;
	}

	/**
	 * @param remote_url the remote_url to set
	 */
	public void setRemote_url(String remote_url) {
		this.remote_url = remote_url;
	}

	/**
	 * @return the identifiers
	 */
	public String getIdentifiers() {
		return identifiers;
	}

	/**
	 * @param identifiers the identifiers to set
	 */
	public void setIdentifiers(String identifiers) {
		this.identifiers = identifiers;
	}

	/**
	 * @param imaging_sessions the imaging_sessions to set
	 */
	public void setImaging_sessions(SyncConfigurationImagingSessions imaging_sessions) {
		this.imaging_sessions = imaging_sessions;
	}
	
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

}
