package org.nrg.xsync.exception;

import lombok.Getter;
import lombok.experimental.Accessors;

@Getter
@Accessors(prefix = "_")
public class XsyncInvalidFilterType extends XsyncConfigurationException {
    private static final long serialVersionUID = 4969582667901805524L;

    private final String _filterType;

    public XsyncInvalidFilterType(final String filterType) {
        super("Invalid filter type found: " + filterType);
        _filterType = filterType;
    }
}
