/**
 * Helper for the RECEIVING XNAT in cross-instance transfer tests.
 *
 * Deliberately generic: it takes a base url and credentials from the
 * environment and assumes nothing about hostnames, naming schemes, or CI. Any
 * reachable XNAT the given account can create a project on will work, so the
 * same tests run against a second dev stack today or a client's own receiving
 * instance later.
 *
 * Uses per-request basic auth rather than a browser session, so no CSRF token
 * is involved.
 */
import { APIRequestContext, expect, request as playwrightRequest } from '@playwright/test';

export interface RemoteConfig {
    url: string;
    username: string;
    password: string;
}

/**
 * Reads the remote instance configuration from the environment. Returns null
 * when REMOTE_XNAT_URL is unset (the cross-instance spec skips); throws when
 * it is set but the credentials are not, because a half-configured remote
 * silently skipping would look like coverage that does not exist.
 */
export function remoteConfigFromEnv(): RemoteConfig | null {
    const url = process.env.REMOTE_XNAT_URL?.replace(/\/$/, '');
    if (!url) return null;
    const username = process.env.REMOTE_ADMIN_USER;
    const password = process.env.REMOTE_ADMIN_PASS;
    if (!username || !password) {
        throw new Error(
            'REMOTE_XNAT_URL is set but REMOTE_ADMIN_USER / REMOTE_ADMIN_PASS are not. ' +
            'Set all three (see README, Cross-instance transfer tests) or unset REMOTE_XNAT_URL.',
        );
    }
    return { url, username, password };
}

export class RemoteXnat {
    private constructor(
        private request: APIRequestContext,
        readonly config: RemoteConfig,
    ) {}

    static async create(config: RemoteConfig): Promise<RemoteXnat> {
        const ctx = await playwrightRequest.newContext({
            baseURL: config.url,
            ignoreHTTPSErrors: true,
            httpCredentials: { username: config.username, password: config.password },
        });
        const login = await ctx.get('/data/JSESSION');
        expect(login.ok(),
            `Could not authenticate to the remote XNAT at ${config.url} as ${config.username}: HTTP ${login.status()}`,
        ).toBeTruthy();
        return new RemoteXnat(ctx, config);
    }

    async dispose(): Promise<void> {
        await this.request.dispose();
    }

    /**
     * An alias token for the configured account. XSync authenticates to the
     * remote with an alias/secret pair, never a raw password.
     */
    async issueAliasToken(): Promise<{ alias: string; secret: string }> {
        const res = await this.request.get('/data/services/tokens/issue');
        expect(res.ok(), `Alias token issue on ${this.config.url} failed: HTTP ${res.status()}`).toBeTruthy();
        const token = await res.json();
        expect(token.alias, 'alias token response carried no alias').toBeTruthy();
        expect(token.secret, 'alias token response carried no secret').toBeTruthy();
        return { alias: token.alias, secret: token.secret };
    }

    async ensureProject(projectId: string): Promise<void> {
        const existing = await this.request.get(`/data/projects/${encodeURIComponent(projectId)}?format=json`);
        if (existing.ok()) return;
        const res = await this.request.put(`/data/projects/${encodeURIComponent(projectId)}`, {
            params: { event_action: 'Added Project' },
        });
        expect(res.ok(), `Create project ${projectId} on ${this.config.url} failed: HTTP ${res.status()} ${await res.text()}`).toBeTruthy();
    }

    async deleteProject(projectId: string): Promise<void> {
        const res = await this.request.delete(`/data/projects/${encodeURIComponent(projectId)}`, {
            params: { removeFiles: 'true', event_reason: 'xsync e2e cleanup' },
        });
        if (res.status() !== 404) {
            expect(res.ok(), `Delete project ${projectId} on ${this.config.url} failed: HTTP ${res.status()}`).toBeTruthy();
        }
    }

    /** Subject labels currently in a project. Empty when the project has none. */
    async subjectLabels(projectId: string): Promise<string[]> {
        const res = await this.request.get(`/data/projects/${encodeURIComponent(projectId)}/subjects?format=json&columns=label`);
        if (!res.ok()) return [];
        const body = await res.json();
        return (body?.ResultSet?.Result ?? []).map((r: any) => r.label);
    }

    /** Experiment labels currently in a project. Empty when the project has none. */
    async experimentLabels(projectId: string): Promise<string[]> {
        const res = await this.request.get(`/data/projects/${encodeURIComponent(projectId)}/experiments?format=json&columns=label`);
        if (!res.ok()) return [];
        const body = await res.json();
        return (body?.ResultSet?.Result ?? []).map((r: any) => r.label);
    }
}
