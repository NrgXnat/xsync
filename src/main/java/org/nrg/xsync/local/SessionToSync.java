package org.nrg.xsync.local;

import java.util.List;
import org.apache.commons.lang.StringUtils;
import org.nrg.xdat.om.XnatImagesessiondata;
import org.nrg.xnat.xsync.anonymize.AnonScanResult;

public class SessionToSync {

  public XnatImagesessiondata session;
  public List<AnonScanResult> anonScanResults;

  public SessionToSync(XnatImagesessiondata session, List<AnonScanResult> anonScanResults) {
    this.session = session;
    this.anonScanResults = anonScanResults;
  }

  public AnonScanResult getByScanId(String id) {
    if (anonScanResults != null) {
      return anonScanResults.stream().filter(result -> StringUtils.equals(result.getId(), id)).findFirst().orElse(null);
    }

    return null;
  }
}
