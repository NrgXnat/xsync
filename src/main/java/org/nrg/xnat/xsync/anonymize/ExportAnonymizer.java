package org.nrg.xnat.xsync.anonymize;

/**
 * @author Mohana Ramaratnam
 *
 */
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.nrg.config.exceptions.ConfigServiceException;
import org.nrg.xdat.model.XnatAbstractresourceI;
import org.nrg.xdat.model.XnatImagescandataI;
import org.nrg.xdat.model.XnatImagesessiondataI;
import org.nrg.xdat.om.XnatImagesessiondata;
import org.nrg.xdat.om.XnatProjectdata;
import org.nrg.xdat.om.XnatResource;
import org.nrg.xdat.om.XnatSubjectdata;
import org.nrg.xdat.om.base.BaseXnatProjectdata;
import org.nrg.xnat.helpers.editscript.DicomEdit;
import org.nrg.xsync.configuration.ProjectSyncConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExportAnonymizer extends AbstractExportAnonymizer implements Callable<java.lang.Void>{
	
	
	
	private final static Logger logger = LoggerFactory.getLogger(ExportAnonymizer.class);

	final String FILE_TYPE="DICOM";
	
	final String projectId;
	final String sessionPath;
	final String label;
	final XnatImagesessiondataI s;
	final String path;
	final String subjectLabel;
	String scriptContent="";
	
	/**
	 * 
	 * @param s The session object.
	 * @param projectId The project Id, eg. xnat_E*
	 * @param sessionPath The root path of this project's session directory
	 */
	public ExportAnonymizer(XnatImagesessiondataI s, String projectId, String sessionPath){
		this.s = s;
		this.projectId= projectId;
		this.sessionPath = sessionPath;
		this.label = s.getLabel();
		this.path = DicomEdit.buildScriptPath(DicomEdit.ResourceScope.PROJECT, projectId);
		this.subjectLabel = null;
	}
	
	public ExportAnonymizer(String label, XnatImagesessiondataI s, String projectId, String sessionPath) {
		this.s = s;
		this.projectId = projectId;
		this.sessionPath = sessionPath;
		this.label = label;
		this.path = DicomEdit.buildScriptPath(DicomEdit.ResourceScope.PROJECT, projectId);
		this.subjectLabel = null;
	} 
	
	public ExportAnonymizer(XnatImagesessiondataI s, String subjectLabel, String projectId, String sessionPath){
		this.s = s;
		this.projectId= projectId;
		this.sessionPath = sessionPath;
		this.label = s.getLabel();
		this.path = DicomEdit.buildScriptPath(DicomEdit.ResourceScope.PROJECT, projectId);
		this.subjectLabel = subjectLabel;
	}
	
	/**
	 * Returns the subject string that will be passed into the 
	 * Anonymize.anonymize function
	 * @return The subject label or subject id (if label is null)
	 */
	@Override
	String getSubject() {
		
		if(null != this.subjectLabel){
			return this.subjectLabel;
		}
		
		String label = null;
		if(s instanceof XnatImagesessiondata){
			XnatSubjectdata d = ((XnatImagesessiondata)this.s).getSubjectData();
			if ( d != null){
				label = d.getLabel();
			}
		}
		
		// If the label is null, return the SubjectId
		return (label != null) ? label : this.s.getSubjectId();
	}
	
	@Override
	String getLabel() {
		return this.label;
	}
	
	@Override
	String getProjectName() {
		return this.projectId;
	}
	
	/**
	 * Retrieve a list of files that need to be anonymized.
	 * By default the files are retrieved from the project's archive space.
	 * @return
	 */
	@Override
	public List<File> getFilesToAnonymize() {
		List<File> ret = new ArrayList<File>();
		// anonymize everything in srcRootPath
		for(final XnatImagescandataI scan: s.getScans_scan()) {
			for (final XnatAbstractresourceI res:scan.getFile()) {
				if (res instanceof XnatResource) {
					final XnatResource abs=(XnatResource)res;
					if (StringUtils.isNotEmpty(abs.getFormat()) && abs.getFormat().equals("DICOM")){
						for (final File f: abs.getCorrespondingFiles(this.sessionPath)){
							ret.add(f);
						}
					}
				}
			}
		}
		return ret;
	}
	
	static Long getDBId (String project) {
		return BaseXnatProjectdata.getProjectInfoIdFromStringId(project);
	}
	
	@Override
	String getScript() {
		try {
			if(scriptContent.equals("")) {
				String anonFilePath = ProjectSyncConfiguration.GetAnonymizationFilePath((XnatImagesessiondata)s,FILE_TYPE);
				File anonFile = new File(anonFilePath);
				if (anonFile.exists()) {
					FileInputStream inputStream = new FileInputStream(anonFilePath);
					try {
					    String origscriptContent = IOUtils.toString(inputStream);
						scriptContent= new String( origscriptContent.getBytes("UTF-8"), Charset.forName("UTF-8") );
					} finally {
					    inputStream.close();
					}
				}
			}
		}catch ( Exception e) {
			logger.error("Failed to retrieve export anonymization script content",e);
			throw new RuntimeException("Failed to retrieve export anonymization script content");
		}
		return scriptContent;
	}
	
	@Override
	boolean isEnabled() {
		return true;
	}
	
	public java.lang.Void call() throws Exception {
		super.call();
		return null;
	}
}