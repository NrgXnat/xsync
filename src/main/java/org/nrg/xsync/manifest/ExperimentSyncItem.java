package org.nrg.xsync.manifest;

import java.util.ArrayList;

import org.nrg.xdat.model.XnatAbstractresourceI;
import org.nrg.xdat.model.XnatImageassessordataI;
import org.nrg.xdat.model.XnatImagescandataI;
import org.nrg.xdat.om.XnatExperimentdata;
import org.nrg.xdat.om.XnatImagesessiondata;

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
		for (ResourceSyncItem r: resources) {
			count += r.getFileCount().intValue();
		}
		return new Integer(count);
	}

	public Long getTotalSyncedFileSize() {
		long size = 0;
		for (ResourceSyncItem r: resources) {
			size += (Long)r.getFileSize();
		}
		for (ScanSyncItem s:scans) {
			for (ResourceSyncItem r: s.getResources()) {
				size += (Long)r.getFileSize();
			}
		}
		for (ExperimentSyncItem s:assessors) {
				size += (Long)s.getTotalSyncedFileSize();
		}
		return new Long(size);
	}

	/**
	 * @return the resources
	 */
	public ArrayList<ResourceSyncItem> getResources() {
		return resources;
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

	
	public void extractDetails(XnatExperimentdata exp) {
		if (exp.getResources_resource() != null && exp.getResources_resource().size() > 0) {
			for (XnatAbstractresourceI r: exp.getResources_resource()) {
				ResourceSyncItem rSync = new ResourceSyncItem(r.getLabel(), r.getLabel());
				rSync.setFileCount(r.getFileCount());
				rSync.setFileSize(r.getFileSize());
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
							ResourceSyncItem rSync = new ResourceSyncItem(r.getLabel(), r.getLabel());
							rSync.setFileCount(r.getFileCount());
							rSync.setFileSize(r.getFileSize());
							scanSync.addResources(rSync);
						}
					}
					addScan(scanSync);	
				}
			}
			if (imgSession.getAssessors()!= null && imgSession.getAssessors().size() > 0) {
				for (XnatImageassessordataI assessor:imgSession.getAssessors()) {
					ExperimentSyncItem assessorSync = new ExperimentSyncItem(assessor.getId(), assessor.getLabel());
					for (XnatAbstractresourceI r: assessor.getOut_file()) {
						ResourceSyncItem rSync = new ResourceSyncItem(r.getLabel(), r.getLabel());
						rSync.setFileCount(r.getFileCount());
						rSync.setFileSize(r.getFileSize());
						assessorSync.addResources(rSync);
					}
					addAssessor(assessorSync);
				}
			}
		}
	}
}
