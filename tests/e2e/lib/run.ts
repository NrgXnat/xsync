/**
 * Per-run identity for created resources.
 *
 * XNAT permanently retires the id of a deleted project, so a fixed project id
 * works exactly once and every later run fails project creation with a 403.
 * Suffixing ids with a per-process token keeps runs independent. Playwright
 * also restarts its worker after a test failure, re-running beforeAll hooks in
 * a fresh process; the fresh token lets those hooks create new projects
 * instead of colliding with the ids the dying worker just deleted.
 */
export const RUN_ID = Date.now().toString(36);

export function projectId(stem: string): string {
    return `${stem}_${RUN_ID}`;
}

/**
 * A per-run fake sync destination. Setup never contacts the remote server, so
 * the url only has to be well formed. Deleting a project leaves its XSync
 * configuration behind (observed live on 1.8.2-SNAPSHOT), and those orphans
 * poison url-scoped assertions and make the dashboard's site-wide
 * enable/disable return 500 for that url, so tests must never assert against
 * a url a previous run also used.
 */
export function fakeRemoteUrl(stem: string): string {
    return `https://xsync-e2e-${stem}-${RUN_ID}.example.org`;
}
