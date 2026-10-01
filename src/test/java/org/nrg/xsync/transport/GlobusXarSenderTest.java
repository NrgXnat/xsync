package org.nrg.xsync.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nrg.xapi.exceptions.NotFoundException;
import org.nrg.xsync.connection.RemoteConnection;
import org.nrg.xsync.connection.RemoteConnectionResponse;
import org.nrg.xsync.globus.GlobusClient;
import org.nrg.xsync.globus.entities.GlobusEndpoint;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Unit tests for {@link GlobusXarSender}: the {@code supports} decision, the
 * path building, and the send orchestration (submit &rarr; wait &rarr; import,
 * with fallback). External interactions are replaced by overriding the
 * class's {@code protected} seams; no network, Globus, or file access except
 * the dedicated stage/cleanup test.
 */
class GlobusXarSenderTest {

    private static RemoteConnectionResponse ok() {
        return new RemoteConnectionResponse(new ResponseEntity<>("ok", HttpStatus.OK));
    }

    /** Configurable seam-overriding subclass; collaborators are unused (null). */
    private static final class TestSender extends GlobusXarSender {
        GlobusXarSender.RouteConfig config =
                new GlobusXarSender.RouteConfig(true, "ep", "/outbox", "/peer", "/srv/peer");
        GlobusEndpoint endpoint = new GlobusEndpoint("ep", "client", "secret", "inboxColl", "outboxColl");
        NotFoundException lookupError;
        RuntimeException submitError;
        GlobusClient.TaskStatus taskStatus = new GlobusClient.TaskStatus("SUCCEEDED", null, null);

        GlobusClient.TransferRequest capturedRequest;
        String lastImportPath;
        boolean importCalled;
        boolean fallbackCalled;
        boolean cleanupCalled;
        final RemoteConnectionResponse importResponse = ok();
        final RemoteConnectionResponse fallbackResponse = ok();

        TestSender() {
            super(null, null, null, null, null, null);
        }

        @Override protected RouteConfig config(final String projectId) {
            return config;
        }

        @Override protected GlobusEndpoint lookupEndpoint(final String endpointName) throws NotFoundException {
            if (lookupError != null) {
                throw lookupError;
            }
            return endpoint;
        }

        @Override protected String opaqueName() {
            return "OPAQUE.xar";
        }

        @Override protected File stage(final File xar, final String outboxDirectory, final String opaqueName) {
            return new File(outboxDirectory, opaqueName);
        }

        @Override protected String obtainToken(final GlobusEndpoint endpoint) {
            return "token";
        }

        @Override protected String submit(final String token, final GlobusClient.TransferRequest request) {
            capturedRequest = request;
            if (submitError != null) {
                throw submitError;
            }
            return "task-1";
        }

        @Override protected GlobusClient.TaskStatus await(final String token, final String taskId) {
            return taskStatus;
        }

        @Override protected RemoteConnectionResponse importByPath(final RemoteConnection connection, final String serverPath) {
            importCalled = true;
            lastImportPath = serverPath;
            return importResponse;
        }

        @Override protected RemoteConnectionResponse fallback(final String projectId, final RemoteConnection connection, final File xar) {
            fallbackCalled = true;
            return fallbackResponse;
        }

        @Override protected void cleanup(final File staged) {
            cleanupCalled = true;
        }
    }

    // --- joinPath ----------------------------------------------------------

    @Test
    void joinPathInsertsASingleSeparator() {
        assertEquals("/peer/OPAQUE.xar", GlobusXarSender.joinPath("/peer", "OPAQUE.xar"));
        assertEquals("/peer/OPAQUE.xar", GlobusXarSender.joinPath("/peer/", "OPAQUE.xar"));
        assertEquals("/OPAQUE.xar", GlobusXarSender.joinPath("", "OPAQUE.xar"));
        assertEquals("/OPAQUE.xar", GlobusXarSender.joinPath(null, "OPAQUE.xar"));
    }

    // --- supports ----------------------------------------------------------

    @Test
    void supportsWhenEnabledConfiguredAndEndpointRegistered() {
        assertTrue(new TestSender().supports("proj1"));
    }

