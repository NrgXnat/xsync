/**
 * Connection Management tab.
 *
 * Covers PLUGINS-234 (site admins choose which transfer types are available)
 * and PLUGINS-235 (Aspera disappears from the UI when it is not enabled).
 *
 * The rule under test, from xsyncConnectionManager.js: HTTPS is the default
 * transport and cannot be switched off while it is the only one enabled, so
 * the HTTPS control stays locked until Aspera is turned on, and turning Aspera
 * back off forces HTTPS on again.
 */
import { test, expect } from '@playwright/test';
import { XsyncApi } from '../lib/api';
import {
    CONNECTION_TAB_READY,
    gotoPluginSettings,
    gotoProjectXsyncTab,
    openXsyncTab,
    setSwitchbox,
    switchbox,
    xsyncTabLink,
    XSYNC_TABS,
} from '../lib/pages';

const ASPERA_PROJECT = 'xsync_e2e_aspera';

test.describe('@admin XSync connection management', () => {
    let api: XsyncApi;
    let originalAspera: boolean;
    let originalHttps: boolean;

    test.beforeAll(async ({ baseURL }) => {
        api = await XsyncApi.create('admin-csrf.txt', '.auth/admin.json', baseURL!);
        const prefs = await api.getSitePreferences();
        originalAspera = prefs.asperaEnabled ?? false;
        originalHttps = prefs.httpsEnabled ?? true;
        // Start from the documented default so the first assertion is not at
        // the mercy of whatever the previous run left behind.
        await api.setSitePreferences({ asperaEnabled: false, httpsEnabled: true });
    });

    test.afterAll(async () => {
        await api.setSitePreferences({ asperaEnabled: originalAspera, httpsEnabled: originalHttps });
        await api.deleteProject(ASPERA_PROJECT);
        await api.dispose();
    });

    test('HTTPS is locked on and Aspera settings are hidden while Aspera is disabled', async ({ page }) => {
        await gotoPluginSettings(page);
        await openXsyncTab(page, XSYNC_TABS.connectionManagement, CONNECTION_TAB_READY);

        await expect(switchbox(page, 'aspera-enabled')).not.toBeChecked();

        // HTTPS is the only enabled transport, so its control is disabled to
        // stop an admin leaving the site with no way to transfer anything.
        await expect(switchbox(page, 'https-enabled')).toBeDisabled();
        expect(await api.isHttpsEnabled()).toBe(true);

        // PLUGINS-235: the Aspera Server Defaults tab is hidden, not merely empty.
        await expect(xsyncTabLink(page, XSYNC_TABS.asperaDefaults)).toBeHidden();
    });

    test('enabling Aspera unlocks the HTTPS control and reveals the Aspera Server Defaults tab', async ({ page }) => {
        await gotoPluginSettings(page);
        await openXsyncTab(page, XSYNC_TABS.connectionManagement, CONNECTION_TAB_READY);

        await setSwitchbox(page, 'aspera-enabled', true);

        await expect(switchbox(page, 'https-enabled')).toBeEnabled();
        await expect(xsyncTabLink(page, XSYNC_TABS.asperaDefaults)).toBeVisible();
        await expect.poll(() => api.isAsperaEnabled()).toBe(true);
    });

    test('disabling Aspera forces HTTPS back on and hides the Aspera tab again', async ({ page }) => {
        await api.setSitePreferences({ asperaEnabled: true });

        await gotoPluginSettings(page);
        await openXsyncTab(page, XSYNC_TABS.connectionManagement, CONNECTION_TAB_READY);
        await setSwitchbox(page, 'aspera-enabled', false);

        await expect(switchbox(page, 'https-enabled')).toBeDisabled();
        await expect(xsyncTabLink(page, XSYNC_TABS.asperaDefaults)).toBeHidden();

        await expect.poll(() => api.isAsperaEnabled()).toBe(false);
        // Turning off the only alternative transport must leave HTTPS enabled,
        // otherwise the site would be left unable to sync at all.
        await expect.poll(() => api.isHttpsEnabled()).toBe(true);
    });

    test('the project XSync panel carries no Aspera notice while Aspera is disabled', async ({ page }) => {
        await api.setSitePreferences({ asperaEnabled: false });
        await api.ensureProject(ASPERA_PROJECT);

        await gotoProjectXsyncTab(page, ASPERA_PROJECT);

        // Assert the panel rendered before asserting something is missing from
        // it. Without this, a panel that failed to load would pass the
        // absence check while covering nothing.
        await expect(page.locator('#xsync_panel_header')).toBeVisible();
        await expect(page.getByText(/NOTICE: Aspera transfers are now supported/i)).toHaveCount(0);
    });
});
