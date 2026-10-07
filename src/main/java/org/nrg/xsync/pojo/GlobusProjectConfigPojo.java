package org.nrg.xsync.pojo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * API carrier for a project's Globus transfer configuration
 * ({@link org.nrg.xsync.globus.GlobusProjectPrefs}).
 *
 * @author XSync
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GlobusProjectConfigPojo {

    private Boolean globusEnabled;
    private String globusEndpointName;
}
