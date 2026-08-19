/**
 * Whitelist enforcement on project sync configuration.
 *
 * Covers the enforcement half of PLUGINS-228 (a project may only be pointed at
 * an approved destination) and PLUGINS-310 (enabling the whitelist surfaces
 * connections that already violate it).
 *
 * Note on status codes: XsyncSetupController catches every failure and rethrows
 * a generic Exception, so a refused setup arrives as a 500 rather than a 400.
 * These tests assert the refusal and the resulting state, not the code.
 */
import { test, expect } from '@playwright/test';
import { XsyncApi, WhitelistSite } from '../lib/api';
import { fakeRemoteUrl, projectId } from '../lib/run';
import {
    clickDialogButton,
    gotoPluginSettings,
    openDialog,
    openXsyncTab,
    setSwitchbox,
    WHITELIST_TAB_READY,
    XSYNC_TABS,
} from '../lib/pages';

const LOCAL_PROJECT = projectId('xsync_e2e_wl_src');
// The remote side of a config is just an id on the destination server, which
// setup never contacts, so no project is created for it.
const REMOTE_PROJECT = 'xsync_e2e_wl_dest';
// Per run: configs orphaned by earlier runs' deleted projects stay on the
// dashboard, so an absence assertion against a reused url would fail.
const OFF_LIST_URL = fakeRemoteUrl('not-approved');

test.describe('@admin XSync whitelist enforcement', () => {
    let api: XsyncApi;
    let siteUrl: string;
    let originalWhitelistEnabled: boolean;
    let localSite: WhitelistSite;

    test.beforeAll(async ({ baseURL }) => {
        api = await XsyncApi.create('admin-csrf.txt', '.auth/admin.json', baseURL!);
        originalWhitelistEnabled = (await api.getSitePreferences()).xsyncWhitelistEnabled ?? false;
        siteUrl = await api.getSiteUrl();
        localSite = {
            siteId: 'xsync_e2e_local',
            siteName: 'This XNAT',
            siteUrl,
            classification: 'RESEARCH',
        };
        await api.ensureProject(LOCAL_PROJECT);
    });

    test.afterAll(async () => {
        await api.deleteWhitelistSite(localSite);
        await api.setSitePreferences({ xsyncWhitelistEnabled: originalWhitelistEnabled });
        await api.deleteProject(LOCAL_PROJECT);
        await api.dispose();
    });

    test('a destination that is not on the whitelist is refused', async () => {
        await api.setSitePreferences({ xsyncWhitelistEnabled: true });

        const res = await api.setupProjectSyncRaw(LOCAL_PROJECT, OFF_LIST_URL, REMOTE_PROJECT);
        expect(res.ok(), `setup to ${OFF_LIST_URL} should have been refused`).toBeFalsy();

        const dashboard = await api.getDashboard();
        expect(dashboard.map(d => d.remoteUrl)).not.toContain(OFF_LIST_URL);
    });

    test('a whitelisted destination is accepted', async () => {
        await api.setSitePreferences({ xsyncWhitelistEnabled: true });
        await api.addWhitelistSite(localSite);

        await api.setupProjectSync(LOCAL_PROJECT, siteUrl, REMOTE_PROJECT);

        const configs = await api.getConfigurationsForRemoteUrl(siteUrl);
        expect(configs.map((c: any) => c.localProject)).toContain(LOCAL_PROJECT);
    });

    test('with the whitelist off, any destination is accepted', async () => {
        await api.setSitePreferences({ xsyncWhitelistEnabled: false });

        await api.setupProjectSync(LOCAL_PROJECT, OFF_LIST_URL, REMOTE_PROJECT);

        await expect.poll(async () => (await api.getDashboard()).map(d => d.remoteUrl)).toContain(OFF_LIST_URL);
    });

    test('PLUGINS-310: an existing connection outside the whitelist is reported', async ({ page }) => {
        // Create the offending connection while the whitelist is off, which is
        // exactly how a site ends up with one before an admin turns it on.
        await api.setSitePreferences({ xsyncWhitelistEnabled: false });
        await api.setupProjectSync(LOCAL_PROJECT, OFF_LIST_URL, REMOTE_PROJECT);
        await api.addWhitelistSite(localSite);

        await api.setSitePreferences({ xsyncWhitelistEnabled: true });
        const nonConforming = await api.getNonConformingRemoteUrls();
        expect(nonConforming.map(d => d.remoteUrl)).toContain(OFF_LIST_URL);

        // The admin sees the same list as a report when they enable the whitelist.
        await api.setSitePreferences({ xsyncWhitelistEnabled: false });
        await gotoPluginSettings(page);
        await openXsyncTab(page, XSYNC_TABS.whitelist, WHITELIST_TAB_READY);
        await setSwitchbox(page, 'limit-to-whitelist', true);

        const report = openDialog(page);
        await report.waitFor({ state: 'visible', timeout: 15_000 });
        await expect(report).toContainText(/Remote urls not conforming to whitelist/i);
        await expect(report).toContainText(OFF_LIST_URL);
        await clickDialogButton(report, 'Close');
    });
});
