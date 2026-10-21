/**
 * XSync site preference persistence and validation.
 *
 * These are the pre-existing settings tabs (Remote Token, Sync Retry, Maximum
 * Total Uncompressed File Size) that the July work builds on. The suite
 * covers them at the REST layer the tab forms POST to, proving values persist
 * and invalid values are refused rather than silently stored: the scheduler
 * consumes these settings long after the admin has left the page, so a bad
 * value that saves quietly surfaces as a runtime failure with no obvious
 * cause.
 */
import { test, expect } from '@playwright/test';
import { XsyncApi, SitePreferences } from '../lib/api';

test.describe('@admin XSync site preferences', () => {
    let api: XsyncApi;
    let original: SitePreferences;

    test.beforeAll(async ({ baseURL }) => {
        api = await XsyncApi.create('admin-csrf.txt', '.auth/admin.json', baseURL!);
        original = await api.getSitePreferences();
    });

    test.afterAll(async () => {
        await api.setSitePreferences({
            tokenRefreshInterval: original.tokenRefreshInterval,
            syncRetryInterval: original.syncRetryInterval,
            syncRetryCount: original.syncRetryCount,
            syncMaxUncompressedZipFileSize: original.syncMaxUncompressedZipFileSize,
        });
        await api.dispose();
    });

    test('interval and retry settings persist through a save', async () => {
        await api.setSitePreferences({
            tokenRefreshInterval: '9 hours',
            syncRetryInterval: '3 hours',
            syncRetryCount: '4',
            syncMaxUncompressedZipFileSize: '1073741824',
        });

        const stored = await api.getSitePreferences();
        expect(stored.tokenRefreshInterval).toBe('9 hours');
        expect(stored.syncRetryInterval).toBe('3 hours');
        expect(stored.syncRetryCount).toBe('4');
        expect(stored.syncMaxUncompressedZipFileSize).toBe('1073741824');
    });

    test('a malformed max file size is refused and the stored value survives', async () => {
        await api.setSitePreferences({ syncMaxUncompressedZipFileSize: '-1' });

        for (const bad of ['abc', '0', '-2']) {
            const res = await api.setSitePreferencesRaw({ syncMaxUncompressedZipFileSize: bad });
            expect(res.status(), `"${bad}" should be refused as a max file size`).toBe(400);
        }

        expect((await api.getSitePreferences()).syncMaxUncompressedZipFileSize).toBe('-1');
    });

    test('an interval below the five minute floor is refused', async () => {
        await api.setSitePreferences({ tokenRefreshInterval: '10 hours' });

        for (const bad of ['2 minutes', 'often', '10']) {
            const res = await api.setSitePreferencesRaw({ tokenRefreshInterval: bad });
            expect(res.status(), `"${bad}" should be refused as a refresh interval`).toBe(400);
        }

        expect((await api.getSitePreferences()).tokenRefreshInterval).toBe('10 hours');
    });
});
