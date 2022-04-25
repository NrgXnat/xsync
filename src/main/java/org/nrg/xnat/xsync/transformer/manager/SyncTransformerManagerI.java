package org.nrg.xnat.xsync.transformer.manager;

import org.nrg.xnat.xsync.transformer.SyncTransformerI;

/**
 * @author Mohana Ramaratnam (mohanakannan9@gmail.com)
 *
 */
public interface SyncTransformerManagerI {

	SyncTransformerI getSyncTransformer(String xsiType);

}
