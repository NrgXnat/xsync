package org.nrg.xnat.xsync.anonymize;

import java.util.List;

import lombok.Getter;
import lombok.Setter;
import org.nrg.dicom.mizer.objects.AnonymizationResult;

@Getter
public class AnonScanResult {

    private final String id;
    @Setter
    private boolean removed = false;
    @Setter
    private List<AnonymizationResult> results;

    public AnonScanResult(final String id) {
    this.id = id;
  }

    public boolean containsResult(final String entryName) {
        return getResults().stream().anyMatch(anonymizationResult -> anonymizationResult.getAbsolutePath().endsWith(entryName));
    }
}
