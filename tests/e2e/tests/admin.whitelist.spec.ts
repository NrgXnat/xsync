/**
 * Whitelist Of Accepted Receiving XNATs tab.
 *
 * Covers PLUGINS-228 (the whitelist itself), PLUGINS-229 (site classification)
 * and PLUGINS-332 (trailing slashes must not survive into a stored entry).
 */
import { test, expect } from '@playwright/test';
import { XsyncApi, WhitelistSite } from '../lib/api';
import {
    clickDialogButton,
    columnLabels,
    gotoPluginSettings,
    openDialog,
    openXmodal,
    openXsyncTab,
    rowContaining,
    setSwitchbox,
    whitelistTable,
    WHITELIST_PANEL,
    WHITELIST_TAB_READY,
    XSYNC_TABS,
} from '../lib/pages';

const SITE: WhitelistSite = {
    siteId: 'xsync_e2e_site',
    siteName: 'XSync E2E Research XNAT',
    siteUrl: 'https://xsync-e2e-research.example.org',
    classification: 'RESEARCH',
};

test.describe('@admin XSync destination whitelist', () => {
    let api: XsyncApi;
    let originalWhitelistEnabled: boolean;

    /**
     * Open the whitelist tab. The wait target is the toggle's label rather
     * than the table div: the div is emptied while whitelisting is off, and
     * an empty div has no bounding box, which Playwright reads as hidden.
     */
    async function openWhitelistTab(page: import('@playwright/test').Page) {
        await gotoPluginSettings(page);
        await openXsyncTab(page, XSYNC_TABS.whitelist, WHITELIST_TAB_READY);
    }

    test.beforeAll(async ({ baseURL }) => {
        api = await XsyncApi.create('admin-csrf.txt', '.auth/admin.json', baseURL!);
        originalWhitelistEnabled = (await api.getSitePreferences()).xsyncWhitelistEnabled ?? false;
        await api.setSitePreferences({ xsyncWhitelistEnabled: true });
        await api.deleteWhitelistSite(SITE);
    });

    test.afterAll(async () => {
        await api.deleteWhitelistSite(SITE);
        await api.setSitePreferences({ xsyncWhitelistEnabled: originalWhitelistEnabled });
        await api.dispose();
    });

    test('the whitelist table appears only while whitelisting is enabled', async ({ page }) => {
        await api.setSitePreferences({ xsyncWhitelistEnabled: false });
        await openWhitelistTab(page);
        await expect(whitelistTable(page)).toHaveCount(0);

        await setSwitchbox(page, 'limit-to-whitelist', true);

        // Enabling the whitelist always opens the non-conforming connections
        // report for an admin (PLUGINS-310), even when the list is empty.
        // Wait for it and dismiss it to get back to the tab.
        const report = openDialog(page);
        await report.waitFor({ state: 'visible', timeout: 15_000 });
        await clickDialogButton(report, 'Close');

        await expect(whitelistTable(page)).toBeVisible();
        await expect(page.locator(`${WHITELIST_PANEL} button`, { hasText: 'Add Site' })).toBeVisible();
        await expect.poll(() => api.isWhitelistEnabled()).toBe(true);
    });

    test('the table lists site id, name, url and classification', async ({ page }) => {
        await api.setSitePreferences({ xsyncWhitelistEnabled: true });
        await api.addWhitelistSite(SITE);

        await openWhitelistTab(page);

        expect(await columnLabels(whitelistTable(page))).toEqual(
            expect.arrayContaining(['Site ID', 'Site Name', 'Site URL', 'Classification', 'Actions']),
        );

        const row = rowContaining(whitelistTable(page), SITE.siteId);
        await expect(row).toContainText(SITE.siteName);
        await expect(row).toContainText(SITE.siteUrl);
        await expect(row).toContainText(SITE.classification);
    });

    test('a site can be added through the admin dialog', async ({ page }) => {
        const added: WhitelistSite = {
            siteId: 'xsync_e2e_added',
            siteName: 'XSync E2E Clinical XNAT',
            siteUrl: 'https://xsync-e2e-clinical.example.org',
            classification: 'CLINICAL',
        };
        await api.deleteWhitelistSite(added);
        await api.setSitePreferences({ xsyncWhitelistEnabled: true });

        try {
            await openWhitelistTab(page);
            await page.locator(`${WHITELIST_PANEL} button`, { hasText: 'Add Site' }).click();

            const dialog = openDialog(page);
            await dialog.waitFor({ state: 'visible' });
            await dialog.locator('#site_id_input').fill(added.siteId);
            await dialog.locator('#site_name_input').fill(added.siteName);
            await dialog.locator('#site_url_input').fill(added.siteUrl);
            await dialog.locator('#classification_input').selectOption(added.classification);
            await clickDialogButton(dialog, 'Save and Close');

            await expect(rowContaining(whitelistTable(page), added.siteId)).toBeVisible();

            const stored = (await api.getWhitelistSites()).find(s => s.siteId === added.siteId);
            expect(stored, `${added.siteId} was not persisted`).toBeDefined();
            expect(stored!.siteUrl).toBe(added.siteUrl);
            expect(stored!.classification).toBe(added.classification);
        } finally {
            await api.deleteWhitelistSite(added);
        }
    });

    test('editing a site keeps the site id fixed and saves the new values', async ({ page }) => {
        await api.setSitePreferences({ xsyncWhitelistEnabled: true });
        await api.addWhitelistSite(SITE);

        await openWhitelistTab(page);
        await rowContaining(whitelistTable(page), SITE.siteId).locator('button', { hasText: 'Edit' }).click();

        const dialog = openDialog(page);
        await dialog.waitFor({ state: 'visible' });

        // The site id keys the record, so edit mode must not allow it to
        // change. The dialog sets the readOnly property directly, so assert
        // the property rather than the attribute.
        await expect(dialog.locator('#site_id_input')).toHaveJSProperty('readOnly', true);

        await dialog.locator('#site_name_input').fill('XSync E2E Renamed XNAT');
        await dialog.locator('#classification_input').selectOption('PUBLIC');
        await clickDialogButton(dialog, 'Save and Close');

        await expect.poll(async () => {
            const stored = (await api.getWhitelistSites()).find(s => s.siteId === SITE.siteId);
            return stored ? [stored.siteName, stored.classification] : null;
        }).toEqual(['XSync E2E Renamed XNAT', 'PUBLIC']);
    });

    test('a site with no name is rejected with a visible warning', async ({ page }) => {
        await api.setSitePreferences({ xsyncWhitelistEnabled: true });

        await openWhitelistTab(page);
        await page.locator(`${WHITELIST_PANEL} button`, { hasText: 'Add Site' }).click();

        const dialog = openDialog(page);
        await dialog.waitFor({ state: 'visible' });
        await dialog.locator('#site_id_input').fill('xsync_e2e_invalid');
        await dialog.locator('#site_name_input').fill('');
        await dialog.locator('#site_url_input').fill('https://xsync-e2e-invalid.example.org');
        await clickDialogButton(dialog, 'Save and Close');

        await expect(dialog.locator('#warning')).toBeVisible();
        await expect(dialog.locator('#warning')).toContainText(/required field/i);

        // The dialog stays open and nothing is written.
        expect((await api.getWhitelistSites()).some(s => s.siteId === 'xsync_e2e_invalid')).toBe(false);
    });

    test('a site can be deleted after confirming', async ({ page }) => {
        const doomed: WhitelistSite = { ...SITE, siteId: 'xsync_e2e_doomed', siteUrl: 'https://xsync-e2e-doomed.example.org' };
        await api.setSitePreferences({ xsyncWhitelistEnabled: true });
        await api.addWhitelistSite(doomed);

        await openWhitelistTab(page);
        await rowContaining(whitelistTable(page), doomed.siteId).locator('button', { hasText: 'Delete' }).click();

        const confirm = openXmodal(page);
        await confirm.waitFor({ state: 'visible' });
        await expect(confirm).toContainText(/Are you sure you want to delete this listing\?/i);
        await confirm.locator('button', { hasText: 'Proceed' }).click();

        await expect(rowContaining(whitelistTable(page), doomed.siteId)).toHaveCount(0);
        expect((await api.getWhitelistSites()).some(s => s.siteId === doomed.siteId)).toBe(false);
    });

    test('PLUGINS-332: a url typed with a trailing slash is stored without one', async ({ page }) => {
        const trailing: WhitelistSite = {
            siteId: 'xsync_e2e_trailing',
            siteName: 'XSync E2E Trailing Slash',
            siteUrl: 'https://xsync-e2e-trailing.example.org',
            classification: 'RESEARCH',
        };
        await api.deleteWhitelistSite(trailing);
        await api.setSitePreferences({ xsyncWhitelistEnabled: true });

        try {
            await openWhitelistTab(page);
            await page.locator(`${WHITELIST_PANEL} button`, { hasText: 'Add Site' }).click();

            const dialog = openDialog(page);
            await dialog.waitFor({ state: 'visible' });
            await dialog.locator('#site_id_input').fill(trailing.siteId);
            await dialog.locator('#site_name_input').fill(trailing.siteName);
            await dialog.locator('#site_url_input').fill(`${trailing.siteUrl}/`);
            await dialog.locator('#classification_input').selectOption(trailing.classification);
            await clickDialogButton(dialog, 'Save and Close');

            // Project sync setup compares the configured remote url against
            // whitelist entries with an exact string match, so a stored
            // trailing slash makes the site permanently unmatchable.
            await expect.poll(async () => {
                const stored = (await api.getWhitelistSites()).find(s => s.siteId === trailing.siteId);
                return stored?.siteUrl;
            }).toBe(trailing.siteUrl);
        } finally {
            await api.deleteWhitelistSite(trailing);
        }
    });

    test('PLUGINS-332: a url posted with a trailing slash is stored without one', async () => {
        // KNOWN GAP, confirmed live on 1.8.2-SNAPSHOT (2026-08-17): the
        // PLUGINS-332 fix normalises the url in the admin dialog only, so this
        // REST path stores the slash verbatim, and setup matches whitelist
        // entries by exact string, leaving a slashed entry unmatchable. The
        // startup bootstrap json loads through this same un-normalised path.
        // test.fail() keeps the gap visible without failing the suite; when
        // the backend normalises, this flips to an unexpected pass and the
        // marker gets removed.
        test.fail(true, 'Backend does not normalise trailing slashes; UI-only fix in PLUGINS-332');
        const trailing: WhitelistSite = {
            siteId: 'xsync_e2e_rest_trailing',
            siteName: 'XSync E2E REST Trailing Slash',
            siteUrl: 'https://xsync-e2e-rest-trailing.example.org',
            classification: 'RESEARCH',
        };
        await api.deleteWhitelistSite(trailing);

        try {
            await api.addWhitelistSite({ ...trailing, siteUrl: `${trailing.siteUrl}/` });
            const stored = (await api.getWhitelistSites()).find(s => s.siteId === trailing.siteId);
            expect(stored, 'site was not persisted').toBeDefined();
            expect(stored!.siteUrl).toBe(trailing.siteUrl);
        } finally {
            await api.deleteWhitelistSite({ ...trailing, siteUrl: `${trailing.siteUrl}/` });
            await api.deleteWhitelistSite(trailing);
        }
    });

    test('an unknown classification is refused', async () => {
        const res = await api.addWhitelistSiteRaw({
            siteId: 'xsync_e2e_bad_class',
            siteName: 'XSync E2E Bad Classification',
            siteUrl: 'https://xsync-e2e-bad-class.example.org',
            classification: 'TOP_SECRET',
        });
        expect(res.status(), 'an unrecognised classification should be a client error').toBe(400);
        expect((await api.getWhitelistSites()).some(s => s.siteId === 'xsync_e2e_bad_class')).toBe(false);
    });

    test('this XNAT is always a permitted destination', async () => {
        // PLUGINS-228: users must always be able to sync between projects on
        // their own XNAT, so the local site is present in the whitelist even
        // though no administrator added it.
        await api.setSitePreferences({ xsyncWhitelistEnabled: true });
        const siteUrl = await api.getSiteUrl();
        const urls = (await api.getWhitelistSites()).map(s => s.siteUrl.replace(/\/$/, ''));
        expect(urls).toContain(siteUrl);
    });
});
