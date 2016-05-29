package org.nrg.xsync.services.local.impl;

import java.util.Collection;
import java.util.Date;
import java.util.Hashtable;

import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;
import org.nrg.xdat.entities.AliasToken;
import org.nrg.xsync.connection.RemoteConnection;
import org.nrg.xsync.connection.RemoteConnectionManager;
import org.nrg.xsync.services.local.XsyncAliasRefreshService;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * @author Mohana Ramaratnam
 *
 */
@Service
public class DefaultXsyncAliasRefresher implements XsyncAliasRefreshService{
	private static final long TWENTYTHREE_HOURS = 23; 
	public static Logger logger = Logger.getLogger(DefaultXsyncAliasRefresher.class);

	/* (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
	
	@Override
	public void refreshToken() {
		//Get all the connection information from the RemoteConnectionManager
		//For each of the connections
		//If they are older than the runtime of current run, refresh them
		//Acquire the lock before you refresh them
		Hashtable<String,RemoteConnection> projectConnections = RemoteConnectionManager.GetAllConnections();
		Collection<RemoteConnection> connections = projectConnections.values();
		Date now = new Date();
		for (RemoteConnection conn:connections) {
			Date connAcquiredTime = conn.getAcquiredDate();
			long hours = getDifference(connAcquiredTime,now); 
			if (hours > TWENTYTHREE_HOURS) {
				logger.info("Refreshing Alias for " + conn.getUrl());
				conn.lock();
				//Refresh the token
				SimpleClientHttpRequestFactory requestFactory =new SimpleClientHttpRequestFactory();
				RestTemplate template = new RestTemplate(requestFactory);
				ResponseEntity<String> response = template.getForEntity(conn.getUrl()+"/data/services/tokens/issue/"+conn.getUsername()+"/"+conn.getPassword(),String.class);
				try {
					AliasToken aliasToken = (AliasToken) new ObjectMapper().readValue(response.getBody(), AliasToken.class);	
					conn.setUsername(aliasToken.getAlias());
					conn.setPassword(aliasToken.getSecret());
				}catch(Exception e) {
					logger.debug(e);
				}
				conn.unlock();
			}
		}
	}
	
	private long getDifference(Date date1, Date date2) {
		long diffHours = 25;
		try {
			//in milliseconds
			Long diff = date2.getTime() - date1.getTime();

			//long diffSeconds = diff / 1000 % 60;
			//long diffMinutes = diff / (60 * 1000) % 60;
			diffHours = diff / (60 * 60 * 1000) % 24;
			//long diffDays = diff / (24 * 60 * 60 * 1000);
		} catch (Exception e) {
			logger.debug(e);
		}
		return diffHours;
	}

}
