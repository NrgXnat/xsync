package org.nrg.xsync.configuration.json;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * @author Mohana Ramaratnam
 *
 */
@JsonIgnoreProperties(ignoreUnknown = true)

public class ImagingSessionConfiguration implements Serializable{
	String xsiType;
	List<String> resources;
	List<ImagingScanConfiguration> scans;
	List<ImagingAssessorConfiguration> assessors;
	Boolean anonymize;
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
	 * @return the scans
	 */
	public List<ImagingScanConfiguration> getScans() {
		return scans;
	}
	/**
	 * @param scans the scans to set
	 */
	public void setScans(List<ImagingScanConfiguration> scans) {
		this.scans = scans;
	}
	/**
	 * @return the anonymize
	 */
	public Boolean getAnonymize() {
		return anonymize;
	}
	/**
	 * @param anonymize the anonymize to set
	 */
	public void setAnonymize(Boolean anonymize) {
		this.anonymize = anonymize;
	}
	/**
	 * @return the assessors
	 */
	public List<ImagingAssessorConfiguration> getAssessors() {
		return assessors;
	}
	/**
	 * @param assessors the assessors to set
	 */
	public void setAssessors(List<ImagingAssessorConfiguration> assessors) {
		this.assessors = assessors;
	}
	
	
	
	public List<String> getScanTypes() {
		List<String> scanTypes = new ArrayList<String>();
		List<ImagingScanConfiguration> scans = getScans();
		for (ImagingScanConfiguration scan:scans) {
			scanTypes.add(scan.getType());
		}
		return scanTypes;
	}
	
	/**
	 * @return the needs_ok_to_sync
	 */
	public Boolean getNeeds_ok_to_sync() {
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
	 * @param needs_ok_to_sync the needs_ok_to_sync to set
	 */
	public void setNeeds_ok_to_sync(Boolean needs_ok_to_sync) {
		this.needs_ok_to_sync = needs_ok_to_sync;
	}
	public String toString() {
		String out ="";
		out += "xsiType: " + xsiType + "\n";
		if (resources != null) {
			out+= "resources:";
			for (String res:resources) 
				out +=res + " \n" ;
		}
		if (scans != null) {
			out+= "scans:";
			for (ImagingScanConfiguration imagingScan:scans) 
				out +=imagingScan + " \n" ;
		}
		if (assessors != null) {
			out+= "assessors:";
			for (ImagingAssessorConfiguration assessor:assessors)
				out +=assessor + " \n";
		}
		out += "anonymize:" + anonymize + "\n";
		out += "needs_ok_to_sync:" + needs_ok_to_sync + "\n";
		
		return out;
	}
}
