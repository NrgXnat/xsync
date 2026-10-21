/**
 * Authenticates the two accounts the suite needs, then confirms the target
 * instance actually carries the code under test.
 *
 * The capability probe fails the run rather than skipping it. Every spec here
 * exercises endpoints introduced in xsyncPlugin 1.8.2. Against 1.8.1 those
 * paths return 404, and a suite that quietly skipped would report green while
 * covering nothing.
 */
import { test as setup, expect, request as playwrightRequest } from '@playwright/test';
import { login } from '../lib/auth';

const ADMIN_USER = process.env.ADMIN_USER || 'admin';
const ADMIN_PASS = process.env.ADMIN_PASS || 'admin';
const NON_ADMIN_USER = process.env.NON_ADMIN_USER || 'testuser';
const NON_ADMIN_PASS = process.env.NON_ADMIN_PASS || 'testuser123';

setup('authenticate as admin', async ({ page }) => {
    await login(page, ADMIN_USER, ADMIN_PASS, 'admin-csrf.txt');
    await page.context().storageState({ path: '.auth/admin.json' });
});

setup('authenticate as non-admin', async ({ page }) => {
    await login(page, NON_ADMIN_USER, NON_ADMIN_PASS, 'nonadmin-csrf.txt');
    await page.context().storageState({ path: '.auth/nonadmin.json' });
});

setup('target instance carries the XSync features under test', async ({ baseURL }) => {
    const ctx = await playwrightRequest.newContext({
        baseURL,
        storageState: '.auth/admin.json',
        ignoreHTTPSErrors: true,
    });

    const pluginRes = await ctx.get('/xapi/plugins/xsyncPlugin');
    expect(pluginRes.ok(), 'xsyncPlugin is not installed on this instance').toBeTruthy();
    const version = (await pluginRes.json()).version;
    console.log(`[XSYNC-E2E] xsyncPlugin version reported by the server: ${version}`);

    // One endpoint from each feature area, so a partial deployment is caught
    // here rather than surfacing as an unrelated-looking failure later.
    const probes: Array<[string, string]> = [
        ['/xapi/xsync/dashboard', 'configuration dashboard (PLUGINS-230)'],
        ['/xapi/xsyncSitePreferences/whitelistSites', 'site whitelist (PLUGINS-228)'],
        ['/xapi/xsyncSitePreferences/blacklistProjects', 'project blacklist (PLUGINS-231)'],
        ['/xapi/xsyncSitePreferences/asperaEnabled', 'connection management (PLUGINS-234)'],
    ];

    const missing: string[] = [];
    for (const [path, feature] of probes) {
        const res = await ctx.get(path);
        if (res.status() === 404) missing.push(`${feature} -> ${path}`);
    }
    await ctx.dispose();

    expect(
        missing,
        `This instance is running xsyncPlugin ${version}, which does not expose:\n  ` +
            missing.join('\n  ') +
            '\nThese tests require 1.8.2-SNAPSHOT or later. Deploy a build from the ' +
            'develop branch before running the suite.',
    ).toEqual([]);
});
