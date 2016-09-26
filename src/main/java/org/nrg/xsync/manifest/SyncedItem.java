package org.nrg.xsync.manifest;

import java.util.ArrayList;
import java.util.Date;
import java.util.Observable;

/**
 * @author Mohana Ramaratnam
 *
 */
public abstract class SyncedItem  extends Observable{
	String localId;
	String localLabel;
	String remoteId;
	String remoteLabel;
	String syncStatus;
	String xsiType;
	String message;
	Date syncTime;
	
	
	public SyncedItem(String localId, String localLabel) {
		super();
		this.localId = localId;
		this.localLabel = localLabel;
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
	
	public String toString() {
		String str = "";
		str += "Local ID:" + this.getLocalId() + "\n";
		str += "Local Label:" + this.getLocalLabel() + "\n";
		str += "XsiType:" + this.getXsiType() + "\n";
		str += "Remote ID:" + this.getRemoteId() + "\n";
		str += "Remote Label:" + this.getRemoteLabel() + "\n";
		str += "Message: " + this.getMessage() + "\n";
		str += "Sync Status:" + this.getSyncStatus() + "\n";
		str += "Sync Time:" + this.getSyncTime() + "\n";
		return str;
	}
	
	public void stateChanged() {
		setChanged();
		notifyObservers(this);
	}
	
	
}
