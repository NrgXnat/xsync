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
import org.nrg.xsync.globus.entities.GlobusNodeConfig;
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
        boolean siteEnabled = true;
        GlobusXarSender.RouteConfig config = new GlobusXarSender.RouteConfig(true, "ep");
        // destination: name, inboxCollectionId, remoteInboxPath, remoteInboxServerDirectory
        GlobusEndpoint endpoint = new GlobusEndpoint("ep", "inboxColl", "/peer", "/srv/peer");
        // node config: clientId, clientSecret, outboxCollectionId, outboxDirectory, inboxCollectionId
        GlobusNodeConfig nodeConfig = new GlobusNodeConfig("client", "secret", "outboxColl", "/outbox", null);
        NotFoundException lookupError;
        NotFoundException nodeConfigError;
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
            super(null, null, null, null, null, null, null, null);
        }

        @Override protected boolean siteGlobusEnabled() {
            return siteEnabled;
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

        @Override protected GlobusNodeConfig nodeConfig() throws NotFoundException {
            if (nodeConfigError != null) {
                throw nodeConfigError;
            }
            return nodeConfig;
        }

        @Override protected String opaqueName() {
            return "OPAQUE.xar";
        }

        @Override protected File stage(final File xar, final String outboxDirectory, final String opaqueName) {
            return new File(outboxDirectory, opaqueName);
        }

        @Override protected String obtainToken() {
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
    void supportsWhenEnabledWithDestinationAndNodeConfig() {
        assertTrue(new TestSender().supports("proj1"));
    }

    @Test
    void doesNotSupportWhenDisabled() {
        final TestSender sender = new TestSender();
        sender.config = new GlobusXarSender.RouteConfig(false, "ep");
        assertFalse(sender.supports("proj1"));
    }

    @Test
    void doesNotSupportWhenNoDestinationSelected() {
        final TestSender sender = new TestSender();
        sender.config = new GlobusXarSender.RouteConfig(true, "  ");
        assertFalse(sender.supports("proj1"));
    }

    @Test
    void doesNotSupportWhenDestinationMissing() {
        final TestSender sender = new TestSender();
        sender.lookupError = new NotFoundException("no such destination");
        assertFalse(sender.supports("proj1"));
    }

    @Test
    void doesNotSupportWhenNodeNotConfigured() {
        final TestSender sender = new TestSender();
        sender.nodeConfigError = new NotFoundException("Globus not configured for this node");
        assertFalse(sender.supports("proj1"));
    }

    @Test
    void doesNotSupportWhenGlobusDisabledSiteWide() {
        final TestSender sender = new TestSender();
        sender.siteEnabled = false;
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

        assertEquals("outboxColl", sender.capturedRequest.sourceCollectionId(), "source is the node outbox");
        assertEquals("/OPAQUE.xar", sender.capturedRequest.sourcePath());
        assertEquals("inboxColl", sender.capturedRequest.destinationCollectionId(), "destination is the peer inbox");
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
    void sendFallsBackWhenDestinationMissing() throws Exception {
        final TestSender sender = new TestSender();
        sender.lookupError = new NotFoundException("no such destination");

        final RemoteConnectionResponse response = sender.send("proj1", null, new File("orig.xar"));

        assertSame(sender.fallbackResponse, response);
        assertTrue(sender.fallbackCalled);
        assertFalse(sender.importCalled);
    }

    @Test
    void sendFallsBackWhenNodeNotConfigured() throws Exception {
        final TestSender sender = new TestSender();
        sender.nodeConfigError = new NotFoundException("Globus not configured for this node");

        final RemoteConnectionResponse response = sender.send("proj1", null, new File("orig.xar"));

        assertSame(sender.fallbackResponse, response);
        assertTrue(sender.fallbackCalled);
        assertFalse(sender.importCalled);
    }

    // --- stage / cleanup (real file I/O) -----------------------------------

    @Test
    void stageCopiesFileAndCleanupDeletesIt(@TempDir final Path tempDir) throws Exception {
        final GlobusXarSender sender = new GlobusXarSender(null, null, null, null, null, null, null, null);

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
