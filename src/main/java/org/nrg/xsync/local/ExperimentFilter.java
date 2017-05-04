package org.nrg.xsync.local;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.nrg.xdat.base.BaseElement;
import org.nrg.xdat.model.XnatAbstractresourceI;
import org.nrg.xdat.model.XnatExperimentdataI;
import org.nrg.xdat.model.XnatExperimentdataShareI;
import org.nrg.xdat.model.XnatImageassessordataI;
import org.nrg.xdat.model.XnatImagescandataI;
import org.nrg.xdat.model.XnatReconstructedimagedataI;
import org.nrg.xdat.model.XnatSubjectassessordataI;
import org.nrg.xdat.model.XnatSubjectdataI;
import org.nrg.xdat.om.XnatAbstractresource;
import org.nrg.xdat.om.XnatExperimentdata;
import org.nrg.xdat.om.XnatImageassessordata;
import org.nrg.xdat.om.XnatImagescandata;
import org.nrg.xdat.om.XnatImagesessiondata;
import org.nrg.xdat.om.XnatResource;
import org.nrg.xdat.om.XnatResourceseries;
import org.nrg.xdat.om.XnatSubjectassessordata;
import org.nrg.xdat.om.XnatSubjectdata;
import org.nrg.xdat.om.XsyncXsyncassessordata;
import org.nrg.xdat.om.base.BaseXnatExperimentdata.UnknownPrimaryProjectException;
import org.nrg.xft.ItemI;
import org.nrg.xft.XFTItem;
import org.nrg.xft.exception.ElementNotFoundException;
import org.nrg.xft.exception.FieldNotFoundException;
import org.nrg.xft.exception.XFTInitException;
import org.nrg.xft.search.ItemSearch;
import org.nrg.xft.security.UserI;
import org.nrg.xnat.exceptions.InvalidArchiveStructure;
import org.nrg.xnat.xsync.anonymize.AnonymizerI;
import org.nrg.xnat.xsync.anonymize.XsyncAnonymizer;
import org.nrg.xsync.configuration.ProjectSyncConfiguration;
import org.nrg.xsync.configuration.json.SyncConfigurationImagingSessionXsiType;
import org.nrg.xsync.configuration.json.SyncConfigurationResource;
import org.nrg.xsync.configuration.json.SyncConfigurationSessionAssessor;
import org.nrg.xsync.configuration.json.SyncConfigurationXsiType;
import org.nrg.xsync.connection.RemoteConnectionManager;
import org.nrg.xsync.manager.SynchronizationManager;
import org.nrg.xsync.tools.XsyncXnatInfo;
import org.nrg.xsync.utils.QueryResultUtil;
import org.nrg.xsync.utils.XsyncUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * @author Mohana Ramaratnam
 *
 */
public class ExperimentFilter {
	private static final Logger _log = LoggerFactory.getLogger(ExperimentFilter.class);

	UserI _user;
	ProjectSyncConfiguration projectSyncConfiguration;
	private final NamedParameterJdbcTemplate _jdbcTemplate;
	private final RemoteConnectionManager    _manager;
	private final XsyncXnatInfo              _xsyncXnatInfo;
	private final QueryResultUtil            _queryResultUtil;
 
	public ExperimentFilter(final RemoteConnectionManager manager, final NamedParameterJdbcTemplate jdbcTemplate, final XsyncXnatInfo xsyncXnatInfo, final QueryResultUtil queryResultUtil, final UserI user, final ProjectSyncConfiguration projectSyncConfiguration) {
		_manager = manager;
		_jdbcTemplate = jdbcTemplate;
		_xsyncXnatInfo = xsyncXnatInfo;
		_queryResultUtil = queryResultUtil;
		_user = user;
		this.projectSyncConfiguration = projectSyncConfiguration;
	}
	
