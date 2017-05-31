package org.nrg.xsync.manifest;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;

import org.nrg.mail.services.MailService;
import org.nrg.xdat.turbine.utils.TurbineUtils;
import org.nrg.xft.security.UserI;
import org.nrg.xsync.services.local.SyncManifestService;
import org.nrg.xsync.tools.XsyncXnatInfo;
import org.nrg.xsync.utils.XsyncFileUtils;
import org.nrg.xsync.utils.XsyncUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Mohana Ramaratnam
 *
 */
public class SyncManifest{
	/** The Constant logger. */
	private final static Logger logger = LoggerFactory.getLogger(SyncManifest.class);

	private final MailService _mailService;
	String localProjectId;
	String remoteProjectId;
	String syncHost;
	Date sync_start_time;
	Date sync_end_time;
	UserI sync_user;

	ArrayList<ResourceSyncItem> resources;
	ArrayList<SubjectSyncItem> subjects;
	private final SyncManifestService _syncManifestService;
	private final XsyncXnatInfo _xnatInfo;

	public SyncManifest(final SyncManifestService syncManifestService, final XsyncXnatInfo xnatInfo, final MailService mailService, String localProjectId, String remoteProjectId, String syncHost) {
		_syncManifestService = syncManifestService;
		_xnatInfo = xnatInfo;
		_mailService = mailService;
		this.localProjectId = localProjectId;
		this.remoteProjectId = remoteProjectId;
		this.syncHost = syncHost;
		resources = new ArrayList<>();
		subjects = new ArrayList<>();
	}


	/**
	 * @return the resources
	 */
	public ArrayList<ResourceSyncItem> getResources() {
		return resources;
	}

	public void addResource(ResourceSyncItem resource) {
		resources.add(resource);
	}

	/**
	 * @param resources the resources to set
	 */
	public void setResources(ArrayList<ResourceSyncItem> resources) {
		this.resources = resources;
	}

	/**
	 * @return the subjects
	 */
	public ArrayList<SubjectSyncItem> getSubjects() {
		return subjects;
	}

	/**
	 * @param subjects the subjects to set
	 */
	public void setSubjects(ArrayList<SubjectSyncItem> subjects) {
		this.subjects = subjects;
	}

	public void addSubject(SubjectSyncItem subject) {
		subjects.add(subject);
	}


	/**
	 * @return the sync_start_time
	 */
	public Date getSync_start_time() {
		return sync_start_time;
	}

	/**
	 * @param sync_start_time the sync_start_time to set
	 */
	public void setSync_start_time(Date sync_start_time) {
		this.sync_start_time = sync_start_time;
	}

	/**
	 * @return the sync_end_time
	 */
	public Date getSync_end_time() {
		return sync_end_time;
	}

	/**
	 * @param sync_end_time the sync_end_time to set
	 */
	public void setSync_end_time(Date sync_end_time) {
		this.sync_end_time = sync_end_time;
	}

	/**
	 * @return the sync_user
	 */
	public UserI getSync_user() {
		return sync_user;
	}

	/**
	 * @param sync_user the sync_user to set
	 */
	public void setSync_user(UserI sync_user) {
		this.sync_user = sync_user;
	}


	/**
	 * @return the localProjectId
	 */
	public String getLocalProjectId() {
		return localProjectId;
	}

	public boolean wasSyncSuccessfull() {
		boolean wasSuccessful = true;
		if (resources.size() > 0) {
			for (SyncedItem sync:resources) {
				if (!sync.getSyncStatus().equals(XsyncUtils.SYNC_STATUS_SYNCED_AND_NOT_VERIFIED) && !sync.getSyncStatus().equals(XsyncUtils.SYNC_STATUS_SYNCED_AND_VERIFIED) &&  !sync.getSyncStatus().equals(XsyncUtils.SYNC_STATUS_SKIPPED) && !sync.getSyncStatus().equals(XsyncUtils.SYNC_STATUS_DELETED) ) {
					wasSuccessful = false;
					break;
				}
			}
		}
		if (subjects.size()>0 && wasSuccessful) {
			for (SyncedItem sync:subjects) {
				if (!sync.getSyncStatus().equals(XsyncUtils.SYNC_STATUS_SYNCED_AND_NOT_VERIFIED) && !sync.getSyncStatus().equals(XsyncUtils.SYNC_STATUS_SYNCED_AND_VERIFIED) &&  !sync.getSyncStatus().equals(XsyncUtils.SYNC_STATUS_SKIPPED) && !sync.getSyncStatus().equals(XsyncUtils.SYNC_STATUS_DELETED) ) {
					wasSuccessful = false;
					break;
				}
			}
		}
		return wasSuccessful;
	}
	
