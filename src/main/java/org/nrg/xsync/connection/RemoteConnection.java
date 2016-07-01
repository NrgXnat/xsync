package org.nrg.xsync.connection;

import java.util.Date;

/**
 * @author Mohana Ramaratnam
 *
 */

public class RemoteConnection {
	/**
	 * 
	 */
	String url;
	String username;
	String password;
	String localProject;
	boolean locked = false;
	Date acquiredTime;
	

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}
	
	public String getLocalProject() {
		return localProject;
	}

	public void setLocalProject(String localProject) {
		this.localProject = localProject;
	}
	
	public void lock() {
		locked = true;
	}

	public void unlock() {
		locked = false;
	}

	
	public boolean isLocked() {
		return locked;
	}
	
	public Date getAcquiredDate() {
		return acquiredTime;
	}
	
	public void setAcquiredDate() {
		acquiredTime = new Date();
	}
	public void setAcquiredDate(Date d) {
		acquiredTime = d;
	}

}