	public Map<String,List<XnatExperimentdataI>> select(XnatSubjectdata subject, String localSubjectId) throws Exception {
		List<XnatExperimentdataI> experimentsDeleted = new ArrayList<XnatExperimentdataI>();
		List<XnatExperimentdataI> experimentsModified = new ArrayList<XnatExperimentdataI>();
		List<XnatExperimentdataI> experimentsAdded = new ArrayList<XnatExperimentdataI>();
		List<XnatExperimentdataI> experimentsMarkedOkToSync = new ArrayList<XnatExperimentdataI>();

		List<XnatExperimentdataI> experimentsConfiguredToBeSynced = new ArrayList<XnatExperimentdataI>();
		Date syncEndDate = (Date)projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getSyncEndTime();
		
		List<XnatSubjectassessordataI> existingExperiments = subject.getExperiments_experiment();
		int total_experiments = existingExperiments.size();
		_log.debug("Existing experiments " + total_experiments);
		int i = 0;
		List<String> experimentIds = new ArrayList<String>();

		while(total_experiments > 0) {
			XnatSubjectassessordataI subjectAssessor = existingExperiments.get(i);
			if (subjectAssessor.getXSIType().startsWith("xsync:")) {
				subject.removeExperiments_experiment(i);
				existingExperiments = subject.getExperiments_experiment();
				total_experiments = subject.getExperiments_experiment().size();
				continue;
			}
			if (subjectAssessor instanceof XnatImagesessiondata) {
				if (projectSyncConfiguration.isImagingSessionToBeSynced(subjectAssessor.getXSIType())) {
					//Does it need an OK before its synced?
					if (projectSyncConfiguration.imagingSessionNeedsOkToSync(subjectAssessor.getXSIType()) ) {
							   experimentIds.add(subjectAssessor.getId());
					}else 
					   experimentsConfiguredToBeSynced.add(subjectAssessor);	
				}
			}else {
				if (projectSyncConfiguration.isSubjectAssessorToBeSynced(subjectAssessor.getXSIType())) {
					//Does it need an OK before its synced?
					if (projectSyncConfiguration.subjectAssessorNeedsOkToSync(subjectAssessor.getXSIType()) ) {
						   experimentIds.add(subjectAssessor.getId());
					}else 
							experimentsConfiguredToBeSynced.add(subjectAssessor);	
				}
			}
			subject.removeExperiments_experiment(i);
			existingExperiments = subject.getExperiments_experiment();
			total_experiments = subject.getExperiments_experiment().size();
		}
		List<Map<String,Object>> experimentDetails = new ArrayList<Map<String,Object>>();
		if (experimentsConfiguredToBeSynced.size() > 0) {
			for (XnatExperimentdataI exp:experimentsConfiguredToBeSynced) {
				Map<String,Object> expTimeLineDetails = getExperimentTimeLineDetails((XnatExperimentdata)exp,syncEndDate);
				experimentDetails.add(expTimeLineDetails);
			}
		}
		//Find the experiments which have been deleted
		MapSqlParameterSource parameters = new MapSqlParameterSource();
		parameters.addValue(QueryResultUtil.PROJECT_QUERY_PARAMETER_NAME, projectSyncConfiguration.getSynchronizationConfiguration().getSource_project_id());
		parameters.addValue(QueryResultUtil.SUBJECT_QUERY_PARAMETER_NAME, localSubjectId);

		
		// Subject has no experiments which are configured to be synced. Have any been deleted?
		String query = _queryResultUtil.getQueryForFetchingSubjectExperimentsDeletedSinceLastSync();
		//Columns
		// id,label,element_name,project,status,last_modified, sync_start_time 		
		_log.debug("Query is " + query);
		List<Map<String,Object>> experiments = _jdbcTemplate.queryForList(query, parameters);
		
		if (experiments != null && experiments.size()>0) {
			for (Map<String,Object> row:experiments) {
				if (projectSyncConfiguration.isSubjectAssessorToBeSynced((String)row.get("element_name")) || projectSyncConfiguration.isImagingSessionToBeSynced((String)row.get("element_name"))) {
					if (row.get("status").equals("deleted")) {
						_log.debug("Experiment Deleted: " + (String)row.get("id"));
						experimentsDeleted.add(createNew((String)row.get("id"),(String)row.get("label"),subject,(String)row.get("element_name")));
					}
				}
			}
		}else {
			_log.debug("No experiment has been deleted for subject");
		}
		

		if (experimentsConfiguredToBeSynced.size() > 0) {
			if (experimentDetails.size()>0) {
				for (Map<String,Object> row:experimentDetails) {
					if (row.get("status").equals(QueryResultUtil.ACTIVE_STATUS)) {
						//Is this new or updated?
						Date experimentInsertDate = (Date)row.get("insert_date");
						int dateComparison = experimentInsertDate.compareTo(syncEndDate);
						if (dateComparison >= 0) { //Inserted at endTime or After endTime
							_log.debug("Experiment Added: " + (String)row.get("id"));
							experimentsAdded.add(getExperiment((String)row.get("id"),experimentsConfiguredToBeSynced));
						}else {
							Date experimentModifiedDate = (Date)row.get("last_modified");
							dateComparison = experimentModifiedDate.compareTo(syncEndDate);
							if (dateComparison >= 0) { //Modified at endTime or After endTime
								_log.debug("Experiment Modified: " + (String)row.get("id"));
								experimentsModified.add(getExperiment((String)row.get("id"),experimentsConfiguredToBeSynced));
							}
						}
					}
				}
			}
		}
		if (experimentIds.size() > 0) {
			   parameters.addValue(QueryResultUtil.REMOTE_PROJECT_QUERY_PARAMETER_NAME, projectSyncConfiguration.getSynchronizationConfiguration().getRemote_project_id());
			   parameters.addValue(QueryResultUtil.REMOTE_URL_QUERY_PARAMETER_NAME, projectSyncConfiguration.getSynchronizationConfiguration().getRemote_url());

			   parameters.addValue(QueryResultUtil.EXPERIMENT_IDS, experimentIds);
				//Look for experiments which may have been marked ok to sync
				//If the sync_only_new flag is set, if the experiment was synced once
				//it will be skipped.
				 query = _queryResultUtil.getQueryForFetchingSubjectExperimentsMarkedOKSinceLastSync();
				 boolean syncOnlyNew = projectSyncConfiguration.isSetToSyncNewOnly();
				 if (syncOnlyNew) {
					//Never synced before
					 query += " and xok.sync_status='"+XsyncUtils.SYNC_STATUS_WAITING_TO_SYNC+"'"; 
				 }
					//Columns
					// id,label,element_name,project,status,last_modified, sync_end_time, insert_date 		
				 experiments = _jdbcTemplate.queryForList(query, parameters);
				if (experiments != null && experiments.size()>0) {
					for (Map<String,Object> row:experiments) {
						if (row.get("status").equals(QueryResultUtil.ACTIVE_STATUS)) {
							_log.debug("Experiment Marked OK to Sync: " + (String)row.get("id"));
							XnatExperimentdataI exp = getExperiment((String)row.get("id"));
							String existingSyncStatus = (String)row.get("sync_status");
							boolean hasBeenSynced = false;
							if (existingSyncStatus != null)  {
								if (!XsyncUtils.SYNC_STATUS_WAITING_TO_SYNC.equals(existingSyncStatus))
									hasBeenSynced = true;
							}
								
							if (hasBeenSynced) {
								//Was this session modified? Is syncOnlyNew set?
								if (!syncOnlyNew) {
									//Were these sessions modified?
									Map<String,Object> expTimeLineDetails = getExperimentTimeLineDetails((XnatExperimentdata)exp,syncEndDate);
									Date experimentModifiedDate = (Date)expTimeLineDetails.get("last_modified");
									if (experimentModifiedDate != null) {
										int dateComparison = experimentModifiedDate.compareTo(syncEndDate);
										if (dateComparison >= 0) { //Modified at endTime or After endTime
											_log.debug("Experiment Modified: " + (String)row.get("id"));
											experimentsModified.add(exp);
										}
									}
								}//Dont do anything otherwise (session was synced
							}else {
								//Has not yet been synced
								experimentsMarkedOkToSync.add(exp);
							}
						}
					}
				}else 
				 _log.debug("None of the configured experiments have changed for subject " + subject.getId());
		}
		
		Map<String,List<XnatExperimentdataI>> filteredResults = new HashMap<String,List<XnatExperimentdataI>>();
		filteredResults.put(QueryResultUtil.ACTIVE_STATUS, experimentsModified);
		filteredResults.put(QueryResultUtil.DELETE_STATUS, experimentsDeleted);
		filteredResults.put(QueryResultUtil.NEW_STATUS, experimentsAdded);
		filteredResults.put(QueryResultUtil.OK_TO_SYNC_STATUS, experimentsMarkedOkToSync);
		return filteredResults;
	}
	
	
	
