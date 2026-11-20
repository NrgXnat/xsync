package org.nrg.xsync.components;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.nrg.framework.configuration.ConfigPaths;
import org.nrg.framework.services.SerializerService;
import org.nrg.prefs.annotations.NrgPreference;
import org.nrg.prefs.annotations.NrgPreferenceBean;
import org.nrg.prefs.beans.AbstractPreferenceBean;
import org.nrg.prefs.exceptions.InvalidPreferenceName;
import org.nrg.prefs.services.NrgPreferenceService;
import org.nrg.xft.exception.InvalidValueException;
import org.nrg.xsync.pojo.WhitelistSitePojo;
import org.nrg.xsync.pojo.XsyncSitePreferencesPojo;
import org.nrg.xsync.services.local.WhitelistXsyncSiteService;
import org.nrg.xsync.transport.SiteTransportPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Class XsyncSitePreferencesBean.
 * @author Mike Hodge
 */
@Component
@NrgPreferenceBean(toolId = XsyncSitePreferencesBean.XSYNC_TOOL_ID, toolName = "XSync Site Preferences")
public class XsyncSitePreferencesBean extends AbstractPreferenceBean implements SiteTransportPolicy {
	@Autowired
	public XsyncSitePreferencesBean(final NrgPreferenceService preferenceService, final ConfigPaths configFolderPaths, SerializerService serializerService, WhitelistXsyncSiteService whitelistXsyncSitesService) {
		super(preferenceService, configFolderPaths);
        this.serializerService = serializerService;
        this.whitelistXsyncSitesService = whitelistXsyncSitesService;
		addInitialWhitelistSitesToPreferences();
	}

	/** The Constant _log. */
    private static final Logger _log = LoggerFactory.getLogger(XsyncSitePreferencesBean.class);

    /** The Constant XSYNC_TOOL_ID. */
    static final String XSYNC_TOOL_ID = "xsync";
	
	/** The Constant DEFAULT_TOKEN_REFRESH_INTERVAL. */
	private static final String DEFAULT_TOKEN_REFRESH_INTERVAL = "10 hours";
	
	/** The Constant DEFAULT_TOKEN_REFRESH_INTERVAL_MILLIS. 10 Hours */
	public static final long DEFAULT_TOKEN_REFRESH_INTERVAL_MILLIS = 10*60*60*1000;

	private static final String DEFAULT_SYNC_RETRY_INTERVAL = "2 hours";

	private static final String DEFAULT_SYNC_RETRY_COUNT = "2";

	private static final String DEFAULT_SYNC_MAX_UNCOMPRESSED_FILESIZE = "-1" ; //All files no limits

	final SerializerService serializerService;
	final WhitelistXsyncSiteService whitelistXsyncSitesService;

	public XsyncSitePreferencesBean(final NrgPreferenceService preferenceService, SerializerService serializerService, WhitelistXsyncSiteService whitelistXsyncSitesService) {
		super(preferenceService);
        this.serializerService = serializerService;
        this.whitelistXsyncSitesService = whitelistXsyncSitesService;
		addInitialWhitelistSitesToPreferences();
    }
    
    /**
     * Gets the token refresh interval.
     *
     * @return the token refresh interval
     */
    @NrgPreference(defaultValue = DEFAULT_TOKEN_REFRESH_INTERVAL)
    public String getTokenRefreshInterval() {
        return getValue("tokenRefreshInterval");
    }

	@NrgPreference(defaultValue = DEFAULT_SYNC_RETRY_INTERVAL)
	public String getSyncRetryInterval() {
		return getValue("syncRetryInterval");
	}

	@NrgPreference(defaultValue = DEFAULT_SYNC_RETRY_COUNT)
	public String getSyncRetryCount() {
		return getValue("syncRetryCount");
	}

	@NrgPreference(defaultValue = DEFAULT_SYNC_MAX_UNCOMPRESSED_FILESIZE)
	public String getSyncMaxUncompressedZipFileSize() {
		return getValue("syncMaxUncompressedZipFileSize");
	}

	@NrgPreference(defaultValue = "false")
	public boolean getXsyncWhitelistEnabled() {
		return getBooleanValue("xsyncWhitelistEnabled");
	}

