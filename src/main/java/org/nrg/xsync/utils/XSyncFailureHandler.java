package org.nrg.xsync.utils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Hashtable;

import javax.mail.MessagingException;

import org.nrg.xdat.XDAT;
import org.nrg.xdat.turbine.utils.AdminUtils;
import org.nrg.xsync.connection.RemoteConnectionResponse;
import org.nrg.xsync.manager.SynchronizationManager;
import org.nrg.xsync.manifest.SubjectSyncItem;
import org.nrg.xsync.tools.XsyncXnatInfo;
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
	
	public static void handle(String project, Exception e, String message) {
		final Hashtable<String,String> info = new Hashtable<String,String>();
		final XsyncXnatInfo xnatInfo = XDAT.getContextService().getBean(XsyncXnatInfo.class);
		
		final String subject= xnatInfo.getSiteId() + " XSYNC: Project " + project +" failed ";
		info.put("SUBJECT", subject);
		StringBuilder sb = new StringBuilder();
		sb.append("<html>");
        sb.append("<body>");
		sb.append("<p>XSync Failed for project "+project+". </p>");
		sb.append("<p>" + message + "</p>");
		sb.append("Enountered error " + e.getLocalizedMessage());
		StringWriter errors = new StringWriter();
		e.printStackTrace(new PrintWriter(errors));
		sb.append(errors.toString());
		sb.append("</body>");
        sb.append("</html>");
		info.put("BODY", sb.toString());
		SynchronizationManager.END_ERROR_FAILURE_SYNC(project);
		try {
			XDAT.getMailService().sendHtmlMessage(AdminUtils.getAuthorizerEmailId(),AdminUtils.getAuthorizerEmailId(), info.get("SUBJECT"),
					info.get("BODY"));
		} catch (MessagingException me) {
			_log.error("Failed to send email.", me);
		} catch (Exception ex) {
			_log.error("Failed to send email.", e);
		}

	}

}