	private XnatExperimentdataI getExperiment(String id, List<XnatExperimentdataI> experiments) {
		XnatExperimentdataI exp = null;
		for (XnatExperimentdataI e:experiments) {
			if (e.getId().equals(id)) {
				exp = e;
				break;
			}
		}
		return exp;
	}

	private XnatExperimentdataI getExperiment(String id) {
		XnatExperimentdataI exp = null;
		exp = XnatExperimentdata.getXnatExperimentdatasById(id, _user, false);
		return exp;
	}

	private Map<String,Object> getExperimentTimeLineDetails(XnatExperimentdata exp, Object sync_end_time) throws Exception {
		// id,label,element_name,project,status,last_modified, sync_end_time, insert_date 		
		Map<String,Object> info = new HashMap<String, Object>();
		XFTItem item = exp.getCurrentDBVersion(); // exp.getItem does not contain the meta fields
		//ItemI itemMeta = item.getMeta(); was returning null
		String metaFieldName = item.getGenericSchemaElement().getMetaDataFieldName();
        Object v = item.getProperty(metaFieldName);
        XFTItem itemMeta = ItemSearch.GetItem(item.getXSIType() + "_meta_data/meta_data_id",v,null,false);

		info.put("id", exp.getId());
		info.put("label", exp.getLabel());
		info.put("element_name", "");
		info.put("project", exp.getProject());
		info.put("status", itemMeta.getProperty("status"));
		info.put("last_modified", itemMeta.getProperty("last_modified"));
		info.put("row_last_modified", itemMeta.getProperty("row_last_modified"));
		info.put("insert_date", itemMeta.getProperty("insert_date"));
		info.put("sync_end_time", sync_end_time);
		return info;
	}
	
	private XnatExperimentdataI createNew(String id, String label, XnatSubjectdata subject,String xsiType) {
		Class c = BaseElement.GetGeneratedClass(xsiType);
		ItemI o = null;
		try {
            o = (ItemI) c.newInstance();
            o.setProperty("id", id);
            o.setProperty("label", label);        
            o.setProperty("project", subject.getProject());
            o.setProperty("subject_ID", subject.getId());
        }catch(Exception e) {
        	_log.debug("Could not instantiate the experiment " + id);
        }
        return new XnatExperimentdata(o);
	}
	