	public void setXsyncWhitelistEnabled(boolean xsyncWhitelistEnabled) {
		try {
			set(String.valueOf(xsyncWhitelistEnabled), "xsyncWhitelistEnabled");
		} catch (InvalidPreferenceName invalidPreferenceName) {
			_log.error("Invalid preference name: xsyncWhitelistEnabled");
		}
	}

	@NrgPreference(defaultValue = "[]")
	public List<String> getSitesBlacklist() {
		return getListValue("sitesBlacklist");
	}

	public void setSitesBlacklist(final List<String> sitesBlacklist) {
		try {
			setListValue("sitesBlacklist", sitesBlacklist);
		} catch (InvalidPreferenceName e) {
			_log.error("Invalid preference name: 'sitesBlacklist'");
		}
	}

	@NrgPreference(defaultValue = "[]")
	public List<String> getProjectBlacklist() {
		return getListValue("projectBlacklist");
	}

	public void setProjectBlacklist(final List<String> projectBlacklist) {
		try {
			setListValue("projectBlacklist", projectBlacklist);
		} catch (InvalidPreferenceName e) {
			_log.error("Invalid preference name: 'projectBlacklist'");
		}
	}

	@NrgPreference(defaultValue = "true")
	public boolean getHttpsEnabled() {
		return getBooleanValue("httpsEnabled");
	}

	public void setHttpsEnabled(boolean httpsEnabled) {
		try {
			set(String.valueOf(httpsEnabled), "httpsEnabled");
		} catch (InvalidPreferenceName invalidPreferenceName) {
			_log.error("Invalid preference name: httpsEnabled");
		}
	}

	@NrgPreference(defaultValue = "false")
	public boolean getAsperaEnabled() {
		return getBooleanValue("asperaEnabled");
	}

	public void setAsperaEnabled(boolean asperaEnabled) {
		try {
			set(String.valueOf(asperaEnabled), "asperaEnabled");
		} catch (InvalidPreferenceName invalidPreferenceName) {
			_log.error("Invalid preference name: asperaEnabled");
		}
	}

	/**
	 * The transport ids an admin has explicitly disabled site-wide, as a JSON
	 * array. Backing store for {@link #isTransportEnabled(String)}; enablement is
	 * opt-out, so the default (empty array) means every transport is enabled.
	 *
	 * @return the disabled transport ids as a JSON array string
	 */
	@NrgPreference(defaultValue = "[]")
	public String getDisabledTransports() {
		return getValue("disabledTransports");
	}

	public void setDisabledTransports(final String disabledTransportsJson) {
		try {
			set(disabledTransportsJson, "disabledTransports");
		} catch (InvalidPreferenceName invalidPreferenceName) {
			_log.error("Invalid preference name: disabledTransports");
		}
	}

	@Override
	public boolean isTransportEnabled(final String transportId) {
		return !disabledTransportIds().contains(transportId);
	}

	/**
	 * Enable or disable a transport site-wide (opt-out: absence from the disabled
	 * set means enabled).
	 *
	 * @param transportId the transport id (see {@code XarSender.transportId()})
	 * @param enabled     {@code true} to enable, {@code false} to disable
	 */
	public void setTransportEnabled(final String transportId, final boolean enabled) {
		final List<String> disabled = new ArrayList<>(disabledTransportIds());
		if (enabled) {
			disabled.removeIf(id -> id.equals(transportId));
		} else if (!disabled.contains(transportId)) {
			disabled.add(transportId);
		}
		try {
			setDisabledTransports(serializerService.getObjectMapper().writeValueAsString(disabled));
		} catch (JsonProcessingException e) {
			_log.error("Could not serialize disabled transports", e);
		}
	}

	private List<String> disabledTransportIds() {
		final String json = getDisabledTransports();
		if (json == null || json.isBlank()) {
			return Collections.emptyList();
		}
		try {
			return serializerService.getObjectMapper().readValue(json, new TypeReference<List<String>>() {});
		} catch (JsonProcessingException e) {
			_log.error("Could not parse disabled transports '{}'; treating all transports as enabled.", json, e);
			return Collections.emptyList();
		}
	}

