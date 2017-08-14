package org.nrg.xnat.xsync.anonymize;

/**
 * @author Mohana Ramaratnam
 *
 */
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.lang3.StringUtils;
import org.nrg.dicom.mizer.exceptions.MizerException;
import org.nrg.dicom.mizer.exceptions.ScriptEvaluationException;
import org.nrg.dicom.mizer.service.MizerService;
import org.nrg.xdat.XDAT;


public abstract class AbstractExportAnonymizer implements Callable<java.lang.Void> {
	public static final Logger logger = LoggerFactory.getLogger(AbstractExportAnonymizer.class);

	AbstractExportAnonymizer next = null;

	abstract String getSubject();

	abstract String getLabel();

	public void setNext(AbstractExportAnonymizer a) {
		this.next = a;
	}

	public void anonymize(File f) throws MizerException {
		
		String scriptContent = this.getScript();
		logger.debug(f.getAbsolutePath());
		if (StringUtils.isNotEmpty(scriptContent)) {
			final MizerService service = XDAT.getContextService().getBeanSafely(MizerService.class);
			service.anonymize(f, this.getProjectName(), this.getSubject(), this.getLabel(), true, new Long(0),scriptContent);
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
