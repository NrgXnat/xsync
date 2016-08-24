package org.nrg.xsync.local;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.nrg.xdat.model.XnatAbstractresourceI;
import org.nrg.xdat.model.XnatImagescandataI;
import org.nrg.xdat.model.XnatReconstructedimagedataI;
import org.nrg.xdat.om.XnatAbstractresource;
import org.nrg.xdat.om.XnatExperimentdata;
import org.nrg.xdat.om.XnatImagesessiondata;
import org.nrg.xdat.om.XnatReconstructedimagedata;
import org.nrg.xft.exception.FieldNotFoundException;
import org.nrg.xft.security.UserI;
import org.nrg.xsync.configuration.ProjectSyncConfiguration;
import org.nrg.xsync.configuration.json.SyncConfigurationImagingSessionAdvancedOption;
import org.nrg.xsync.utils.QueryResultUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * @author Mohana Ramaratnam
 *
 */
public class ReconstructionFilter {
	private static final Logger _log = LoggerFactory.getLogger(ResourceFilter.class);

	private final UserI _user;
	private final NamedParameterJdbcTemplate _jdbcTemplate;
	private final QueryResultUtil _queryResultUtil;

	
	public ReconstructionFilter(final UserI user, final NamedParameterJdbcTemplate jdbcTemplate, final QueryResultUtil queryResultUtil) {
		_user = user;
		_jdbcTemplate = jdbcTemplate;
		_queryResultUtil = queryResultUtil;
	}

	
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
		if (exp instanceof XnatImagesessiondata) {
			SyncConfigurationImagingSessionAdvancedOption sessionOption = projectSyncConfiguration.getSynchronizationConfiguration().getImagingSessionAdvancedOptions(exp.getXSIType());
			List<XnatImagescandataI> scans = ((XnatImagesessiondata) exp).getScans_scan();
			ArrayList<String> scanTypes = new ArrayList<String>();
			for (int i = 0; i < scans.size(); i++) {
				if (sessionOption.isAllowedToSyncScan(scans.get(i).getType())) {
					scanTypes.add(scans.get(i).getType());
				}
			}

			filterRecons(exp,scanTypes,projectSyncConfiguration);
		}
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
	private void filterRecons(XnatExperimentdata exp, List<String> scan_types,ProjectSyncConfiguration projectSyncConfiguration)
			throws  Exception {
		while (findAndRemoveRecons(exp, scan_types));
		List<XnatReconstructedimagedataI> recons = ((XnatImagesessiondata) exp).getReconstructions_reconstructedimage();
		for (int i = 0; i < recons.size(); i++) {
			while (findAndRemoveReconFiles(recons.get(i),projectSyncConfiguration));
		}
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

	/**
	 * Find and remove recon files.
	 *
	 * @param exp
	 *            the exp
	 * @param scan_types
	 *            the scan_types
	 * @return true, if successful
	 */
	private boolean findAndRemoveReconFiles(XnatReconstructedimagedataI recon,ProjectSyncConfiguration projectSyncConfiguration) throws Exception {
		boolean found = false;
		ResourceFilter resourceFilter = new ResourceFilter(_user,_jdbcTemplate,_queryResultUtil);
		Date syncEndDate = (Date)projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getSyncEndTime();
		List<XnatAbstractresourceI> resources = recon.getIn_file();
		for (int i=0; i< resources.size(); i++) {
			XnatAbstractresource res = (XnatAbstractresource)resources.get(i);
			if (!resourceFilter.hasResourceBeenModified(res, syncEndDate)) {
				((XnatReconstructedimagedata)recon).removeIn_file(i);
				found = true;
				break;
			}
		}
		resources = recon.getOut_file();
		for (int i=0; i< resources.size(); i++) {
			XnatAbstractresource res = (XnatAbstractresource)resources.get(i);
			if (!resourceFilter.hasResourceBeenModified(res, syncEndDate)) {
					((XnatReconstructedimagedata)recon).removeIn_file(i);
					found = true;
					break;
			}
		}
		return found;
	}

}
