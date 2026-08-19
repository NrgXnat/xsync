/**
 * Configuration Dashboard tab.
 *
 * Covers PLUGINS-230 (the dashboard itself), PLUGINS-312 (full connection
 * history) and PLUGINS-314 (disabling a single project sync or every sync to a
 * remote url).
 *
 * PLUGINS-313, the stack trace popup on a failed row, is not covered here. It
 * needs a connection that has actually failed a sync, which means a reachable
 * second XNAT and a real transfer. Flagged rather than faked.
 */
import { test, expect } from '@playwright/test';
import { XsyncApi, WhitelistSite } from '../lib/api';
import { fakeRemoteUrl, projectId } from '../lib/run';
import {
    columnLabels,
    dashboardTable,
    DASHBOARD_PANEL,
    clickDialogButton,
    gotoPluginSettings,
    openDialog,
    openXmodal,
    openXsyncTab,
    rowContaining,
    XSYNC_TABS,
} from '../lib/pages';

const PROJECT_A = projectId('xsync_e2e_dash_a');
const PROJECT_B = projectId('xsync_e2e_dash_b');
// The remote side of a config is just an id on the destination server, which
// setup never contacts, so no project is created for it.
const REMOTE_PROJECT = 'xsync_e2e_dash_dest';
// A per-run destination url. Assertions scoped to this url cannot be polluted
// by configs orphaned when earlier runs deleted their projects; url-wide
// enable also 500s when any config's source project is gone, so a shared url
// would break the PLUGINS-314 tests forever after one run.
const REMOTE_URL = fakeRemoteUrl('dash');
const DASH_SITE: WhitelistSite = {
    siteId: projectId('xsync_e2e_dash_site'),
    siteName: 'XSync E2E Dashboard Site',
    siteUrl: REMOTE_URL,
    classification: 'RESEARCH',
};

