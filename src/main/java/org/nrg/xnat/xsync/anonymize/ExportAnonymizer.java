package org.nrg.xnat.xsync.anonymize;

import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * @author Mohana Ramaratnam
 */
import org.apache.commons.lang3.StringUtils;
import org.nrg.dicom.mizer.objects.AnonymizationResult;
import org.nrg.xdat.model.XnatAbstractresourceI;
import org.nrg.xdat.model.XnatImagescandataI;
import org.nrg.xdat.model.XnatImagesessiondataI;
import org.nrg.xdat.om.XnatImagesessiondata;
import org.nrg.xdat.om.XnatResource;
import org.nrg.xdat.om.XnatSubjectdata;
import org.nrg.xdat.om.base.BaseXnatProjectdata;
import org.nrg.xnat.helpers.editscript.DicomEdit;
import org.nrg.xsync.tools.XsyncXnatInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExportAnonymizer extends AbstractExportAnonymizer implements Callable<List<AnonymizationResult>>{
	
	private final static Logger logger = LoggerFactory.getLogger(ExportAnonymizer.class);

	final String FILE_TYPE="DICOM";
	
	final String projectId;
	final String sessionPath;
	final String label;
	final XnatImagesessiondataI s;
	final String path;
	final String subjectLabel;

	final XnatImagescandataI scan;

	String scriptContent="";
	private final XsyncXnatInfo _xsyncXnatInfo;
	
	/**
	 * 
	 * @param s The session object.
	 * @param projectId The project Id, eg. xnat_E*
	 * @param sessionPath The root path of this project's session directory
	 */
	public ExportAnonymizer(final XsyncXnatInfo xsyncXnatInfo, XnatImagesessiondataI s, String projectId, String sessionPath, final XnatImagescandataI scan){
		_xsyncXnatInfo = xsyncXnatInfo;
		this.s = s;
		this.projectId= projectId;
		this.sessionPath = sessionPath;
		this.label = s.getLabel();
		this.path = DicomEdit.buildScriptPath(DicomEdit.ResourceScope.PROJECT, projectId);
		this.subjectLabel = null;
		this.scan = scan;
	}
	
	public ExportAnonymizer(final XsyncXnatInfo xsyncXnatInfo, String label, XnatImagesessiondataI s, String projectId, String sessionPath, final XnatImagescandataI scan) {
		_xsyncXnatInfo = xsyncXnatInfo;
		this.s = s;
		this.projectId = projectId;
		this.sessionPath = sessionPath;
		this.label = label;
		this.path = DicomEdit.buildScriptPath(DicomEdit.ResourceScope.PROJECT, projectId);
		this.subjectLabel = null;
		this.scan = scan;
	} 
	
	public ExportAnonymizer(final XsyncXnatInfo xsyncXnatInfo, XnatImagesessiondataI s, String subjectLabel, String projectId, String sessionPath, final XnatImagescandataI scan){
		_xsyncXnatInfo = xsyncXnatInfo;
		this.s = s;
		this.projectId= projectId;
		this.sessionPath = sessionPath;
		this.label = s.getLabel();
		this.path = DicomEdit.buildScriptPath(DicomEdit.ResourceScope.PROJECT, projectId);
		this.subjectLabel = subjectLabel;
		this.scan = scan;
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
	 * @return The files to be anonymized.
	 */
	@Override
	public List<File> getFilesToAnonymize() {
		List<File> ret = new ArrayList<>();
		// anonymize everything in srcRootPath
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
		return ret;
	}
	
	static Long getDBId (String project) {
		return BaseXnatProjectdata.getProjectInfoIdFromStringId(project);
	}
	
	@Override
	String getScript() {
		try {
			if(scriptContent.equals("")) {
				String anonymizationFromConfig = _xsyncXnatInfo.getDicomAnonymization(s.getProject());
				scriptContent= new String( anonymizationFromConfig.getBytes("UTF-8"), Charset.forName("UTF-8") );
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
}