/**
 * REST helpers for XSync test setup, assertions and teardown.
 *
 * Owns a standalone APIRequestContext rather than borrowing Playwright's
 * per-test fixture, so the same instance can be used from beforeAll, the test
 * body and afterAll. Call dispose() in afterAll.
 */
import { APIRequestContext, APIResponse, expect, request as playwrightRequest } from '@playwright/test';
import { readCsrfToken } from './auth';

export interface WhitelistSite {
    siteId: string;
    siteName: string;
    siteUrl: string;
    /** CLINICAL, RESEARCH or PUBLIC. Any other value is rejected as a 400. */
    classification: string;
}

export interface RemoteUrlDetails {
    remoteUrl: string;
    siteName: string;
    classification: string;
    numberProjects: number;
    numberErrors: number;
}

export interface SitePreferences {
    tokenRefreshInterval?: string;
    syncRetryInterval?: string;
    syncRetryCount?: string;
    syncMaxUncompressedZipFileSize?: string;
    xsyncWhitelistEnabled?: boolean;
    httpsEnabled?: boolean;
    asperaEnabled?: boolean;
}

export class XsyncApi {
    private constructor(
        private request: APIRequestContext,
        private csrfToken: string,
    ) {}

    static async create(csrfFile: string, storageStatePath: string, baseURL: string): Promise<XsyncApi> {
        const ctx = await playwrightRequest.newContext({
            baseURL,
            storageState: storageStatePath,
            ignoreHTTPSErrors: true,
        });
        return new XsyncApi(ctx, readCsrfToken(csrfFile));
    }

    async dispose(): Promise<void> {
        await this.request.dispose();
    }

    private headers(): Record<string, string> {
        return { 'XNAT-CSRF': this.csrfToken };
    }

    // ---------------------------------------------------------------- site preferences

    async getSitePreferences(): Promise<SitePreferences> {
        const res = await this.request.get('/xapi/xsyncSitePreferences');
        expect(res.ok(), `GET site preferences failed: HTTP ${res.status()}`).toBeTruthy();
        return res.json();
    }

    /** Raw response so a test can assert on a rejected value. */
    async setSitePreferencesRaw(prefs: SitePreferences): Promise<APIResponse> {
        return this.request.post('/xapi/xsyncSitePreferences', {
            headers: { ...this.headers(), 'Content-Type': 'application/json' },
            data: prefs,
        });
    }

    /** Partial update. Fields left out keep their current value. */
    async setSitePreferences(prefs: SitePreferences): Promise<void> {
        const res = await this.setSitePreferencesRaw(prefs);
        expect(res.ok(), `POST site preferences failed: HTTP ${res.status()} ${await res.text()}`).toBeTruthy();
    }

    async isHttpsEnabled(): Promise<boolean> {
        const res = await this.request.get('/xapi/xsyncSitePreferences/httpsEnabled');
        expect(res.ok()).toBeTruthy();
        return res.json();
    }

    async isAsperaEnabled(): Promise<boolean> {
        const res = await this.request.get('/xapi/xsyncSitePreferences/asperaEnabled');
        expect(res.ok()).toBeTruthy();
        return res.json();
    }

    async isWhitelistEnabled(): Promise<boolean> {
        const res = await this.request.get('/xapi/xsyncProjectPreferences/whitelistEnabled');
        expect(res.ok()).toBeTruthy();
        return res.json();
    }

    // ---------------------------------------------------------------- whitelist

    async getWhitelistSites(): Promise<WhitelistSite[]> {
        const res = await this.request.get('/xapi/xsyncSitePreferences/whitelistSites');
        expect(res.ok(), `GET whitelist failed: HTTP ${res.status()}`).toBeTruthy();
        return res.json();
    }

    /** Raw response so a test can assert on a rejection instead of a success. */
    async addWhitelistSiteRaw(site: WhitelistSite): Promise<APIResponse> {
        return this.request.post('/xapi/xsyncSitePreferences/whitelistSites/add', {
            headers: { ...this.headers(), 'Content-Type': 'application/json' },
            data: site,
        });
    }

