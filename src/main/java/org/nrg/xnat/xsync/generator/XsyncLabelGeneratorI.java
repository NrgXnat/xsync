/**
 * 
 */
package org.nrg.xnat.xsync.generator;

import org.nrg.xft.XFTItem;
import org.nrg.xft.security.UserI;
import org.nrg.xsync.configuration.ProjectSyncConfiguration;


/**
 * The Interface XsyncIdGeneratorI.
 * 
 * @author Atul
 */
public interface XsyncLabelGeneratorI {

	/**
	 * Using this API every site can use their own custom id format for Subject and Session labels.
	 *
	 * @param user the user
	 * @param item the item
	 * @param projectSyncConfiguration the project sync configuration
	 * @return the string
	 * @throws Exception the exception
	 */
	String generateId(UserI user, XFTItem item, ProjectSyncConfiguration projectSyncConfiguration) throws Exception;
}
