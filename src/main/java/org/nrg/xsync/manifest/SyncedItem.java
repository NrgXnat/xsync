package org.nrg.xsync.manifest;

import java.util.ArrayList;
import java.util.Date;
import java.util.Observable;
import org.nrg.xdat.model.XnatAbstractresourceI;

import org.nrg.xsync.tools.XSyncTools;
import org.nrg.xsync.utils.XsyncUtils;

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
		if (syncTime==null) {
			syncTime = new Date();
		}
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
		final  String newline = XSyncTools.NEWLINE;
		str += "Local ID:" + this.getLocalId() + newline;
		str += "Local Label:" + this.getLocalLabel() + newline;
		str += "XsiType:" + this.getXsiType() + newline;
		str += "Remote ID:" + this.getRemoteId() + newline;
		str += "Remote Label:" + this.getRemoteLabel() + newline;
		str += "Message: " + this.getMessage() + newline;
		str += "Sync Status:" + this.getSyncStatus() + newline;
		str += "Sync Time:" + this.getSyncTime() + newline;
		return str;
	}
	
	public void stateChanged() {
		setChanged();
		notifyObservers(this);
	}
	
	protected ResourceSyncItem getResourceSyncItem(XnatAbstractresourceI r) {
		ResourceSyncItem rSync = new ResourceSyncItem(r.getLabel(), r.getLabel());
		if (r.getFileCount() != null && r.getFileSize()!=null) {
			boolean hasBeenSkipped = r.getFileCount()<0 && (Long)r.getFileSize()<0;	
			rSync.setFileCount(r.getFileCount()>0?r.getFileCount():0);
			rSync.setFileSize((Long)r.getFileSize()>0?r.getFileSize():new Long(0));
			if (hasBeenSkipped) {
				rSync.setSyncStatus(XsyncUtils.SYNC_STATUS_SKIPPED);
			}
		}else {
			rSync.setFileCount(0);
			rSync.setFileSize(new Long(0));
		}
		return rSync;
	}
	
}
