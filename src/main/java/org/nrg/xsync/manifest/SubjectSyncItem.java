package org.nrg.xsync.manifest;

import java.util.ArrayList;

/**
 * @author Mohana Ramaratnam
 *
 */
public class SubjectSyncItem extends SyncedItem {
	
	ArrayList<ExperimentSyncItem> experiments;
	ArrayList<ResourceSyncItem> resources;


	public SubjectSyncItem(String localId, String localLabel) {
		super(localId, localLabel);
		resources = new ArrayList<ResourceSyncItem>();
		experiments = new ArrayList<ExperimentSyncItem>();
	}
		
	
	/**
	 * @return the resources
	 */
	public ArrayList<ResourceSyncItem> getResources() {
		return resources;
	}

	public void addResources(ResourceSyncItem resource) {
		resources.add(resource);
		stateChanged();
	}

	/**
	 * @param resources the resources to set
	 */
	public void setResources(ArrayList<ResourceSyncItem> resources) {
		this.resources = resources;
	}

	
	/**
	 * @return the experiments
	 */
	public ArrayList<ExperimentSyncItem> getExperiments() {
		return experiments;
	}

	public void addExperiment(ExperimentSyncItem experiment) {
		experiments.add(experiment);
		stateChanged();
	}

	
	/**
	 * @param experiments the experiments to set
	 */
	public void setExperiments(ArrayList<ExperimentSyncItem> experiments) {
		this.experiments = experiments;
	}
	

}