	public String getOverAllSyncStatusWhenSucessfull() {
		String overAllStatus = XsyncUtils.SYNC_STATUS_SYNCED_AND_VERIFIED;
		if (resources.size() > 0) {
			for (SyncedItem sync:resources) {
				if (!sync.getSyncStatus().equals(XsyncUtils.SYNC_STATUS_SYNCED_AND_VERIFIED))  {
					overAllStatus = sync.getSyncStatus();
					break;
				}
			}
		}
		if (subjects.size()>0 && overAllStatus.equals(XsyncUtils.SYNC_STATUS_SYNCED_AND_VERIFIED)) {
			for (SyncedItem sync:subjects) {
				if (!sync.getSyncStatus().equals(XsyncUtils.SYNC_STATUS_SYNCED_AND_VERIFIED) ) {
					overAllStatus = sync.getSyncStatus();
					break;
				}
			}
		}
		return overAllStatus;
	}
	

	public String getSyncHost() {
		return syncHost;
	}

	public String getRemoteProjectId() {
		return remoteProjectId;
	}

	public boolean shouldNotify() {
		boolean shouldNotify = false;
		if (resources != null && resources.size() > 0 ) {
			shouldNotify = true;
		}
		if (subjects != null && subjects.size() > 0) {
			for (SubjectSyncItem sub : subjects) {
				ArrayList<ExperimentSyncItem> exps = sub.getExperiments();
				if (exps != null && exps.size() > 0) {
					shouldNotify = true;
					break;
				}
				ArrayList<ResourceSyncItem> subResources = sub.getResources();
				if (subResources != null && subResources.size() > 0) {
					shouldNotify = true;
					break;
				}
			}
		}
		return shouldNotify;	
	}
	
	public void informUser() {
		final Hashtable<String, String> info = syncInfoAsHTML();
		try {
			_mailService.sendHtmlMessage(_xnatInfo.getAdminEmail(), this.sync_user.getEmail(), info.get("SUBJECT"),
					info.get("BODY"));
		} catch (Exception e) {
			logger.error("Failed to send email.", e);
		}
	}

