package org.nrg.xsync.manifest;

import java.io.File;
import java.util.ArrayList;

import org.nrg.xdat.model.XnatAbstractresourceI;
import org.nrg.xdat.model.XnatImageassessordataI;
import org.nrg.xdat.model.XnatImagescandataI;
import org.nrg.xdat.om.XnatExperimentdata;
import org.nrg.xdat.om.XnatImageassessordata;
import org.nrg.xdat.om.XnatImagesessiondata;
import org.nrg.xdat.om.XnatResource;
import org.nrg.xdat.om.XnatResourceseries;
import org.nrg.xsync.manager.SynchronizationManager;
import org.nrg.xsync.utils.XsyncFileUtils;
import org.nrg.xsync.utils.XsyncUtils;

/**
 * @author Mohana Ramaratnam
 *
 */
public class ExperimentSyncItem extends SyncedItem {
	
	ArrayList<ResourceSyncItem> resources;
	ArrayList<ScanSyncItem> scans;
	ArrayList<ExperimentSyncItem> assessors;

	
	public ExperimentSyncItem(String localId, String localLabel) {
		super(localId, localLabel);
		resources = new ArrayList<ResourceSyncItem>();
		scans = new ArrayList<ScanSyncItem>();
		assessors = new ArrayList<ExperimentSyncItem>();
	}
	
	public Integer getTotalSyncedFileCount() {
		int count = 0;
		try {
			for (ResourceSyncItem r: resources) {
				count += r.getFileCount().intValue();
			}
			for (ScanSyncItem s:scans) {
				for (ResourceSyncItem r: s.getResources()) {
					count += r.getFileCount().intValue();
				}
			}
			for (ExperimentSyncItem s:assessors) {
					count += s.getTotalSyncedFileCount();
			}
		}catch(NullPointerException npe) {}
		return new Integer(count);
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
		}catch(NullPointerException npe) {
		}
		return new Long(size);
	}

	public String getFormattedTotalSyncedFileSize() {
		return XsyncFileUtils.getFormattedFileSize(getTotalSyncedFileSize());
	}	
	
	
	/**
	 * @return the resources
	 */
	public ArrayList<ResourceSyncItem> getResources() {
		return resources;
	}

	/**
	 * @return the scans
	 */
	public ArrayList<ScanSyncItem> getScans() {
		return scans;
	}

	
	public void addResources(ResourceSyncItem resource) {
		resources.add(resource);
	}

	/**
	 * @param resources the resources to set
	 */
	public void setResources(ArrayList<ResourceSyncItem> resources) {
		this.resources = resources;
	}

	/**
	 * @return the assessors
	 */
	public ArrayList<ExperimentSyncItem> getAssessors() {
		return assessors;
	}

	public void addAssessor(ExperimentSyncItem assessor) {
		assessors.add(assessor);
	}

	public void addScan(ScanSyncItem scan) {
		scans.add(scan);
	}

	/**
	 * @param assessors the assessors to set
	 */
	public void setAssessors(ArrayList<ExperimentSyncItem> assessors) {
		this.assessors = assessors;
	}

	public void extractAssessorDetails(XnatImageassessordata ass) {
		if (ass.getResources_resource() != null && ass.getResources_resource().size() > 0) {
			for (XnatAbstractresourceI r: ass.getResources_resource()) {
				ResourceSyncItem rSync = getResourceSyncItem(r);
				addResources(rSync);
			}
		}
		if (ass.getIn_file() != null && ass.getIn_file().size() > 0) {
			for (XnatAbstractresourceI r: ass.getIn_file()) {
				ResourceSyncItem rSync = getResourceSyncItem(r);
				addResources(rSync);
			}
		}
		if (ass.getOut_file() != null && ass.getOut_file().size() > 0) {
			for (XnatAbstractresourceI r: ass.getOut_file()) {
				ResourceSyncItem rSync = getResourceSyncItem(r);
				addResources(rSync);
			}
		}
		
		
	}
	
	public void extractDetails(XnatExperimentdata exp) {
		if (exp.getResources_resource() != null && exp.getResources_resource().size() > 0) {
			for (XnatAbstractresourceI r: exp.getResources_resource()) {
				ResourceSyncItem rSync = getResourceSyncItem(r);
				addResources(rSync);
			}
		}
		if (exp instanceof XnatImagesessiondata) {
			XnatImagesessiondata imgSession = (XnatImagesessiondata) exp;
			if (imgSession.getScans_scan() != null && imgSession.getScans_scan().size() > 0) {
				for (XnatImagescandataI scan: imgSession.getScans_scan()) {
					ScanSyncItem scanSync = new ScanSyncItem(scan.getId(), scan.getId());
					if (scan.getFile() != null && scan.getFile().size() > 0) {
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
	

	private ResourceSyncItem getResourceSyncItem(XnatAbstractresourceI r) {
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
