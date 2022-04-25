package org.nrg.xnat.xsync.transformer;

import java.util.Map;

import org.nrg.xdat.om.XnatExperimentdata;

/**
 * @author Mohana Ramaratnam (mohanakannan9@gmail.com)
 * 
 * Datatypes which are modelled in an odd way and can not be handled by Xsync 
 * usual route of transforming the XML to include the Remote Id's would implement
 * this interface to transform themselves for the remote XNAT.
 *
 */
public interface SyncTransformerI {

	public void transform(XnatExperimentdata exp, Map<String, String> attributes);

}
