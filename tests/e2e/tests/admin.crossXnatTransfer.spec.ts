/**
 * Cross-instance transfer: data leaving this XNAT and arriving on another.
 *
 * Everything else in the suite exercises governance on one instance, using
 * local-to-local configurations. This spec covers the scenario XSync exists
 * for: a real transfer from this XNAT (the sender under test) to a second,
 * reachable XNAT (the receiver).
 *
 * The receiver comes from the environment: REMOTE_XNAT_URL plus
 * REMOTE_ADMIN_USER / REMOTE_ADMIN_PASS. When REMOTE_XNAT_URL is unset the
 * spec skips, loudly, as a deliberate environment gate: a second instance is
 * a genuine infrastructure prerequisite, unlike the plugin version, which
 * global setup refuses to run without. Nothing here assumes hostnames,
 * naming schemes, or any particular CI, so a client can point these at two
 * instances on their own network.
 *
 * The spec creates its own per-run projects on BOTH instances and deletes
 * them afterwards. Pre-existing data on the receiver is never read or
 * touched beyond listing what this run created.
 */
import { test, expect } from '@playwright/test';
import { XsyncApi } from '../lib/api';
import { remoteConfigFromEnv, RemoteXnat } from '../lib/remote';
import { projectId } from '../lib/run';
const REMOTE = remoteConfigFromEnv();

const SOURCE_PROJECT = projectId('xsync_e2e_xfer_src');
const DEST_PROJECT = projectId('xsync_e2e_xfer_dest');
const SUBJECT = projectId('xsync_e2e_subj');


// A real transfer includes remote validation, packaging and upload, so give
// the polling room without letting a hang eat the run.
const SYNC_DEADLINE_MS = 180_000;

test.describe('@admin @crossxnat XSync transfer between two XNAT instances', () => {
    test.skip(!REMOTE,
        'REMOTE_XNAT_URL is not set. Configure a receiving XNAT to run the cross-instance transfer tests; see README.');
    test.setTimeout(SYNC_DEADLINE_MS + 120_000);

    let api: XsyncApi;
    let remote: RemoteXnat;
    let originalWhitelistEnabled: boolean;

    test.beforeAll(async ({ baseURL }) => {
        api = await XsyncApi.create('admin-csrf.txt', '.auth/admin.json', baseURL!);
        remote = await RemoteXnat.create(REMOTE!);
        originalWhitelistEnabled = (await api.getSitePreferences()).xsyncWhitelistEnabled ?? false;
        await api.setSitePreferences({ xsyncWhitelistEnabled: false });

        await api.ensureProject(SOURCE_PROJECT);
        const subjectRes = await api.putSubject(SOURCE_PROJECT, SUBJECT);
        expect(subjectRes.ok(), `Create subject failed: HTTP ${subjectRes.status()}`).toBeTruthy();

        await remote.ensureProject(DEST_PROJECT);
    });

    test.afterAll(async () => {
        await api.setSitePreferences({ xsyncWhitelistEnabled: originalWhitelistEnabled });
        await api.deleteProject(SOURCE_PROJECT);
        await remote.deleteProject(DEST_PROJECT);
        await remote.dispose();
        await api.dispose();
    });

    // One test rather than a chain: the arrival, the history record, and the
    // dashboard view all describe the same transfer, and Playwright restarts
    // its worker after any failure, which would rebuild the fixtures under
    // new names and turn the follow-on tests into noise.
    test('a subject transfers to the receiving XNAT and the sender records it', async () => {
        // XSync authenticates to the receiver with an alias token, never a
        // password; issue one there and store it on the sender. The save
        // endpoint itself validates the credentials live against the
        // receiver, so this line passing already proves connectivity.
        const token = await remote.issueAliasToken();
        await api.setupProjectSync(SOURCE_PROJECT, REMOTE!.url, DEST_PROJECT);
        await api.saveRemoteCredentials(SOURCE_PROJECT, {
            host: REMOTE!.url,
            remoteProject: DEST_PROJECT,
            alias: token.alias,
            secret: token.secret,
            username: REMOTE!.username,
        });

        const started = await api.triggerProjectSync(SOURCE_PROJECT);
        expect(started).toContain('synchronization started');

        // The outcome that matters is observed on the receiver. Poll the
        // destination, but stop early with the recorded reason if the sender
        // declares failure.
        await expect.poll(async () => {
            const arrived = await remote.subjectLabels(DEST_PROJECT);
            if (arrived.includes(SUBJECT)) return 'arrived';
            const failed = (await api.getProjectSyncHistory(SOURCE_PROJECT))
                .find((h: any) => /fail|interrupt|conflict/i.test(h.syncStatus ?? ''));
            return failed ? `sender reported: ${failed.syncStatus}` : 'pending';
        }, {
            message: `subject ${SUBJECT} did not arrive at ${REMOTE!.url}/${DEST_PROJECT}`,
            timeout: SYNC_DEADLINE_MS,
            intervals: [2_000, 3_000, 5_000],
        }).toBe('arrived');

        // The sender's history records the same transfer. A completed
        // project sync reports the status string "Complete [Verified]";
        // the SYNCED_* constants in XsyncUtils are per-experiment, not
        // per-project.
        await expect.poll(async () => (await api.getProjectSyncHistory(SOURCE_PROJECT)).length, {
            message: 'no history entry appeared for the completed sync',
        }).toBeGreaterThan(0);
        const history = await api.getProjectSyncHistory(SOURCE_PROJECT);
        const latest = history[history.length - 1];
        expect(latest.remoteHost).toBe(REMOTE!.url);
        expect(latest.remoteProject).toBe(DEST_PROJECT);
        expect(latest.syncStatus).toMatch(/complete/i);
        expect(latest.totalSubjects).toBeGreaterThanOrEqual(1);

        // And the admin dashboard breakdown shows the connection with that
        // same outcome as its last sync status.
        const config = (await api.getConfigurationsForRemoteUrl(REMOTE!.url))
            .find((c: any) => c.localProject === SOURCE_PROJECT);
        expect(config, `${SOURCE_PROJECT} missing from the dashboard breakdown for ${REMOTE!.url}`).toBeDefined();
        expect(config.lastSyncStatus).toMatch(/complete/i);
    });

    // PLUGINS-313, the failed-row stack trace popup, was attempted here and
    // removed after live evidence: a sync that fails before transfer (valid
    // credentials, destination project absent on the receiver) writes NO
    // history entry, so lastSyncStatus stays "Never Synced" and the failed
    // link the popup hangs off never renders. Only a mid-transfer failure
    // reaches that state, and staging one requires receiver-side fault
    // injection. Declared in the README instead of faked.
});