	/**
	 * Sets the Max. Total Uncompressed File Size
	 *
	 * @param syncMaxUncompressedZipFileSize the max total file size before compression
	 * @throws InvalidValueException the invalid value exception
	 */
	public void setSyncMaxUncompressedZipFileSize(final String syncMaxUncompressedZipFileSize) throws InvalidValueException {
		throwForInvalidMaxSizePreference(syncMaxUncompressedZipFileSize);
		try {
			set(syncMaxUncompressedZipFileSize,"syncMaxUncompressedZipFileSize");
		} catch (InvalidPreferenceName invalidPreferenceName) {
			_log.error("Invalid preference name: syncMaxUncompressedZipFileSize");
		}
	}

	private void throwForInvalidMaxSizePreference(String syncMaxUncompressedZipFileSize) throws InvalidValueException {
		final String validationMessage = "syncMaxUncompressedZipFileSize must be -1 or a positive integer";
		final long size;
		try {
			size = Long.parseLong(syncMaxUncompressedZipFileSize);
		} catch (NumberFormatException e) {
			throw new InvalidValueException(validationMessage);
		}
		if (size < -1 || size == 0) {
			throw new InvalidValueException(validationMessage);
		}
	}

	public long getSyncMaxUncompressedZipFileSizeAsLong() {
		return Long.parseLong(getSyncMaxUncompressedZipFileSize());
	}

	/**
     * Sets the token refresh interval.
     *
     * @param tokenRefreshInterval the new token refresh interval
     * @throws InvalidValueException the invalid value exception
     */
    public void setTokenRefreshInterval(final String tokenRefreshInterval) throws InvalidValueException {
        try {
        	// Check value
       		calculateIntervalInMillis(tokenRefreshInterval);
       		set(tokenRefreshInterval,"tokenRefreshInterval");
        } catch (InvalidPreferenceName invalidPreferenceName) {
            _log.error("Invalid preference name: tokenRefreshInterval");
        }
    }

	/**
	 * Gets the token refresh interval in millis.
	 *
	 * @return the token refresh interval in millis
	 */
	public long getTokenRefreshIntervalInMillis() {
		try {
			return calculateIntervalInMillis(getValue("tokenRefreshInterval"));
		} catch (InvalidValueException e) {
            _log.info("XSync - Invalid token refresh interval specified - {}.  Using default.", getValue("tokenRefreshInterval"));
			try {
				return calculateIntervalInMillis(DEFAULT_TOKEN_REFRESH_INTERVAL);
			} catch (InvalidValueException e1) {
				return 1000 * 60 * 60 * 10;
			}
		}
	}

	/**
	 * Sets the sync retry interval.
	 *
	 * @param syncRetryInterval the new sync retry interval
	 * @throws InvalidValueException the invalid value exception
	 */
	public void setSyncRetryInterval(final String syncRetryInterval) throws InvalidValueException {
		try {
			calculateIntervalInMillis(syncRetryInterval);
			set(syncRetryInterval,"syncRetryInterval");
		} catch (InvalidPreferenceName invalidPreferenceName) {
			_log.error("Invalid preference name: syncRetryInterval");
		}
	}

	/**
	 * Gets the sync retry interval in millis.
	 *
	 * @return the sync retry interval in millis
	 */
	public long getSyncRetryIntervalInMillis() {
		try {
			return calculateIntervalInMillis(getValue("syncRetryInterval"));
		} catch (InvalidValueException e) {
            _log.info("XSync - Invalid sync refresh interval specified - {}.  Using default.", getValue("syncRetryInterval"));
			try {
				return calculateIntervalInMillis(DEFAULT_SYNC_RETRY_INTERVAL);
			} catch (InvalidValueException e1) {
				return (long) (1000 * 60 * 60 * 10);
			}
		}
	}

	/**
	 * Sets the sync retry count.
	 *
	 * @param syncRetryCount the new sync retry count
	 * @throws InvalidValueException the invalid value exception
	 */
	public void setSyncRetryCount(final String syncRetryCount) throws InvalidValueException {
		try {
			set(syncRetryCount, "syncRetryCount");
		} catch (InvalidPreferenceName invalidPreferenceName) {
			_log.error("Invalid preference name: syncRetryCount");
		}
	}

