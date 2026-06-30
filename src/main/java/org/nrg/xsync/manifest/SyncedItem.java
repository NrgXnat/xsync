package org.nrg.xsync.manifest;

import java.util.Date;
import java.util.Observable;

import lombok.Getter;
import lombok.Setter;
import org.nrg.xdat.model.XnatAbstractresourceI;
import org.nrg.xsync.tools.XSyncTools;
import org.nrg.xsync.utils.XsyncUtils;

/**
 * @author Mohana Ramaratnam
 *
 */
@Getter
public abstract class SyncedItem  extends Observable{
    String localId;
    String localLabel;
    @Setter
    String remoteId;
    @Setter
    String remoteLabel;
    String syncStatus;
    @Setter
    String xsiType;
    @Setter
    String message;
    @Setter
    Date syncTime;
	
	public SyncedItem(String localId, String localLabel) {
		super();
		this.localId = localId;
		this.localLabel = localLabel;
		message = "";
	}

	public void setSyncStatus(String syncStatus) {
		this.syncStatus = syncStatus;
		if (syncTime==null) {
			syncTime = new Date();
		}
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
			rSync.setFileSize((Long)r.getFileSize()>0?r.getFileSize(): 0L);
			if (hasBeenSkipped) {
				rSync.setSyncStatus(XsyncUtils.SYNC_STATUS_SKIPPED);
			}
		} else {
			rSync.setFileCount(0);
			rSync.setFileSize(0L);
		}
		return rSync;
	}
}