	/**
	 * @param targetsubject
	 *            subject for experiment for correction
	 * @param origExperiment
	 *            experiment for correction
	 * @throws Exception
	 */
	private XnatImagesessiondata correctIDandLabel(XnatSubjectdataI targetsubject,XnatImagesessiondata origExperiment) throws Exception {
		XFTItem item = origExperiment.getItem().copy();
		XnatImagesessiondata targetExperiment = (XnatImagesessiondata) BaseElement.GetGeneratedItem(item);
		String newid = "";
		IdMapper idMapper = new IdMapper(_manager, _queryResultUtil, _jdbcTemplate, _user, projectSyncConfiguration);
		String alreadyAssignedRemoteId = idMapper.getRemoteAccessionId(origExperiment.getId());
		if (alreadyAssignedRemoteId != null) {
			newid = alreadyAssignedRemoteId;
		}
		targetExperiment.setId(newid);
		//targetExperiment.setProject(targetsubject.getProject());
		targetExperiment.setSubjectId(targetsubject.getLabel());
		// correct shared projects
		for (XnatExperimentdataShareI share : targetExperiment.getSharing_share()) {
			if (share.getLabel() != null) {
				share.setLabel("");
			}
		}
		return targetExperiment;
	}

	/**
	 * @param targetsubject
	 *            subject for experiment for correction
	 * @param origExperiment
	 *            experiment for correction
	 * @throws Exception
	 */
	public void correctIDandLabel(XnatImageassessordata targetAssessor,XnatImageassessordata origAss,String remoteImageSessionId, String remoteProjectId) throws Exception {
		String newid = "";
		IdMapper idMapper = new IdMapper(_manager, _queryResultUtil, _jdbcTemplate, _user, projectSyncConfiguration);
		String alreadyAssignedRemoteId = idMapper.getRemoteAccessionId(origAss.getId());
		if (alreadyAssignedRemoteId != null) {
			newid = alreadyAssignedRemoteId;
		}
		targetAssessor.setId(newid);
		targetAssessor.setImagesessionId(remoteImageSessionId);
		targetAssessor.setProject(remoteProjectId);

		// correct shared projects
		for (XnatExperimentdataShareI share : targetAssessor.getSharing_share()) {
			if (share.getLabel() != null) {
				share.setLabel("");
			}
		}
		return;
	}
	

	/**
	 * @param targetsubject    subject for experiment for correction
	 * @param origExperiment   The original experiment.
	 * @throws Exception When something goes wrong.
	 */
	private XnatSubjectassessordata correctIDandLabel(XnatSubjectdataI targetsubject,XnatSubjectassessordata origExperiment) throws Exception {
		XFTItem item = origExperiment.getItem().copy();
		XnatSubjectassessordata targetExperiment = (XnatSubjectassessordata) BaseElement.GetGeneratedItem(item);
		String newid = "";
		IdMapper idMapper = new IdMapper(_manager, _queryResultUtil, _jdbcTemplate, _user, projectSyncConfiguration);
		String alreadyAssignedRemoteId = idMapper.getRemoteAccessionId(origExperiment.getId());
		if (alreadyAssignedRemoteId != null) {
			newid = alreadyAssignedRemoteId;
		}
		targetExperiment.setId(newid);
		//targetExperiment.setProject(targetsubject.getProject());
		targetExperiment.setSubjectId(targetsubject.getLabel());
		// correct shared projects
		for (XnatExperimentdataShareI share : targetExperiment.getSharing_share()) {
			if (share.getLabel() != null) {
				share.setLabel("");
			}
		}
		return targetExperiment;
	}

	/**
	 * Reset prearchive.
	 *
	 * @param exp
	 *            the exp
	 */
	private void resetPrearchive(XnatImagesessiondata exp) {
		exp.setPrearchivepath(null);

	}
	
	private boolean hasResourceBeenModified(XnatAbstractresource resource, String exptId) throws Exception{
		final ArrayList<XsyncXsyncassessordata> assessors = XsyncXsyncassessordata.getXsyncXsyncassessordatasByField("xsync:xsyncAssessorData/synced_experiment_id", exptId, _user, true);
		if (assessors ==null || assessors.size()<1) {
			return true;
		}
		// We would only want skip sending files if we had a successful send to the remote project.
		final Iterator<XsyncXsyncassessordata> i = assessors.iterator();
		while (i.hasNext()) {
			final XsyncXsyncassessordata a = i.next();
			if (a.getSyncStatus()==null || !a.getSyncStatus().equals(XsyncUtils.SYNC_STATUS_SYNCED_AND_VERIFIED) ||
					a.getRemoteProjectId()==null ||
					!a.getRemoteProjectId().equals(projectSyncConfiguration.getSynchronizationConfiguration().getRemote_project_id()) ||
					a.getRemoteUrl()==null ||
					!a.getRemoteUrl().equals(projectSyncConfiguration.getSynchronizationConfiguration().getRemote_url())) {
				i.remove();
			}
		}
		if (assessors.isEmpty()) {
			return true;
		}
		Collections.sort(assessors,new Comparator<XsyncXsyncassessordata>(){
			@Override
			public int compare(XsyncXsyncassessordata a0, XsyncXsyncassessordata a1) {
				Date a0d = (a0.getSyncTime() instanceof Date) ? (Date)a0.getSyncTime() : null;
				Date a1d = (a1.getSyncTime() instanceof Date) ? (Date)a1.getSyncTime() : null;
				if (a0d!=null && a1d!=null) {
					return ((Date)a1.getSyncTime()).compareTo((Date)a0.getSyncTime());
				} else if (a0d!=null) {
					return 1;
				} else if (a1d!=null) {
					return -1;
				} 
				return 0;
			}
		});
		final XsyncXsyncassessordata syncAssessor = assessors.get(0); 
		final Date syncTime = (syncAssessor.getSyncTime() instanceof Date) ? (Date)syncAssessor.getSyncTime() : null;
		if (syncTime==null) {
			return true;
		}
		final ResourceFilter resourceFilter = new ResourceFilter(_user,_jdbcTemplate,_queryResultUtil);
		if (!resourceFilter.hasResourceBeenModified(resource, syncTime)) {
			return false;
		}
		return true;
	}
	
