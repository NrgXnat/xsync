package org.nrg.xsync.configuration.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.nrg.xdat.base.BaseElement;
import org.nrg.xdat.model.XnatExperimentdataI;
import org.nrg.xdat.model.XnatImagescandataI;
import org.nrg.xft.exception.ElementNotFoundException;
import org.nrg.xft.exception.FieldNotFoundException;
import org.nrg.xft.exception.XFTInitException;
import org.nrg.xnat.helpers.xmlpath.XMLPathShortcuts;
import org.nrg.xsync.exception.XsyncConfigurationException;
import org.nrg.xsync.exception.XsyncInvalidFilterType;
import org.nrg.xsync.utils.XsyncUtils;
import org.nrg.xsync.utils.XsyncUtils.FilterType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Class SyncConfigurationXsiType.
 *
 * @author Mohana Ramaratnam
 * @author Atul Kaushal
 */
@JsonIgnoreProperties(ignoreUnknown = true)

public class SyncConfigurationXsiType {

	/** The logger. */
	public static final Logger logger = LoggerFactory.getLogger(SyncConfigurationXsiType.class);

	String xsi_type;
	Boolean needs_ok_to_sync;
	SyncConfigurationResource resources;
	
	/** The scan filters. */
	List<SyncConfigurationFilter> scan_filters = new ArrayList<>();
	
	/** The filters. */
	List<SyncConfigurationFilter> filters = new ArrayList<>();

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
	@SuppressWarnings("unused")
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
		cfg.setScan_filters(new ArrayList<>());
		cfg.setFilters(new ArrayList<>());
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
		return resources == null || resources.isAllowedToSync(label);
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
		List<SyncConfigurationFilter> filtersByObj = getFiltersByObjectType(item);
		if (filtersByObj == null || filtersByObj.isEmpty()) {
			return true;
		}

		boolean                       isAllowed       = false;
		boolean                       excluded        = false;
		List<SyncConfigurationFilter> inclusionFilter = new ArrayList<>();
		List<SyncConfigurationFilter> exclusionFilter = new ArrayList<>();
		try {
			for (final SyncConfigurationFilter filter : filtersByObj) {
				if (XsyncUtils.SYNC_TYPE_ALL.equals(filter.getSync_type())) {
					return true;
				}
				if (XsyncUtils.SYNC_TYPE_NONE.equals(filter.getSync_type())) {
					return false;
				}
				if (XsyncUtils.SYNC_TYPE_EXCLUDE.equals(filter.getSync_type())) {
					exclusionFilter.add(filter);
				} else if (XsyncUtils.SYNC_TYPE_INCLUDE.equals(filter.getSync_type())) {
					inclusionFilter.add(filter);
				}
			}
			if (!exclusionFilter.isEmpty()) {
				for (final SyncConfigurationFilter filter : exclusionFilter) {
					if (!isIncludedInFilterList(filter.getFilter_values(), filter.getFilter_type(), getValue(item, filter))) {
						isAllowed = true;
					} else {
						isAllowed = false;
						excluded = true;
						break;
					}
				}
			}
			if (!excluded && !inclusionFilter.isEmpty()) {
				for (final SyncConfigurationFilter filter : inclusionFilter) {
					if (isIncludedInFilterList(filter.getFilter_values(), filter.getFilter_type(), getValue(item, filter))) {
						isAllowed = true;
					} else {
						isAllowed = false;
						break;
					}
				}
			}
		} catch (XFTInitException | ElementNotFoundException | FieldNotFoundException e) {
			throw new XsyncConfigurationException("Errors in filters configuration.  Kindly check xsync configuration JSON.", e);
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
	 * @param filter
	 *            the filter
	 * @return the value
	 * @throws XFTInitException
	 *             the XFT init exception
	 * @throws ElementNotFoundException
	 *             the element not found exception
	 * @throws FieldNotFoundException
	 *             the field not found exception
	 */
	private String getValue(BaseElement item, SyncConfigurationFilter filter)
			throws XFTInitException, ElementNotFoundException, FieldNotFoundException {
		Object val = null;
		if (item instanceof XnatExperimentdataI) {
			val = item.getItem().getProperty(getCompleteXMLPath(XMLPathShortcuts.EXPERIMENT_DATA, filter.getXml_path()));
		} else if (item instanceof XnatImagescandataI) {
			val = item.getItem().getProperty(getCompleteXMLPath(XMLPathShortcuts.IMAGE_SCAN_DATA, filter.getXml_path()));
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
		return StringUtils.getIfBlank(XMLPathShortcuts.getInstance().getShortcuts(xsiType, true).get(xmlPath), () -> xmlPath);
	}

	/**
	 * Checks if is included in filter list.
	 *
	 * @param filterList
	 *            the filter list
	 * @param filterType
	 *            the filter type
	 * @param value
	 *            the value
	 * @return true, if is included in filter list
	 * @throws XsyncConfigurationException When an error is encountered in the XSync configuration.
	 */
	private boolean isIncludedInFilterList(List<String> filterList, String filterType, String value) throws XsyncConfigurationException {
		if (!isFilterTypePresentInEnum(filterType)) {
			throw new XsyncInvalidFilterType(filterType);
		}
		if (XsyncUtils.FilterType.CONTAINS.toString().equalsIgnoreCase(filterType)) {
			return filterList.contains(value);
		}
		if (CollectionUtils.isEmpty(filterList)) {
			return false;
		}
		return filterList.stream().map(Pattern::compile).map(pattern -> pattern.matcher(value)).anyMatch(Matcher::matches);
	}

	/**
	 * Checks if filter type present in enum.
	 *
	 * @param filterType
	 *            the filter type
	 * @return true, if filter type present in enum
	 */
	private static boolean isFilterTypePresentInEnum(String filterType) {
		return Arrays.stream(FilterType.values())
					 .map(Objects::toString)
					 .anyMatch(type -> type.equalsIgnoreCase(filterType));
	}
}
