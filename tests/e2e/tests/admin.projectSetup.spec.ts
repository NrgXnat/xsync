/**
 * Project sync configuration persistence and input validation.
 *
 * The setup endpoint is what every governance control ultimately guards, so
 * the suite proves the baseline itself: a stored configuration reads back
 * with the values that were written, and malformed setups are refused. The
 * governance features are covered in their own specs; this one covers the
 * thing they gate.
 *
 * Note on status codes: XsyncSetupController catches every failure and
 * rethrows a generic Exception, so refusals arrive as 500 rather than 400.
 * These tests assert the refusal and that nothing was stored.
 */
import { test, expect } from '@playwright/test';
import { XsyncApi } from '../lib/api';
import { fakeRemoteUrl, projectId } from '../lib/run';

const PROJECT = projectId('xsync_e2e_setup');
const REMOTE_PROJECT = 'xsync_e2e_setup_dest';
const REMOTE_URL = fakeRemoteUrl('setup');

test.describe('@admin XSync project setup', () => {
    let api: XsyncApi;
    let originalWhitelistEnabled: boolean;

    test.beforeAll(async ({ baseURL }) => {
        api = await XsyncApi.create('admin-csrf.txt', '.auth/admin.json', baseURL!);
        originalWhitelistEnabled = (await api.getSitePreferences()).xsyncWhitelistEnabled ?? false;
        await api.setSitePreferences({ xsyncWhitelistEnabled: false });
        await api.ensureProject(PROJECT);
    });

    test.afterAll(async () => {
        await api.setSitePreferences({ xsyncWhitelistEnabled: originalWhitelistEnabled });
        await api.deleteProject(PROJECT);
        await api.dispose();
    });

    test('a stored configuration reads back with the values that were written', async () => {
        await api.setupProjectSync(PROJECT, REMOTE_URL, REMOTE_PROJECT);

        const stored = await api.getProjectSyncConfig(PROJECT);
        expect(stored.source_project_id).toBe(PROJECT);
        expect(stored.remote_url).toBe(REMOTE_URL);
        expect(stored.remote_project_id).toBe(REMOTE_PROJECT);
        expect(stored.enabled).toBe(true);
        expect(stored.sync_frequency).toBe('on demand');
        expect(stored.identifiers).toBe('use_local');
        expect(stored.sync_new_only).toBe(true);
        expect(stored.anonymize).toBe(false);
        expect(stored.no_of_retry_days).toBe(3);
        expect(stored.imaging_sessions?.sync_type).toBe('all');
        expect(stored.project_resources?.sync_type).toBe('none');
    });

    test('a body whose source project contradicts the url is refused', async () => {
        const res = await api.setupProjectSyncRaw(PROJECT, REMOTE_URL, REMOTE_PROJECT, {
            source_project_id: 'some_other_project',
        });
        expect(res.ok(), 'mismatched source_project_id should be refused').toBeFalsy();
    });

    test('a blank source project id is refused', async () => {
        const res = await api.setupProjectSyncRaw(PROJECT, REMOTE_URL, REMOTE_PROJECT, {
            source_project_id: '',
        });
        expect(res.ok(), 'blank source_project_id should be refused').toBeFalsy();
    });

    test('setup for a project that does not exist is refused', async () => {
        const ghost = projectId('xsync_e2e_no_such');
        const res = await api.setupProjectSyncRaw(ghost, REMOTE_URL, REMOTE_PROJECT);
        expect(res.ok(), 'setup for a nonexistent project should be refused').toBeFalsy();

        const configs = await api.getConfigurationsForRemoteUrl(REMOTE_URL);
        expect(configs.map((c: any) => c.localProject)).not.toContain(ghost);
    });
});
