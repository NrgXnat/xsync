package org.nrg.xsync.configuration.json;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * @author Mohana Ramaratnam
 *
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SubjectAssessorConfiguration {
	String xsiType;
	List<String> resources;
	Boolean needs_ok_to_sync;
	/**
	 * @return the xsiType
	 */
	public String getXsiType() {
		return xsiType;
	}
	/**
	 * @param xsiType the xsiType to set
	 */
	public void setXsiType(String xsiType) {
		this.xsiType = xsiType;
	}
	
	/**
	 * @return the resources
	 */
	public List<String> getResources() {
		return resources;
	}
	/**
	 * @param resources the resources to set
	 */
	public void setResources(List<String> resources) {
		this.resources = resources;
	}
	/**
	 * @return the needsSyncFlag
	 */
	public Boolean getNeedsSyncFlag() {
		return needs_ok_to_sync;
	}
	
	public boolean checkOkToSync() {
		boolean ok = false;
		if (needs_ok_to_sync != null ) {
			ok =  needs_ok_to_sync.booleanValue();
		}
		return ok;	
	}
	/**
	 * @param needsSyncFlag the needsSyncFlag to set
	 */
	public void setNeedsSyncFlag(Boolean needsSyncFlag) {
		this.needs_ok_to_sync = needsSyncFlag;
	}	

	
	public String toString() {
		String out ="";
		out += "xsiType: " + xsiType + "\n";
		if (resources != null) {
			out+= "resources:";
			for (String res:resources) 
				out +=res + " \n" ;
		}
		out += "needs_ok_to_sync:" + needs_ok_to_sync + "\n";

		return out;
	}
}
