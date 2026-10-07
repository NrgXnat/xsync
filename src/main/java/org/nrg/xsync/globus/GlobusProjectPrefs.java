package org.nrg.xsync.globus;

import org.nrg.framework.constants.Scope;
import org.nrg.prefs.annotations.NrgPreference;
import org.nrg.prefs.annotations.NrgPreferenceBean;
import org.nrg.prefs.beans.AbstractPreferenceBean;
import org.nrg.prefs.entities.Preference;
import org.nrg.prefs.exceptions.InvalidPreferenceName;
import org.nrg.prefs.services.NrgPreferenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Per-project Globus transfer settings, mirroring {@code AsperaProjectPrefs}.
 *
 * <p>Minimal configuration for {@link org.nrg.xsync.transport.GlobusXarSender}:
 * whether Globus is enabled, which registered {@link
 * org.nrg.xsync.globus.entities.GlobusEndpoint} to use (that endpoint supplies
 * the client credentials and the inbox/outbox guest-collection UUIDs), and the
 * paths a transfer needs.</p>
 *
 * <p>Two destination paths are needed because a transfer and the subsequent
 * import use different addressings: the <em>collection-relative</em> path is
 * where Globus writes within the destination inbox guest collection, while the
 * <em>server</em> path is the destination host's local filesystem path the
 * import-by-path call reads.</p>
 *
 * @author XSync
 */
@Component
@NrgPreferenceBean(toolId = "xsyncGlobusProject", toolName = "XSync Globus Project Preferences")
public class GlobusProjectPrefs extends AbstractPreferenceBean {

    public static final Scope SCOPE = Scope.Project;
    public static final String GLOBUS_ENABLED = "globusEnabled";
    public static final String GLOBUS_ENDPOINT_NAME = "globusEndpointName";

    @Autowired
    protected GlobusProjectPrefs(final NrgPreferenceService preferenceService) {
        super(preferenceService);
    }

    @NrgPreference
    public Boolean getGlobusEnabled() {
        return null;
    }

    /**
     * @param entityId the project id
     * @return whether Globus is enabled for the project
     */
    public Boolean getGlobusEnabled(final String entityId) {
        return this.getBooleanValue(SCOPE, entityId, GLOBUS_ENABLED);
    }

    public void setGlobusEnabled(final String entityId, final Boolean enabled) {
        try {
            removeSiteLevelPreferenceIfExists(GLOBUS_ENABLED);
            this.setBooleanValue(SCOPE, entityId, enabled, GLOBUS_ENABLED);
        } catch (InvalidPreferenceName e) {
            _logger.error("Invalid Globus preference name: {}", GLOBUS_ENABLED);
        }
    }

    @NrgPreference
    public String getGlobusEndpointName() {
        return null;
    }

    /**
     * @param entityId the project id
     * @return the name of the registered Globus endpoint to transfer with
     */
    public String getGlobusEndpointName(final String entityId) {
        return this.getValue(SCOPE, entityId, GLOBUS_ENDPOINT_NAME);
    }

    public void setGlobusEndpointName(final String entityId, final String endpointName) {
        try {
            removeSiteLevelPreferenceIfExists(GLOBUS_ENDPOINT_NAME);
            this.set(SCOPE, entityId, endpointName, GLOBUS_ENDPOINT_NAME);
        } catch (InvalidPreferenceName e) {
            _logger.error("Invalid Globus preference name: {}", GLOBUS_ENDPOINT_NAME);
        }
    }

    // Workaround (XNAT-5134): keep these project-scoped, never site-level.
    private void removeSiteLevelPreferenceIfExists(final String key) {
        try {
            if (sitePreferenceExists(key) != null) {
                this.delete(Scope.Site, "", key);
            }
        } catch (InvalidPreferenceName e) {
            // Do nothing.
        }
    }

    private Preference sitePreferenceExists(final String key) {
        final Preference p = this.getPreference(Scope.Site, "", key);
        if (p != null) {
            // Expected: the @NrgPreference framework auto-creates a site-level
            // default for each project-scoped key; removeSiteLevelPreferenceIfExists
            // deletes it. This is the XNAT-5134 workaround path, not an error.
            _logger.debug("Removing stray site-level default for project preference (KEY={}).", key);
        }
        return p;
    }

    private static final Logger _logger = LoggerFactory.getLogger(GlobusProjectPrefs.class);
}
