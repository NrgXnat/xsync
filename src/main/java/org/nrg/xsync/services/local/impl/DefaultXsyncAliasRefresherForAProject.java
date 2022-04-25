package org.nrg.xsync.services.local.impl;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import org.nrg.framework.exceptions.NotFoundException;
import org.nrg.framework.services.ContextService;
import org.nrg.framework.services.SerializerService;
import org.nrg.xdat.XDAT;
import org.nrg.xdat.turbine.utils.AdminUtils;
import org.nrg.xsync.connection.RemoteConnection;
import org.nrg.xsync.connection.RemoteConnectionHandler;
import org.nrg.xsync.connection.RemoteConnectionResponse;
import org.nrg.xsync.remote.alias.RemoteAliasEntity;
import org.nrg.xsync.remote.alias.services.RemoteAliasService;
import org.nrg.xsync.services.remote.RemoteRESTService;



/**
 * @author Mohana Ramaratnam
 *
 */
@Slf4j
public class DefaultXsyncAliasRefresherForAProject implements Runnable{

	private final RemoteConnection _conn;
	private final RemoteAliasEntity _connEntity;
	private final RemoteRESTService _restService;
	private final SerializerService _serializer;
	private final RemoteAliasService _aliasService;
	private static final long sleep = TimeUnit.MINUTES.toMillis(15L);
	private static final int maxTries = 4;

	public DefaultXsyncAliasRefresherForAProject(RemoteAliasEntity connEntity, RemoteConnection conn, 
			final RemoteRESTService restService, final SerializerService serializer, final RemoteAliasService aliasService) {
		_connEntity = connEntity;
		_conn = conn;
		_restService = restService;
		_serializer = serializer;
		_aliasService = aliasService;
	}
	
	public void run() {
		//Refresh the token
		int count = 0;
		if (!RemoteConnectionHandler.lock(_conn)) {
			log.info("Alias token refresh already running for {} {}", _conn.getLocalProject(), _conn.getUrl());
			return;
		}
		while(true) {
		    try {
		    	final String tokenUrl = _conn.getUrl() + "/data/services/tokens/issue/" + _conn.getUsername() + "/" + _conn.getPassword();
				final RemoteConnectionResponse remoteResponse = _restService.getResult(_conn, tokenUrl);
				log.debug("Token issue called - {} - (SUCCESS={})", tokenUrl, remoteResponse.wasSuccessful());
				/* IMPORTANT - September 27, 2016
				 * The following code was replaced as XNAT was sending the estimatedExpirationDate
				 * in a format which is not a standard date format and so the deserialize method is failing
				 * 
					final AliasToken aliasToken = _serializer.deserializeJson(remoteResponse.getResponse().getBody(), AliasToken.class);
					conn.setUsername(aliasToken.getAlias());
					conn.setPassword(aliasToken.getSecret());
					connEntity.setRemote_alias_token(aliasToken.getAlias());
					connEntity.setRemote_alias_password(aliasToken.getSecret());
					_aliasService.update(connEntity);
				 */
				if (!remoteResponse.wasSuccessful()) {
					throw new RuntimeException("XsyncAliasTokenRefresh Failed for project " + _conn.getLocalProject() +
							" (HTTP Status Code " + remoteResponse.getResponse().getStatusCode() +"). Retrying...");
				}
				final Map<String, String> token = _serializer.deserializeJsonToMapOfStrings(remoteResponse.getResponse().getBody());
				final String alias = token.get("alias");
				final String secret = token.get("secret");
				final String expirationTime = token.get("estimatedExpirationTime");
				final long l = Long.parseLong(expirationTime);
				final Date expirationDate = new Date(l);
				_conn.setUsername(alias);
				_conn.setPassword(secret);
				_connEntity.setRemote_alias_token(alias);
				_connEntity.setRemote_alias_password(secret);
				_connEntity.setEstimatedExpirationTime(expirationDate);
				_aliasService.update(_connEntity);
				log.debug("Connection information successfully updated");
				break;
		    } catch (Exception e) {
	             if (maxTries == 0 || ++count == maxTries) {
	             		AdminUtils.sendAdminEmail("XSync token refresh failure",
								"XSync token refresh failure for local project  " +
										_connEntity.getLocal_project() + ", host " + _conn.getUrl() +
										". Attempted to refresh token " + maxTries + " times. " +
										"New credentials may need to be provided or the offending entity (id=" +
										_connEntity.getId() + ") removed from the xhbm_remote_alias_entity table.");
	             		break;
	             }
				try {
					log.error("XsyncAliasTokenRefresh: retrycount {} out of {}", count, maxTries, e);
					Thread.sleep(sleep);
				} catch (InterruptedException e1) {
					// Ignore
				}
	    	} finally {
	    		RemoteConnectionHandler.unlock(_conn);
	    	}
		}
	}
}


	
