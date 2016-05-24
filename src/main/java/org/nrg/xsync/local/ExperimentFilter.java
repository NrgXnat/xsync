package org.nrg.xsync.local;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.nrg.xdat.XDAT;
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
import org.nrg.xdat.om.XnatImagesessiondata;
import org.nrg.xdat.om.XnatResource;
import org.nrg.xdat.om.XnatResourceseries;
import org.nrg.xdat.om.XnatSubjectassessordata;
import org.nrg.xdat.om.XnatSubjectdata;
import org.nrg.xdat.om.base.BaseXnatExperimentdata.UnknownPrimaryProjectException;
import org.nrg.xft.ItemI;
import org.nrg.xft.XFTItem;
import org.nrg.xft.exception.ElementNotFoundException;
import org.nrg.xft.exception.FieldNotFoundException;
import org.nrg.xft.exception.XFTInitException;
import org.nrg.xft.security.UserI;
import org.nrg.xnat.exceptions.InvalidArchiveStructure;
import org.nrg.xnat.xsync.anonymize.AnonymizerI;
import org.nrg.xnat.xsync.anonymize.XsyncAnonymizer;
import org.nrg.xsync.configuration.ProjectSyncConfiguration;
import org.nrg.xsync.configuration.json.ImagingSessionConfiguration;
import org.nrg.xsync.manager.SynchronizationManager;
import org.nrg.xsync.utils.QueryResultUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
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
	
	public ExperimentFilter(UserI user,ProjectSyncConfiguration projectSyncConfiguration) {
		_user = user;
		this.projectSyncConfiguration = projectSyncConfiguration;
	}
	
	public Map<String,List<XnatExperimentdataI>> select(XnatSubjectdata subject, String localSubjectId) throws Exception {
		List<XnatExperimentdataI> experimentsDeleted = new ArrayList<XnatExperimentdataI>();
		List<XnatExperimentdataI> experimentsModified = new ArrayList<XnatExperimentdataI>();

		List<XnatExperimentdataI> experimentsConfiguredToBeSynced = new ArrayList<XnatExperimentdataI>();

		List<XnatSubjectassessordataI> existingExperiments = subject.getExperiments_experiment();
		int total_experiments = existingExperiments.size();
		_log.debug("Existing experiments " + total_experiments);
		int i = 0;
		while(total_experiments > 0) {
			XnatSubjectassessordataI subjectAssessor = existingExperiments.get(i);
			if (projectSyncConfiguration.isSubjectAssessorToBeSynced(subjectAssessor.getXSIType()) || projectSyncConfiguration.isImagingSessionToBeSynced(subjectAssessor.getXSIType())) {
				   experimentsConfiguredToBeSynced.add(subjectAssessor);	
			}
			subject.removeExperiments_experiment(i);
			existingExperiments = subject.getExperiments_experiment();
			total_experiments = subject.getExperiments_experiment().size();
		}
		//Find the experiments which have been deleted
		MapSqlParameterSource parameters = new MapSqlParameterSource();
		parameters.addValue(QueryResultUtil.PROJECT_QUERY_PARAMETER_NAME, projectSyncConfiguration.getSynchronizationConfiguration().getProject());
		parameters.addValue(QueryResultUtil.SUBJECT_QUERY_PARAMETER_NAME, localSubjectId);

		QueryResultUtil queryUtil = new QueryResultUtil();
		NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(
				new JdbcTemplate(XDAT.getDataSource()));
		if (experimentsConfiguredToBeSynced.size() > 0) {
			List<String> experimentIds = new ArrayList<String>();
			for (XnatExperimentdataI experiment:experimentsConfiguredToBeSynced) {
				experimentIds.add(experiment.getId());
			}
			parameters.addValue(QueryResultUtil.EXPERIMENT_IDS, experimentIds);

			String query = queryUtil.getQueryForFetchingSubjectExperimentsSinceLastSync();
			//Columns
			// id,label,element_name,project,status,last_modified, sync_start_time 		
			_log.debug("Query is " + query);
			List<Map<String,Object>> experiments = jdbcTemplate.queryForList(query, parameters);
			
			if (experiments != null && experiments.size()>0) {
				for (Map<String,Object> row:experiments) {
					if (row.get("status").equals(QueryResultUtil.ACTIVE_STATUS)) {
						_log.debug("Experiment Modified: " + (String)row.get("id"));
						experimentsModified.add(getExperiment((String)row.get("id"),experimentsConfiguredToBeSynced));
					}else if (row.get("status").equals(QueryResultUtil.DELETE_STATUS)) {
						_log.debug("Experiment Deleted: " + (String)row.get("id"));
						experimentsDeleted.add(createNew((String)row.get("id"), subject,(String)row.get("element_name")));
					}
				}
			}else {
				//Look for experiments which may have been marked ok to sync
				 query = queryUtil.getQueryForFetchingSubjectExperimentsMarkedOKSinceLastSync();
					//Columns
					// id,label,element_name,project,status,last_modified, sync_start_time 		
				_log.debug("Query is " + query);
				experiments = jdbcTemplate.queryForList(query, parameters);
				if (experiments != null && experiments.size()>0) {
					for (Map<String,Object> row:experiments) {
						if (row.get("status").equals(QueryResultUtil.ACTIVE_STATUS)) {
							_log.debug("Experiment Modified: " + (String)row.get("id"));
							experimentsModified.add(getExperiment((String)row.get("id"),experimentsConfiguredToBeSynced));
						}
					}
				}else 
				 _log.debug("None of the configured experiments have changed for subject " + subject.getId());
			}
		}else { // Subject has no experiments which are configured to be synced. Have any been deleted?
			String query = queryUtil.getQueryForFetchingSubjectExperimentsDeletedSinceLastSync();
			//Columns
			// id,label,element_name,project,status,last_modified, sync_start_time 		
			_log.debug("Query is " + query);
			List<Map<String,Object>> experiments = jdbcTemplate.queryForList(query, parameters);
			
			if (experiments != null && experiments.size()>0) {
				for (Map<String,Object> row:experiments) {
					if (projectSyncConfiguration.isSubjectAssessorToBeSynced((String)row.get("element_name")) || projectSyncConfiguration.isImagingSessionToBeSynced((String)row.get("element_name"))) {
						if (row.get("status").equals("active")) {
							_log.debug("Experiment Modified: " + (String)row.get("id"));
							experimentsModified.add(createNew((String)row.get("id"),subject,(String)row.get("element_name") ));
						}else if (row.get("status").equals("deleted")) {
							_log.debug("Experiment Deleted: " + (String)row.get("id"));
							experimentsDeleted.add(createNew((String)row.get("id"),subject,(String)row.get("element_name")));
						}
					}
				}
			}else {
				_log.debug("Nothing has changed for project");
			}
		}
		
		Map<String,List<XnatExperimentdataI>> filteredResults = new HashMap<String,List<XnatExperimentdataI>>();
		filteredResults.put("active", experimentsModified);
		filteredResults.put("deleted", experimentsDeleted);
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
	
	private XnatExperimentdataI createNew(String id, XnatSubjectdata subject,String xsiType) {
		Class c = BaseElement.GetGeneratedClass(xsiType);
		ItemI o = null;
		try {
            o = (ItemI) c.newInstance();
            o.setProperty("id", id);
            o.setProperty("project", subject.getProject());
            o.setProperty("subject_ID", subject.getId());
        }catch(Exception e) {
        	_log.debug("Could not instantiate the experiment " + id);
        }
        return new XnatExperimentdata(o);
	}
	
	/**
	 * @param newEXPT
	 *            experiment for correction
	 * @throws Exception
	 */
	private XnatImagesessiondata correctIDandLabel(XnatSubjectdataI targetsubject,XnatImagesessiondata origExperiment) throws Exception {
		XFTItem item = origExperiment.getItem().copy();
		XnatImagesessiondata targetExperiment = (XnatImagesessiondata) BaseElement.GetGeneratedItem(item);
		String newid = "";
		IdMapper idMapper = new IdMapper(this._user, this.projectSyncConfiguration);
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
	 * @param newEXPT
	 *            experiment for correction
	 * @throws Exception
	 */
	private XnatSubjectassessordata correctIDandLabel(XnatSubjectdataI targetsubject,XnatSubjectassessordata origExperiment) throws Exception {
		XFTItem item = origExperiment.getItem().copy();
		XnatSubjectassessordata targetExperiment = (XnatSubjectassessordata) BaseElement.GetGeneratedItem(item);
		String newid = "";
		IdMapper idMapper = new IdMapper(this._user, this.projectSyncConfiguration);
		//String alreadyAssignedRemoteId = idMapper.getRemoteAccessionId(origExperiment.getId());
		//if (alreadyAssignedRemoteId != null) {
		//	newid = alreadyAssignedRemoteId;
		//}
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
	
	/**
	 * Modify expt resource.
	 *
	 * @param payload
	 *            the payload
	 * @param resource
	 *            the resource
	 * @param orig
	 *            the orig
	 * @param exp
	 *            the exp
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
	private void modifyExptResource(XnatAbstractresourceI resource, XnatExperimentdata orig,
			XnatExperimentdata exp) throws IOException, UnknownPrimaryProjectException, InvalidArchiveStructure,
					ElementNotFoundException, FieldNotFoundException, XFTInitException {
		String filepath = orig.getArchiveRootPath() + "arc001/";// +
																// orig.getArchiveDirectoryName();
		String newFilepath = SynchronizationManager.GET_SYNC_FILE_PATH(orig.getProject(),orig);

		if (resource instanceof XnatResource) {
			String path = ((XnatResource) resource).getUri();
			String newURI = path.replace(filepath, newFilepath);
			((XnatResource) resource).setUri(newURI);
			modifyExptResourceFiles(path, newURI);
		} else if (resource instanceof XnatResourceseries) {
			String path = ((XnatResourceseries) resource).getPath();
			String newURI = path.replace(filepath, newFilepath);
			((XnatResourceseries) resource).setPath(newURI);
			modifyExptResourceFiles(path, newURI);
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
	private void modifyExptResourceFiles(String catalogFile, String newCatalogFile)
			throws IOException, UnknownPrimaryProjectException, InvalidArchiveStructure, ElementNotFoundException,
			FieldNotFoundException, XFTInitException {

		// this is path to catalog
		String newCatalogFileParentDir = newCatalogFile.substring(0, newCatalogFile.lastIndexOf(File.separatorChar));
		new File(newCatalogFileParentDir).mkdirs();
		File sourceCatalog = new File(catalogFile);
		File destCatalog = new File(newCatalogFile);

		File source = sourceCatalog.getParentFile();
		File dest = destCatalog.getParentFile();

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
	 * @param user
	 *            the user
	 * @param payload
	 *            the payload
	 * @param orig
	 *            the orig
	 * @param exp
	 *            the exp
	 * @param ed
	 *            the ed
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
					modifyExptResource((XnatAbstractresource) res, orig, exp);
				}

				if (exp instanceof XnatImagesessiondata) {
					resetPrearchive((XnatImagesessiondata) exp);
					filterScantypes(orig, exp);
					for (final XnatImagescandataI scan : ((XnatImagesessiondata) exp).getScans_scan()) {
						scan.setImageSessionId(exp.getLabel());
						for (final XnatAbstractresourceI res : scan.getFile()) {
							modifyExptResource((XnatAbstractresource) res, orig, exp);
						}
					}
					
					filterRecons(exp);
					for (final XnatReconstructedimagedataI recon : ((XnatImagesessiondata) exp)
							.getReconstructions_reconstructedimage()) {
						recon.setImageSessionId(exp.getLabel());
						ReconstructionFilter reconFilter = new ReconstructionFilter();
						reconFilter.correctIDandLabel(recon);
						for (final XnatAbstractresourceI res : recon.getIn_file()) {
							modifyExptResource((XnatAbstractresource) res, orig, exp);
						}
						for (final XnatAbstractresourceI res : recon.getOut_file()) {
							modifyExptResource((XnatAbstractresource) res, orig, exp);
						}
					}

					for (final XnatImageassessordataI assess : ((XnatImagesessiondata) exp).getAssessors_assessor()) {
						assess.setImagesessionId(exp.getLabel());
						for (XnatExperimentdataShareI share : assess.getSharing_share()) {
							if (share.getLabel() != null) {
								share.setLabel("");
							}
						}
						for (final XnatAbstractresourceI res : assess.getResources_resource()) {
							modifyExptResource((XnatAbstractresource) res, orig, exp);
						}

						for (final XnatAbstractresourceI res : assess.getIn_file()) {
							modifyExptResource((XnatAbstractresource) res, orig, exp);
						}

						for (final XnatAbstractresourceI res : assess.getOut_file()) {
							modifyExptResource((XnatAbstractresource) res, orig, exp);
						}
						
					}
					Boolean isExptToBeAnonymized = projectSyncConfiguration.getImagingSessionConfiguration(exp.getXSIType()).getAnonymize(); 
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
	
	
	private void anonymize(XnatImagesessiondata exp, String destProject) {
		try {
		File sessionDir = exp.getSessionDir();
		if (sessionDir != null) {
			AnonymizerI simpleExportAnonymizer = new XsyncAnonymizer();
			simpleExportAnonymizer.anonymize((XnatImagesessiondata) exp, destProject);
		}else {
			_log.debug("There are no files to anonymize");
		}
		}catch(Exception e) {
			_log.error(e.getMessage());
		}
	}
	
	/**
	 * Filter experiment resources.
	 *
	 * @param orig
	 *            the orig
	 * @param exp
	 *            the exp
	 * @param payload
	 *            the payload
	 * @throws IndexOutOfBoundsException
	 *             the index out of bounds exception
	 * @throws FieldNotFoundException
	 *             the field not found exception
	 */
	public void filterExperimentResources(XnatExperimentdata exp)
			throws IndexOutOfBoundsException, FieldNotFoundException {
		ImagingSessionConfiguration session = projectSyncConfiguration.getImagingSessionConfiguration(exp.getXSIType());
	    List<String> sessionResources = session.getResources();
		while (findAndRemoveExperimentResources(exp, sessionResources))	;
		return;
	}

	/**
	 * Find and remove experiment resources.
	 *
	 * @param exp
	 *            the exp
	 * @param resourceType
	 *            the resource type
	 * @return true, if successful
	 */
	private boolean findAndRemoveExperimentResources(XnatExperimentdata exp, List<String> resourceType) {
		boolean found = false;
		List<XnatAbstractresourceI> resource = exp.getResources_resource();
		for (int i = 0; i < resource.size(); i++) {
			if (resourceType != null && !resourceType.contains(resource.get(i).getLabel())) {
				exp.removeResources_resource(i);
				found = true;
				break;
			}
		}
		return found;
	}
	
	/**
	 * Filter scantypes.
	 *
	 * @param orig
	 *            the orig
	 * @param exp
	 *            the exp
	 * @param payload
	 *            the payload
	 * @throws IndexOutOfBoundsException
	 *             the index out of bounds exception
	 * @throws FieldNotFoundException
	 *             the field not found exception
	 */
	private void filterScantypes(XnatExperimentdata orig, XnatExperimentdata exp)
			throws IndexOutOfBoundsException, FieldNotFoundException {
		ImagingSessionConfiguration session = projectSyncConfiguration.getImagingSessionConfiguration(exp.getXSIType());
	    List<String> scanTypes = session.getScanTypes();
		while (findAndRemoveScantypes(orig, exp, scanTypes))
			;
		return;
	}

	
	/**
	 * Find and remove scantypes.
	 *
	 * @param exp
	 *            the exp
	 * @param payload
	 *            the payload
	 * @return true, if successful
	 */
	private boolean findAndRemoveScantypes(XnatExperimentdata orig, XnatExperimentdata exp, List<String> configuredScans) {
		boolean found = false;
		List<XnatImagescandataI> scans = ((XnatImagesessiondata) exp).getScans_scan();
		for (int i = 0; i < scans.size(); i++) {
			if (configuredScans != null && configuredScans.size() > 0) {// no
				if (!configuredScans.contains(scans.get(i).getType())) {
					((XnatImagesessiondata) exp).removeScans_scan(i);
					found = true;
					return true;
				} 
			} 
		}
		return found;
	}

	/**
	 * Copy subject assessor.
	 *
	 * @param user
	 *            the user
	 * @param payload
	 *            the payload
	 * @param subject
	 *            the subject
	 * @param newSubject
	 *            the new subject
	 * @param xnatSubjectassessordataI
	 *            the xnat subjectassessordata i
	 * @param assess
	 *            the assess
	 * @param ed
	 *            the ed
	 * @throws Exception
	 *             the exception
	 */
	public XnatSubjectassessordataI prepareSubjectAssessorToSync(XnatSubjectdata origSubject,XnatSubjectdata newSubject,
			XnatSubjectassessordataI orig)
					throws Exception {
		XnatSubjectassessordataI assess = (XnatSubjectassessordataI) correctIDandLabel((XnatSubjectdata)newSubject,(XnatSubjectassessordata)orig);
		for (final XnatAbstractresourceI res : assess.getResources_resource()) {
			//modifySubjectAssessorResource((XnatAbstractresource) res, origSubject, newSubject);
			modifyExptResource((XnatAbstractresource) res, (XnatExperimentdata)orig, (XnatExperimentdata)assess);

		}
		return assess;
		
	}

	/**
	 * Modify subject resource.
	 *
	 * @param payload
	 *            the payload
	 * @param resource
	 *            the resource
	 * @param subject
	 *            the subject
	 * @param newSubject
	 *            the new subject
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
	private void modifySubjectAssessorResource(XnatAbstractresourceI resource, XnatSubjectdata subject,
			XnatSubjectdata newSubject) throws IOException, UnknownPrimaryProjectException, InvalidArchiveStructure,
					ElementNotFoundException, FieldNotFoundException, XFTInitException {

		String filepath = subject.getArchiveRootPath() + "subjects/" + subject.getArchiveDirectoryName();
		String newFilepath = SynchronizationManager.GET_SYNC_FILE_PATH(subject.getProject(), subject);

		if (resource instanceof XnatResource) {
			String path = ((XnatResource) resource).getUri();
			String newURI = path.replace(filepath, newFilepath);
			((XnatResource) resource).setUri(newURI);
			modifySubjResourceFiles(path, newURI);

		} else if (resource instanceof XnatResourceseries) {
			String path = ((XnatResourceseries) resource).getPath();
			String newURI = path.replace(filepath, newFilepath);
			((XnatResourceseries) resource).setPath(newURI);
			modifySubjResourceFiles(path, newURI);
			// payload.addManifestEntry(new
			// PayloadManifestEntry(newURI,PayloadManifestEntry.SUCCESS,""));
		}

	}

	/**
	 * Modify subj resource files.
	 *
	 * @param catalogFile
	 *            the catalog file
	 * @param newCatalogFile
	 *            the new catalog file
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 */
	private void modifySubjResourceFiles(String catalogFile, String newCatalogFile)
			throws IOException {

		String newCatalogFileParentDir = newCatalogFile.substring(0, newCatalogFile.lastIndexOf(File.separatorChar));
		new File(newCatalogFileParentDir).mkdirs();
		File sourceCatalog = new File(catalogFile);
		File destCatalog = new File(newCatalogFile);

		File source = sourceCatalog.getParentFile();
		File dest = destCatalog.getParentFile();

		// copy the actual files
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


}
