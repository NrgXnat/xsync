package org.nrg.xsync.services.local.impl;

import java.util.List;
import java.util.Map;

import org.nrg.framework.services.SerializerService;
import org.nrg.xdat.turbine.utils.AdminUtils;
import org.nrg.xsync.connection.RemoteConnection;
import org.nrg.xsync.connection.RemoteConnectionHandler;
import org.nrg.xsync.connection.RemoteConnectionResponse;
import org.nrg.xsync.remote.alias.RemoteAliasEntity;
import org.nrg.xsync.remote.alias.services.RemoteAliasService;
import org.nrg.xsync.services.local.XsyncAliasRefreshService;
import org.nrg.xsync.services.remote.RemoteRESTService;
import org.nrg.xsync.utils.QueryResultUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;


/**
 * The Class DefaultXsyncAliasRefresher.
 *
 * @author Mohana Ramaratnam, Mike Hodge
 */
@Service
public class DefaultXsyncAliasRefresher implements XsyncAliasRefreshService{
	
	/** The logger. */
	private static final Logger logger = LoggerFactory.getLogger(DefaultXsyncAliasRefresher.class);
	
	private final RemoteAliasService _aliasService;
	private final RemoteRESTService _restService;
	private final JdbcTemplate _jdbcTemplate;
	private final SerializerService _serializer;
	private final QueryResultUtil _queryResultUtil;

	@Autowired
	public DefaultXsyncAliasRefresher(final RemoteAliasService aliasService, final RemoteRESTService restService, final JdbcTemplate jdbcTemplate, final SerializerService serializer, final QueryResultUtil queryResultUtil) {
		_aliasService = aliasService;
		_restService = restService;
		_jdbcTemplate = jdbcTemplate;
		_serializer = serializer;
		_queryResultUtil = queryResultUtil;
	}

	/* (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void refreshToken() {
		//Get all the connection information from the RemoteConnectionManager
		//For each of the connections
		//Acquire the lock before you refresh them
		final RemoteConnectionHandler remoteConnectionHandler = new RemoteConnectionHandler(_jdbcTemplate, _queryResultUtil);
		final List<RemoteAliasEntity> remoteAliasEntities = _aliasService.getAll();
		if (remoteAliasEntities == null || remoteAliasEntities.size()<1) {
			return;
		}
		for (final RemoteAliasEntity connEntity:remoteAliasEntities) {
			final RemoteConnection conn = remoteConnectionHandler.toRemoteConnection(connEntity);
			logger.info("Refreshing Alias for " + conn.getUrl());
			conn.lock();
			//Refresh the token
			try {
				final RemoteConnectionResponse remoteResponse = _restService.getResult(conn,
						conn.getUrl() + "/data/services/tokens/issue/" + conn.getUsername() + "/" + conn.getPassword());
//				final RemoteConnectionResponse remoteResponse = _restService.getResult(conn,
//						conn.getUrl() + "/data/services/tokens/issue/" + conn.getUsername() + "/" + conn.getPassword());

				if (!remoteResponse.wasSuccessful()) {
					AdminUtils.sendAdminEmail("XSync token refresh failure", "XSync token refresh failure for local project  " +
							connEntity.getLocal_project() + ", host " + conn.getUrl() + 
							".  New credentials may need to be provided.  (HTTP Status=" + remoteResponse.getResponse().getStatusCode() + ")");
				}
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
				final Map<String, String> token = _serializer.deserializeJsonToMapOfStrings(remoteResponse.getResponse().getBody());
				final String alias = token.get("alias");
				final String secret = token.get("secret");
				conn.setUsername(alias);
				conn.setPassword(secret);
				connEntity.setRemote_alias_token(alias);
				connEntity.setRemote_alias_password(secret);				
				
			}catch(Exception e) {
				logger.error("An error occurred while refreshing an alias token", e);
				AdminUtils.sendAdminEmail("XSync token refresh failure", "XSync token refresh failure for local project  " +
						connEntity.getLocal_project() + ", host " + conn.getUrl() + 
						".  New credentials may need to be provided.  (Exception=" + e.toString() + ")");
			}
			conn.unlock();
		}
	}
}
