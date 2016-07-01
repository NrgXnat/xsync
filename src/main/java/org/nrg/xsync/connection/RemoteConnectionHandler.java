package org.nrg.xsync.connection;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.nrg.xdat.XDAT;
import org.nrg.xsync.exception.XsyncRemoteConnectionException;
import org.nrg.xsync.remote.alias.RemoteAliasEntity;
import org.nrg.xsync.utils.QueryResultUtil;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * @author Mohana Ramaratnam
 *
 */
public class RemoteConnectionHandler {

	private List<RemoteAliasEntity> rowsToObject(List<Map<String,Object>> rows) {
		List<RemoteAliasEntity> rowsAsList = new ArrayList<RemoteAliasEntity>();
		for (Map<String,Object> row:rows) {
			RemoteAliasEntity remoteAliasEntity = new RemoteAliasEntity();
			String local_project = (String)row.get("local_project");
			String remote_host = (String)row.get("remote_host");
			String remote_alias_token = (String) row.get("remote_alias_token");
			String remote_alias_password = (String) row.get("remote_alias_password");
			Date acquiredTime = (Date) row.get("acquired_time");
			remoteAliasEntity = new RemoteAliasEntity();
			remoteAliasEntity.setAcquiredTime(acquiredTime);
			remoteAliasEntity.setRemote_alias_password(remote_alias_password);
			remoteAliasEntity.setRemote_alias_token(remote_alias_token);
			remoteAliasEntity.setLocal_project(local_project);
			remoteAliasEntity.setRemote_host(remote_host);
			rowsAsList.add(remoteAliasEntity);
		}
		return rowsAsList;
	}
	
	private RemoteAliasEntity getRemoteAliasEntity(String localProjectId, String remoteHost) throws XsyncRemoteConnectionException {
		RemoteAliasEntity remoteAliasEntity = null;
		QueryResultUtil queryUtil = new QueryResultUtil();
		String query = queryUtil.getRemoteConnectionQuery();
		MapSqlParameterSource parameters = new MapSqlParameterSource();
		parameters.addValue("LOCAL_PROJECT", localProjectId);
		parameters.addValue("REMOTE_HOST", remoteHost);
		NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(
				new JdbcTemplate(XDAT.getDataSource()));
		List<Map<String,Object>> results = jdbcTemplate.queryForList(query, parameters);
		if (results == null || results.size() < 1) {
			throw new XsyncRemoteConnectionException("Unable to find remote connection information");
		}else {
			List<RemoteAliasEntity> remoteAliasEntities = rowsToObject(results);
			remoteAliasEntity = remoteAliasEntities.get(0);
		}
		return remoteAliasEntity;
	}
	
	private RemoteConnection getRemoteConnectionObject(String localProjectId, String remoteHost) throws XsyncRemoteConnectionException {
		RemoteAliasEntity remoteAliasEntity = getRemoteAliasEntity(localProjectId, remoteHost);
		RemoteConnection conn = new RemoteConnection();
		conn.setUrl(remoteAliasEntity.getRemote_host());
		conn.setUsername(remoteAliasEntity.getRemote_alias_token());
		conn.setPassword(remoteAliasEntity.getRemote_alias_password());
		conn.setLocalProject(remoteAliasEntity.getLocal_project());
		conn.getAcquiredDate();
		return conn;
	}

	public RemoteConnection toRemoteConnection(RemoteAliasEntity remoteAliasEntity) {
		RemoteConnection conn = new RemoteConnection();
		conn.setUrl(remoteAliasEntity.getRemote_host());
		conn.setUsername(remoteAliasEntity.getRemote_alias_token());
		conn.setPassword(remoteAliasEntity.getRemote_alias_password());
		conn.setAcquiredDate(remoteAliasEntity.getAcquiredTime());
		conn.setLocalProject(remoteAliasEntity.getLocal_project());
		return conn;
	}
	
	public  RemoteConnection getConnection(String localProjectId, String remoteHost) throws XsyncRemoteConnectionException{		
		RemoteConnection conn =  getRemoteConnectionObject(localProjectId,remoteHost);
		if (conn.isLocked()) {
			//Scheduler may be acquiring a token
			//Wait for a minute?
			try {
			    Thread.sleep(60000);                 //1000 milliseconds is one second.
			} catch(InterruptedException ex) {
			    Thread.currentThread().interrupt();
			}
		}
		if (conn.isLocked()) {
			throw new XsyncRemoteConnectionException("Unable to clear lock for connection " + conn.getUrl() + " Project: " + localProjectId);
		}
		//Hopefully by now the aliasToken has been acquired
		return conn;
	}

}