    async addWhitelistSite(site: WhitelistSite): Promise<WhitelistSite[]> {
        const res = await this.addWhitelistSiteRaw(site);
        expect(res.ok(), `Add whitelist site ${site.siteId} failed: HTTP ${res.status()} ${await res.text()}`).toBeTruthy();
        return res.json();
    }

    /** Deletes a whitelist entry. A site that is already gone is not an error. */
    async deleteWhitelistSite(site: WhitelistSite): Promise<void> {
        const res = await this.request.delete('/xapi/xsyncSitePreferences/whitelistSites/delete', {
            headers: { ...this.headers(), 'Content-Type': 'application/json' },
            data: site,
        });
        if (res.status() !== 400 && res.status() !== 404) {
            expect(res.ok(), `Delete whitelist site ${site.siteId} failed: HTTP ${res.status()}`).toBeTruthy();
        }
    }

    // ---------------------------------------------------------------- project blacklist

    async getBlacklistedProjects(): Promise<string[]> {
        const res = await this.request.get('/xapi/xsyncSitePreferences/blacklistProjects');
        expect(res.ok(), `GET project blacklist failed: HTTP ${res.status()}`).toBeTruthy();
        return res.json();
    }

    async isProjectBlacklisted(projectId: string): Promise<boolean> {
        const res = await this.request.get(`/xapi/xsyncSitePreferences/blacklistProjects/${encodeURIComponent(projectId)}`);
        expect(res.ok()).toBeTruthy();
        return res.json();
    }

    async addProjectToBlacklistRaw(projectId: string): Promise<APIResponse> {
        return this.request.post(`/xapi/xsyncSitePreferences/blacklistProjects/${encodeURIComponent(projectId)}`, {
            headers: this.headers(),
        });
    }

    async addProjectToBlacklist(projectId: string): Promise<string[]> {
        const res = await this.addProjectToBlacklistRaw(projectId);
        expect(res.ok(), `Blacklist ${projectId} failed: HTTP ${res.status()} ${await res.text()}`).toBeTruthy();
        return res.json();
    }

    /** Removes a project from the blacklist. A project not on it is not an error. */
    async removeProjectFromBlacklist(projectId: string): Promise<void> {
        const res = await this.request.delete(`/xapi/xsyncSitePreferences/blacklistProjects/${encodeURIComponent(projectId)}`, {
            headers: this.headers(),
        });
        if (res.status() !== 400) {
            expect(res.ok(), `Un-blacklist ${projectId} failed: HTTP ${res.status()}`).toBeTruthy();
        }
    }

    // ---------------------------------------------------------------- configuration dashboard

    async getDashboard(): Promise<RemoteUrlDetails[]> {
        const res = await this.request.get('/xapi/xsync/dashboard');
        expect(res.ok(), `GET dashboard failed: HTTP ${res.status()}`).toBeTruthy();
        return res.json();
    }

    async getNonConformingRemoteUrls(): Promise<RemoteUrlDetails[]> {
        const res = await this.request.get('/xapi/xsync/dashboard/whitelist');
        expect(res.ok(), `GET non-conforming urls failed: HTTP ${res.status()}`).toBeTruthy();
        return res.json();
    }

    async getConfigurationsForRemoteUrl(remoteUrl: string): Promise<any[]> {
        const res = await this.request.get('/xapi/xsync/dashboard/remoteUrl', { params: { remoteUrl } });
        expect(res.ok(), `GET configurations for ${remoteUrl} failed: HTTP ${res.status()}`).toBeTruthy();
        return res.json();
    }

    async setRemoteUrlEnabled(remoteUrl: string, enabled: boolean): Promise<void> {
        const res = await this.request.put('/xapi/xsync/dashboard/enable', {
            headers: this.headers(),
            params: { remoteUrl, enabled },
        });
        expect(res.ok(), `Set ${remoteUrl} enabled=${enabled} failed: HTTP ${res.status()}`).toBeTruthy();
    }

    // ---------------------------------------------------------------- project sync configuration

    /** The stored sync configuration for a project. */
    async getProjectSyncConfig(projectId: string): Promise<any> {
        const res = await this.request.get(`/xapi/xsync/setup/projects/${encodeURIComponent(projectId)}`);
        expect(res.ok(), `GET sync config for ${projectId} failed: HTTP ${res.status()}`).toBeTruthy();
        return res.json();
    }

