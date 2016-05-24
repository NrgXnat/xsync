package org.nrg.xsync.manifest;

/**
 * @author Mohana Ramaratnam
 *
 */
public class ResourceSyncItem extends SyncedItem{

	Integer fileCount;
	Object fileSize;
	
	public ResourceSyncItem(String localId, String localLabel) {
		super(localId, localLabel);
		this.remoteLabel = localLabel;
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
}