	/**
	 * Modify expt resource.
	 *
	 * @param resource
	 *            the resource
	 * @param orig
	 *            the orig
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 * @throws UnknownPrimaryProjectException
	 *             the unknown primary project exception
	 * @throws InvalidArchiveStructure
	 *             the invalid archive structure
	 * @throws ElementNotFoundException
	 *             the element not found exception
	 * @throws FieldNotFoundException
	 *             the field not found exception
	 * @throws XFTInitException
	 *             the XFT init exception
	 */

	public void modifyExptResource(XnatAbstractresourceI resource, XnatExperimentdata orig, boolean copy) throws IOException, UnknownPrimaryProjectException, InvalidArchiveStructure,ElementNotFoundException, FieldNotFoundException, XFTInitException, Exception {
		String filepath = orig.getArchiveRootPath() + "arc001/";// +
		// orig.getArchiveDirectoryName();
		String newFilepath = SynchronizationManager.GET_SYNC_FILE_PATH(orig.getProject(),orig);
		boolean hasResourceBeenModified = hasResourceBeenModified((XnatAbstractresource)resource, orig.getId());
		
		if (resource instanceof XnatResource) {
			String path = ((XnatResource) resource).getUri();
			String newURI = path.replace(File.pathSeparator, "/").replace(filepath, newFilepath);
			((XnatResource) resource).setUri(newURI);
			if (hasResourceBeenModified) {
				if (copy) copyFiles(path, newURI);
			}else {
					resource.setFileCount((resource.getFileCount() != null)?-1*resource.getFileCount():-1);
					resource.setFileSize((resource.getFileSize() != null)?-1*(Long)resource.getFileSize():-1);
			}
		} else if (resource instanceof XnatResourceseries) {
			String path = ((XnatResourceseries) resource).getPath();
			String newURI = path.replace(filepath, newFilepath);
			((XnatResourceseries) resource).setPath(newURI);
			if (hasResourceBeenModified) {
				if (copy) copyFiles(path, newURI);
			}else {
				resource.setFileCount(-1*resource.getFileCount());
				resource.setFileSize(-1*(Long)resource.getFileSize());
			}
		}
	}

	
	/**
	 * Modify expt resource files.
	 *
	 * @param catalogFile
	 *            the catalog file
	 * @param newCatalogFile
	 *            the new catalog file
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 * @throws UnknownPrimaryProjectException
	 *             the unknown primary project exception
	 * @throws InvalidArchiveStructure
	 *             the invalid archive structure
	 * @throws ElementNotFoundException
	 *             the element not found exception
	 * @throws FieldNotFoundException
	 *             the field not found exception
	 * @throws XFTInitException
	 *             the XFT init exception
	 */
	private void copyFiles(String catalogFile, String newCatalogFile)
			throws IOException, UnknownPrimaryProjectException, InvalidArchiveStructure, ElementNotFoundException,
			FieldNotFoundException, XFTInitException {
		// this is path to catalog
		//String newCatalogFileParentDir = newCatalogFile.substring(0, newCatalogFile.lastIndexOf(File.separatorChar));
		//new File(newCatalogFileParentDir).mkdirs();
		File sourceCatalog = new File(catalogFile);
		File destCatalog = new File(newCatalogFile);

		File source = sourceCatalog.getParentFile();
		File dest = destCatalog.getParentFile();
		if (!dest.exists()) dest.mkdirs();
		// copy the actual files for resource and catalog.
		try {
			if (source.exists()) {
				FileUtils.copyDirectory(source, dest);
			}
		} catch (IOException e) {
			_log.error("", e);
			throw e;
			// don't continue if the file copy failed
		}
	}

	
	private void filterRecons(XnatExperimentdata exp) throws Exception{
		ReconstructionFilter reconFilter = new ReconstructionFilter();
		reconFilter.filter(exp, projectSyncConfiguration);
	}

