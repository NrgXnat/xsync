# XSync — Globus Transfer Support: Research & Implementation Plan

_Status: draft for review. Author: (fill in). Date: 2026-07-27
(code references re-verified against the latest `develop` revision
2026-07-28, after the Lombok/cleanup pass and the admin dashboard work)._

This plan covers adding **Globus Transfer** as a third data-movement
method for XSync, alongside the existing HTTPS and Aspera transports. It
records what we need to authenticate to Globus, how Globus Transfer maps
onto XSync's architecture, the new components required, and a phased
build order. External Globus facts are cited to the official docs (see
References); anything not yet verified is flagged `[VERIFY]`.

---

## 1. Goal and scope

**Goal.** Let a source XNAT push a project's data to a destination XNAT
by moving the packaged data over **Globus Transfer** (endpoint-to-
endpoint, managed, checksum-verified) instead of streaming it over HTTPS
or Aspera.

**Requirements.** This plan implements the *XNAT Data Import &
Synchronization* proposal. That document defines four workstreams; **this
plan delivers most of them in the client model**:
workstream 2 (Globus integration foundation — endpoint config, multiple
endpoints with distinct credentials, a site-level management UI with
connection testing), workstream 3 (Globus as an XSYNC backend — project
outbox/inbox, obfuscated filenames, multiple destinations, archiving-
status polling, delete-after-import, a status/history view), and a
**client-side** version of workstream 4 (Globus as an upload service /
DICOM inbox — a POSIX collection plus XNAT-side auto-import, no custom
connector). Workstream 1 (XSync governance) is largely built already —
see the Developer's Guide §10–§11 — with a few gaps called out in §14.
The proposal's larger vision is a **hub-and-spoke** network: a central
XNAT ingests from all DICOM devices and distributes to spokes. Section 14
maps every requirement to where it is addressed; the connector
alternative for workstream 4 is `GLOBUS_CONNECTOR_PLAN.md`, and the
choice is argued in `GLOBUS_APPROACH_COMPARISON.md`.

**In scope (first cut).** Session/experiment data — i.e. the XAR files
XSync already builds — moved over Globus, then imported on the
destination via the existing XAR import-by-path call. This is exactly
the scope Aspera covers today.

**Out of scope (first cut), revisit later.**

