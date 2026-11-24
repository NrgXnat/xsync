/**
 * Connection Management tab.
 *
 * Covers PLUGINS-234 (site admins choose which transfer types are available)
 * and PLUGINS-235 (Aspera disappears from the UI when it is not enabled).
 *
 * The Aspera switch drives the site-wide transport-enablement gate the resolver
 * consults (.../xsyncSitePreferences/transportEnabled/aspera). HTTPS is the
 * always-on baseline and has no toggle: there is nothing to lock, so the tests
 * only verify Aspera's enable state and the visibility of the Aspera Server
 * Defaults tab.
 */
import { test, expect } from '@playwright/test';
import { XsyncApi } from '../lib/api';
import { projectId } from '../lib/run';
import {
    CONNECTION_TAB_READY,
    expandProjectXsyncSection,
    gotoPluginSettings,
    gotoProjectXsyncTab,
    openXmodal,
    openXsyncTab,
    setSwitchbox,
    switchbox,
    xsyncTabLink,
    XSYNC_TABS,
} from '../lib/pages';

const ASPERA_PROJECT = projectId('xsync_e2e_aspera');

test.describe('@admin XSync connection management', () => {
    let api: XsyncApi;
    let originalAspera: boolean;

    test.beforeAll(async ({ baseURL }) => {
        api = await XsyncApi.create('admin-csrf.txt', '.auth/admin.json', baseURL!);
        originalAspera = await api.isTransportEnabled('aspera');
        // Start from the documented default so the first assertion is not at
        // the mercy of whatever the previous run left behind.
        await api.setTransportEnabled('aspera', false);
    });

    test.afterAll(async () => {
        await api.setTransportEnabled('aspera', originalAspera);
        await api.deleteProject(ASPERA_PROJECT);
        await api.dispose();
    });

    test('Aspera settings are hidden while Aspera is disabled', async ({ page }) => {
        await gotoPluginSettings(page);
        await openXsyncTab(page, XSYNC_TABS.connectionManagement, CONNECTION_TAB_READY);

        await expect(switchbox(page, 'aspera-enabled')).not.toBeChecked();

        // PLUGINS-235: the Aspera Server Defaults tab is hidden, not merely empty.
        await expect(xsyncTabLink(page, XSYNC_TABS.asperaDefaults)).toBeHidden();
    });

    test('enabling Aspera reveals the Aspera Server Defaults tab', async ({ page }) => {
        await gotoPluginSettings(page);
        await openXsyncTab(page, XSYNC_TABS.connectionManagement, CONNECTION_TAB_READY);

        await setSwitchbox(page, 'aspera-enabled', true);

        await expect(xsyncTabLink(page, XSYNC_TABS.asperaDefaults)).toBeVisible();
        await expect.poll(() => api.isTransportEnabled('aspera')).toBe(true);
    });

    test('disabling Aspera hides the Aspera tab again and leaves HTTPS enabled', async ({ page }) => {
        await api.setTransportEnabled('aspera', true);

        await gotoPluginSettings(page);
        await openXsyncTab(page, XSYNC_TABS.connectionManagement, CONNECTION_TAB_READY);
        await setSwitchbox(page, 'aspera-enabled', false);

        await expect(xsyncTabLink(page, XSYNC_TABS.asperaDefaults)).toBeHidden();

        await expect.poll(() => api.isTransportEnabled('aspera')).toBe(false);
        // HTTPS is the always-on baseline, so the site can still transfer even
        // with every optional transport disabled.
        expect(await api.isTransportEnabled('https')).toBe(true);
    });

    test('the project XSync panel carries no Aspera notice while Aspera is disabled', async ({ page }) => {
        await api.setTransportEnabled('aspera', false);
        await api.ensureProject(ASPERA_PROJECT);

        await gotoProjectXsyncTab(page, ASPERA_PROJECT);

        // The notice, when Aspera is on, renders inside the configuration
        // dialog. Open that dialog (Begin Configuration, since this project
        // has no config yet) so the absence check inspects the place the
        // notice would actually appear.
        await expandProjectXsyncSection(page);
        await page.locator('#xsync-begin-config').click();
        // The config editor is an xmodal, not an XNAT.ui.dialog.
        const dialog = openXmodal(page);
        await dialog.waitFor({ state: 'visible', timeout: 15_000 });
        await dialog.locator('#site_select_menu, #xsync-config-remote-url').first()
            .waitFor({ state: 'visible', timeout: 15_000 });
        await expect(dialog.getByText(/NOTICE: Aspera transfers are now supported/i)).toHaveCount(0);
    });
});
