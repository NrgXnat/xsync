/**
 * XsyncAdministrator site role.
 *
 * Covers PLUGINS-232. The role, and not site-admin status, is what opens the
 * site-wide XSync settings, so a plain user gains access when granted it and
 * loses that access when it is revoked.
 *
 * Runs under the non-admin storage state. The admin REST context is created
 * separately, since granting a role is itself an admin action.
 */
import { test, expect } from '@playwright/test';
import { XsyncApi } from '../lib/api';
import { gotoPluginSettings, xsyncTabLink, XSYNC_TABS } from '../lib/pages';

const ROLE = 'XsyncAdministrator';
const NON_ADMIN_USER = process.env.NON_ADMIN_USER || 'testuser';
const ADMIN_USER = process.env.ADMIN_USER || 'admin';

test.describe('@nonadmin XSync administrator role', () => {
    let adminApi: XsyncApi;
    let userApi: XsyncApi;
    let hadRoleAlready: boolean;

    test.beforeAll(async ({ baseURL }) => {
        adminApi = await XsyncApi.create('admin-csrf.txt', '.auth/admin.json', baseURL!);
        userApi = await XsyncApi.create('nonadmin-csrf.txt', '.auth/nonadmin.json', baseURL!);
        hadRoleAlready = (await adminApi.getRoles(NON_ADMIN_USER)).includes(ROLE);
        await adminApi.removeRole(NON_ADMIN_USER, ROLE);
    });

    test.afterAll(async () => {
        if (hadRoleAlready) {
            await adminApi.addRole(NON_ADMIN_USER, ROLE);
        } else {
            await adminApi.removeRole(NON_ADMIN_USER, ROLE);
        }
        await adminApi.dispose();
        await userApi.dispose();
    });

    test('existing site admins hold the role after the plugin upgrade', async () => {
        // AssignXsyncAdministratorRoleToSiteAdminOnUpgrade grants it on first
        // startup of 1.8.2, mirroring how the Container Manager role rolled out.
        expect(await adminApi.getRoles(ADMIN_USER)).toContain(ROLE);
    });

    test('a user without the role cannot reach the site-wide XSync settings', async () => {
        const whitelist = await userApi.addWhitelistSiteRaw({
            siteId: 'xsync_e2e_unauthorised',
            siteName: 'Should Not Be Created',
            siteUrl: 'https://xsync-e2e-unauthorised.example.org',
            classification: 'RESEARCH',
        });
        expect(whitelist.status(), 'a plain user must not be able to edit the whitelist').toBe(403);

        const blacklist = await userApi.addProjectToBlacklistRaw('xsync_e2e_unauthorised');
        expect(blacklist.status(), 'a plain user must not be able to edit the project blacklist').toBe(403);
    });

    test('granting the role opens the site-wide XSync settings', async ({ page }) => {
        await adminApi.addRole(NON_ADMIN_USER, ROLE);

        const site = {
            siteId: 'xsync_e2e_role_site',
            siteName: 'XSync E2E Role Site',
            siteUrl: 'https://xsync-e2e-role.example.org',
            classification: 'RESEARCH',
        };
        try {
            const res = await userApi.addWhitelistSiteRaw(site);
            expect(res.status(), `${ROLE} should permit whitelist administration`).toBe(200);

            await gotoPluginSettings(page);
            await expect(xsyncTabLink(page, XSYNC_TABS.whitelist)).toBeVisible();
            await expect(xsyncTabLink(page, XSYNC_TABS.projectBlacklist)).toBeVisible();
        } finally {
            await adminApi.deleteWhitelistSite(site);
        }
    });

    test('revoking the role closes that access again', async () => {
        await adminApi.addRole(NON_ADMIN_USER, ROLE);
        await adminApi.removeRole(NON_ADMIN_USER, ROLE);

        const res = await userApi.addProjectToBlacklistRaw('xsync_e2e_revoked');
        expect(res.status(), 'access should end with the role').toBe(403);
    });
});