	/**
	 * Copy experiment.
	 *
	 * @param orig
	 *            the orig
	 * @return the xnat experimentdata
	 * @throws Exception
	 *             the exception
	 */
	public XnatImagesessiondata  prepareImagingSessionToSync(XnatSubjectdata newSubject, XnatImagesessiondata orig) throws Exception {
		XnatImagesessiondata exp = null;
		try {
			exp = correctIDandLabel(newSubject,orig);
			
			filterExperimentResources(exp);
			if (!orig.getId().equals(exp.getId())) {
				for (final XnatAbstractresourceI res : exp.getResources_resource()) {
					modifyExptResource((XnatAbstractresource) res, orig, false);
				}

				if (exp instanceof XnatImagesessiondata) {
					resetPrearchive((XnatImagesessiondata) exp);
					filterScantypes(exp);
					for (final XnatImagescandataI scan : ((XnatImagesessiondata) exp).getScans_scan()) {
						scan.setImageSessionId(exp.getLabel());
						for (final XnatAbstractresourceI res : scan.getFile()) {
							modifyExptResource((XnatAbstractresource) res, orig, true);
						}
					}
					
					filterRecons(exp);
					for (final XnatReconstructedimagedataI recon : ((XnatImagesessiondata) exp)
							.getReconstructions_reconstructedimage()) {
						recon.setImageSessionId(exp.getLabel());
						ReconstructionFilter reconFilter = new ReconstructionFilter();
						reconFilter.correctIDandLabel(recon);
						for (final XnatAbstractresourceI res : recon.getIn_file()) {
							modifyExptResource((XnatAbstractresource) res, orig, false);
						}
						for (final XnatAbstractresourceI res : recon.getOut_file()) {
							modifyExptResource((XnatAbstractresource) res, orig, false);
						}
					}
					filterAssessors(orig, exp);
					for (final XnatImageassessordataI assess : ((XnatImagesessiondata) exp).getAssessors_assessor()) {
						for (XnatExperimentdataShareI share : assess.getSharing_share()) {
							if (share.getLabel() != null) {
								share.setLabel("");
							}
						}
						/*
						for (final XnatAbstractresourceI res : assess.getResources_resource()) {
							modifyExptResource((XnatAbstractresource) res, orig);
						}

						for (final XnatAbstractresourceI res : assess.getIn_file()) {
							modifyExptResource((XnatAbstractresource) res, orig);
						}

						for (final XnatAbstractresourceI res : assess.getOut_file()) {
							modifyExptResource((XnatAbstractresource) res, orig);
						}
						*/
						
					}
					Boolean isExptToBeAnonymized = projectSyncConfiguration.getSynchronizationConfiguration().getAnonymize(); 
					_log.debug("Exp " + exp.getLabel() + " needs to be anonymized " + isExptToBeAnonymized);
					if (isExptToBeAnonymized) {
						_log.debug("About to anonymize " + exp.getLabel());
						 anonymize((XnatImagesessiondata)exp, newSubject.getProject());
						_log.debug("DONE - anonymize " + exp.getLabel());						
					}
				} else {
				}
				
			}
		} catch (Exception ex) {
			_log.error(ex.toString() + " " + ex.getLocalizedMessage());
			throw new Exception(ex);
		}
		return exp;
	}
	
	
	private void anonymize(XnatImagesessiondata exp, String destProject) throws Exception {
		if (exp.getScans_scan() != null && exp.getScans_scan().size() > 0) {
			//Check to see if there are any files which have been copied. If there are any, anonymization needs to be performed
			boolean hasDataToBeAnonymized = checkIfHasDataToBeAnonymized(exp);
			if (hasDataToBeAnonymized) {
				try {
					File sessionDir = exp.getSessionDir();
					if (sessionDir != null) {
						AnonymizerI simpleExportAnonymizer = new XsyncAnonymizer(_xsyncXnatInfo);
						simpleExportAnonymizer.anonymize((XnatImagesessiondata) exp, destProject);
					}else {
						_log.debug("There are no files to anonymize");
					}
				}catch(Exception e) {
					_log.error(e.getMessage());
					throw e;
				}
			}
		}
	}
	
	
	private boolean checkIfHasDataToBeAnonymized(XnatImagesessiondata exp) {
		boolean hasData = false;
		for (final XnatImagescandataI scan : ((XnatImagesessiondata) exp).getScans_scan()) {
			for (final XnatAbstractresourceI res : scan.getFile()) {
				if (res.getFileCount() != null  && res.getFileCount() > 0  ) {
					hasData = true;
					break;
				}
			}
			if (hasData) break;
		}
		return hasData;
	}
	
	/**
	 * Filter experiment resources.
	 *
	 * @param exp
	 *            the exp
	 * @throws IndexOutOfBoundsException
	 *             the index out of bounds exception
	 * @throws FieldNotFoundException
	 *             the field not found exception
	 */
	public void filterExperimentResources(XnatExperimentdata exp)
			throws Exception {
		SyncConfigurationImagingSessionXsiType session = projectSyncConfiguration.getSynchronizationConfiguration().getImagingSession(exp.getXSIType());
	    SyncConfigurationResource sessionResources = session.getResources();
		while (findAndRemoveExperimentResources(exp, sessionResources))	;
		return;
	}

