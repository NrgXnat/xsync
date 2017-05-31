package org.nrg.xsync.configuration.json;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.apache.log4j.Logger;
import org.nrg.xdat.model.XnatImagescandataI;
import org.nrg.xdat.om.XnatImagescandata;
import org.nrg.xft.exception.ElementNotFoundException;
import org.nrg.xft.exception.FieldNotFoundException;
import org.nrg.xft.exception.XFTInitException;
import org.nrg.xsync.utils.XsyncUtils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 *
 * @author Mohana Ramaratnam
 * @author Atul Kaushal
 */
@JsonIgnoreProperties(ignoreUnknown = true)

public class SyncConfigurationImagingSessionXsiType extends SyncConfigurationXsiType {

	/** The Constant logger. */
	public static final Logger logger = Logger.getLogger(SyncConfigurationImagingSessionXsiType.class);

	SyncConfigurationScanTypes scan_types;
	SyncConfigurationResource scan_resources;
	SyncConfigurationSessionAssessor session_assessors;

	/**
	 * @return the scan_types
	 */
	public SyncConfigurationScanTypes getScan_types() {
		return scan_types;
	}
	/**
	 * @param scan_types the scan_types to set
	 */
	public void setScan_types(SyncConfigurationScanTypes scan_types) {
		this.scan_types = scan_types;
	}
	/**
	 * @return the scan_resources
	 */
	public SyncConfigurationResource getScan_resources() {
		return scan_resources;
	}
	/**
	 * @param scan_resources the scan_resources to set
	 */
	public void setScan_resources(SyncConfigurationResource scan_resources) {
		this.scan_resources = scan_resources;
	}
	/**
	 * @return the session_assessors
	 */
	public SyncConfigurationSessionAssessor getSession_assessors() {
		return session_assessors;
	}
	/**
	 * @param session_assessors the session_assessors to set
	 */
	public void setSession_assessors(SyncConfigurationSessionAssessor session_assessors) {
		this.session_assessors = session_assessors;
	}

	/**
	 * Default configuration.
	 * 
	 * @param xsiType
	 *            the xsiType to set
	 * @return the SyncConfigurationImagingSessionXsiType object with default
	 *         configuration values.
	 */
	public static  SyncConfigurationImagingSessionXsiType GetDefaultImagingSessionSyncConfigurationAdvancedOption(String xsiType) {
		SyncConfigurationImagingSessionXsiType advOption = new SyncConfigurationImagingSessionXsiType();
		advOption.setXsi_type(xsiType);
		advOption.setNeeds_ok_to_sync(false);
		advOption.setResources(SyncConfigurationResource.GetDefaultSyncConfigurationResource());
		advOption.setScan_types(SyncConfigurationScanTypes.GetDefaultSyncConfigurationScanTypes());
		advOption.setScan_resources(SyncConfigurationResource.GetDefaultSyncConfigurationResource());
		advOption.setSession_assessors(SyncConfigurationSessionAssessor.GetDefaultSyncConfigurationSessionAssessor());
		advOption.setScan_filters(new ArrayList<SyncConfigurationFilter>());
		advOption.setFilters(new ArrayList<SyncConfigurationFilter>());
		return advOption;
	}
	
	public boolean isAllowedToSyncScan(String scanType) {
		boolean isAllowed = false;
		if (scan_types == null) {
			isAllowed = true;
		}else {
			isAllowed = scan_types.isAllowedToSync(scanType);
		}
		return isAllowed;
	}

	public boolean isAllowedToSyncScanResource(String scanResourceLabel) {
		boolean isAllowed = false;
		if (scan_resources == null) {
			isAllowed = true;
		}else {
			isAllowed = scan_resources.isAllowedToSync(scanResourceLabel);
		}
		return isAllowed;
	}

	public boolean isAllowedToSyncAssessor(String assessorXsiType) {
		boolean isAllowed = false;
		if (session_assessors == null) {
			isAllowed = true;
		} else {
			isAllowed = session_assessors.isAllowedToSync(assessorXsiType);
		}
		return isAllowed;
	}