- Moving project/subject/assessor **resource ZIPs** over Globus (Aspera
  doesn't do these either; they stay on HTTPS for now — see §6.4).
- Replacing the XAR-build / ID-remap / anonymize / import pipeline. Globus
  changes *transport only*.
- Globus Flows / Action Providers to orchestrate transfer-plus-import in
  one managed step (a promising future direction, §9).

**Key design decision.** Globus is a drop-in alternative to the Aspera
*file-movement step*. The XAR is still built by the current pipeline,
IDs are still remapped, anonymization still runs, and the destination
still imports the XAR through `RemoteRESTService.importXar(connection,
remotePath)`. See §4.

**This is a Transfer API *client*, not a Globus *connector*.** A Globus
"connector" (per the Community Connector Program) is a server-side **Data
Storage Interface (DSI)** plugged into Globus Connect Server that exposes
a *proprietary storage system* as a Globus collection. XSync does not need
one, because XNAT stores its archive and cache on an ordinary **POSIX
filesystem**, which the **built-in POSIX connector** already exposes. This
plan makes XSync a *client* of the Transfer API over stock POSIX
collections. The connector path is a different, much larger undertaking
that solves a different problem — see §9 for the full comparison.

---

## 2. How XSync moves data today (the seam we plug into)

XSync builds a **XAR** (zip of session XML + files) per session in a
cache dir, then transports it. Two transports exist:

- **HTTPS** — `RemoteRESTServiceImpl.importXar(connection, File xar)`
  (`services/remote/RemoteRESTServiceImpl.java:143`) POSTs the XAR
  multipart to the destination's `/data/services/import`. XSync holds the
  bytes and streams them.
- **Aspera** — a **two-step "stage then import-by-path"** pattern:
  1. `AsperaClient.upload(...)` (`aspera/AsperaClient.java:28`) shells out
     to `ascp` to copy the XAR into a directory on the destination host.
  2. `XsyncExperimentTransfer.asperaXarSend(...)`
     (`local/XsyncExperimentTransfer.java:529`) then calls
     `_manager.importXar(connection, xarPath)` — the **string-path
     overload** (`RemoteRESTServiceImpl.importXar(connection, String
     xarPath)`, line 80), which POSTs to
     `/data/services/import?import-handler=XAR&localFilePath=<path>&removeLocalFileAfterImport=true`.
     The destination XNAT reads the XAR **from its own filesystem** at
     that path and imports it.

The transport choice is made in `storeXar(...)`
(`XsyncExperimentTransfer.java:306`) at these lines:

```
final RemoteConnectionResponse connectionResponse =
    (shouldUseAspera()) ? asperaXarSend(_localProject.getId(), connection, xar)
                        : _manager.importXar(connection, xar);
```

`shouldUseAspera()` (line 512) gates on `AsperaProjectPrefs.getAsperaEnabled`
plus required config. The same ternary is repeated for session XARs (line
338), scan XARs (line 401), and assessor XARs (line 451).

**This is where Globus plugs in.** Globus is functionally identical to
the Aspera path: move the XAR file to a directory on the destination host,
then call the same `importXar(connection, remotePath)`. The only thing
that changes is *how the bytes get there*.

---

## 3. Globus concepts we depend on

Verified against the Globus docs (§References).

- **Collections / endpoints.** Globus moves data between **collections**
  (a Globus Connect Server v5 mapped/guest collection, or a Globus
  Connect Personal endpoint), each identified by a **UUID** and each
  mapping a Globus namespace onto a host filesystem. Transfers are
  **collection-to-collection**. → **Each XNAT must be fronted by a Globus
  collection** whose namespace includes the relevant staging directory.
  This is a deployment prerequisite, not code (§8).
- **Transfer is asynchronous and managed.** You submit a transfer task
  and Globus performs it in the background, retrying and checksum-
  verifying. You then poll the task for completion. The backend runs at
  most 3 transfer tasks per user concurrently; others queue.
- **Transfer REST flow** (base `https://transfer.api.globus.org/v0.10`):
  1. `GET /submission_id` → `{ "value": "<uuid>", "DATA_TYPE":
     "submission_id" }` (idempotency token; re-submitting with the same
     id can't double-submit).
  2. `POST /transfer` with a **transfer document**:
     ```json
     {
       "DATA_TYPE": "transfer",
       "submission_id": "<from step 1>",
       "source_endpoint": "<source collection UUID>",
       "destination_endpoint": "<dest collection UUID>",
       "verify_checksum": true,
       "notify_on_succeeded": false,
       "notify_on_failed": false,
       "label": "xsync <project> <session>",
       "DATA": [
         { "DATA_TYPE": "transfer_item",
           "source_path": "<collection-relative path to the XAR>",
           "destination_path": "<collection-relative dest path>",
           "recursive": false }
       ]
     }
     ```
     → `202 Accepted`, `{ "task_id": "<uuid>", ... }`.
  3. `GET /task/<task_id>` → poll `status` until `SUCCEEDED` / `FAILED`
     (`ACTIVE` while running/queued).
- **`verify_checksum: true`** gives end-to-end integrity on the transfer
  leg — we can fold this into XSync's existing verification story (§6.5).

---

## 4. Authentication: what we need

Globus Auth is an OAuth2 provider. XSync runs **unattended, scheduled
transfers with no human in the loop**, so the right grant is the
**client credentials grant** with a **confidential client** — the client
authenticates as *itself* (a service identity), not on behalf of a user.

What we need to obtain and configure:

1. **A registered confidential client** (an "app" in the Globus developer
   console) → yields a **client ID** and **client secret**. The client
   secret is a credential and must be stored as a secret (§7).
2. **Client credentials token request.** POST to the Globus Auth token
   endpoint (`https://auth.globus.org/v2/oauth2/token` `[VERIFY exact
   endpoint against Auth API reference]`) with
   `grant_type=client_credentials`, HTTP Basic auth = `clientId:secret`,
   and a `scope` parameter. Client credentials grants return **access
   tokens only, no refresh tokens** — so we simply re-request when a token
   nears expiry (simpler than the XNAT alias-token refresh we already run).
3. **Scopes.** The scope requested is the base Transfer scope, and **only**
   that:
   ```
   urn:globus:auth:scope:transfer.api.globus.org:all
   ```
   XSync transfers exclusively against **guest** collections (§5, §2.1 of
   the Admin Manual). Guest collections are authorized by their **ACLs**
   plus this base Transfer scope — they do **not** use a per-collection
   `data_access` dependent scope. The `data_access` dependent scope
   (`https://auth.globus.org/scopes/<COLLECTION_UUID>/data_access`) is valid
   **only for non-high-assurance _mapped_ collections**; requesting it for a
   guest collection is rejected by Globus Auth with `UNKNOWN_SCOPE_ERROR`.
   Confirmed against live Globus (endpoint Test, 2026-09) and the Globus
   [application access guide](https://docs.globus.org/globus-connect-server/v5/application/)
   and globus-sdk `add_dependent_data_access_scope` docs.
4. **ACLs / permissions on the collections.** The client's identity must
   have **read** on the source collection and **read-write** on the
   destination collection (collection owners get rw by default; others are
   granted via `add_endpoint_acl_rule`, principal type `identity`). This
   is the Globus analog of exchanging XNAT alias tokens today (§8).
5. **Consents.** For guest collections there is **no dependent consent to
   establish**: the confidential client obtains a base Transfer token via
   client-credentials, and the guest-collection **ACL** (step 4) is what
   authorizes the read/write. This was the single biggest auth unknown in
   early drafts; it is now resolved — guest collections deliberately avoid
   the `data_access` consent friction that mapped collections carry.

**No official Java Globus SDK.** The Globus SDK is Python (plus community
JS/Go). `[VERIFY current state]`. We will implement the handful of REST
calls directly using the Apache `HttpClient` / Spring `RestTemplate`
machinery XSync already uses for XNAT REST — this is a small surface
(token, submission_id, transfer, task) and keeps us dependency-light.

---

## 5. Trust / onboarding model between two XNATs

Today, connecting two XNATs means the destination issues an **alias
token** to a source-side user, stored in `xhbm_remote_alias_entity`
(§9 of the Developer's Guide). The Globus equivalent, which runs *in
addition* to the XNAT credentials (still needed for the import-by-path
call), is:

- **Destination side** stands up a Globus collection over its XAR import-
  staging directory and shares its **collection UUID** and the
  **destination path** with the source, and grants the **source's Globus
  client identity** read-write ACL on that collection.
- **Source side** stands up a Globus collection over the XSync cache dir,
  registers a confidential client, and configures the destination
  collection UUID/path.

Recommended model (decision needed, §10): **one confidential "service"
client per source XNAT**, granted ACLs on each destination collection it
syncs to. Alternative: per-destination clients. The single-client model
is simpler to operate and mirrors "one source pushes to many
destinations."

---

## 6. Architecture and new components

Mirror the Aspera package (`org.nrg.xsync.aspera`) with a new
`org.nrg.xsync.globus` package. Aspera is the working template for
"alternate transport"; Globus follows it closely.

### 6.1 `globus/GlobusAuthService.java` (`@Service`) — implemented

- `String getTransferToken(GlobusCredentials, Collection<String>
  dataAccessCollectionIds)` — client-credentials token request for the base
  Transfer scope (guest collections need no `data_access` dependent scope;
  see §4.3). The collection-ids parameter is retained for a planned
  collection-reachability check (an `ls` in connection Test) but does not
  affect the requested scope. A `forceRefresh` overload bypasses the cache
  for connection tests.
- In-memory token cache keyed by `(client id + scope)`, with a 5-minute
  expiry margin; re-fetch on expiry (no refresh token). Since the scope is
  now the constant base Transfer scope, this is effectively one token per
  client, reused across every guest collection its ACLs cover. Analogous to
  the JSESSIONID cache in `RemoteConnectionManager` but for bearer tokens.
- Storage-agnostic: credentials are supplied by the caller
  (`GlobusEndpointService.credentialsFor(name)`), not read here (§6.7).

### 6.2 `globus/GlobusClient.java` (`@Component`)

Thin REST client over the Transfer API (Apache HttpClient, bearer auth):

- `String getSubmissionId(token)` → `GET /submission_id`.
- `String submitTransfer(token, srcColl, dstColl, srcPath, dstPath,
  label, verifyChecksum)` → `POST /transfer`, returns `task_id`.
- `TaskStatus getTaskStatus(token, taskId)` → `GET /task/{id}`.
- `boolean waitForTask(token, taskId, timeout)` — block-poll to
  `SUCCEEDED`/`FAILED` (first cut; matches Aspera's blocking
  `proc.waitFor()` and the 2-hour poll cap in
  `RemoteRESTServiceImpl.monitorAsyncImport`, line 342). Async task
  tracking is a later option (§10).
- A `GlobusStatus` progress holder analogous to `AsperaStatus`.

### 6.3 Preferences: `globus/GlobusSitePrefs` + `globus/GlobusProjectPrefs`

> **Implemented so far.** The credential + collection portion of this
> sketch is realized by the `GlobusEndpoint` Hibernate entity (§6.7) and
> its management API/UI, **not** a preference bean. A `GlobusEndpoint`
> holds `name`, `clientId`, `clientSecret`, and the node's
> `inboxCollectionId` / `outboxCollectionId` (per-node inbox/outbox — the
> abstraction arrived at in §5/§14.1 — superseding the earlier
> `source`/`destination` framing below). The base-path / path-duality and
> per-project toggle items are still open design.

Mirror `AsperaSitePrefs` / `AsperaProjectPrefs` (`@NrgPreferenceBean`,
project-scope for project prefs). Fields:

- `globusEnabled` (project) — the per-project toggle.
- `clientId` / `clientSecret` — **now on `GlobusEndpoint`** (§6.7), not a
  preference; secret never stored as a plain pref.
- inbox / outbox collection UUIDs — **now `GlobusEndpoint.inboxCollectionId`
  / `outboxCollectionId`** (§6.7). A given transfer reads the source's
  outbox and writes the destination's inbox.
- `sourceCollectionBasePath` / source filesystem root mapping.
- `destinationCollectionBasePath` — Globus namespace dest dir.
- `destinationServerImportPath` — the **server-local** path the
  destination XNAT reads for `importXar(localFilePath=...)` (see §6.6,
  the "path duality" problem).
- `transferApiBaseUrl` / `authBaseUrl` (defaults to Globus production;
  overridable for test).
- `verifyChecksum` (default true), `taskTimeout`.

### 6.4 Site-level transfer-method toggle

The `develop` branch added site toggles `httpsEnabled` and `asperaEnabled`
to `XsyncSitePreferencesBean` (§4.3 of the Developer's Guide). Add
`globusEnabled` the same way (`@NrgPreference(defaultValue = "false")`,
getter/setter, include in `XsyncSitePreferencesPojo` and `toPojo()`), and
surface it in the "Connection Management" admin tab
(`site-settings.yaml`). Transfer-method selection becomes a 3-way
resolution (HTTPS / Aspera / Globus), see §6.5.

### 6.5 Integration into `XsyncExperimentTransfer`

- Add `shouldUseGlobus()` mirroring `shouldUseAspera()` (line 512):
  gate on project `globusEnabled` + required config present; log and fall
  back if misconfigured.
- Add `globusXarSend(projectId, connection, xar)` mirroring
  `asperaXarSend` (line 529):
  1. Translate the local XAR path to a **source collection-relative
     path** (§6.6).
  2. `token = authService.getTransferToken(src, dst)`.
  3. `taskId = globusClient.submitTransfer(token, src, dst, srcPath,
     dstPath, label, verifyChecksum)`; `waitForTask(...)`.
  4. On success, call `_manager.importXar(connection,
     destinationServerImportPath + xar.getName())` — the **existing
     import-by-path REST call**, unchanged.
  5. On failure, fall back to HTTPS `importXar(connection, xar)` (Aspera
     already does this fallback), or fail the item per policy.
- Replace the two-way ternaries at lines 338, 401, 451 with a small
  **transfer-method selector** so HTTPS/Aspera/Globus resolve in one place
  rather than nested ternaries. Suggest a private
  `sendXar(projectId, connection, xar)` helper that picks the method.

### 6.6 The "path duality" problem (highest-risk detail)

Globus `source_path`/`destination_path` are **collection-relative**, but
`importXar(localFilePath=...)` needs a **server-local filesystem path** on
the destination. So for the destination we need *both*:

- the collection-relative `destination_path` (for `submit_transfer`), and
- the server-local path (for `importXar`).

These differ by the collection's root-vs-filesystem mapping. Options:

- **(A)** Configure both explicitly (`destinationCollectionBasePath` +
  `destinationServerImportPath`) and derive the per-file paths by
  appending the XAR filename. Simple, explicit, recommended for v1.
- **(B)** Assume a POSIX collection rooted at `/` so collection path ==
  filesystem path. Fragile; depends on how the collection was created.

The same duality applies on the source side: the XAR is written under the
XNAT cache by `SynchronizationManager.GET_SYNC_XAR_PATH(...)`; the source
collection must include that path, and we translate local→collection-
relative via `sourceCollectionBasePath`.

**De-risk this in the Phase-0 spike before writing production code.**

### 6.7 Credential storage (secrets)

**Decided.** Globus endpoint credentials are stored in a **Hibernate
entity**, `GlobusEndpoint` (one row per endpoint: `name`, `clientId`,
`clientSecret`, and the node's `inboxCollectionId` / `outboxCollectionId`
— at least one required), with a `GlobusEndpointRepository` and
`GlobusEndpointService`. This keeps secrets off the XFT/queryable data
model (Developer's Guide §3.3) and supports "multiple endpoints, different
credentials" (WS2). `GlobusEndpointService.credentialsFor(name)` bridges
to the `GlobusCredentials` that `GlobusAuthService` consumes. Non-secret
config (collection paths, toggles) can remain ordinary `@NrgPreference`s.

**The secret is stored in plaintext for now** — matching the existing
`RemoteAliasEntity`, which stores its alias secret in plaintext today.

### 6.8 Secret encryption (planned, not yet implemented)

At-rest encryption of stored secrets is deferred pending team discussion.
Intended approach and constraints:

- **Mechanism:** a JPA `AttributeConverter<String,String>` on the secret
  column (AES/GCM), transparent to the rest of the code; the DB stores
  Base64 ciphertext. The key lives in XNAT app config/env, **not** the DB.
- **Threat model (agreed):** deliberately *not* protecting against an
  authorized user who has both DB and app-config access; the goal is only
  that secrets are not readable by casual `SELECT` — a reader must take an
  explicit decrypt step. (So plain Base64 is insufficient; use real
  encryption.)
- **Apply consistently:** encrypt both `GlobusEndpoint.clientSecret` and
  `RemoteAliasEntity`'s alias secret, so the codebase is uniform.
- **Migration (the reason it's deferred):** existing XSync deployments
  hold **plaintext** secrets in `RemoteAliasEntity` (and, once shipped,
  `GlobusEndpoint`). Turning on encryption needs an upgrade migration that
  encrypts existing rows and a way to tell encrypted from plaintext values
  (e.g. a version/prefix marker or a one-time "encrypt on next load"
  pass). Design this with the team before enabling encryption.

---

## 7. XAPI and UI

- **`XsyncPreferencesController`** — add Globus site/project preference
  GET/POST endpoints, mirroring the Aspera ones
  (`xsyncProjectPreferences/project/{id}/aspera`) and the
  `httpsEnabled`/`asperaEnabled` getters. Add `globusEnabled` getters at
  site and project scope. **Gate them with the new authorizer layer**
  (§11.5 of the Developer's Guide): site-level Globus config →
  `@AuthDelegate(XsyncAdministratorUserAuthorization.class)`; project-level
  → `XsyncEditProjectUserAuthority` / `XsyncReadProjectUserAuthority` with
  `restrictTo = AccessLevel.Authorizer` and `projectId` as the first
  method argument. Do **not** reintroduce inline `canEditProject` checks.
- **Spawner YAML** — add a "Globus" project settings panel
  (`project-settings.yaml`, mirroring the Aspera panel) for
  client/collection/path config, and a `globusEnabled` switch in the
  site "Connection Management" tab (`site-settings.yaml`).
- **Admin JS** — extend the connection-manager UI
  (`admin/xsyncConnectionManager.js`) to include Globus enable/disable and
  a "test connection" action (submit a no-op / list-collection call). The
  latest `develop` also adds a **configuration dashboard**
  (`admin/xsyncConfigurationDashboard.js`, `/xsync/dashboard`, backed by
  `XsyncConfigurationService`) that lists every configured remote
  destination and lets an admin enable/disable it; Globus destinations
  should show up there too (a Globus connection is still an
  `XsyncXsyncprojectdata` config with a `remote_url`), so no dashboard
  change is required beyond making sure Globus-configured projects are
  represented in the sync history the dashboard reads.

---

## 8. Deployment prerequisites (ops, not code)

For the plugin to work, each participating XNAT needs:

- A **Globus Connect Server v5** (or Connect Personal) **collection** over
  the relevant staging directory (source: the XSync XAR cache; dest: the
  XAR import-staging dir the XNAT can read).
- The destination admin grants the **source's Globus client identity**
  read-write ACL on the destination collection, and shares the collection
  UUID + destination path.
- Network/firewall for GridFTP/HTTPS as required by GCSv5.

Document this as an onboarding runbook (Phase 5). It is the operational
counterpart to today's "exchange alias tokens" step.

---

## 9. Alternatives considered

### 9.1 Building a custom Globus connector (DSI) — **not the same as this plan**

The Globus [Community Connector Program](https://www.globus.org/connectors/community-connector-program)
invites organizations to develop a **connector** for their storage. It is
worth being explicit that **a connector is not what this plan describes**,
and is not what XSync needs for server-to-server data exchange.

> The connector path is worked up as a full **alternative** in
> `GLOBUS_CONNECTOR_PLAN.md` (it also enables a Globus **DICOM inbox**),
> and the two approaches are compared with a recommendation in
> `GLOBUS_APPROACH_COMPARISON.md`. The summary below is why *this* plan
> stays a client; the comparison doc is the place to make the actual
> choice, since a connector serves a broader goal than XSync transport.

**What a connector actually is.** A connector is a **Data Storage
Interface (DSI)** — a server-side plugin to **Globus Connect Server**
that exposes a storage system as a Globus collection so Globus can read
and write it like a filesystem. Connectors exist for *non-POSIX /
proprietary* back-ends: S3, tape/HPSS, SpectraLogic BlackPearl,
ActiveScale, etc. Globus Connect Server already "supports all
POSIX-compliant file systems" through its **built-in POSIX connector** —
you only develop a custom connector when your storage is *not* a POSIX
filesystem. (Sources: the Connectors catalog and the premium-storage-
connectors docs, §References.)

**Why XSync does not need one.** XNAT keeps its archive and the XSync XAR
cache on an ordinary **POSIX filesystem on disk**. The stock POSIX
connector already makes those directories available as a Globus
collection. Writing a DSI to re-expose a POSIX filesystem *as* a POSIX
filesystem adds nothing.

**Why the client approach is the better fit for our goal:**

1. **Effort and ownership.** A connector is a partnership-scale program:
   implement Globus's DSI SPI, provide an open-source license, pass Globus
   code review and validation, and **maintain compatibility across every
   future Globus Connect Server release**. It couples us to Globus's
   connector SPI and cadence. The client approach is ~4 stable public REST
   calls (token / submission_id / transfer / task) reusing the existing
   Aspera seam (§6.5), and couples us only to the public Transfer/Auth
   APIs.
2. **Deployment burden on every site.** A connector must be installed and
   maintained by a sysadmin on each site's Globus **data transfer nodes**,
   version-locked to the XNAT DSI. The client approach asks each site only
   to stand up a stock GCS POSIX collection over a staging directory —
   the same class of ops task as running an Aspera receiver today (§8).
3. **It protects the pipeline.** XSync's value is that every session is
   ID-remapped, de-identified, and filtered *before* it lands. A DSI that
   exposed the XNAT archive natively to Globus would invite writing bytes
   straight into the archive, **bypassing** that pipeline — exactly what
   must not happen. Keeping XNAT a *client* that submits already-packaged,
   already-anonymized XARs keeps the pipeline mandatory.
4. **Right tool for the stated goal.** Our goal is scheduled, server-to-
   server XAR exchange between two XNATs (§1). The Transfer API is the
   client-side tool for exactly that. A connector solves a *different*
   problem (below).

**When a connector *would* be the right investment.** If the goal were to
let Globus users **interactively browse and transfer XNAT data by its
logical hierarchy** (project → subject → experiment), with XNAT
permissions enforced, treating an XNAT as a first-class Globus endpoint —
that is a genuine "XNAT-as-a-Globus-collection" product feature, and a
DSI is the sanctioned way to build it. It is a separate initiative with a
different owner and timeline, and even then the POSIX connector plus path
mapping may cover simple cases without a custom DSI. It is **not** a
prerequisite for, or a competitor to, the server-to-server transport this
plan delivers; the two could coexist later.

### 9.2 Other alternatives

- **Bulk archive directory sync over Globus (skip XAR/import).** Rejected
  for v1: it bypasses ID remapping, anonymization, and filtering, which
  are the whole point of XSync. Globus-as-transport preserves all of that.
- **Globus Flows / Action Providers** to run transfer-then-import as one
  managed, retryable flow. Attractive long-term (removes our block-poll
  and our import-trigger coupling), but larger scope and a new dependency;
  revisit after v1 proves the transport.

---

## 10. Decisions needed before/along the way

1. **Client / endpoint model:** the proposal (WS2) mandates support
   for **multiple Globus endpoints, possibly with different credentials**,
   per project. So the model is **not** one global client — it is a set of
   configured endpoints, each with its own credential (client id/secret or
   token), any of which a project may target (WS3 also requires **multiple
   destinations per project**). Decide whether credentials are per-endpoint
   or shared where the same Globus identity spans endpoints (§14, §5).
2. **Secret storage:** _Resolved_ — Hibernate entity (`GlobusEndpoint`),
   secret plaintext for now; at-rest encryption + migration deferred to
   team discussion (§6.7, §6.8).
3. **Sync vs async task handling:** block-poll (recommended v1, matches
   Aspera) vs. true async task tracking integrated with
   `SyncStatusService` (§6.2).
4. **Collection type:** _Resolved_ — XSync uses **guest** collections
   (non-high-assurance), authorized by ACLs + the base Transfer scope, with
   no `data_access` dependent scope or consent (§4.3, §4.5). This was
   confirmed against live Globus. High-assurance collections remain a
   separate future consideration only if unanonymized PHI must transit
   Globus; XSync anonymizes source-side, so the transferred XAR is already
   de-identified.
5. **Path config:** explicit dual paths (A) vs. rooted-at-`/` assumption
   (B) (§6.6).
6. **Governance interplay (whitelist / blacklist / role):** the `develop`
   governance layer (§10 of the Developer's Guide) has three parts,
   all of which a Globus destination must respect:
   - The **whitelist** gate is inlined in `XsyncSetupController.setup`
     (`XsyncSetupController.java:86-90`) against the config `remote_url`; a
     Globus connection is still an `XsyncXsyncprojectdata` with a
     `remote_url`, so it is already covered — but decide whether a Globus
     destination *also* needs an allow-list keyed on **collection UUID**
     (a URL match alone doesn't constrain which collection bytes land in).
     If so, extend the same setup gate / `XsyncConfigurationService`
     rather than adding a parallel mechanism, so the dashboard audit view
     covers Globus too.
   - The **project blacklist** (`XsyncSetupController.java:79-81`,
     `enableOrDisableSingleConnection`) already blocks Globus configs for
     barred projects for free — no work, just don't bypass `setup`.
   - The **Xsync Administrator role** governs the admin surface; Globus
     admin config must go through the authorizer layer (§7).

---

## 11. Phased implementation

- **Phase 0 — Auth + transport spike (de-risk).** No plugin code. In a
  dev environment: register a confidential client; obtain a base Transfer
  token via client-credentials; `submit_transfer` a XAR file between two
  guest collections the client holds ACLs on; confirm the destination XNAT
  can `importXar` by path. The auth/consent unknown (§4.5) is already
  resolved (guest collections: base scope + ACL, no `data_access`);
  path-duality (§6.6) remains. **Exit criteria:** a XAR round-trips
  end-to-end via Globus by hand.
- **Phase 1 — Auth + client services.** `GlobusAuthService` (token +
  cache) and `GlobusClient` (submission_id / transfer / task / wait), with
  unit tests (mock HTTP) and one integration test against the dev
  collections. No wiring into the sync pipeline yet.
- **Phase 2 — Globus foundation: preferences + management UI (WS2).**
  `GlobusSitePrefs` / `GlobusProjectPrefs` holding a **set** of endpoints
  with per-endpoint credentials; secret storage; the site **Globus
  Management Interface** (register/edit endpoints, associate with
  projects, **test connection**); `globusEnabled` toggle. WS2 is the
  stated prerequisite for WS3/WS4.
- **Phase 3 — Wire into the send path (WS3 outbox).**
  `shouldUseGlobus()` + `globusXarSend()` writing to the project **outbox**
  with **obfuscated filenames**; refactor the transport ternaries in
  `XsyncExperimentTransfer` (lines 338/401/451) into a per-target selector
  supporting **multiple destinations**; reuse `importXar` for the
  triggered path. Session, scan, and assessor XARs.
- **Phase 4 — Destination inbox + archiving-status polling (WS3/WS4).**
  Destination-side **inbox watcher/auto-import**; **poll archiving
  status** through to archived; **delete XAR after successful import**;
  fold `verify_checksum` into verification; integrate task + ingest
  progress with `SyncStatusService` and the dashboard.
- **Phase 5 — Upload Service (WS4, client model).** Per-project inbox with
  expected subdirs (`DICOM/`), directory-watch auto-import, customizable
  import-handler SPI, status view, prearchive-comparable permissions
  (§14.2).
- **Phase 6 — Hardening + docs + ops.** Failure handling + fallback;
  token-expiry; governance interplay (§10 decision 6); onboarding runbook
  (§8); update the Developer's Guide; `xnat_rest_tests` where feasible.

Later / optional: resource-ZIP transfers over Globus (§1); Globus Flows
(§9); GCS Manager endpoint provisioning (§14.1); the custom-connector
route to WS4 (`GLOBUS_CONNECTOR_PLAN.md`).

WS1 governance gaps (Helm propagation, hide Aspera UI, classification
handling, filtered/shared sync) are tracked in §14.3.

---

## 12. Testing

- **Unit:** `GlobusAuthService` token cache/expiry; `GlobusClient` request
  building and task-status parsing (mock HTTP).
- **Integration:** against two real dev collections — token acquisition,
  submit, poll to SUCCEEDED, then import-by-path on a dev XNAT. Gate
  behind config so CI without Globus creds skips it.
- **End-to-end:** a project sync with `globusEnabled`, verifying a session
  lands and imports on the destination and the manifest/history reflect a
  verified transfer.

---

## 13. Effort / risk summary

- **Lowest risk:** the send-path wiring — it's a near-clone of the proven
  Aspera path and reuses `importXar` untouched.
- **Highest risk / do first:** ~~Globus auth consents~~ _retired_ — the
  auth/consent question is resolved (guest collections: base Transfer scope
  + ACL, no `data_access`; confirmed against live Globus). The remaining
  first-order risk is the **path duality** (§6.6); the Phase-0 spike now
  exists to retire that.
- **New external dependency:** none required if we hand-roll the ~4 REST
  calls (no official Java SDK). `[VERIFY]`

---

## 14. Requirements incorporation & traceability

This section maps the proposal's requirements onto this plan and gives
the design detail those requirements call for. Governance (workstream 1)
is covered by the shipped code — see the Developer's Guide — so only its
*gaps* appear here.

### 14.1 Design points the requirements call for

- **Project "outbox" and "inbox" (WS3).** Model each project's Globus
  configuration as a named **outbox** (a directory the source project's
  Globus collection exposes, where XSync writes XARs) and, on the
  receiving side, an **inbox** (a directory the destination project's
  collection exposes, which the destination XNAT auto-ingests). These are
  the collection paths handled in §6.3/§6.6, and both are per-project
  preferences.
- **Multiple endpoints, multiple credentials (WS2).** The preference model
  (§6.3) holds a **set** of Globus endpoints, each with its own
  credential (see §10 decision 1). A project may target
  **several destinations** (WS3), so a project's Globus config is a *list*
  of (endpoint, inbox) targets, not one — the send path (§6.5) iterates
  them, and the transfer-method selector resolves per target.
- **Site Globus Management Interface + connection test (WS2).** Add a
  Plugin-Settings admin UI (and XAPI) to register/edit Globus endpoints,
  **test connectivity** (a no-op `GET /submission_id` or an
  `ls`/endpoint-status call proves auth + reachability), and associate
  endpoints with projects. "Create new endpoints for projects" implies
  provisioning **guest collections** via the GCS Manager API — larger than
  a Transfer client; flag as `[VERIFY / possible Phase-2 stretch]`.
  Gate this UI with `@AuthDelegate(XsyncAdministratorUserAuthorization)`.
- **Filename obfuscation in transit (WS3).** File names must not be
  visible to the transfer service. The XAR is already a zip (inner names
  contained), but the **XAR filename itself** and any staged path segments
  must be opaque: name staged files by a **hash/opaque token** and keep
  the label↔token mapping inside the manifest/DB, not in the path. Apply
  on write to the outbox; the destination restores context after ingest.
- **Destination-side auto-ingest from the inbox (WS3, WS4).** The
  **destination** XNAT must **watch its Globus inbox and import
  automatically**, rather than the source triggering `importXar`.
  Implement a destination-side inbox watcher/importer (a scheduled scan or
  Globus task-completion signal → Import Service). **Deployment
  consequence:** the Globus path requires plugin logic on the destination
  too — unlike traditional XSync, which installs only on the source.
- **Archiving-status polling (WS3).** The sync-confirmation/verification
  step must **poll the destination's archiving status** (prearchive →
  archive), not just the Globus task. Extend the verification path (Dev
  Guide §7.4 `verifySync`) to poll the destination Import Service /
  prearchive until archived or failed, feeding `SyncStatusService`.
- **Delete-after-import (WS3).** Once the destination confirms successful
  import, delete the XAR from the local (outbox) endpoint if Globus has
  not already removed it — mirror the existing post-import cleanup.
- **Status/history management view (WS3, WS4).** Surface Globus transfer +
  ingest status/history in the admin **Configuration Dashboard** (Dev
  Guide §11.4). Globus connections are ordinary `XsyncXsyncprojectdata`
  configs, so they appear there already; add transfer/task state and inbox
  ingest state to the dashboard read-models.
- **Non-Globus unaffected (WS3).** The per-target transfer-method selector
  (§6.5) already makes HTTPS/Aspera/Globus coexist on the same server and
  projects; regression-test traditional XSync alongside Globus.

### 14.2 Workstream 4 — Globus as an Upload Service (client model)

WS4 (auto-import of data placed in expected directory structures; a
"DICOM subdirectory ... acts like a DICOM Inbox") is achievable **without
a custom connector**: expose an XNAT-side inbox directory through a stock
**POSIX** Globus collection, and have XNAT auto-import what lands there —
exactly XNAT's existing filesystem **DICOM Inbox** feature (Dev Guide
data-model; XNAT KB `xnat-features/file-management.md`). Scope for this
plan:

- A per-project Globus **inbox** with expected subdirectories (e.g.
  `DICOM/`), watched by a destination-side importer that calls the Import
  Service / inbox importer; XNAT's normal routing → anonymization →
  prearchive → archive then runs.
- A **customizable import handler** SPI so new data formats define their
  own ingestion (mirror XNAT's import-handler extension point).
- A **status view** of data available / in-transit / imported (the
  dashboard, above).
- **Prearchive-comparable permissions:** Globus ACLs on the inbox
  collection govern who may write; XNAT prearchive permissions govern
  review/archive after landing.

The **custom-connector** route to WS4 (a DSI that virtualizes XNAT and
enforces per-user XNAT permissions at transfer time) is written up
separately in `GLOBUS_CONNECTOR_PLAN.md`; it is **not required** by these
requirements but adds richer per-user egress. See the comparison doc.

### 14.3 Governance gaps (workstream 1) not yet in the code

Most of WS1 is shipped (Dev Guide §10–§11). Outstanding per the proposal:

- **Whitelist propagation across the AIS, including via Helm charts**
  (WS1.1) — deploy-time provisioning of the whitelist (and the seed JSON)
  through Helm; not yet present. Track as a deployment/config task.
- **Hide Aspera references throughout the XSync UI** (WS1.4) — the UI
  still surfaces Aspera (Dev Guide §14–§15). With Globus arriving as the
  preferred backend, hide/retire Aspera UI affordances.
- **Security classifications drive sensitive-data handling** (WS1.1a) —
  `SiteClassification` (PUBLIC/RESEARCH/CLINICAL) exists on whitelist
  entries but is not yet used to *constrain* configs; wire it into
  validation/warnings.
- **Filtered configs + sync only necessary/shared data** (WS1.5) — XSync
  filtering exists (Dev Guide §7.3); confirm coverage for "shared data"
  and only-what's-necessary and close any gaps.

### 14.4 Traceability table

| Requirement (from the proposal) | Where addressed |
|---|---|
| WS1 governance (whitelist, blacklist, admin role, dashboard) | Shipped — Dev Guide §10–§11; gaps in §14.3 |
| WS1.1 Helm/AIS propagation; WS1.4 hide Aspera; WS1.1a classification handling; WS1.5 filtered/shared | §14.3 (open) |
| WS2 Globus foundation: project endpoints, multi-endpoint/credentials, site mgmt UI, test connection | §4, §5, §6.1–§6.3, §7, §14.1; endpoint provisioning `[VERIFY]` |
| WS3 outbox/inbox; move XAR to outbox → trigger remote inbox | §6.5, §14.1 |
| WS3 auto-ingest from inbox | §14.1, §14.2 (destination watcher) |
| WS3 delete-after-import | §14.1 |
| WS3 poll archiving status | §14.1 (extends §7.4 `verifySync`) |
| WS3 status/history view | §14.1 (Dashboard, Dev Guide §11.4) |
| WS3 non-Globus unaffected | §6.4/§6.5 transfer-method selector |
| WS3 multiple destinations per project | §10.1, §14.1 |
| WS3 filename obfuscation | §14.1 |
| WS4 upload service / DICOM inbox (directory-watch auto-import) | §14.2 (client model); DSI alternative in `GLOBUS_CONNECTOR_PLAN.md` |
| WS4 customizable import handler | §14.2 |
| WS4 management/status monitoring | §14.2 (Dashboard) |
| WS4 prearchive-comparable permissions | §14.2 |

---

## References (verified 2026-07-27; connector refs added 2026-08-03)

- Globus Auth API reference — https://docs.globus.org/api/auth/reference/
- Accessing Globus Connect Server with Application (client) Credentials —
  https://docs.globus.org/globus-connect-server/v5/use-client-credentials/
- Clients, Scopes, and Consents —
  https://docs.globus.org/guides/overviews/clients-scopes-and-consents/
- Transfer API — Task Submission (submission_id, transfer document) —
  https://docs.globus.org/api/transfer/task_submit/
- Transfer API — Task Management (task status) —
  https://docs.globus.org/api/transfer/task/
- Transfer API — Overview —
  https://docs.globus.org/api/transfer/overview/
- Community Connector Program (connector = DSI; §9.1) —
  https://www.globus.org/connectors/community-connector-program
- Connectors catalog (POSIX built-in; S3/tape/etc. are connectors) —
  https://www.globus.org/connectors
- Premium storage connectors docs (DSI examples: S3, POSIX staging,
  BlackPearl, ActiveScale) —
  https://docs.globus.org/premium-storage-connectors/

_Items marked `[VERIFY]` must be confirmed against current Globus docs or
in the Phase-0 spike before implementation; treat them as open until then._
