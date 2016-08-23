package org.nrg.xsync.configuration;

import org.nrg.prefs.annotations.NrgPreference;
import org.nrg.prefs.annotations.NrgPreferenceBean;
import org.nrg.prefs.beans.AbstractPreferenceBean;
import org.nrg.prefs.exceptions.InvalidPreferenceName;
import org.nrg.xft.exception.InvalidValueException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Class XsyncSitePreferencesBean.
 * @author Mike Hodge
 */
@NrgPreferenceBean(toolId = XsyncSitePreferencesBean.XSYNC_TOOL_ID, toolName = "XSync Site Preferences")
public class XsyncSitePreferencesBean extends AbstractPreferenceBean {

    /** The Constant _log. */
    private static final Logger _log = LoggerFactory.getLogger(XsyncSitePreferencesBean.class);
    
    /** The Constant XSYNC_TOOL_ID. */
    public static final String XSYNC_TOOL_ID = "xsync";
	
	/** The Constant DEFAULT_TOKEN_REFRESH_INTERVAL. */
	public static final String DEFAULT_TOKEN_REFRESH_INTERVAL = "10 hours";
	
	/** The Constant DEFAULT_TOKEN_REFRESH_INTERVAL_MILLIS. */
	public static final long DEFAULT_TOKEN_REFRESH_INTERVAL_MILLIS = 10*60*60*1000;

	private static final String DEFAULT_SYNC_RETRY_INTERVAL = "2 hours";

	private static final String DEFAULT_SYNC_RETRY_COUNT = "2";
    
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
            _log.error("Invalid preference name");
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
			_log.info("XSync - Invalid token refresh interval specified - " + getValue("tokenRefreshInterval") + ".  Using default.");
			try {
				return calculateIntervalInMillis(DEFAULT_TOKEN_REFRESH_INTERVAL);
			} catch (InvalidValueException e1) {
				return Long.valueOf(1000*60*60*10);
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
			_log.error("Invalid preference name");
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
			_log.info("XSync - Invalid sync refresh interval specified - " + getValue("syncRetryInterval") + ".  Using default.");
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
			_log.error("Invalid preference name");
		}
	}

	/**
	 * Gets the sync retry interval in millis.
	 *
	 * @return the sync retry interval in millis
	 */
	public int getSyncRetryCountInt() {
		try {
			return Integer.parseInt(getValue("syncRetryInterval"));
		} catch (Exception e) {
			_log.info("XSync - Invalid sync refresh count specified - " + getValue("syncRetryInterval") + ".  Using default.");
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
				final long intervalNum = Long.valueOf(intervalArr[0]);
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
			}
			throw new InvalidValueException("XSync - Invalid interval specified - " + intervalStr);
	}

}