test.describe('@admin XSync configuration dashboard', () => {
    let api: XsyncApi;
    let originalWhitelistEnabled: boolean;

    test.beforeAll(async ({ baseURL }) => {
        api = await XsyncApi.create('admin-csrf.txt', '.auth/admin.json', baseURL!);
        originalWhitelistEnabled = (await api.getSitePreferences()).xsyncWhitelistEnabled ?? false;

        await api.ensureProject(PROJECT_A);
        await api.ensureProject(PROJECT_B);

        // Two local projects configured to sync to the same destination, so
        // the dashboard has one remote-url row with two project connections.
        await api.setSitePreferences({ xsyncWhitelistEnabled: false });
        await api.setupProjectSync(PROJECT_A, REMOTE_URL, REMOTE_PROJECT);
        await api.setupProjectSync(PROJECT_B, REMOTE_URL, REMOTE_PROJECT);
    });

    test.afterAll(async () => {
        // Best-effort: a failed step must not stop the settings restore or
        // the project deletions behind it.
        await api.setRemoteUrlEnabled(REMOTE_URL, true).catch(() => {});
        await api.deleteWhitelistSite(DASH_SITE);
        await api.setSitePreferences({ xsyncWhitelistEnabled: originalWhitelistEnabled });
        await api.deleteProject(PROJECT_A);
        await api.deleteProject(PROJECT_B);
        await api.dispose();
    });

    test('the dashboard lists each remote url with its project and error counts', async ({ page }) => {
        await api.setSitePreferences({ xsyncWhitelistEnabled: false });
        await gotoPluginSettings(page);
        await openXsyncTab(page, XSYNC_TABS.configurationDashboard, DASHBOARD_PANEL);

        const row = rowContaining(dashboardTable(page), REMOTE_URL);
        await expect(row).toBeVisible();

        const details = (await api.getDashboard()).find(d => d.remoteUrl === REMOTE_URL);
        expect(details, `${REMOTE_URL} missing from the dashboard`).toBeDefined();
        expect(details!.numberProjects).toBeGreaterThanOrEqual(2);
        await expect(row).toContainText(String(details!.numberProjects));
    });

    test('site name and security tier appear only while the whitelist is enabled', async ({ page }) => {
        await api.setSitePreferences({ xsyncWhitelistEnabled: false });
        await gotoPluginSettings(page);
        await openXsyncTab(page, XSYNC_TABS.configurationDashboard, DASHBOARD_PANEL);

        let labels = await columnLabels(dashboardTable(page));
        expect(labels).toContain('Remote Url');
        expect(labels).not.toContain('Remote Site');
        expect(labels).not.toContain('Security Tier');

        // Those two columns are only meaningful once destinations are
        // classified, which is what enabling the whitelist provides. Register
        // this run's destination with a known name and tier, then confirm the
        // dashboard resolves exactly that entry for the row.
        await api.addWhitelistSite(DASH_SITE);
        await api.setSitePreferences({ xsyncWhitelistEnabled: true });

        await gotoPluginSettings(page);
        await openXsyncTab(page, XSYNC_TABS.configurationDashboard, DASHBOARD_PANEL);

        labels = await columnLabels(dashboardTable(page));
        expect(labels).toContain('Remote Site');
        expect(labels).toContain('Security Tier');

        const row = rowContaining(dashboardTable(page), REMOTE_URL);
        await expect(row).toContainText(DASH_SITE.siteName);
        await expect(row).toContainText(DASH_SITE.classification);
    });

    test('the project count opens a per-project breakdown for that remote url', async ({ page }) => {
        await api.setSitePreferences({ xsyncWhitelistEnabled: false });
        await gotoPluginSettings(page);
        await openXsyncTab(page, XSYNC_TABS.configurationDashboard, DASHBOARD_PANEL);

        await rowContaining(dashboardTable(page), REMOTE_URL).locator('a').first().click();

        const details = openDialog(page);
        await details.waitFor({ state: 'visible', timeout: 15_000 });
        await expect(details).toContainText(`Details for ${REMOTE_URL}`);

        expect(await columnLabels(details.locator('table.xsync-configuration-table'))).toEqual(
            expect.arrayContaining(['Local Project', 'Remote Project', 'Enabled', 'Frequency', 'Last Sync Status', 'Actions']),
        );
        await expect(details).toContainText(PROJECT_A);
        await expect(details).toContainText(PROJECT_B);

        await clickDialogButton(details, 'Close');
    });

    test('PLUGINS-314: disabling a remote url disables every connection to it', async ({ page }) => {
        await api.setSitePreferences({ xsyncWhitelistEnabled: false });
        await api.setRemoteUrlEnabled(REMOTE_URL, true);

        await gotoPluginSettings(page);
        await openXsyncTab(page, XSYNC_TABS.configurationDashboard, DASHBOARD_PANEL);

        await rowContaining(dashboardTable(page), REMOTE_URL).locator('button', { hasText: 'Disable' }).click();

        const confirm = openXmodal(page);
        await confirm.waitFor({ state: 'visible' });
        await expect(confirm).toContainText(/disable all connections/i);
        await confirm.locator('button', { hasText: /^\s*OK\s*$|^\s*Yes\s*$|Proceed/i }).first().click();

        await expect.poll(async () => {
            const configs = await api.getConfigurationsForRemoteUrl(REMOTE_URL);
            return configs.map((c: any) => String(c.status));
        }, { message: 'every connection to this url should be disabled' }).not.toContain('true');

        // The button flips to the inverse action once the url is disabled.
        await expect(rowContaining(dashboardTable(page), REMOTE_URL).locator('button', { hasText: 'Enable' })).toBeVisible();
    });

    test('PLUGINS-314: a single project connection can be toggled from the breakdown', async ({ page }) => {
        await api.setSitePreferences({ xsyncWhitelistEnabled: false });
        await api.setRemoteUrlEnabled(REMOTE_URL, true);

        await gotoPluginSettings(page);
        await openXsyncTab(page, XSYNC_TABS.configurationDashboard, DASHBOARD_PANEL);
        await rowContaining(dashboardTable(page), REMOTE_URL).locator('a').first().click();

        const details = openDialog(page);
        await details.waitFor({ state: 'visible', timeout: 15_000 });

        // The enabled column is an XNAT switchbox: the checkbox itself is
        // display:none, so read state from the input but click the visible
        // toggle beside it.
        const projectRow = details.locator('tbody tr').filter({ hasText: PROJECT_A });
        await expect(projectRow.locator('input[type="checkbox"]').first()).toBeChecked();
        await projectRow.locator('span.switchbox-outer').first().click();

        await expect.poll(async () => {
            const config = (await api.getConfigurationsForRemoteUrl(REMOTE_URL))
                .find((c: any) => c.localProject === PROJECT_A);
            return config ? String(config.status) : 'missing';
        }, { message: `${PROJECT_A} should be disabled and ${PROJECT_B} untouched` }).toBe('false');

        const untouched = (await api.getConfigurationsForRemoteUrl(REMOTE_URL))
            .find((c: any) => c.localProject === PROJECT_B);
        expect(String(untouched.status)).toBe('true');
    });

    test('PLUGINS-312: a connection exposes both its recent and its full history', async ({ page }) => {
        await api.setSitePreferences({ xsyncWhitelistEnabled: false });
        await gotoPluginSettings(page);
        await openXsyncTab(page, XSYNC_TABS.configurationDashboard, DASHBOARD_PANEL);
        await rowContaining(dashboardTable(page), REMOTE_URL).locator('a').first().click();

        const details = openDialog(page);
        await details.waitFor({ state: 'visible', timeout: 15_000 });
        await details.locator('tbody tr').filter({ hasText: PROJECT_A })
            .locator('button', { hasText: 'History' }).click();

        const recent = openDialog(page);
        await recent.waitFor({ state: 'visible', timeout: 15_000 });
        await expect(recent).toContainText(new RegExp(`History for project ${PROJECT_A} within the last month`, 'i'));

        await clickDialogButton(recent, 'Full History');

        const full = openDialog(page);
        await full.waitFor({ state: 'visible', timeout: 15_000 });
        await expect(full).toContainText(new RegExp(`Full history for project: ${PROJECT_A}`, 'i'));
    });
});
