/**
 * Project Blacklist tab.
 *
 * Covers PLUGINS-231. The site default is that any project may use XSync; a
 * project named on the blacklist loses the ability to create connections, has
 * its existing connections deactivated, and stops showing XSync in its UI.
 */
import { test, expect } from '@playwright/test';
import { XsyncApi } from '../lib/api';
import { fakeRemoteUrl, projectId } from '../lib/run';
import {
    blacklistTable,
    BLACKLIST_PANEL,
    clickDialogButton,
    gotoPluginSettings,
    gotoProjectXsyncTab,
    openDialog,
    openXsyncTab,
    rowContaining,
    XSYNC_TABS,
} from '../lib/pages';

const BLOCKED_PROJECT = projectId('xsync_e2e_blocked');
// The remote side of a config is just an id on the destination server, which
// setup never contacts, so no project is created for it.
const REMOTE_PROJECT = 'xsync_e2e_bl_dest';
// Per run, so configs orphaned by earlier runs cannot leak into assertions.
const REMOTE_URL = fakeRemoteUrl('bl');

test.describe('@admin XSync project blacklist', () => {
    let api: XsyncApi;
    let originalWhitelistEnabled: boolean;

    test.beforeAll(async ({ baseURL }) => {
        api = await XsyncApi.create('admin-csrf.txt', '.auth/admin.json', baseURL!);
        originalWhitelistEnabled = (await api.getSitePreferences()).xsyncWhitelistEnabled ?? false;
        // Enforcement of the blacklist is what is under test here, so keep the
        // whitelist out of the way.
        await api.setSitePreferences({ xsyncWhitelistEnabled: false });
        await api.ensureProject(BLOCKED_PROJECT);
    });

    test.beforeEach(async () => {
        await api.removeProjectFromBlacklist(BLOCKED_PROJECT);
    });

    test.afterAll(async () => {
        await api.removeProjectFromBlacklist(BLOCKED_PROJECT);
        await api.setSitePreferences({ xsyncWhitelistEnabled: originalWhitelistEnabled });
        await api.deleteProject(BLOCKED_PROJECT);
        await api.dispose();
    });

    test('a project can be added to the blacklist from the admin tab', async ({ page }) => {
        await gotoPluginSettings(page);
        await openXsyncTab(page, XSYNC_TABS.projectBlacklist, BLACKLIST_PANEL);

        await page.locator(`${BLACKLIST_PANEL} button`, { hasText: 'Add Project' }).click();

        const dialog = openDialog(page);
        await dialog.waitFor({ state: 'visible' });
        await dialog.locator('#projects_dropdown').selectOption(BLOCKED_PROJECT);
        await clickDialogButton(dialog, 'Add');

        await expect(rowContaining(blacklistTable(page), BLOCKED_PROJECT)).toBeVisible();
        expect(await api.isProjectBlacklisted(BLOCKED_PROJECT)).toBe(true);
    });

    test('a blacklisted project cannot create an XSync connection', async () => {
        await api.addProjectToBlacklist(BLOCKED_PROJECT);

        const res = await api.setupProjectSyncRaw(BLOCKED_PROJECT, REMOTE_URL, REMOTE_PROJECT);
        expect(res.ok(), 'setup for a blacklisted project should have been refused').toBeFalsy();

        const configs = await api.getConfigurationsForRemoteUrl(REMOTE_URL);
        expect(configs.map((c: any) => c.localProject)).not.toContain(BLOCKED_PROJECT);
    });

    test('blacklisting a project deactivates the connection it already had', async () => {
        await api.setupProjectSync(BLOCKED_PROJECT, REMOTE_URL, REMOTE_PROJECT);

        const before = (await api.getConfigurationsForRemoteUrl(REMOTE_URL))
            .find((c: any) => c.localProject === BLOCKED_PROJECT);
        expect(before, 'the connection under test was not created').toBeDefined();
        expect(String(before.status)).toBe('true');

        await api.addProjectToBlacklist(BLOCKED_PROJECT);

        await expect.poll(async () => {
            const after = (await api.getConfigurationsForRemoteUrl(REMOTE_URL))
                .find((c: any) => c.localProject === BLOCKED_PROJECT);
            return after ? String(after.status) : 'missing';
        }, { message: 'the existing connection should have been disabled' }).toBe('false');
    });

    test('a blacklisted project shows no XSync panel on its Manage page', async ({ page }) => {
        // Confirm the panel is there first, so the absence check below cannot
        // pass merely because the page failed to render.
        await gotoProjectXsyncTab(page, BLOCKED_PROJECT);
        await expect(page.locator('#xsync_panel_header')).toBeVisible();

        await api.addProjectToBlacklist(BLOCKED_PROJECT);

        await gotoProjectXsyncTab(page, BLOCKED_PROJECT);
        await expect(page.locator('#xsync_panel_header')).toHaveCount(0);
    });

    test('adding the same project twice is refused', async () => {
        await api.addProjectToBlacklist(BLOCKED_PROJECT);

        const res = await api.addProjectToBlacklistRaw(BLOCKED_PROJECT);
        expect(res.status(), 'a duplicate blacklist entry should be a client error').toBe(400);
        expect((await api.getBlacklistedProjects()).filter(p => p === BLOCKED_PROJECT)).toHaveLength(1);
    });

    test('removing a project from the blacklist restores XSync for it', async ({ page }) => {
        await api.addProjectToBlacklist(BLOCKED_PROJECT);

        await gotoPluginSettings(page);
        await openXsyncTab(page, XSYNC_TABS.projectBlacklist, BLACKLIST_PANEL);
        await rowContaining(blacklistTable(page), BLOCKED_PROJECT).locator('button', { hasText: 'Remove' }).click();

        await expect(rowContaining(blacklistTable(page), BLOCKED_PROJECT)).toHaveCount(0);
        expect(await api.isProjectBlacklisted(BLOCKED_PROJECT)).toBe(false);

        // The project can configure XSync again.
        await api.setupProjectSync(BLOCKED_PROJECT, REMOTE_URL, REMOTE_PROJECT);
        const configs = await api.getConfigurationsForRemoteUrl(REMOTE_URL);
        expect(configs.map((c: any) => c.localProject)).toContain(BLOCKED_PROJECT);
    });
});
