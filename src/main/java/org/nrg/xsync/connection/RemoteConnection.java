package org.nrg.xsync.connection;

import lombok.extern.slf4j.Slf4j;
import org.nrg.framework.exceptions.NotFoundException;
import org.nrg.xdat.XDAT;
import org.nrg.xsync.remote.alias.RemoteAliasEntity;
import org.nrg.xsync.remote.alias.services.RemoteAliasService;

import java.util.Date;

/**
 * @author Mohana Ramaratnam
 *
 */

@Slf4j
public class RemoteConnection {
	/**
	 * 
	 */
	String url;
	String username;
	String password;
	String localProject;
	Date acquiredTime;
	long remoteAliasId;

	public RemoteConnection(long remoteAliasId) {
		this.remoteAliasId = remoteAliasId;
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
	
	public String getLocalProject() {
		return localProject;
	}

	public void setLocalProject(String localProject) {
		this.localProject = localProject;
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

	public long getRemoteAliasId() {
		return remoteAliasId;
	}

	public void setRemoteAliasId(long remoteAliasId) {
		this.remoteAliasId = remoteAliasId;
	}

	public void useRefreshedAliasToken() {
		RemoteAliasService ras = XDAT.getContextService().getBeanSafely(RemoteAliasService.class);
		if (ras == null) {
			log.error("Cannot refresh remote alias bc no RemoteAliasService found");
			return;
		}
		try {
			RemoteAliasEntity remoteAliasEntity = ras.get(remoteAliasId);
			setUsername(remoteAliasEntity.getRemote_alias_token());
			setPassword(remoteAliasEntity.getRemote_alias_password());
		} catch (NotFoundException e) {
			log.error("Cannot find remote alias", e);
		}
	}
}
