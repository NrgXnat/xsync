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
	
	String project;
	Boolean auto_sync;
	List<String> projectresources;
	List<String> subjectresources;
	List<SubjectAssessorConfiguration> subjectassessors;
	List<ImagingSessionConfiguration> imagingsessions;
	/**
	 * @return the project
	 */
	public String getProject() {
		return project;
	}
	/**
	 * @param project the project to set
	 */
	public void setProject(String project) {
		this.project = project;
	}
	/**
	 * @return the projectresources
	 */
	public List<String> getProjectresources() {
		return projectresources;
	}
	/**
	 * @param projectresources the projectresources to set
	 */
	public void setProjectresources(List<String> projectresources) {
		this.projectresources = projectresources;
	}
	/**
	 * @return the subjectresources
	 */
	public List<String> getSubjectresources() {
		return subjectresources;
	}
	/**
	 * @param subjectresources the subjectresources to set
	 */
	public void setSubjectresources(List<String> subjectresources) {
		this.subjectresources = subjectresources;
	}
	/**
	 * @return the subjectrssessors
	 */
	public List<SubjectAssessorConfiguration> getSubjectassessors() {
		return subjectassessors;
	}

	public SubjectAssessorConfiguration getSubjectassessors(String xsiType) {
		SubjectAssessorConfiguration subjCfg = null;
		List<SubjectAssessorConfiguration> subjectAssessorConfigurations = getSubjectassessors();
		if (subjectAssessorConfigurations != null) {
			for (SubjectAssessorConfiguration subjAssCfg:subjectAssessorConfigurations) {
				if (subjAssCfg.getXsiType().equals(xsiType)) {
					subjCfg = subjAssCfg;
					break;
				}
			}
		}
		return subjCfg;
	}
	
	/**
	 * @param subjectrssessors the subjectrssessors to set
	 */
	public void setSubjectassessors(List<SubjectAssessorConfiguration> subjectassessors) {
		this.subjectassessors = subjectassessors;
	}
	/**
	 * @return the imagingsessions
	 */
	public List<ImagingSessionConfiguration> getImagingsessions() {
		return imagingsessions;
	}

	public ImagingSessionConfiguration getImagingsessions(String xsiType) {
		ImagingSessionConfiguration imagingsession = null;
		List<ImagingSessionConfiguration> imagingSessionConfigurations = getImagingsessions();
		if (imagingSessionConfigurations != null) {
			for (ImagingSessionConfiguration imagingSessionCfg:imagingSessionConfigurations) {
				if (imagingSessionCfg.getXsiType().equals(xsiType)) {
					imagingsession = imagingSessionCfg;
					break;
				}
			}
		}
		return imagingsession;
	}

	/**
	 * @param imagingsessions the imagingsessions to set
	 */
	public void setImagingsessions(List<ImagingSessionConfiguration> imagingsessions) {
		this.imagingsessions = imagingsessions;
	}
	
	
	public boolean checkSubjectAssessorOkToSync(String xsiType) {
		boolean ok = false;
		SubjectAssessorConfiguration subjCfg = getSubjectassessors(xsiType);
		if (subjCfg != null) {
			ok = subjCfg.checkOkToSync();
		}
		return ok;
	}

	public boolean checkImagingSessionOkToSync(String xsiType) {
		boolean ok = false;
		ImagingSessionConfiguration imgSessionCfg = getImagingsessions(xsiType);
		if (imgSessionCfg != null) {
			ok = imgSessionCfg.checkOkToSync();
		}
		return ok;
	}
	
	/**
	 * @return the auto_update
	 */
	public Boolean getAuto_sync() {
		return auto_sync;
	}
	/**
	 * @param auto_update the auto_update to set
	 */
	public void setAuto_sync(Boolean auto_update) {
		this.auto_sync = auto_update;
	}
	public String toString() {
		String out = "";
		out += "project:" + project + "\n";
		if (projectresources != null) {
			out+= "projectresources:";
			for (String resources:projectresources)
				out +=resources + " ";
			out +=   "\n";
		}
		if (subjectresources != null) {
			out+= "subjectresources:";
			for (String resources:subjectresources)
				out +=resources + " ";
			out +=   "\n";
		}
		if (subjectassessors != null) {
			out+= "subjectassessors:";
			for (SubjectAssessorConfiguration subjA:subjectassessors)
				out +=subjA + " ";
			out +=   "\n";
		}
		if (imagingsessions != null) {
			out+= "imagingsessions:";
			for (ImagingSessionConfiguration imagingSession:imagingsessions)
				out +=imagingSession;
		}
		return out;
	}

}
