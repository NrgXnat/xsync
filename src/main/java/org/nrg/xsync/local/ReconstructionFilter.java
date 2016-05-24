package org.nrg.xsync.local;

import java.util.ArrayList;
import java.util.List;

import org.nrg.xdat.model.XnatReconstructedimagedataI;
import org.nrg.xdat.om.XnatExperimentdata;
import org.nrg.xdat.om.XnatImagesessiondata;
import org.nrg.xft.exception.FieldNotFoundException;
import org.nrg.xsync.configuration.ProjectSyncConfiguration;
import org.nrg.xsync.configuration.json.ImagingScanConfiguration;
import org.nrg.xsync.configuration.json.ImagingSessionConfiguration;

/**
 * @author Mohana Ramaratnam
 *
 */
public class ReconstructionFilter {
	
	
	/**
	 * correctIDandLabel.
	 *
	 * @param newRecon
	 *            the new recon
	 * @return the string
	 * @throws Exception
	 *             the exception
	 */
	public void correctIDandLabel(XnatReconstructedimagedataI newRecon) throws Exception {
		newRecon.setId("");
	}


	public void filter(XnatExperimentdata exp, ProjectSyncConfiguration projectSyncConfiguration) throws Exception {
		ImagingSessionConfiguration session = projectSyncConfiguration.getImagingSessionConfiguration(exp.getXSIType());
	    List<ImagingScanConfiguration> scanConfigurations = session.getScans();
	    List<String> scanTypes = new ArrayList<String>();
	    for (ImagingScanConfiguration scan : scanConfigurations) {
	    	scanTypes.add(scan.getType());
	    }
	    filterRecons(exp,scanTypes);
	}
	
	/**
	 * Filter recons.
	 *
	 * @param exp
	 *            the exp
	 * @param scan_types
	 *            the scan_types
	 * @throws IndexOutOfBoundsException
	 *             the index out of bounds exception
	 * @throws FieldNotFoundException
	 *             the field not found exception
	 */
	private void filterRecons(XnatExperimentdata exp, List<String> scan_types)
			throws IndexOutOfBoundsException, FieldNotFoundException {
		while (findAndRemoveRecons(exp, scan_types));
		return;
	}

	/**
	 * Find and remove recons.
	 *
	 * @param exp
	 *            the exp
	 * @param scan_types
	 *            the scan_types
	 * @return true, if successful
	 */
	private boolean findAndRemoveRecons(XnatExperimentdata exp, List<String> scan_types) {
		boolean found = false;
		List<XnatReconstructedimagedataI> recons = ((XnatImagesessiondata) exp).getReconstructions_reconstructedimage();
		for (int i = 0; i < recons.size(); i++) {
			if (scan_types != null && !scan_types.contains(recons.get(i).getType())) {
				((XnatImagesessiondata) exp).removeReconstructions_reconstructedimage(i);
				found = true;
				break;
			}
		}
		return found;
	}

}
