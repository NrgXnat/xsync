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
import org.nrg.xdat.base.BaseElement;
import org.nrg.xdat.model.XnatExperimentdataI;
import org.nrg.xdat.model.XnatImagescandataI;
import org.nrg.xdat.om.XnatImagescandata;
import org.nrg.xdat.om.XnatSubjectassessordata;
import org.nrg.xft.exception.ElementNotFoundException;
import org.nrg.xft.exception.FieldNotFoundException;
import org.nrg.xft.exception.XFTInitException;
import org.nrg.xnat.helpers.xmlpath.XMLPathShortcuts;
import org.nrg.xsync.exception.XsyncConfigurationException;
import org.nrg.xsync.utils.XsyncUtils;
import org.nrg.xsync.utils.XsyncUtils.FilterType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The Class SyncConfigurationXsiType.
 *
 * @author Mohana Ramaratnam
 * @author Atul Kaushal
 */
@JsonIgnoreProperties(ignoreUnknown = true)

public class SyncConfigurationXsiType {

	/** The logger. */
	public static final Logger logger = Logger.getLogger(SyncConfigurationXsiType.class);

	String xsi_type;
	Boolean needs_ok_to_sync;
	SyncConfigurationResource resources;
	
	/** The scan filters. */
	List<SyncConfigurationFilter> scan_filters =new ArrayList<SyncConfigurationFilter>();
	
	/** The filters. */
	List<SyncConfigurationFilter> filters =new ArrayList<SyncConfigurationFilter>();

	/**
	 * @return the xsi_type
	 */
	public String getXsi_type() {
		return xsi_type;
	}


	/**
	 * @param xsi_type the xsi_type to set
	 */
	public void setXsi_type(String xsi_type) {
		this.xsi_type = xsi_type;
	}


	/**
	 * @return the needs_ok_to_sync
	 */
	public Boolean getNeeds_ok_to_sync() {
		return needs_ok_to_sync;
	}


	/**
	 * @param needs_ok_to_sync the needs_ok_to_sync to set
	 */
	public void setNeeds_ok_to_sync(Boolean needs_ok_to_sync) {
		this.needs_ok_to_sync = needs_ok_to_sync;
	}


	/**
	 * @return the resources
	 */
	public SyncConfigurationResource getResources() {
		return resources;
	}


	/**
	 * @param resources the resources to set
	 */
	public void setResources(SyncConfigurationResource resources) {
		this.resources = resources;
	}

	/**
	 * Gets the scan filters.
	 *
	 * @return the scan_filters
	 */
	public List<SyncConfigurationFilter> getScan_filters() {
		return scan_filters;
	}

	/**
	 * Sets the scan filters.
	 *
	 * @param scan_filters
	 *            the scan_filters to set
	 */
	public void setScan_filters(List<SyncConfigurationFilter> scan_filters) {
		this.scan_filters = scan_filters;
	}

	/**
	 * Gets the filters.
	 *
	 * @return the filters
	 */
	public List<SyncConfigurationFilter> getFilters() {
		return filters;
	}

	/**
	 * Sets the filters.
	 *
	 * @param filters
	 *            the filters to set
	 */
	public void setFilters(List<SyncConfigurationFilter> filters) {
		this.filters = filters;
	}
	
	
	/**
	 * Gets the default sync configuration.
	 *
	 * @param xsiType the xsi type
	 * @return the sync configuration xsi type
	 */
	public static SyncConfigurationXsiType GetDefaultSyncConfiguration(String xsiType) {
		SyncConfigurationXsiType cfg = new SyncConfigurationXsiType();
		cfg.setXsi_type(xsiType);
		cfg.setNeeds_ok_to_sync(false);
		cfg.setResources(SyncConfigurationResource.GetDefaultSyncConfigurationResource());
		cfg.setScan_filters(new ArrayList<SyncConfigurationFilter>());
		cfg.setFilters(new ArrayList<SyncConfigurationFilter>());
		return cfg;
	}

	/**
	 * Checks if is resource allowed to sync.
	 *
	 * @param label
	 *            the label
	 * @return true, if is resource allowed to sync
	 */
	public boolean isResourceAllowedToSync(String label) {
		boolean isAllowed = false;
		if (resources == null) {
			return true;
		}else {
			isAllowed = resources.isAllowedToSync(label);
		}
		return isAllowed;
	}

