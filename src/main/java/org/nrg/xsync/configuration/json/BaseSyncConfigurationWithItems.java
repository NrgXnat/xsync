package org.nrg.xsync.configuration.json;

import java.util.ArrayList;
import java.util.List;

import org.nrg.xsync.utils.XsyncUtils;

/**
 * @author Mohana Ramaratnam
 *
 */
public class BaseSyncConfigurationWithItems extends BaseSyncConfiguration{
	List<String> items = new ArrayList<String>();

	
	/**
	 * @return the items
	 */
	public List<String> getItems() {
		return items;
	}
	
	/**
	 * @param items the items to set
	 */
	public void setItems(List<String> items) {
		this.items = items;
	}
	
	protected boolean isIncludedInItemList(String label) {
		boolean contains = false;
		for (String x:items) {
			if (x.equals(label)) {
				contains = true;
				break;
			}
		}
		return contains;
	}	
	
	public boolean isAllowedToSync(String item) {
		boolean isAllowed = false;
		if (sync_type.equals(XsyncUtils.SYNC_TYPE_ALL)) {
			isAllowed = true;
		}else if (sync_type.equals(XsyncUtils.SYNC_TYPE_NONE)) {
			return false;
		}else if (sync_type.equals(XsyncUtils.SYNC_TYPE_INCLUDE)) {
			if (isIncludedInItemList(item)) {
				isAllowed = true;
			}
		}else if (sync_type.equals(XsyncUtils.SYNC_TYPE_EXCLUDE)) {
			if (!isIncludedInItemList(item)) {
				isAllowed = true;
			}
		}
		return isAllowed;
	}
	

}