    /**
     * Raw response so a test can assert that setup was refused. `overrides`
     * replaces individual fields of the default body, for malformed-input
     * tests.
     */
    async setupProjectSyncRaw(projectId: string, remoteUrl: string, remoteProjectId: string,
                              overrides: Record<string, unknown> = {}): Promise<APIResponse> {
        return this.request.post(`/xapi/xsync/setup/projects/${encodeURIComponent(projectId)}`, {
            headers: { ...this.headers(), 'Content-Type': 'application/json' },
            data: {
                enabled: true,
                source_project_id: projectId,
                // The value the UI writes for On Demand, from the frequency
                // select in xsync-config.js. It contains a space.
                sync_frequency: 'on demand',
                sync_new_only: true,
                identifiers: 'use_local',
                remote_url: remoteUrl,
                remote_project_id: remoteProjectId,
                notification_emails: '',
                customIdentifiers: 'dateTimeLabelGenerator',
                anonymize: false,
                no_of_retry_days: 3,
                project_resources: { sync_type: 'none' },
                subject_resources: { sync_type: 'none' },
                subject_assessors: { sync_type: 'none' },
                imaging_sessions: { sync_type: 'all' },
                ...overrides,
            },
        });
    }

    async setupProjectSync(projectId: string, remoteUrl: string, remoteProjectId: string): Promise<void> {
        const res = await this.setupProjectSyncRaw(projectId, remoteUrl, remoteProjectId);
        expect(res.ok(), `XSync setup for ${projectId} failed: HTTP ${res.status()} ${await res.text()}`).toBeTruthy();
    }

    // ---------------------------------------------------------------- generic XNAT

    /** Creates a project if it does not already exist. Safe to call repeatedly. */
    async ensureProject(projectId: string): Promise<void> {
        const existing = await this.request.get(`/data/projects/${encodeURIComponent(projectId)}?format=json`);
        if (existing.ok()) return;

        const res = await this.request.put(`/data/projects/${encodeURIComponent(projectId)}`, {
            headers: this.headers(),
            params: { event_action: 'Added Project' },
        });
        expect(res.ok(), `Create project ${projectId} failed: HTTP ${res.status()} ${await res.text()}`).toBeTruthy();
    }

    async deleteProject(projectId: string): Promise<void> {
        const res = await this.request.delete(`/data/projects/${encodeURIComponent(projectId)}`, {
            headers: this.headers(),
            params: { removeFiles: 'true', event_reason: 'xsync e2e cleanup' },
        });
        if (res.status() !== 404) {
            expect(res.ok(), `Delete project ${projectId} failed: HTTP ${res.status()}`).toBeTruthy();
        }
    }

    /** Grants a site-wide role to a user. Used for the XsyncAdministrator checks. */
    async addRole(username: string, role: string): Promise<void> {
        const res = await this.request.put(`/xapi/users/${encodeURIComponent(username)}/roles/${encodeURIComponent(role)}`, {
            headers: this.headers(),
        });
        expect(res.ok(), `Grant ${role} to ${username} failed: HTTP ${res.status()}`).toBeTruthy();
    }

    async removeRole(username: string, role: string): Promise<void> {
        const res = await this.request.delete(`/xapi/users/${encodeURIComponent(username)}/roles/${encodeURIComponent(role)}`, {
            headers: this.headers(),
        });
        if (res.status() !== 404) {
            expect(res.ok(), `Revoke ${role} from ${username} failed: HTTP ${res.status()}`).toBeTruthy();
        }
    }

    async getRoles(username: string): Promise<string[]> {
        const res = await this.request.get(`/xapi/users/${encodeURIComponent(username)}/roles`);
        expect(res.ok(), `GET roles for ${username} failed: HTTP ${res.status()}`).toBeTruthy();
        return res.json();
    }

    /** The site URL as XNAT reports it. This is always a permitted XSync destination. */
    async getSiteUrl(): Promise<string> {
        const res = await this.request.get('/xapi/siteConfig/siteUrl');
        expect(res.ok(), `GET siteUrl failed: HTTP ${res.status()}`).toBeTruthy();
        return (await res.text()).replace(/^"|"$/g, '').replace(/\/$/, '');
    }
}