	/**
	 * Checks if allowed to sync scan filters.
	 *
	 * @param scan            the XnatImagescandataI object
	 * @return true, if is allowed to sync scan filters
	 * @throws Exception the exception
	 */
	public boolean isAllowedToSyncScanFilters(XnatImagescandataI scan) throws Exception  {
		boolean isAllowed = false;
		boolean excluded = false;
		List<SyncConfigurationFilter> inclusionFilter = new ArrayList<SyncConfigurationFilter>();
		List<SyncConfigurationFilter> exclusionFilter = new ArrayList<SyncConfigurationFilter>();
		try {
			if (scan_filters == null) {
				isAllowed = true;
			} else {
				for (Iterator<SyncConfigurationFilter> iterator = scan_filters.iterator(); iterator.hasNext();) {
					SyncConfigurationFilter filter = iterator.next();
					if (XsyncUtils.SYNC_TYPE_ALL.equals(filter.getSync_type())) {
						isAllowed = true;
						return isAllowed;
					} else if (XsyncUtils.SYNC_TYPE_NONE.equals(filter.getSync_type())) {
						isAllowed = false;
						return isAllowed;
					} else if (XsyncUtils.SYNC_TYPE_EXCLUDE.equals(filter.getSync_type())) {
						exclusionFilter.add(filter);
					} else if (XsyncUtils.SYNC_TYPE_INCLUDE.equals(filter.getSync_type())) {
						inclusionFilter.add(filter);
					}
				}
				if (!exclusionFilter.isEmpty()) {
					Iterator<SyncConfigurationFilter> iter = exclusionFilter.iterator();
					while (iter.hasNext()) {
						SyncConfigurationFilter fltr = iter.next();
						String value = getValue(scan, fltr);
						if (!isIncludedInFilterList(fltr.getFilter_values(), fltr.getFilter_type(), value)) {
							isAllowed = true;
						} else {
							isAllowed = false;
							break;
						}
					}
				}
				if (!excluded && !inclusionFilter.isEmpty()) {
					Iterator<SyncConfigurationFilter> iter = inclusionFilter.iterator();
					while (iter.hasNext()) {
						SyncConfigurationFilter fltr = iter.next();
						String value = getValue(scan, fltr);
						if (isIncludedInFilterList(fltr.getFilter_values(), fltr.getFilter_type(), value)) {
							isAllowed = true;
						} else {
							isAllowed = false;
							break;
						}
					}
				}
			}

		} catch (XFTInitException | ElementNotFoundException | FieldNotFoundException e) {
			logger.debug("XML Path specified in configuration not found.");
			e.printStackTrace();
			throw e;
		}
		return isAllowed;
	}

	/**
	 * Gets the value.
	 *
	 * @param scan
	 *            the scan
	 * @param fltr
	 *            the fltr
	 * @return the value
	 * @throws XFTInitException
	 *             the XFT init exception
	 * @throws ElementNotFoundException
	 *             the element not found exception
	 * @throws FieldNotFoundException
	 *             the field not found exception
	 */
	private String getValue(XnatImagescandataI scan, SyncConfigurationFilter fltr)
			throws XFTInitException, ElementNotFoundException, FieldNotFoundException {
		Object val = ((XnatImagescandata) scan).getItem().getProperty(fltr.getXml_path());
		return val == null ? null : val.toString();
	}

	/**
	 * Checks if is included in filter list.
	 *
	 * @param filterList            the filter list
	 * @param filter_type            the filter type
	 * @param value            the value
	 * @return true, if is included in filter list
	 * @throws ScriptException the script exception
	 */
	protected boolean isIncludedInFilterList(ArrayList<String> filterList, String filter_type, String value) throws ScriptException {
		boolean contains = false;
		if (XsyncUtils.FilterType.CONTAINS.toString().equalsIgnoreCase(filter_type)) {
			if (filterList.contains(value)) {
				contains = true;
			}
		} else if (XsyncUtils.FilterType.REGEX.toString().equalsIgnoreCase(filter_type)) {
			if (filterList != null && !filterList.isEmpty()) {
				Pattern pattern = null;
				Matcher matcher = null;
				for (String regex : filterList) {
					pattern = Pattern.compile(regex);
					matcher = pattern.matcher(value);
					contains = matcher.matches();
					if (contains)
						break;
				}
			}
		} else if (XsyncUtils.FilterType.EVAL.toString().equalsIgnoreCase(filter_type)) {
			if (filterList != null && !filterList.isEmpty()) {
				ScriptEngineManager mgr = new ScriptEngineManager();
				ScriptEngine engine = mgr.getEngineByName(XsyncUtils.GROOVY_SCRIPT_ENGINE);
				for (String evalText : filterList) {
					try {
						Object obj = engine.eval(evalText.replace(XsyncUtils.EVAL_PLACE_HOLDER, value));
						if (obj != null && obj instanceof Boolean) {
							contains = Boolean.valueOf(obj.toString());
						}
					} catch (ScriptException e) {
						logger.debug("Issue in Groovy Eval script. Please re-check the script. " + evalText);
						e.printStackTrace();
						throw e;
					}
				}
			}
		}
		return contains;
	}
}
