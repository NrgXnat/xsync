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
 * the url only has to be well formed. Assertions here are scoped by remote
 * url (dashboard rows, per-url enable/disable), so a url reused across runs
 * would let one run's leftover state affect another's; a fresh url per run
 * keeps every run's assertions independent.
 */
export function fakeRemoteUrl(stem: string): string {
    return `https://xsync-e2e-${stem}-${RUN_ID}.example.org`;
}
