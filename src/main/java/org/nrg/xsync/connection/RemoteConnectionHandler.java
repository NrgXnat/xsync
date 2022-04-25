package org.nrg.xsync.connection;

import java.util.*;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class RemoteConnectionHandler {
	public RemoteConnectionHandler(final JdbcTemplate jdbcTemplate, final QueryResultUtil queryResultUtil) {
		this(new NamedParameterJdbcTemplate(jdbcTemplate), queryResultUtil);
	}

	public RemoteConnectionHandler(final NamedParameterJdbcTemplate jdbcTemplate, final QueryResultUtil queryResultUtil) {
		_jdbcTemplate = jdbcTemplate;
		_queryResultUtil = queryResultUtil;
	}

	private List<RemoteAliasEntity> rowsToObject(List<Map<String,Object>> rows) {
		List<RemoteAliasEntity> rowsAsList = new ArrayList<>();
		for (Map<String,Object> row:rows) {
			Long id = (Long) row.get("id");
			String local_project = (String) row.get("local_project");
			String remote_host = (String) row.get("remote_host");
			String remote_alias_token = (String) row.get("remote_alias_token");
			String remote_alias_password = (String) row.get("remote_alias_password");
			Date acquiredTime = (Date) row.get("acquired_time");
			RemoteAliasEntity remoteAliasEntity = new RemoteAliasEntity();
			remoteAliasEntity.setId(id == null ? 0L : id);
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
		String query = _queryResultUtil.getRemoteConnectionQuery();
		MapSqlParameterSource parameters = new MapSqlParameterSource();
		parameters.addValue("LOCAL_PROJECT", localProjectId);
		parameters.addValue("REMOTE_HOST", remoteHost);
		List<Map<String,Object>> results = _jdbcTemplate.queryForList(query, parameters);
		if (results == null || results.size() < 1) {
			throw new XsyncRemoteConnectionException("Unable to find remote connection information");
		}
		return rowsToObject(results).get(0);
	}
	
	private RemoteConnection getRemoteConnectionObject(String localProjectId, String remoteHost) throws XsyncRemoteConnectionException {
		RemoteAliasEntity remoteAliasEntity = getRemoteAliasEntity(localProjectId, remoteHost);
		return toRemoteConnection(remoteAliasEntity);
	}

	public RemoteConnection toRemoteConnection(RemoteAliasEntity remoteAliasEntity) {
		RemoteConnection conn = new RemoteConnection(remoteAliasEntity.getId());
		conn.setUrl(remoteAliasEntity.getRemote_host());
		conn.setUsername(remoteAliasEntity.getRemote_alias_token());
		conn.setPassword(remoteAliasEntity.getRemote_alias_password());
		conn.setAcquiredDate(remoteAliasEntity.getAcquiredTime());
		conn.setLocalProject(remoteAliasEntity.getLocal_project());
		return conn;
	}
	
	public RemoteConnection getConnection(String localProjectId, String remoteHost) throws XsyncRemoteConnectionException{
		RemoteConnection conn = getRemoteConnectionObject(localProjectId, remoteHost);
		if (isLocked(conn)) {
			//Scheduler may be acquiring a token
			//Wait for a minute?
			try {
			    Thread.sleep(TimeUnit.MINUTES.toMillis(1));
			} catch(InterruptedException ex) {
			    Thread.currentThread().interrupt();
			}
		}
		if (isLocked(conn)) {
			throw new XsyncRemoteConnectionException("Alias token refresh running on " + conn.getUrl() +
					" from project " + localProjectId);
		}
		//Hopefully by now the aliasToken has been acquired
		return conn;
	}

	public static synchronized boolean isLocked(RemoteConnection connection) {
		Long expireDt = LOCKED_CONNECTIONS.get(getUid(connection));
		return expireDt != null && System.currentTimeMillis() - expireDt < EXPIRE_MS;
	}

	public static synchronized boolean lock(RemoteConnection connection) {
		if (isLocked(connection)) return false;
		LOCKED_CONNECTIONS.put(getUid(connection), System.currentTimeMillis());
		return true;
	}

	public static synchronized void unlock(RemoteConnection connection) {
		LOCKED_CONNECTIONS.remove(getUid(connection));
	}

	private static String getUid(RemoteConnection connection) {
		return connection.getLocalProject() + connection.getUrl();
	}

	private final NamedParameterJdbcTemplate _jdbcTemplate;
	private final QueryResultUtil            _queryResultUtil;
	private static final Map<String, Long>	 LOCKED_CONNECTIONS = new HashMap<>();
	private static final long				 EXPIRE_MS = TimeUnit.HOURS.toMillis(1L);
}
