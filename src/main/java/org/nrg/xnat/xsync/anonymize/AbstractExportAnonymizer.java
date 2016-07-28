package org.nrg.xnat.xsync.anonymize;

/**
 * @author Mohana Ramaratnam
 *
 */
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.nrg.config.services.ConfigService;
import org.nrg.dcm.Anonymize;
import org.nrg.dcm.DicomUtils;
import org.nrg.dcm.edit.AttributeException;
import org.nrg.dcm.edit.ScriptEvaluationException;
import org.nrg.xnat.xsync.anonymize.AbstractExportAnonymizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


public abstract class AbstractExportAnonymizer implements Callable<java.lang.Void> {
	public static Logger logger = Logger.getLogger(AbstractExportAnonymizer.class);

	AbstractExportAnonymizer next = null;

	abstract String getSubject();

	abstract String getLabel();

	public void setNext(AbstractExportAnonymizer a) {
		this.next = a;
	}

	public void anonymize(File f)
			throws AttributeException, ScriptEvaluationException, FileNotFoundException, IOException {
		String scriptContent = this.getScript();
		logger.debug(f.getAbsolutePath());
		
		if (StringUtils.isNotEmpty(scriptContent)) {
			Anonymize.anonymize(f, this.getProjectName(), this.getSubject(), this.getLabel(), true, new Long(0),scriptContent);
			checkAnonymization(f);
			if (this.next != null) {
				this.next.anonymize(f);
			}
		} else {
			throw new ScriptEvaluationException("No anonymization script found");
			//TODO
			// this project does not have an anon script
			//Just copy the files
			
		}
	}

	/*
	 * Must fail if anonymization script is now run successfully.
	 * 
	 */
	void checkAnonymization(File f) {
		try {
			DicomObject dcmFile = DicomUtils.read(f);
			String deident = dcmFile.getString(Tag.DeidentificationMethod);
			if (deident != null) {
				if (StringUtils.contains("XSYNC anonymization script",deident) == false) {
					logger.error("Cannot find Data DeidentificationMethod in dicom. FAILED!! ");
					throw new RuntimeException("Cannot find Data DeidentificationMethod in dicom. Anonymization FAILED");
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			logger.error("",e);
			throw new RuntimeException("Cannot find Data DeidentificationMethod in dicom. Anonymization FAILED");
		}
	}

	/**
	 * Get the appropriate edit script.
	 * 
	 * @return
	 */
	abstract String getScript();

	/**
	 * Check if editing is enabled.
	 * 
	 * @return
	 */
	abstract boolean isEnabled();

	/**
	 * Sometimes the session passed in isn't associated with a project, for
	 * instance if the session is in the prearchive so subclasses must specify
	 * how to get the project name.
	 * 
	 * @return
	 */
	abstract String getProjectName();

	/**
	 * Get the list of files that need to be anonymized.
	 * 
	 * @return
	 * @throws IOException
	 */
	abstract List<File> getFilesToAnonymize() throws IOException;

	public java.lang.Void call() throws Exception {
		if (this.getScript() != null && this.isEnabled()) {
			List<File> fs = this.getFilesToAnonymize();
			for (File f : fs) {
				this.anonymize(f);
			}
		} else {
			// there is no anon script
		}
		return null;
	}

	

}