    @Test
    void doesNotSupportWhenDisabled() {
        final TestSender sender = new TestSender();
        sender.config = new GlobusXarSender.RouteConfig(false, "ep", "/outbox", "/peer", "/srv/peer");
        assertFalse(sender.supports("proj1"));
    }

    @Test
    void doesNotSupportWhenConfigIncomplete() {
        final TestSender sender = new TestSender();
        sender.config = new GlobusXarSender.RouteConfig(true, "ep", "/outbox", "  ", "/srv/peer");
        assertFalse(sender.supports("proj1"));
    }

    @Test
    void doesNotSupportWhenEndpointMissing() {
        final TestSender sender = new TestSender();
        sender.lookupError = new NotFoundException("no such endpoint");
        assertFalse(sender.supports("proj1"));
    }

    // --- send orchestration ------------------------------------------------

    @Test
    void sendSubmitsCorrectTransferThenImports() throws Exception {
        final TestSender sender = new TestSender();
        final RemoteConnectionResponse response = sender.send("proj1", null, new File("orig.xar"));

        assertSame(sender.importResponse, response, "on success, the import response is returned");
        assertTrue(sender.importCalled);
        assertFalse(sender.fallbackCalled);
        assertTrue(sender.cleanupCalled, "the staged file must be cleaned up");

        assertEquals("outboxColl", sender.capturedRequest.sourceCollectionId());
        assertEquals("/OPAQUE.xar", sender.capturedRequest.sourcePath());
        assertEquals("inboxColl", sender.capturedRequest.destinationCollectionId());
        assertEquals("/peer/OPAQUE.xar", sender.capturedRequest.destinationPath());
        assertTrue(sender.capturedRequest.verifyChecksum());
        assertEquals("XSync proj1", sender.capturedRequest.label());
        assertEquals("/srv/peer/OPAQUE.xar", sender.lastImportPath);
    }

    @Test
    void sendFallsBackWhenTaskDoesNotSucceed() throws Exception {
        final TestSender sender = new TestSender();
        sender.taskStatus = new GlobusClient.TaskStatus("FAILED", null, "boom");

        final RemoteConnectionResponse response = sender.send("proj1", null, new File("orig.xar"));

        assertSame(sender.fallbackResponse, response);
        assertTrue(sender.fallbackCalled);
        assertFalse(sender.importCalled);
        assertTrue(sender.cleanupCalled);
    }

    @Test
    void sendFallsBackOnTransferException() throws Exception {
        final TestSender sender = new TestSender();
        sender.submitError = new RuntimeException("submit failed");

        final RemoteConnectionResponse response = sender.send("proj1", null, new File("orig.xar"));

        assertSame(sender.fallbackResponse, response);
        assertTrue(sender.fallbackCalled);
        assertTrue(sender.cleanupCalled);
    }

    @Test
    void sendFallsBackWhenEndpointMissing() throws Exception {
        final TestSender sender = new TestSender();
        sender.lookupError = new NotFoundException("no such endpoint");

        final RemoteConnectionResponse response = sender.send("proj1", null, new File("orig.xar"));

        assertSame(sender.fallbackResponse, response);
        assertTrue(sender.fallbackCalled);
        assertFalse(sender.importCalled);
    }

    // --- stage / cleanup (real file I/O) -----------------------------------

    @Test
    void stageCopiesFileAndCleanupDeletesIt(@TempDir final Path tempDir) throws Exception {
        final GlobusXarSender sender = new GlobusXarSender(null, null, null, null, null, null);

        final File source = tempDir.resolve("orig.xar").toFile();
        Files.writeString(source.toPath(), "payload");
        final String outbox = tempDir.resolve("outbox").toString();

        final File staged = sender.stage(source, outbox, "OPAQUE.xar");
        assertTrue(staged.exists());
        assertEquals("OPAQUE.xar", staged.getName());
        assertEquals("payload", Files.readString(staged.toPath()));

        sender.cleanup(staged);
        assertFalse(staged.exists());
    }
}
