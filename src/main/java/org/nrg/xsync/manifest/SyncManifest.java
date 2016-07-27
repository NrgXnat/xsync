package org.nrg.xsync.manifest;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.*;

import javax.mail.MessagingException;

import org.nrg.xdat.XDAT;
import org.nrg.xdat.turbine.utils.AdminUtils;
import org.nrg.xdat.turbine.utils.TurbineUtils;
import org.nrg.xft.security.UserI;
import org.nrg.xsync.services.local.impl.HibernateSyncHistoryService;
import org.nrg.xsync.tools.XsyncXnatInfo;
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

	String localProjectId;
	String remoteProjectId;
	String syncHost;
	Date sync_start_time;
	Date sync_end_time;
	UserI sync_user;

	ArrayList<ResourceSyncItem> resources;
	ArrayList<SubjectSyncItem> subjects;

	public SyncManifest(String localProjectId, String remoteProjectId, String syncHost) {
		this.localProjectId = localProjectId;
		this.remoteProjectId = remoteProjectId;
		this.syncHost = syncHost;
		resources = new ArrayList<ResourceSyncItem>();
		subjects = new ArrayList<SubjectSyncItem>();
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
				if (!sync.getSyncStatus().equals(XsyncUtils.SYNC_STATUS_SYNCED) &&  !sync.getSyncStatus().equals(XsyncUtils.SYNC_STATUS_SKIPPED) && !sync.getSyncStatus().equals(XsyncUtils.SYNC_STATUS_DELETED) ) {
					wasSuccessful = false;
					break;
				}
			}
		}
		if (subjects.size()>0 && wasSuccessful) {
			for (SyncedItem sync:subjects) {
				if (!sync.getSyncStatus().equals(XsyncUtils.SYNC_STATUS_SYNCED) &&  !sync.getSyncStatus().equals(XsyncUtils.SYNC_STATUS_SKIPPED) && !sync.getSyncStatus().equals(XsyncUtils.SYNC_STATUS_DELETED) ) {
					wasSuccessful = false;
					break;
				}
			}
		}
		return wasSuccessful;
	}

	public String getSyncHost() {
		return syncHost;
	}

	public String getRemoteProjectId() {
		return remoteProjectId;
	}

	public void informUser() {
		final Hashtable<String, String> info = syncInfoAsHTML();
		try {
			XDAT.getMailService().sendHtmlMessage(AdminUtils.getAuthorizerEmailId(), this.sync_user.getEmail(), info.get("SUBJECT"),
					info.get("BODY"));
		} catch (MessagingException me) {
			logger.error("Failed to send email.", me);
		} catch (Exception e) {
			logger.error("Failed to send email.", e);
		}
	}

	/**
	 * Format sync information to requesting user.
	 *
	 */
	public Hashtable<String, String> syncInfoAsHTML() {
		final Hashtable<String,String> info = new Hashtable<String,String>();
		final XsyncXnatInfo xnatInfo = XDAT.getContextService().getBean(XsyncXnatInfo.class);
		final String subject="Project " + this.localProjectId +" data synced from "+ xnatInfo.getSiteId()+" to " + this.syncHost;
		info.put("SUBJECT", subject);
			StringBuilder sb = new StringBuilder();
			sb.append("<html>");
	        sb.append("<body>");
			sb.append("<p>The following data  was synced from project "+this.localProjectId+" on "+TurbineUtils.GetFullServerPath()+" to "+ this.syncHost+"/data/projects/"+this.remoteProjectId+" requested by "+this.sync_user.getUsername()+". </p>");


			sb.append("<table>");
			sb.append("<tr>");
			sb.append("<th> Source Project </th>");
			sb.append("<th> Target Project </th>");
			sb.append("<th> Sync Start Time </th>");
			sb.append("<th> Sync End Time </th>");
			sb.append("<th> Status </th>");
			sb.append("</tr>");

			sb.append("<tr>");
			sb.append("<td>" + this.localProjectId + "</td>");
			sb.append("<td>" +this.remoteProjectId + "</td>");
			sb.append("<td>" +this.getSync_start_time() + "</td>");
			sb.append("<td>" +this.getSync_end_time() + "</td>");
			sb.append("<td>" +(this.wasSyncSuccessfull()?"Synced":"Sync Failed/Incomplete") + "</td>");
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
					 sb.append("<td> " + res.localLabel + " </td>");
					 sb.append("<td> " + res.getFileCount() + " </td>");
					 sb.append("<td> " + res.getFileSize() + " </td>");
					 sb.append("<td> " + res.getSyncStatus() + " </td>");
					 sb.append("<td> " + res.getMessage() + " </td>");
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
					 sb.append("<td> " + sub.localLabel + " </td>");
					 sb.append("<td> " + (sub.getRemoteId()==null?"":sub.getRemoteId()) + " </td>");
					 sb.append("<td> " + sub.getSyncStatus() + " </td>");
					 sb.append("<td> " + (sub.getMessage()==null?"":sub.getMessage()) + " </td>");
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
						 sb.append("<td> " + exp.getLocalLabel() + " </td>");
						 sb.append("<td> " + (exp.getRemoteId()==null?"":exp.getRemoteId()) + " </td>");
						 sb.append("<td> " + exp.getXsiType() + " </td>");
						 sb.append("<td> " + exp.getSyncStatus() + " </td>");
						 sb.append("<td> " + (exp.getMessage()==null?"":exp.getMessage()) + " </td>");
						 Integer fileCnt = exp.getTotalSyncedFileCount();
						 String fileCntStr = (fileCnt == 0?"NA":fileCnt.toString());
						 sb.append("<td> " + fileCntStr + " </td>");
						 Long fileSize = exp.getTotalSyncedFileSize();
						 String fileSizeStr = (fileSize == 0?"NA":fileSize.toString());
						 sb.append("<td> " + fileSizeStr + " </td>");
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

			BufferedWriter writer = null;
		    try {
		        file.getParentFile().mkdirs();
		        writer = new BufferedWriter(new FileWriter(file));
		        writer.write(info.get("BODY"));
		    } catch (Exception e) {
		        e.printStackTrace();
		    } finally {
		        try {
		            // Close the writer regardless of what happens...
		            writer.close();
		            } catch (Exception e) {
		        }
		    }
		}

		public synchronized void syncInfoToDatabase() {
			final HibernateSyncHistoryService service =
					XDAT.getContextService().getBean(HibernateSyncHistoryService.class);
			service.saveHistoryToDatabase(this);
		}
}
