package org.nrg.xsync.remote.alias;

import java.util.Date;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import org.nrg.framework.orm.hibernate.AbstractHibernateEntity;

/**
 * @author Mohana Ramaratnam
 *
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"remote_host", "local_project"}))
public class RemoteAliasEntity extends AbstractHibernateEntity {
	
	
	/**
	 * @return the remote_alias_token
	 */
	public String getRemote_alias_token() {
		return remote_alias_token;
	}
	/**
	 * @param remote_alias_token the remote_alias_token to set
	 */
	public void setRemote_alias_token(String remote_alias_token) {
		this.remote_alias_token = remote_alias_token;
		this.acquiredTime = new Date();
	}
	/**
	 * @return the remote_alias_password
	 */
	public String getRemote_alias_password() {
		return remote_alias_password;
	}
	/**
	 * @param remote_alias_password the remote_alias_password to set
	 */
	public void setRemote_alias_password(String remote_alias_password) {
		this.remote_alias_password = remote_alias_password;
	}
	
	
	/**
	 * @return the remote_host
	 */
	public String getRemote_host() {
		return remote_host;
	}
	/**
	 * @param remote_host the remote_host to set
	 */
	public void setRemote_host(String remote_host) {
		this.remote_host = remote_host;
	}
	/**
	 * @return the local_project
	 */
	public String getLocal_project() {
		return local_project;
	}
	/**
	 * @param local_project the local_project to set
	 */
	public void setLocal_project(String local_project) {
		this.local_project = local_project;
	}

	/**
	 * @return the acquiredTime
	 */
	public Date getAcquiredTime() {
		return acquiredTime;
	}
	/**
	 * @param acquiredTime the acquiredTime to set
	 */
	public void setAcquiredTime(Date acquiredTime) {
		this.acquiredTime = acquiredTime;
	}


	private String remote_alias_token;
	private String remote_alias_password;
	private String remote_host;
	private String local_project;
	Date acquiredTime;
	
}