	/**
	 * Format sync information to requesting user.
	 *
	 */
	public Hashtable<String, String> syncInfoAsHTML() {
		final Hashtable<String,String> info = new Hashtable<>();
		final String subject="Project " + this.localProjectId +" data synced from "+ _xnatInfo.getSiteId()+" to " + this.syncHost;
		info.put("SUBJECT", subject);
			StringBuilder sb = new StringBuilder();
			sb.append("<html>");
	        sb.append("<body>");
			sb.append("<p>The following data  was synced from project ").append(localProjectId).append(" on ").append(TurbineUtils.GetFullServerPath()).append(" to ").append(syncHost).append("/data/projects/").append(this.remoteProjectId).append(" requested by ").append(sync_user.getUsername()).append(". </p>");


			sb.append("<table>");
			sb.append("<tr>");
			sb.append("<th> Source Project </th>");
			sb.append("<th> Target Project </th>");
			sb.append("<th> Sync Start Time </th>");
			sb.append("<th> Sync End Time </th>");
			sb.append("<th> Status </th>");
			sb.append("</tr>");

			sb.append("<tr>");
			sb.append("<td>").append(this.localProjectId).append("</td>");
			sb.append("<td>").append(this.remoteProjectId).append("</td>");
			sb.append("<td>").append(this.getSync_start_time()).append("</td>");
			sb.append("<td>").append(this.getSync_end_time()).append("</td>");
			sb.append("<td>").append(this.wasSyncSuccessfull() ? "Synced" : "Sync Failed/Incomplete").append("</td>");
			sb.append("</tr>");
			sb.append("</table>");
			if (resources.size() > 0) {
				sb.append("<p> Project Resources synced</p>");

				sb.append("<table>");
				sb.append("<tr>");
				sb.append("<th> Resource Label </th>");
				sb.append("<th> File Count </th>");
				sb.append("<th> File Size </th>");
				sb.append("<th> Status </th>");
				sb.append("<th> Message </th>");
				sb.append("</tr>");

				for (ResourceSyncItem res : resources) {
					 sb.append("<tr>");
					 sb.append("<td> ").append(res.localLabel).append(" </td>");
					 sb.append("<td> ").append(res.getFileCount() == null ? "NA" : res.getFileCount()).append(" </td>");
					 sb.append("<td> ").append(res.getFileSize() == null ? "NA" : XsyncFileUtils.getHumanReadableFileSize((Long)res.getFileSize())).append(" </td>");
					 sb.append("<td> ").append(res.getSyncStatus()).append(" </td>");
					 sb.append("<td> ").append(res.getMessage()).append(" </td>");
					 sb.append("</tr>");
				}
				sb.append("</table>");
			}
			if (subjects.size()>0) {
				sb.append("<p>  Subjects synced</p>");

				sb.append("<table>");
				sb.append("<tr>");
				sb.append("<th> Subject Label </th>");
				sb.append("<th> Remote ID </th>");
				sb.append("<th> Status </th>");
				sb.append("<th> Message </th>");
				sb.append("</tr>");

				for (SubjectSyncItem sub : subjects) {
					 sb.append("<tr>");
					 sb.append("<td> ").append(sub.localLabel).append(" </td>");
					 sb.append("<td> ").append(sub.getRemoteId() == null ? "" : sub.getRemoteId()).append(" </td>");
					 sb.append("<td> ").append(sub.getSyncStatus()).append(" </td>");
					 sb.append("<td> ").append(sub.getMessage() == null ? "" : sub.getMessage()).append(" </td>");
					 sb.append("</tr>");
				}
				sb.append("</table>");
				sb.append("<p>  Experiments synced</p>");

				sb.append("<table>");
				sb.append("<tr>");
				sb.append("<th> Experiment Label </th>");
				sb.append("<th> Experiment Remote ID </th>");
				sb.append("<th> Experiment Type </th>");
				sb.append("<th> Status </th>");
				sb.append("<th> Message </th>");
				sb.append("<td>  Total Files  </td>");
				sb.append("<td>  Total File Size </td>");
				sb.append("</tr>");

				for (SubjectSyncItem sub : subjects) {
					ArrayList<ExperimentSyncItem> exps = sub.getExperiments();
					for (ExperimentSyncItem exp: exps) {
						 sb.append("<tr>");
						 sb.append("<td> ").append(exp.getLocalLabel()).append(" </td>");
						 sb.append("<td> ").append(exp.getRemoteId() == null ? "" : exp.getRemoteId()).append(" </td>");
						 sb.append("<td> ").append(exp.getXsiType()).append(" </td>");
						 sb.append("<td> ").append(exp.getSyncStatus()).append(" </td>");
						 sb.append("<td> ").append(exp.getMessage() == null ? "" : exp.getMessage()).append(" </td>");
						 Integer fileCnt = exp.getTotalSyncedFileCount();
						 String fileCntStr = (fileCnt == 0?"NA":fileCnt.toString());
						 sb.append("<td> ").append(fileCntStr).append(" </td>");
						 Long fileSize = exp.getTotalSyncedFileSize();
						 String fileSizeStr = (fileSize == 0?"NA":XsyncFileUtils.getHumanReadableFileSize(fileSize));
						 sb.append("<td> ").append(fileSizeStr).append(" </td>");
						 sb.append("</tr>");
					}
				}
				sb.append("</table>");

			}


			sb.append("</body>");
            sb.append("</html>");
			logger.debug(sb.toString());
			info.put("BODY", sb.toString());
			return info;
		}
		/**
		 * Format sync information to requesting user.
		 *
		 */
		public void syncInfoToFile(File file) {
			final Hashtable<String, String> info = syncInfoAsHTML();

			file.getParentFile().mkdirs();
			try (final BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
		        writer.write(info.get("BODY"));
		    } catch (IOException e) {
				logger.error("An error occurred writing the sync file", e);
			}
		}

		public synchronized void syncInfoToDatabase() {
			_syncManifestService.persistHistory(this);
		}
}
