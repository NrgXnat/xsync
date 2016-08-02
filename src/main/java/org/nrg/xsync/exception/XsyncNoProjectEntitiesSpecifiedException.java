package org.nrg.xsync.exception;

public class XsyncNoProjectEntitiesSpecifiedException extends Exception {
    public XsyncNoProjectEntitiesSpecifiedException(final String projectId) {
        _projectId = projectId;
    }

    public String getProjectId() {
        return _projectId;
    }

    private final String _projectId;
}
