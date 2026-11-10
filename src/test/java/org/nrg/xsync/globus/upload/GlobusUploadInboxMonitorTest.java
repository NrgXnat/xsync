package org.nrg.xsync.globus.upload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;
import org.nrg.xsync.globus.GlobusClient;

/**
 * Unit tests for {@link GlobusUploadInboxMonitor}: path resolution (including
 * the traversal guard and the required {@code <PROJECT_ID>/<user>/<archive>}
 * layout), the detect-then-import orchestration, and at-most-once idempotency.
 * External interactions (primary-node check, Globus, XNAT import) are replaced
 * by overriding the class's {@code protected} seams.
 */
class GlobusUploadInboxMonitorTest {

    private static final String INBOX = "/opt/xnat/data/inbox";

    /** Seam-overriding subclass; collaborators are unused (null). */
    private static final class TestMonitor extends GlobusUploadInboxMonitor {
        boolean primary = true;
        Optional<String> inboxColl = Optional.of("inbox-coll");
        String inboxPath = INBOX;
        List<GlobusClient.CompletedTask> tasks = new ArrayList<>();
        // task id -> delivered destination paths
        final Map<String, List<String>> deliveredByTask = new ConcurrentHashMap<>();
        RuntimeException listError;
        RuntimeException importError;

        final List<ResolvedUpload> imported = new ArrayList<>();
        int tokenCalls;
        // Usernames with an XNAT grant; null authorizes all (so pre-existing tests import).
        Set<String> authorizedUsers;

        TestMonitor() {
            super(null, null, null, null, null);
        }

        @Override protected boolean isPrimaryNode() { return primary; }
        @Override protected Optional<String> inboxCollectionId() { return inboxColl; }
        @Override protected String inboxPath() { return inboxPath; }
        @Override protected String token() { tokenCalls++; return "token"; }

        @Override
        protected boolean isAuthorizedUpload(final String projectId, final String xnatUsername) {
            return authorizedUsers == null || authorizedUsers.contains(xnatUsername);
        }

        @Override
        protected List<GlobusClient.CompletedTask> completedTasks(final String token, final String collectionId,
                                                                  final java.time.Instant since) {
            if (listError != null) {
                throw listError;
            }
            return tasks;
        }

        @Override
        protected List<String> destinationPaths(final String token, final String taskId) {
            return deliveredByTask.getOrDefault(taskId, List.of());
        }

        @Override
        protected void importArchive(final ResolvedUpload upload) {
            if (importError != null) {
                throw importError;
            }
            imported.add(upload);
        }
    }

    private static GlobusClient.CompletedTask task(final String id) {
        return new GlobusClient.CompletedTask(id, "2026-10-28T00:00:00Z");
    }

    // --- path resolution --------------------------------------------------

    @Test
    void resolvesProjectUserAndAbsolutePath() {
        final Optional<GlobusUploadInboxMonitor.ResolvedUpload> r =
                GlobusUploadInboxMonitor.resolveUpload(INBOX, "/PRJ/alice/study.tgz");
        assertTrue(r.isPresent());
        assertEquals("PRJ", r.get().projectId());
        assertEquals("alice", r.get().username());
        assertEquals(INBOX + "/PRJ/alice/study.tgz", r.get().absolutePath());
    }

    @Test
    void rejectsPathWithTooFewSegments() {
        assertTrue(GlobusUploadInboxMonitor.resolveUpload(INBOX, "/PRJ/study.tgz").isEmpty(),
                "project + archive without a user level is not the expected layout");
        assertTrue(GlobusUploadInboxMonitor.resolveUpload(INBOX, "/PRJ/").isEmpty());
        assertTrue(GlobusUploadInboxMonitor.resolveUpload(INBOX, "").isEmpty());
    }

    @Test
    void rejectsTraversalOutsideInbox() {
        assertTrue(GlobusUploadInboxMonitor.resolveUpload(INBOX, "/PRJ/../../etc/passwd").isEmpty(),
                "a path that normalizes outside the inbox must be rejected");
    }

    // --- orchestration + idempotency --------------------------------------

    @Test
    void importsEachDeliveredArchiveFromCompletedTasks() {
        final TestMonitor m = new TestMonitor();
        m.tasks = List.of(task("t-1"));
        m.deliveredByTask.put("t-1", List.of("/PRJ/alice/study.tgz"));

        m.poll();

        assertEquals(1, m.imported.size());
        assertEquals("PRJ", m.imported.get(0).projectId());
        assertEquals("alice", m.imported.get(0).username());
    }

    @Test
    void doesNotReimportAnAlreadyHandledTask() {
        final TestMonitor m = new TestMonitor();
        m.tasks = List.of(task("t-1"));
        m.deliveredByTask.put("t-1", List.of("/PRJ/alice/study.tgz"));

        m.poll();
        m.poll(); // same task id appears again next poll

        assertEquals(1, m.imported.size(), "a task id must be imported at most once");
    }

    @Test
    void skipsMalformedPathsButImportsValidSiblings() {
        final TestMonitor m = new TestMonitor();
        m.tasks = List.of(task("t-1"));
        m.deliveredByTask.put("t-1", List.of("/badpath.tgz", "/PRJ/bob/good.zip"));

        m.poll();

        assertEquals(1, m.imported.size());
        assertEquals("bob", m.imported.get(0).username());
    }

    @Test
    void importFailureIsSwallowedAndTaskNotRetried() {
        final TestMonitor m = new TestMonitor();
        m.tasks = List.of(task("t-1"));
        m.deliveredByTask.put("t-1", List.of("/PRJ/alice/study.tgz"));
        m.importError = new RuntimeException("no edit access");

        m.poll();   // attempt fails
        m.importError = null;
        m.poll();   // must NOT retry (at-most-once)

        assertTrue(m.imported.isEmpty(), "a failed task is handled once, not retried in-process");
    }

    @Test
    void skipsUploadWithNoMatchingGrantButImportsAuthorizedSibling() {
        final TestMonitor m = new TestMonitor();
        m.authorizedUsers = Set.of("alice"); // bob has no XNAT upload grant
        m.tasks = List.of(task("t-1"));
        m.deliveredByTask.put("t-1", List.of("/PRJ/alice/ok.tgz", "/PRJ/bob/denied.tgz"));

        m.poll();

        assertEquals(1, m.imported.size(), "only the upload with a matching grant imports");
        assertEquals("alice", m.imported.get(0).username());
    }

    @Test
    void noOpOffPrimaryNode() {
        final TestMonitor m = new TestMonitor();
        m.primary = false;
        m.tasks = List.of(task("t-1"));
        m.deliveredByTask.put("t-1", List.of("/PRJ/alice/study.tgz"));

        m.poll();

        assertTrue(m.imported.isEmpty());
        assertEquals(0, m.tokenCalls, "off the primary node the monitor must not even request a token");
    }

    @Test
    void noOpWhenNoInboxCollectionConfigured() {
        final TestMonitor m = new TestMonitor();
        m.inboxColl = Optional.empty();
        m.tasks = List.of(task("t-1"));

        m.poll();

        assertTrue(m.imported.isEmpty());
        assertEquals(0, m.tokenCalls);
    }

    @Test
    void listFailureAbortsPassWithoutThrowing() {
        final TestMonitor m = new TestMonitor();
        m.listError = new RuntimeException("endpoint-manager 500");

        m.poll(); // must not propagate

        assertTrue(m.imported.isEmpty());
    }
}