	/**
	 * Filter experiment resources.
	 *
	 * @param exp
	 *            the exp
	 * @throws IndexOutOfBoundsException
	 *             the index out of bounds exception
	 * @throws FieldNotFoundException
	 *             the field not found exception
	 */
	public void filterSubjectAssessorResources(XnatSubjectassessordata exp)
			throws Exception {
		SyncConfigurationXsiType session = projectSyncConfiguration.getSynchronizationConfiguration().getSubjectAssessor(exp.getXSIType());
	    SyncConfigurationResource sessionResources = session.getResources();
		while (findAndRemoveExperimentResources(exp, sessionResources))	;
		return;
	}
	
	
	/**
	 * Find and remove experiment resources.
	 *
	 * @param exp
	 *            the exp
	 * @return true, if successful
	 */
	private boolean findAndRemoveExperimentResources(XnatExperimentdata exp, SyncConfigurationResource resourcesCfg) {
		boolean found = false;
		if (resourcesCfg == null) {
			return false;
		}
		List<XnatAbstractresourceI> resource = exp.getResources_resource();
		for (int i = 0; i < resource.size(); i++) {
			if (!resourcesCfg.isAllowedToSync(resource.get(i).getLabel())) {
				exp.removeResources_resource(i);
				found = true;
				break;
			}
		}
		return found;
	}


	/**
	 * Find and remove experiment resources.
	 *
	 * @param assessor
	 *            the exp
	 * @param resourcesCfg
	 *            the resource type
	 * @return true, if successful
	 */
	private boolean findAndRemoveAssessorResources(XnatImageassessordataI assessor, SyncConfigurationResource resourcesCfg) {
		boolean found = false;
		if (resourcesCfg == null) {
			return found;
		}
		List<XnatAbstractresourceI> resource = assessor.getResources_resource();
		for (int i = 0; i < resource.size(); i++) {
			if (!resourcesCfg.isAllowedToSync(resource.get(i).getLabel())) {
				((XnatImageassessordata)assessor).removeResources_resource(i);
				found = true;
				break;
			}
		}
		
		return found;
	}

	/**
	 * Find and remove experiment resources.
	 *
	 * @param assessor
	 *            the exp
	 * @param resourcesCfg
	 *            the resource type
	 * @return true, if successful
	 */
	private boolean findAndRemoveAssessorOutResources(XnatImageassessordataI assessor, SyncConfigurationResource resourcesCfg) {
		boolean found = false;
		if (resourcesCfg == null) {
			return found;
		}
		List<XnatAbstractresourceI> resource = assessor.getOut_file();
		for (int i = 0; i < resource.size(); i++) {
			if (!resourcesCfg.isAllowedToSync(resource.get(i).getLabel())) {
				((XnatImageassessordata)assessor).removeOut_file(i);
				found = true;
				break;
			}
		}
		
		return found;
	}
	
	/**
	 * Filter scantypes.
	 *
	 * @param exp
	 *            the exp
	 * @throws IndexOutOfBoundsException
	 *             the index out of bounds exception
	 * @throws FieldNotFoundException
	 *             the field not found exception
	 */
	private void filterScantypes(XnatExperimentdata exp)
			throws IndexOutOfBoundsException, FieldNotFoundException, Exception {
		SyncConfigurationImagingSessionXsiType sessionOption = projectSyncConfiguration.getSynchronizationConfiguration().getImagingSession(exp.getXSIType());
		while (findAndRemoveScantypes(exp, sessionOption))
			;
		filterScanResources(exp,sessionOption);
		return;
	}
	
	/**
	 * Filter scan resources.
	 *
	 * @param exp
	 *            the exp
	 * @param sessionOption Session options.
	 * @throws IndexOutOfBoundsException
	 *             the index out of bounds exception
	 * @throws FieldNotFoundException
	 *             the field not found exception
	 */
	public void filterScanResources(XnatExperimentdata exp,SyncConfigurationImagingSessionXsiType sessionOption)
			throws IndexOutOfBoundsException, FieldNotFoundException {
		SyncConfigurationResource rscOption = sessionOption.getScan_resources();
		if (rscOption == null) return;
		List<XnatImagescandataI> scans = ((XnatImagesessiondata)exp).getScans_scan();
		for (XnatImagescandataI scan:scans) {
				while (findAndRemoveScanResources(scan, rscOption))
					;
			}
		return;
	}

	
	
/**
 * Find and remove experiment resources.
 *
 * @param assessor
 *            the exp
 * @param resourcesCfg
 *            the resource type
 * @return true, if successful
 */
private boolean findAndRemoveScanResources(XnatImagescandataI scan, SyncConfigurationResource resourcesCfg) {
	boolean found = false;
	if (resourcesCfg == null) {
		return found;
	}
	List<XnatAbstractresourceI> resource = scan.getFile();
	for (int i = 0; i < resource.size(); i++) {
		if (!resourcesCfg.isAllowedToSync(resource.get(i).getLabel())) {
			((XnatImagescandata)scan).removeFile(i);
			found = true;
			break;
		}
	}
	return found;
}

	
	/**
	 * Filter Assessors.
	 *
	 * @param orig
	 *            the orig
	 * @param exp
	 *            the exp
	 * @throws IndexOutOfBoundsException
	 *             the index out of bounds exception
	 * @throws FieldNotFoundException
	 *             the field not found exception
	 */
	private void filterAssessors(XnatExperimentdata orig, XnatExperimentdata exp)
			throws IndexOutOfBoundsException, FieldNotFoundException {
		SyncConfigurationImagingSessionXsiType sessionOption = projectSyncConfiguration.getSynchronizationConfiguration().getImagingSession(exp.getXSIType());
		while (findAndRemoveAssessors(orig, exp, sessionOption))
			;
		//Now for each assessor, look at the resources which are configured to be synced
		filterAssessorResources(exp,sessionOption);
		return;
	}
	
