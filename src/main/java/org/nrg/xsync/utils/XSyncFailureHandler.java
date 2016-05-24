package org.nrg.xsync.utils;

import org.nrg.xsync.connection.RemoteConnectionResponse;
import org.nrg.xsync.manager.SynchronizationManager;
import org.nrg.xsync.manifest.SubjectSyncItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Mohana Ramaratnam
 *
 */
public class XSyncFailureHandler {
	private static final Logger _log = LoggerFactory.getLogger(XSyncFailureHandler.class);
	
	public static void handle(String project, String localId,	String xsiType,String remoteId) {
		_log.error("Failed to sync " + project + " xsiType " + localId );
	}

	public static void handle (String project, String localId,	String xsiType, String preassigned_remote_id, SubjectSyncItem subjectSyncInfo, RemoteConnectionResponse response) {
		handle(project,localId,	xsiType,preassigned_remote_id );
		if (preassigned_remote_id != null) {
			subjectSyncInfo.setRemoteId(preassigned_remote_id);
		}
		subjectSyncInfo.setSyncStatus(XsyncUtils.SYNC_STATUS_FAILED);
		subjectSyncInfo.setXsiType(xsiType);
		subjectSyncInfo.setMessage(response.getResponseBody());
		SynchronizationManager.UPDATE_MANIFEST(project, subjectSyncInfo);

	}

	public static void handle (String project, String localId,	String xsiType, String preassigned_remote_id, SubjectSyncItem subjectSyncInfo, Exception e) {
		handle(project,localId,	xsiType,preassigned_remote_id );
		if (preassigned_remote_id != null) {
			subjectSyncInfo.setRemoteId(preassigned_remote_id);
		}
		subjectSyncInfo.setSyncStatus(XsyncUtils.SYNC_STATUS_FAILED);
		subjectSyncInfo.setXsiType(xsiType);
		subjectSyncInfo.setMessage(e.getMessage());
		SynchronizationManager.UPDATE_MANIFEST(project, subjectSyncInfo);

	}

}
