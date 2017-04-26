package org.nrg.xsync.services.local.impl;

import java.io.IOException;
import java.util.Date;
import java.util.Map;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.log4j.Logger;
import org.nrg.framework.services.SerializerService;
import org.nrg.xdat.turbine.utils.AdminUtils;
import org.nrg.xsync.connection.RemoteConnection;
import org.nrg.xsync.connection.RemoteConnectionResponse;
import org.nrg.xsync.remote.alias.RemoteAliasEntity;
import org.nrg.xsync.remote.alias.services.RemoteAliasService;
import org.nrg.xsync.services.remote.RemoteRESTService;



/**
 * @author Mohana Ramaratnam
 *
 */
public class DefaultXsyncAliasRefresherForAProject implements Runnable{

	RemoteConnection _conn;
	RemoteAliasEntity _connEntity;
	private final RemoteRESTService _restService;
	private final SerializerService _serializer;
	private final RemoteAliasService _aliasService;
	private long sleep = 900000; //in Millis = 15 minutes
	private int maxTries = 4;

	/** The logger. */
	public static Logger logger = Logger.getLogger(DefaultXsyncAliasRefresherForAProject.class);
	
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
		while(true) {
			_conn.lock();
		    try {
		    	final String tokenUrl = _conn.getUrl() + "/data/services/tokens/issue/" + _conn.getUsername() + "/" + _conn.getPassword();
				final RemoteConnectionResponse remoteResponse = _restService.getResult(_conn, tokenUrl);
				logger.debug("Token issue called - " + tokenUrl + " - (SUCCESS=" + remoteResponse.wasSuccessful() + ")");
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
					throw new RuntimeException("XsyncAliasTokenRefresh Failed for project " + _conn.getLocalProject() + " (HTTP Status Code " + remoteResponse.getResponse().getStatusCode() +") . Retrying...");
				}
				final Map<String, String> token = _serializer.deserializeJsonToMapOfStrings(remoteResponse.getResponse().getBody());
				final String alias = token.get("alias");
				final String secret = token.get("secret");
				final String expirationTime = token.get("estimatedExpirationTime");
				final Long l = Long.parseLong(expirationTime);
				_conn.setUsername(alias);
				_conn.setPassword(secret);
				_connEntity.setRemote_alias_token(alias);
				_connEntity.setRemote_alias_password(secret);	
				final Date expirationDate = new Date(l);
				_connEntity.setEstimatedExpirationTime(expirationDate);
				_aliasService.update(_connEntity);
				logger.debug("Connection information successfully updated");
				break;
		    } catch (RuntimeException re) {
		    	try {
		    		logger.error(ExceptionUtils.getStackTrace(re));
		    		logger.error("XsyncAliasTokenRefresh: retrycount "+ count + " out of " + maxTries);
					if (maxTries > 0) Thread.sleep(sleep);
			     } catch (InterruptedException e1) {
				      e1.printStackTrace();
			     }
	             // handle exception
	             if (maxTries == 0 || ++count == maxTries) {
	             		AdminUtils.sendAdminEmail("XSync token refresh failure", "XSync token refresh failure for local project  " +
		       			_connEntity.getLocal_project() + ", host " + _conn.getUrl() + 
		       	         	". Attempted to refresh token " + maxTries + " times. New credentials may need to be provided.");
	               		throw re;
	             }
	    	} catch(Exception e) {
		    	try {
					Thread.sleep(5000);
			    } catch (InterruptedException e1) {
			    	 // Do nothing
			    }
	    		logger.error(ExceptionUtils.getStackTrace(e));
	    	}finally{
	    		_conn.unlock();
	    	}
		}
	}
}


	
