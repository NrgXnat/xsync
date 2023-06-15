package org.nrg.xsync.utils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Hashtable;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.nrg.mail.services.MailService;
import org.nrg.xsync.connection.RemoteConnectionResponse;
import org.nrg.xsync.exception.XsyncIdConflictException;
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
	
	public static void handle (String project, String localId,	String xsiType, String preassigned_remote_id, SubjectSyncItem subjectSyncInfo, RemoteConnectionResponse response) {
		handle(project, localId);
		if (preassigned_remote_id != null) {
			subjectSyncInfo.setRemoteId(preassigned_remote_id);
		}
		subjectSyncInfo.setSyncStatus(XsyncUtils.SYNC_STATUS_FAILED);
		subjectSyncInfo.setXsiType(xsiType);
		subjectSyncInfo.setMessage(response.getResponseBody());
		SynchronizationManager.UPDATE_MANIFEST(project, subjectSyncInfo);

	}

	public static void handle (String project, String localId,	String xsiType, String preassigned_remote_id, SubjectSyncItem subjectSyncInfo, Exception e) {
		handle(project, localId);
		if (preassigned_remote_id != null) {
			subjectSyncInfo.setRemoteId(preassigned_remote_id);
		}
		if (e instanceof XsyncIdConflictException) {
			subjectSyncInfo.setSyncStatus(XsyncUtils.SYNC_STATUS_CONFLICT);
		} else {
			subjectSyncInfo.setSyncStatus(XsyncUtils.SYNC_STATUS_FAILED);
		}
		subjectSyncInfo.setXsiType(xsiType);
		subjectSyncInfo.setMessage(ExceptionUtils.getStackTrace(e));
		subjectSyncInfo.stateChanged();
		SynchronizationManager.UPDATE_MANIFEST(project, subjectSyncInfo);

	}

	public static void handle(final MailService mailService, String adminEmail, String siteId, String project, Exception e, String message) {
		final Hashtable<String,String> info = new Hashtable<>();

		final String subject= siteId + " XSYNC: Project " + project +" failed ";
		info.put("SUBJECT", subject);
		StringBuilder sb = new StringBuilder();
		sb.append("<html>").append("<body>");
		sb.append("<p>XSync Failed for project ").append(project).append(". </p>");
		sb.append("<p>").append(message).append("</p>");
		sb.append("<p>").append("To mitigate this problem, try to reset remote credentials. Details of the error are below.").append("</p>");
		sb.append("Encountered error ").append(e.getLocalizedMessage());
		StringWriter errors = new StringWriter();
		e.printStackTrace(new PrintWriter(errors));
		sb.append(errors.toString());
		sb.append("</body>").append("</html>");
		info.put("BODY", sb.toString());
		SynchronizationManager.END_ERROR_FAILURE_SYNC(project);
		try {
			mailService.sendHtmlMessage(adminEmail, adminEmail, info.get("SUBJECT"), info.get("BODY"));
		} catch (Exception ex) {
			_log.error("Failed to send email.", e);
		}
	}

	private static void handle(String project, String localId) {
		_log.error("Failed to sync " + project + " xsiType " + localId );
	}

}