	/**
	 * Gets the sync retry interval in millis.
	 *
	 * @return the sync retry interval in millis
	 */
	public int getSyncRetryCountInt() {
		try {
			return Integer.parseInt(getValue("syncRetryCount"));
		} catch (Exception e) {
            _log.info("XSync - Invalid sync refresh count specified - {}.  Using default.", getValue("syncRetryCount"));
			try {
				return Integer.parseInt(DEFAULT_SYNC_RETRY_COUNT);
			} catch (Exception e1) {
				return 2;
			}
		}
	}
	
	/**
	 * Calculate refresh interval in millis.
	 *
	 * @param intervalStr the interval str
	 * @return the long
	 * @throws InvalidValueException the invalid value exception
	 */
	private static long calculateIntervalInMillis(final String intervalStr) throws InvalidValueException {
		final String[] intervalArr = intervalStr.split("[\\s]+");
		final long minIntervalMilis = 300000;

		if (intervalArr.length==2) {
			final long intervalNum = Long.parseLong(intervalArr[0]);
			Long interval = null;
			if (intervalArr[1].toLowerCase().contains("hour")) {
				interval = intervalNum*1000*60*60;
			} else if (intervalArr[1].toLowerCase().contains("minute")) {
				interval = intervalNum*1000*60;
			}
			if (interval != null && interval>=minIntervalMilis) {
				return interval;
			} else {
				throw new InvalidValueException("XSync - Interval too short - Specify minimum of " + minIntervalMilis*1000*60 + " minutes.");
			}
		} else {
			throw new InvalidValueException("XSync - Invalid interval specified - " + intervalStr);
		}
	}
	
	private void addInitialWhitelistSitesToPreferences() {
		try {
			ClassPathResource resource = new ClassPathResource("META-INF/xnat/xsyncSiteWhitelist.json");
			JsonNode rootNode = serializerService.deserializeJson(resource.getInputStream());

			if (rootNode.has("allowedSites")) {
				List<WhitelistSitePojo> whitelistedSites = serializerService.getObjectMapper()
						.convertValue(rootNode.get("allowedSites"), new TypeReference<>() {});
				whitelistXsyncSitesService.addWhiteListSitesFromJson(whitelistedSites);
			} else {
				_log.info("Whitelist json does not have proper formatting.");
			}
		} catch (IOException e) {
			_log.info("No xsync whitelist json provided at startup.");
		}
	}

	public void update(final XsyncSitePreferencesPojo xsyncSitePreferencesPojo) throws InvalidValueException {
		if (null != xsyncSitePreferencesPojo.getSyncRetryCount()) {
			this.setSyncRetryCount(xsyncSitePreferencesPojo.getSyncRetryCount());
		}
		if (null != xsyncSitePreferencesPojo.getSyncRetryInterval()) {
			this.setSyncRetryInterval(xsyncSitePreferencesPojo.getSyncRetryInterval());
		}
		if (null != xsyncSitePreferencesPojo.getTokenRefreshInterval()) {
			this.setTokenRefreshInterval(xsyncSitePreferencesPojo.getTokenRefreshInterval());
		}
		if (null != xsyncSitePreferencesPojo.getSyncMaxUncompressedZipFileSize()) {
			this.setSyncMaxUncompressedZipFileSize(xsyncSitePreferencesPojo.getSyncMaxUncompressedZipFileSize());
		}
		if (null != xsyncSitePreferencesPojo.getXsyncWhitelistEnabled()) {
			this.setXsyncWhitelistEnabled(xsyncSitePreferencesPojo.getXsyncWhitelistEnabled());
		}
		if (null != xsyncSitePreferencesPojo.getHttpsEnabled()) {
			this.setHttpsEnabled(xsyncSitePreferencesPojo.getHttpsEnabled());
		}
		if (null != xsyncSitePreferencesPojo.getAsperaEnabled()) {
			this.setAsperaEnabled(xsyncSitePreferencesPojo.getAsperaEnabled());
		}
	}

	public XsyncSitePreferencesPojo toPojo() {
		return new XsyncSitePreferencesPojo(
				getTokenRefreshInterval(),
				getSyncRetryInterval(),
				getSyncRetryCount(),
				getSyncMaxUncompressedZipFileSize(),
				getXsyncWhitelistEnabled(),
				getHttpsEnabled(),
				getAsperaEnabled()
		);
	}
}
