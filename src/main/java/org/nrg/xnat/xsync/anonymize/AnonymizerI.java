package org.nrg.xnat.xsync.anonymize;

/**
 * @author Mohana Ramaratnam
 *
 */
import org.nrg.xdat.om.XnatImagesessiondata;


public interface AnonymizerI {
	void anonymize(final XnatImagesessiondata session, final String destProject) throws Exception;     
}
