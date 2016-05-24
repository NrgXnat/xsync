package org.nrg.xsync.connection;

import java.io.Serializable;

import org.nrg.xdat.entities.AliasToken;

/**
 * @author Mohana Ramaratnam
 *
 */
public class RemoteConnection implements Serializable{
	/**
	 * 
	 */
	String url;
	String username;
	String password;
	String jsessionid;
	
	
	
	public String getJsessionid() {
		return jsessionid;
	}
	public void setJsessionid(String jsessionid) {
		this.jsessionid = jsessionid;
	}
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
	
	

}
