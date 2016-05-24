package org.nrg.xsync.configuration.json;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * @author Mohana Ramaratnam
 *
 */
@JsonIgnoreProperties(ignoreUnknown = true)

public class ImagingScanConfiguration implements Serializable{
	String type;
	List<String> resources;
	/**
	 * @return the type
	 */
	public String getType() {
		return type;
	}
	/**
	 * @param type the type to set
	 */
	public void setType(String type) {
		this.type = type;
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
	
	public String toString() {
		String out = "";
		out += "type:" + type + "\n";
		if (resources != null) {
			out += "resources:";
			for (String resource:resources) {
				out += resource + " ";
			}
		}
		return out;
	}
}
