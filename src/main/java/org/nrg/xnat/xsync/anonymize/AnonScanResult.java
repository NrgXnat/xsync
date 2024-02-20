package org.nrg.xnat.xsync.anonymize;

import java.util.List;
import org.nrg.dicom.mizer.objects.AnonymizationResult;

public class AnonScanResult {

  private final String id;
  private boolean removed = false;
  private List<AnonymizationResult> results;

  public AnonScanResult(final String id) {
    this.id = id;
  }

  public void setRemoved(final boolean removed) {
    this.removed = removed;
  }

  public boolean isRemoved() {
    return this.removed;
  }

  public String getId() {
    return this.id;
  }

  public List<AnonymizationResult> getResults() {
    return this.results;
  }

  public void setResults(final List<AnonymizationResult> results) {
    this.results = results;
  }

  public boolean containsResult(final String entryName) {
    return getResults().stream().anyMatch(anonymizationResult -> anonymizationResult.getAbsolutePath().endsWith(entryName));
  }
}
