package org.nrg.xsync.manifest;

import org.nrg.xsync.tools.XSyncTools;
import org.nrg.xsync.utils.XsyncFileUtils;

/**
 * @author Mohana Ramaratnam
 *
 */
public class ResourceSyncItem extends SyncedItem {

	Integer fileCount;
	Object fileSize;
	
	public ResourceSyncItem(String localId, String localLabel) {
		super(localId, localLabel);
		this.remoteLabel = localLabel;
		this.fileCount  = new Integer(0);
		this.fileSize = new Long(0);
	}

	/**
	 * @return the fileCount
	 */
	public Integer getFileCount() {
		return fileCount;
	}

	/**
	 * @param fileCount the fileCount to set
	 */
	public void setFileCount(Integer fileCount) {
		this.fileCount = fileCount;
	}

	/**
	 * @return the fileSize
	 */
	public Object getFileSize() {
		return fileSize;
	}
	
	

	/**
	 * @param fileSize the fileSize to set
	 */
	public void setFileSize(Object fileSize) {
		this.fileSize = fileSize;
	}

	@Override
	public String toString() {
		StringBuilder stringBuilder = new StringBuilder();
		final  String newline = XSyncTools.NEWLINE;
		stringBuilder.append("Local ID:");
		stringBuilder.append(getLocalId());
		stringBuilder.append(newline);
		stringBuilder.append("Local Label:");
		stringBuilder.append(getLocalLabel());
		stringBuilder.append(newline);
		stringBuilder.append("Remote Label:");
		stringBuilder.append(getRemoteLabel());
		stringBuilder.append(newline);
		stringBuilder.append("File Count: ");
		stringBuilder.append(getFileCount());
		stringBuilder.append(newline);
		stringBuilder.append("File Size: ");
		stringBuilder.append(XsyncFileUtils.getHumanReadableFileSize((Long)getFileSize()));
		stringBuilder.append(newline);
		stringBuilder.append("Message: ");
		stringBuilder.append(getMessage());
		stringBuilder.append(newline);
		stringBuilder.append("Sync Status: ");
		stringBuilder.append(getSyncStatus());
		stringBuilder.append(newline);
		stringBuilder.append("Sync Time: ");
		stringBuilder.append(getSyncTime());
		stringBuilder.append(newline);
		return stringBuilder.toString();
	}

}
