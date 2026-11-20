package org.nrg.xsync.connection;

import lombok.Getter;
import lombok.Setter;
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

@Setter
@Getter
@Slf4j
public class RemoteConnection {
	/**
	 * 
	 */
	String url;
	String username;
	String password;
	String localProject;
	/** The destination's remote project id; set per-send so a transport can
	 *  resolve per-destination config keyed on (url, remoteProjectId). May be
	 *  null for connections not built for a XAR send. */
	String remoteProjectId;
	Date acquiredDate;
	long remoteAliasId;

	public RemoteConnection(long remoteAliasId) {
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
