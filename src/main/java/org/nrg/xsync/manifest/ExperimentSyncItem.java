package org.nrg.xsync.manifest;

import java.util.ArrayList;

import lombok.Getter;
import lombok.Setter;
import org.nrg.xdat.model.XnatAbstractresourceI;
import org.nrg.xdat.model.XnatImagescandataI;
import org.nrg.xdat.om.XnatExperimentdata;
import org.nrg.xdat.om.XnatImageassessordata;
import org.nrg.xdat.om.XnatImagesessiondata;
import org.nrg.xsync.utils.XsyncFileUtils;
import org.nrg.xsync.utils.XsyncUtils;

/**
 * @author Mohana Ramaratnam
 *
 */
@Getter
public class ExperimentSyncItem extends SyncedItem {

    @Setter
    ArrayList<ResourceSyncItem> resources;
    ArrayList<ScanSyncItem> scans;
    @Setter
    ArrayList<ExperimentSyncItem> assessors;

	public ExperimentSyncItem(String localId, String localLabel) {
		super(localId, localLabel);
		resources = new ArrayList<>();
		scans = new ArrayList<>();
		assessors = new ArrayList<>();
	}
	
	public void updateSyncStatus(String status, String msg) {
		boolean someSyncFailed = false;
		String childStatus = null;
		String message = "";
		if (resources != null && !resources.isEmpty()) {
			for (ResourceSyncItem r: resources) {
				if (r.getSyncStatus()!=null) {
					if (r.getSyncStatus().equals(XsyncUtils.SYNC_STATUS_FAILED)) {
						someSyncFailed = true;
						message += " Resource " + r.getLocalLabel() + " failed to sync. ";
					}else if (!r.getSyncStatus().equals(XsyncUtils.SYNC_STATUS_SYNCED_AND_VERIFIED)) {
						childStatus = r.getSyncStatus();
						message += "Resource " + r.getLocalLabel() + " sync needs to be verified. ";
					}
				}
			}
		}
		if (scans != null && !scans.isEmpty()) {
			for (ScanSyncItem r: scans) {
				if (r.getSyncStatus() != null && r.getSyncStatus().equals(XsyncUtils.SYNC_STATUS_FAILED)) {
					someSyncFailed = true;
					message += " Scan " + r.getLocalId() + " failed to sync. ";
				}else if (!r.getSyncStatus().equals(XsyncUtils.SYNC_STATUS_SYNCED_AND_VERIFIED)) {
					childStatus = r.getSyncStatus();
					message += "Scan " + r.getLocalId() + " sync needs to be verified. ";
				}
			}
		}
		if (assessors != null && !assessors.isEmpty()) {
			for (ExperimentSyncItem r: assessors) {
				if (r.getSyncStatus() != null && r.getSyncStatus().equals(XsyncUtils.SYNC_STATUS_FAILED)) {
					someSyncFailed = true;
					message += " Assessor " + r.getLocalId() + " failed to sync. ";
				} else if (!r.getSyncStatus().equals(XsyncUtils.SYNC_STATUS_SYNCED_AND_VERIFIED)) {
					childStatus = r.getSyncStatus();
					message += "Assessor " + r.getLocalId() + " sync needs to be verified. ";
				}
			}
		}
		if (someSyncFailed) {
			setSyncStatus(XsyncUtils.SYNC_STATUS_FAILED);
			setMessage("Sync failed. " + message);
		}else {
			if (childStatus == null) {
				setSyncStatus(XsyncUtils.SYNC_STATUS_SYNCED_AND_VERIFIED);
				setMessage(msg + " synced");
			}else {
				setSyncStatus(childStatus);
				setMessage(message);
			}
		}
	}
	
	public Integer getTotalSyncedFileCount() {
		int count = 0;
		try {
			for (ResourceSyncItem r: resources) {
				count += r.getFileCount();
			}
			for (ScanSyncItem s:scans) {
				for (ResourceSyncItem r: s.getResources()) {
					count += r.getFileCount();
				}
			}
			for (ExperimentSyncItem s:assessors) {
					count += s.getTotalSyncedFileCount();
			}
		} catch(NullPointerException ignored) {}
		return count;
	}

	public Long getTotalSyncedFileSize() {
		long size = 0;
		try {
			for (ResourceSyncItem r: resources) {
				size += (r.getFileSize()!=null?(Long)r.getFileSize():0);
			}
			for (ScanSyncItem s:scans) {
				for (ResourceSyncItem r: s.getResources()) {
					size += (r.getFileSize()!=null?(Long)r.getFileSize():0);
				}
			}
			for (ExperimentSyncItem s:assessors) {
					size += (s.getTotalSyncedFileSize()!=null?(Long)s.getTotalSyncedFileSize():0);
			}
		} catch(NullPointerException ignored) {
		}
		return size;
	}

	public String getFormattedTotalSyncedFileSize() {
		return XsyncFileUtils.getFormattedFileSize(getTotalSyncedFileSize());
	}


    public void addResources(ResourceSyncItem resource) {
		resources.add(resource);
	}

    public void addAssessor(ExperimentSyncItem assessor) {
		assessors.add(assessor);
	}

	public void addScan(ScanSyncItem scan) {
		scans.add(scan);
	}

    public void extractAssessorDetails(XnatImageassessordata ass) {
		if (ass.getResources_resource() != null && !ass.getResources_resource().isEmpty()) {
			for (XnatAbstractresourceI r: ass.getResources_resource()) {
				ResourceSyncItem rSync = getResourceSyncItem(r);
				addResources(rSync);
			}
		}
		if (ass.getIn_file() != null && !ass.getIn_file().isEmpty()) {
			for (XnatAbstractresourceI r: ass.getIn_file()) {
				ResourceSyncItem rSync = getResourceSyncItem(r);
				addResources(rSync);
			}
		}
		if (ass.getOut_file() != null && !ass.getOut_file().isEmpty()) {
			for (XnatAbstractresourceI r: ass.getOut_file()) {
				ResourceSyncItem rSync = getResourceSyncItem(r);
				addResources(rSync);
			}
		}
	}
	
	public void extractDetails(XnatExperimentdata exp) {
		if (exp.getResources_resource() != null && !exp.getResources_resource().isEmpty()) {
			for (XnatAbstractresourceI r: exp.getResources_resource()) {
				ResourceSyncItem rSync = getResourceSyncItem(r);
				addResources(rSync);
			}
		}
		if (exp instanceof XnatImagesessiondata) {
			XnatImagesessiondata imgSession = (XnatImagesessiondata) exp;
			if (imgSession.getScans_scan() != null && !imgSession.getScans_scan().isEmpty()) {
				for (XnatImagescandataI scan: imgSession.getScans_scan()) {
					ScanSyncItem scanSync = new ScanSyncItem(scan.getId(), scan.getId());
					if (scan.getFile() != null && !scan.getFile().isEmpty()) {
						for (XnatAbstractresourceI r: scan.getFile()) {
							ResourceSyncItem rSync = getResourceSyncItem(r);
							scanSync.addResources(rSync);
						}
					}
					addScan(scanSync);	
				}
			}
		}
	}
}
