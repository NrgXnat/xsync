package org.nrg.xnat.xsync.anonymize;

/**
 * @author Mohana Ramaratnam
 *
 */
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.nrg.xnat.archive.ArchivingException;
import org.nrg.xnat.helpers.merge.MergeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.lang3.StringUtils;
import org.nrg.dicom.mizer.exceptions.MizerException;
import org.nrg.dicom.mizer.exceptions.ScriptEvaluationException;
import org.nrg.dicom.mizer.objects.AnonymizationResult;
import org.nrg.dicom.mizer.objects.AnonymizationResultReject;
import org.nrg.dicom.mizer.service.MizerService;
import org.nrg.xdat.XDAT;


public abstract class AbstractExportAnonymizer implements Callable<Boolean> {
	public static final Logger logger = LoggerFactory.getLogger(AbstractExportAnonymizer.class);

	AbstractExportAnonymizer next = null;

	abstract String getSubject();

	abstract String getLabel();

	public void setNext(AbstractExportAnonymizer a) {
		this.next = a;
	}

	public List<AnonymizationResult> anonymize(List<File> files) throws MizerException {
		
		String scriptContent = this.getScript();
		if (StringUtils.isNotEmpty(scriptContent)) {
			final MizerService service = XDAT.getContextService().getBeanSafely(MizerService.class);
			final List<AnonymizationResult> anonResult = service.anonymize(files, this.getProjectName(), this.getSubject(), this.getLabel(), new Long(0), scriptContent, true, false);

			try {
				MergeUtils.deleteRejectedFiles(logger, anonResult, getProjectName());
			} catch (ArchivingException e) {
				throw new MizerException(e);
			}

			if (this.next != null) {
				final List<AnonymizationResult> joiner = new ArrayList<>();
				joiner.addAll(anonResult);
				joiner.addAll(this.next.anonymize(files));
				return joiner;
			}
			return anonResult;
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

	public Boolean call() throws Exception {
		if (this.getScript() != null && this.isEnabled()) {
			final List<File> fs = this.getFilesToAnonymize();
			return this.anonymize(fs).stream().allMatch(AnonymizationResultReject.class::isInstance);
		} else {
			// there is no anon script
		}
		return false;
	}

	

}
