package org.nrg.xsync.manifest;

import java.util.ArrayList;
import java.util.Date;

/**
 * @author Mohana Ramaratnam
 *
 */
public abstract class SyncedItem  {
	String localId;
	String localLabel;
	String remoteId;
	String remoteLabel;
	String syncStatus;
	String xsiType;
	String message;
	Date syncTime;
	
	
	ArrayList<SyncedItem> resources;
	
	ArrayList<SyncedItem> experiments;
	
	ArrayList<SyncedItem> assessors;
	
	
	
	public SyncedItem(String localId, String localLabel) {
		this.localId = localId;
		this.localLabel = localLabel;
		resources = new ArrayList<SyncedItem>();
		experiments = new ArrayList<SyncedItem>();
		assessors = new ArrayList<SyncedItem>();
	}

	/**
	 * @return the localId
	 */
	public String getLocalId() {
		return localId;
	}

	/**
	 * @return the localLabel
	 */
	public String getLocalLabel() {
		return localLabel;
	}


	/**
	 * @return the remoteId
	 */
	public String getRemoteId() {
		return remoteId;
	}

	/**
	 * @param remoteId the remoteId to set
	 */
	public void setRemoteId(String remoteId) {
		this.remoteId = remoteId;
	}

	/**
	 * @return the remoteLabel
	 */
	public String getRemoteLabel() {
		return remoteLabel;
	}

	/**
	 * @param remoteLabel the remoteLabel to set
	 */
	public void setRemoteLabel(String remoteLabel) {
		this.remoteLabel = remoteLabel;
	}

	/**
	 * @return the xsiType
	 */
	public String getXsiType() {
		return xsiType;
	}

	/**
	 * @param xsiType the xsiType to set
	 */
	public void setXsiType(String xsiType) {
		this.xsiType = xsiType;
	}

	/**
	 * @return the syncStatus
	 */
	public String getSyncStatus() {
		return syncStatus;
	}

	/**
	 * @param syncStatus the syncStatus to set
	 */
	public void setSyncStatus(String syncStatus) {
		this.syncStatus = syncStatus;
	}

	/**
	 * @return the resources
	 */
	public ArrayList<SyncedItem> getResources() {
		return resources;
	}

	public void addResources(SyncedItem resource) {
		resources.add(resource);
	}

	/**
	 * @param resources the resources to set
	 */
	public void setResources(ArrayList<SyncedItem> resources) {
		this.resources = resources;
	}

	/**
	 * @return the experiments
	 */
	public ArrayList<SyncedItem> getExperiments() {
		return experiments;
	}

	public void addExperiment(SyncedItem experiment) {
		experiments.add(experiment);
	}

	
	/**
	 * @param experiments the experiments to set
	 */
	public void setExperiments(ArrayList<SyncedItem> experiments) {
		this.experiments = experiments;
	}

	/**
	 * @return the assessors
	 */
	public ArrayList<SyncedItem> getAssessors() {
		return assessors;
	}

	public void addAssessor(SyncedItem assessor) {
		assessors.add(assessor);
	}

	/**
	 * @param assessors the assessors to set
	 */
	public void setAssessors(ArrayList<SyncedItem> assessors) {
		this.assessors = assessors;
	}

	/**
	 * @return the message
	 */
	public String getMessage() {
		return message;
	}

	/**
	 * @param message the message to set
	 */
	public void setMessage(String message) {
		this.message = message;
	}

	/**
	 * @return the syncTime
	 */
	public Date getSyncTime() {
		return syncTime;
	}

	/**
	 * @param syncTime the syncTime to set
	 */
	public void setSyncTime(Date syncTime) {
		this.syncTime = syncTime;
	}
	
	
}