	/**
	 * Filter experiment resources.
	 *
	 * @param exp
	 *            the exp
	 * @param sessionOption Session options.
	 * @throws IndexOutOfBoundsException
	 *             the index out of bounds exception
	 * @throws FieldNotFoundException
	 *             the field not found exception
	 */
	public void filterAssessorResources(XnatExperimentdata exp,SyncConfigurationImagingSessionXsiType sessionOption)
			throws IndexOutOfBoundsException, FieldNotFoundException {
		SyncConfigurationSessionAssessor assessorOption = sessionOption.getSession_assessors();
		if (assessorOption == null) return;
		List<XnatImageassessordataI> assessors = ((XnatImagesessiondata)exp).getAssessors_assessor();
		if (assessors == null) return;
		for (XnatImageassessordataI ass:assessors) {
			SyncConfigurationXsiType assessorAdvOption = assessorOption.getXsiType(ass.getXSIType());
			if (assessorAdvOption != null) {
				while (findAndRemoveAssessorResources(ass, assessorAdvOption.getResources()))
					;
				while (findAndRemoveAssessorOutResources(ass, assessorAdvOption.getResources()))
					;
			}
		}
		return;
	}


	
	
	
	/**
	 * Find and remove scantypes.
	 *
	 * @param exp
	 *            the exp
	 * @param sessionOption
	 *            Session options.
	 * @return true, if successful
	 */
	private boolean findAndRemoveScantypes(XnatExperimentdata exp, SyncConfigurationImagingSessionXsiType sessionOption) {
		boolean found = false;
		if (sessionOption == null || sessionOption.getScan_types() == null) {
			return false;
		}
		List<XnatImagescandataI> scans = ((XnatImagesessiondata) exp).getScans_scan();
		for (int i = 0; i < scans.size(); i++) {
			if (!sessionOption.isAllowedToSyncScan(scans.get(i).getType())) {
				((XnatImagesessiondata) exp).removeScans_scan(i);
				found = true;
				return true;
			}
		}
		return found;
	}

	
	/**
	 * Find and remove assessors.
	 *
	 * @param exp
	 *            the exp
	 * @return true, if successful
	 */
	private boolean findAndRemoveAssessors(XnatExperimentdata orig, XnatExperimentdata exp, SyncConfigurationImagingSessionXsiType sessionOption) {
		boolean found = false;
		if (sessionOption == null || sessionOption.getSession_assessors() == null) {
			return false;
		}
		List<XnatImageassessordataI> assessors = ((XnatImagesessiondata) exp).getAssessors_assessor();
		for (int i = 0; i < assessors.size(); i++) {
			if (!sessionOption.isAllowedToSyncAssessor(assessors.get(i).getXSIType())) {
				((XnatImagesessiondata) exp).removeAssessors_assessor(i);
				return true;
			}
		}
		return found;
	}

	/**
	 * Copy subject assessor.
	 *
	 * @param origSubject
	 *            the original subject
	 * @param newSubject
	 *            the new subject
	 * @param orig
	 *            the xnat subjectassessordata i
	 * @throws Exception
	 *             the exception
	 */
	public XnatSubjectassessordataI prepareSubjectAssessorToSync(XnatSubjectdata origSubject,XnatSubjectdata newSubject,
			XnatSubjectassessordataI orig)
					throws Exception {
		XnatSubjectassessordataI assess = (XnatSubjectassessordataI) correctIDandLabel((XnatSubjectdata)newSubject,(XnatSubjectassessordata)orig);
		filterSubjectAssessorResources((XnatSubjectassessordata)assess);
		for (final XnatAbstractresourceI res : assess.getResources_resource()) {
			//modifySubjectAssessorResource((XnatAbstractresource) res, origSubject, newSubject);
			modifyExptResource((XnatAbstractresource) res, (XnatExperimentdata)orig, false);

		}
		return assess;
		
	}




}