	/**
	 * Checks if allowed to sync filters.
	 *
	 * @param item
	 *            the XnatImagescandataI object
	 * @return true, if allowed to sync filters
	 * @throws Exception
	 *             the exception
	 */
	public boolean isAllowedToSyncFilters(BaseElement item) throws Exception {
		boolean isAllowed = false;
		boolean excluded = false;
		List<SyncConfigurationFilter> inclusionFilter = new ArrayList<SyncConfigurationFilter>();
		List<SyncConfigurationFilter> exclusionFilter = new ArrayList<SyncConfigurationFilter>();
		List<SyncConfigurationFilter> filtersByObj = getFiltersByObjectType(item);
		try {
			if (filtersByObj == null || filtersByObj.isEmpty()) {
				isAllowed = true;
			} else {
				for (Iterator<SyncConfigurationFilter> iterator = filtersByObj.iterator(); iterator.hasNext();) {
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
						String value = getValue(item, fltr);
						if (!isIncludedInFilterList(fltr.getFilter_values(), fltr.getFilter_type(), value)) {
							isAllowed = true;
						} else {
							isAllowed = false;
							excluded=true;
							break;
						}
					}
				}
				if (!excluded && !inclusionFilter.isEmpty()) {
					Iterator<SyncConfigurationFilter> iter = inclusionFilter.iterator();
					while (iter.hasNext()) {
						SyncConfigurationFilter fltr = iter.next();
						String value = getValue(item, fltr);
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
			logger.error("Errors in filters configuration.  Kindly check xsync configuration JSON.", e);
			e.printStackTrace();
			throw new XsyncConfigurationException("Errors in filters configuration.  Kindly check xsync configuration.");
		}
		return isAllowed;
	}

	/**
	 * Gets the filters by object type.
	 *
	 * @param item
	 *            the item
	 * @return the filters by object type
	 */
	private List<SyncConfigurationFilter> getFiltersByObjectType(BaseElement item) {
		if (item instanceof XnatExperimentdataI) {
			return filters;
		} else {
			return scan_filters;
		}
	}

	/**
	 * Gets the value.
	 *
	 * @param item
	 *            the item
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
	private String getValue(BaseElement item, SyncConfigurationFilter fltr)
			throws XFTInitException, ElementNotFoundException, FieldNotFoundException {
		Object val = null;
		if (item instanceof XnatExperimentdataI) {
			val = ((XnatSubjectassessordata) item).getItem()
					.getProperty(getCompleteXMLPath(XMLPathShortcuts.EXPERIMENT_DATA, fltr.getXml_path()));
		} else if (item instanceof XnatImagescandataI) {
			val = ((XnatImagescandata) item).getItem()
					.getProperty(getCompleteXMLPath(XMLPathShortcuts.IMAGE_SCAN_DATA, fltr.getXml_path()));
		}
		return val == null ? null : val.toString();
	}

	/**
	 * Gets the complete XML path.
	 *
	 * @param xsiType
	 *            the xsi type
	 * @param xmlPath
	 *            the xml path
	 * @return the complete XML path
	 */
	private String getCompleteXMLPath(String xsiType, String xmlPath) {
		String completePath = XMLPathShortcuts.getInstance().getShortcuts(xsiType, true).get(xmlPath);
		return completePath == null ? xmlPath : completePath;
	}

	/**
	 * Checks if is included in filter list.
	 *
	 * @param filterList
	 *            the filter list
	 * @param filter_type
	 *            the filter type
	 * @param value
	 *            the value
	 * @return true, if is included in filter list
	 * @throws ScriptException
	 *             the script exception
	 * @throws XFTInitException
	 * @throws XsyncConfigurationException 
	 */
	private boolean isIncludedInFilterList(ArrayList<String> filterList, String filter_type, String value)
			throws ScriptException, XsyncConfigurationException {
		if (!isFilterTypePresentInEnum(filter_type)) {
			String errMsg=new StringBuilder("\"").append(filter_type).append("\" does not belong to filter types permissible values.").toString();
			logger.error(errMsg);
			throw new XsyncConfigurationException(errMsg);
		}
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
						logger.error("Issue in Groovy Eval script. Please re-check the script in xsync filters. " + evalText);
						e.printStackTrace();
						throw new XsyncConfigurationException("Issue in Groovy Eval script. Please re-check the script in xsync filters. " + evalText);
					}
				}
			}
		}
		return contains;
	}

	/**
	 * Checks if filter type present in enum.
	 *
	 * @param filterType
	 *            the filter type
	 * @return true, if filter type present in enum
	 */
	private static boolean isFilterTypePresentInEnum(String filterType) {

		for (FilterType filter : XsyncUtils.FilterType.values()) {
			if (filter.toString().equalsIgnoreCase(filterType)) {
				return true;
			}
		}
		return false;
	}
}
