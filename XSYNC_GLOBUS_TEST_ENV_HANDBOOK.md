# XSync + Globus — QA Test Environment Setup Handbook

_Draft. How to stand up the two-site test bed that the acceptance
checklist in `XSYNC_GLOBUS_ADMIN_MANUAL.md` §13 runs against._

## About this handbook

**Goal.** Build a reproducible environment with two XNATs and two Globus
Connect Server (GCS) collections so QA can exercise the full XSync–Globus
feature set: endpoint config, XSync-over-Globus, the Globus DICOM inbox,
governance, and monitoring.

**Marker convention.**

- Plain steps are real, verifiable infrastructure (XNAT, GCS, Globus Auth)
  and can be executed today.
- **[TARGET]** steps depend on the plugin's Globus features, which are not
  yet built; they reference `XSYNC_GLOBUS_ADMIN_MANUAL.md` (the projected
  behavior) and will firm up as the plan is implemented.
- **[OPEN]** flags a plan decision that affects setup (see manual §14).

**Placeholders.** `UPPERCASE_IDS` (e.g. `SRC_COLLECTION_ID`) are values
you record as you go; substitute your own. Commands are examples — never
paste an ID you haven't captured from your own environment.

---

## 1. Topology

```
        ┌─────────────── SITE A (source) ───────────────┐        ┌─────────────── SITE B (destination) ──────────┐
        │  XNAT-A (1.10.x + XSync/Globus plugin)         │        │  XNAT-B (1.10.x + XSync/Globus plugin)         │
        │    archive + XSync cache/OUTBOX  ───────┐      │        │      ┌───────  INBOX (+ DICOM/ subdir)         │
        │  GCS-A (POSIX collection over OUTBOX)    │      │        │      │   GCS-B (POSIX collection over INBOX)   │
        └──────────────────────────────────────── │ ─────┘        └───── │ ───────────────────────────────────────┘
                                                   │  Globus Transfer     │
                                                   └──────────────────────┘
                                          (checksummed, resumable, endpoint-to-endpoint)
```

Two Globus confidential clients (different credentials) let QA prove the
"multiple endpoints, distinct credentials" requirement.

### Tiers

- **Tier 1 — minimal (recommended to start).** Both XNATs and both GCS
  nodes on one or two Linux hosts/VMs, same LAN. Covers nearly all of §13.
- **Tier 2 — realistic.** Two separate hosts/networks mirroring
  production (WAN between sites), for performance/failure realism.

Build Tier 1 first; promote to Tier 2 once the flow is proven.

---

## 2. Prerequisites

- Two Linux hosts/VMs (GCS v5 supports current RHEL/Rocky/Debian/Ubuntu;
  it installs systemd services and must be set up as root).
- Docker + Docker Compose on the XNAT hosts (or your standard XNAT
  deployment method).
- Outbound HTTPS to Globus services; the GCS data-transfer ports open
  between the two nodes (Tier 2).
- A **Globus account** and a **Project** in the Globus developers console.
- A **Globus subscription** associated with the endpoint — **required**:
  this integration uses **guest collections**, which are a premium feature.
  Confirm the entitlement before building the test bed.
- The built **XSync plugin JAR** with Globus support (when available; use
  the current XSync build for governance/HTTPS testing in the meantime).
- DICOM test data (a small de-identified study or two; e.g. a public
  sample set).

---

## 3. Part A — Two XNAT instances

1. Deploy **XNAT-A** and **XNAT-B** (XNAT 1.10.x). The official
   `xnat/xnat-docker-compose` is the simplest route; or use your standard
   deployment. Give each a distinct hostname/URL you will reuse below
   (`https://xnat-a.test`, `https://xnat-b.test`).
2. Complete the first-time admin setup on each; create a site admin.
3. **Install the XSync plugin** into each XNAT's `plugins` folder and
   restart Tomcat. Install on **both** — the destination needs it for
   Globus inbox auto-ingest (manual §2). **[TARGET** for Globus features.**]**
4. Confirm the plugin loaded (XSync appears under Administer → Plugin
   Settings) and, on upgrade installs, that existing admins received the
   **Xsync Administrator** role (manual §3).
5. Note the **XNAT cache path** on XNAT-A (the XSync outbox lives here) and
   pick an **inbox path** on XNAT-B (e.g. `/data/xnat/inbox` and a project
   subtree). These become GCS collection roots in Part B.

---

## 4. Part B — Globus Connect Server on each site

Do this on GCS-A (Site A) and GCS-B (Site B). Commands per the GCS v5 CLI;
run node setup as root.

1. **Install GCS v5** per the Quickstart (add the Globus repo, install
   `globus-connect-server54`).
2. **Set up the endpoint** (interactive; creates the endpoint in Globus):
   ```bash
   globus-connect-server endpoint setup "XNAT-A Test Endpoint" \
     --organization "QA" --owner <your-globus-id>@globusid.org
   ```
   Record the **Endpoint ID**.
3. **Set up the node** (as root; starts the systemd services):
   ```bash
   sudo globus-connect-server node setup
   sudo systemctl status globus-gridftp-server
   ```
4. **Create a POSIX storage gateway:**
   ```bash
   globus-connect-server storage-gateway create posix "XNAT-A Gateway" \
     --domain test
   ```
   Record the **Storage Gateway ID**.
5. **Create a mapped collection** rooted at the XSync outbox (Site A) or
   the inbox (Site B):
   ```bash
   # Site A (source): root at the XSync cache/outbox base
   globus-connect-server collection create <STORAGE_GATEWAY_ID> \
     /data/xnat/cache/ "XNAT-A Outbox Collection"
   # Site B (destination): root at the inbox base
   globus-connect-server collection create <STORAGE_GATEWAY_ID> \
     /data/xnat/inbox/ "XNAT-B Inbox Collection"
   ```
   Record each **Collection ID** and its **root path**.
6. Record, for each site: **Endpoint ID, Collection ID, collection root
   path, and the server-local path** it maps to. XNAT needs both the
   collection-relative path (for transfers) and the server-local path (for
   import). This is the "path duality" the plan calls out — keep them
   straight. **[OPEN:** path config — plan §6.6.**]**

---

## 5. Part C — Globus Auth: confidential clients

Create **two** clients so QA can register two endpoints with different
credentials (checklist item 1).

1. Go to `https://app.globus.org/settings/developers` → **"Register a
   service account or application credential for automation."**
2. Create/select a Project; give the app a name (e.g. `xsync-qa-client-1`).
3. **Record the Client ID.** Its identity string is:
   ```
   <CLIENTID>@clients.auth.globus.org
   ```
4. **Add Client Secret** → name it → **copy it immediately** (it is shown
   once). Store securely.
5. Repeat for a second client (`xsync-qa-client-2`) with its own secret.
6. Keep a table: client name → Client ID → identity → which endpoint uses
   it.

The only scope needed is the base Transfer scope
`urn:globus:auth:scope:transfer.api.globus.org:all`. Because the QA bed
uses **guest** collections, access is governed by their **ACLs** — there is
no per-collection `data_access` dependent scope or consent (that scope is
mapped-collection-only and is rejected on guest collections with
`UNKNOWN_SCOPE_ERROR`). This was confirmed against live Globus.

---

## 6. Part D — Directory layout and ACLs

### 6.1 Directory layout

Create the directories the collections expose (owned by the XNAT service
user so XNAT can read/write them):

```
Site A (source), under the outbox collection root:
  /data/xnat/cache/xsync/outbox/<PROJECT>/        # XSync writes XARs here

Site B (destination), under the inbox collection root:
  /data/xnat/inbox/<PROJECT>/                      # XSync XARs land here
  /data/xnat/inbox/<PROJECT>/DICOM/                # upload-service DICOM inbox
```

The exact subpaths are **[TARGET]** — they must match what the plugin's
project outbox/inbox settings expect (manual §6.2, §8.1).

### 6.2 Grant the client identities access (ACLs)

Give each client identity the right permission on the right collection.
Source needs read; destination needs read-write.

```bash
# Client 1 gets READ on the Site A outbox collection
globus endpoint permission create <SRC_COLLECTION_ID>:/xsync/outbox/ \
  --permissions r --identity <CLIENT1_ID>@clients.auth.globus.org

# Client 1 gets READ/WRITE on the Site B inbox collection
globus endpoint permission create <DST_COLLECTION_ID>:/ \
  --permissions rw --identity <CLIENT1_ID>@clients.auth.globus.org
```

Repeat for Client 2 against a second endpoint if you are testing the
distinct-credentials path. Verify with:

```bash
globus endpoint permission list <DST_COLLECTION_ID>
```

### 6.3 Prove a raw transfer works (before involving XNAT)

Sanity-check the plumbing with the CLI, acting as the client credentials
(or your own identity), independent of the plugin:

```bash
echo hello > /data/xnat/cache/xsync/outbox/TEST/probe.txt
globus transfer <SRC_COLLECTION_ID>:/xsync/outbox/TEST/probe.txt \
  <DST_COLLECTION_ID>:/TEST/probe.txt
globus task list        # watch it reach SUCCEEDED
```

If this fails, fix Globus before touching XNAT. This retires the auth/ACL
and path-mapping unknowns (plan Phase 0).

---

## 7. Part E — Wire XNAT to Globus  [TARGET]

Follow `XSYNC_GLOBUS_ADMIN_MANUAL.md` §5.2:

1. On XNAT-A, Administer → Plugin Settings → XSync → **Globus Management
   Interface** → register an endpoint: paste the **Site B Collection ID**,
   its base path, and the server-local inbox path; attach **Client 1**
   credentials.
2. Register a **second** endpoint with **Client 2** credentials (for
   checklist item 1).
3. **Enable Globus** as an allowed transfer method (Connection Management,
   §5.1).
4. **Test connection** on each endpoint; expect pass for good creds and a
   clear failure for a deliberately-broken one.
5. Associate the endpoint(s) with the source test project (Part F).

---

## 8. Part F — Test projects and data

1. On **XNAT-B**, create the **destination project** (e.g. `QA_DEST`).
   Create a project owner user there and generate credentials/token for
   XSync to use (manual §6.1).
2. On **XNAT-A**, create the **source project** (e.g. `QA_SRC`) and load
   test data:
   - Upload 2–3 small DICOM sessions (compressed uploader or DICOM SCP).
   - Add a project resource and a subject resource (to test resource
     sync).
3. Configure XSync on `QA_SRC` **[TARGET]** (manual §6.2): destination =
   `https://xnat-b.test` / `QA_DEST`; transfer method = **Globus**;
   endpoint = the one from Part E; set the project **outbox** and remote
   **inbox** paths to match Part D.
4. For multi-destination testing (item 9), add a **second** destination —
   one Globus, one HTTPS — to the same project.
5. For anonymization tests, enable **Anonymize** and paste a simple Mizer
   script.

---

## 9. Part G — Governance and role fixtures

To exercise §13's governance items without needing more sites:

1. **Whitelist:** enable it; add `https://xnat-b.test` (classification
   e.g. RESEARCH). Confirm the **local site** is implicitly present
   (manual §5.4). Keep one *unlisted* URL handy to test rejection (item
   16).
2. **Blacklist:** add a throwaway project id to the project blacklist and
   confirm it cannot save a config (item 17).
3. **Role:** create a user with **only** the Xsync Administrator role (not
   Site Admin) to verify scoped access (item 18).
4. **Non-conforming connection:** create a config to an allowed site, then
   remove that site from the whitelist, so the dashboard shows a
   non-conforming connection to disable (item 19).

---

## 10. Part H — End-to-end smoke test

Proves the core path (checklist items 4–8) before a full QA pass:

1. On `QA_SRC`, mark a session **OK to Sync** if the config requires it,
   then start an **on-demand** Globus sync.
2. Watch: XAR built → appears in the Site A **outbox under an opaque
   name** → Globus **task SUCCEEDED** → XAR in the Site B **inbox** →
   session **auto-imported** at XNAT-B → status advances only **after
   archiving** → XAR **deleted** from the outbox.
3. Confirm the dashboard shows the transfer + ingest status and the
   requester received an email.

If any step stalls, see §12.

---

## 11. Reset between test runs

To re-run cleanly (incremental sync keys off prior success):

- **XNAT-A:** clear the project's XSync history/remote-map for the
  destination (so entities re-sync); empty the outbox directory; delete
  any leftover XARs in the cache.
- **XNAT-B:** clear the destination project's prearchive/archive of test
  sessions; empty the inbox directory.
- **Globus:** cancel stray tasks (`globus task cancel <TASK_ID>`); no ACL
  changes needed between runs.
- Re-mark OK-to-sync as needed.

Document the exact reset for your build; the remote-map reset detail is
**[TARGET]** (depends on the plugin's admin surface).

---

## 12. Test-bed troubleshooting

| Symptom | Check |
|---|---|
| `globus transfer` (Part D) fails auth | ACL identity string exact (`<CLIENTID>@clients.auth.globus.org`); permission on the right guest collection and subpath. (No `data_access` consent applies — guest collections use ACL + base Transfer scope.) |
| `UNKNOWN_SCOPE_ERROR` on a token request | A `data_access` scope was requested for a guest collection — valid only for mapped collections. Register/transfer against the **guest** UUID with the base Transfer scope. |
| Transfer OK by CLI but XNAT test-connection fails | Endpoint credentials in the Globus Management Interface; collection UUID; network from the XNAT host. |
| XAR lands in inbox but no import | Destination XNAT running the plugin; inbox path matches the collection root + project subpath; destination inbox watcher enabled. |
| Import happens but status never verifies | Archiving poll target/timeout; destination pipeline stalled (check prearchive). |
| Node services down | `sudo systemctl status globus-gridftp-server`; re-run `node setup`. |
| Permission denied writing outbox/inbox | Directory ownership vs. the XNAT service user and the GCS mapped identity. |

---

## 13. What each Part enables (map to acceptance checklist §13)

| Checklist items | Requires |
|---|---|
| 1–3 (foundation) | Parts B, C, E (two clients/endpoints, test connection, association) |
| 4–8 (XSync over Globus) | Parts D, F, H (outbox/inbox, project, smoke test) |
| 9 (multi-destination) | Part F step 4 |
| 10–11 (fallback / HTTPS regression) | Part F + a working HTTPS destination |
| 12 (name obfuscation) | Part H observation of outbox filenames |
| 13–15 (upload service / DICOM inbox) | Part D `DICOM/` subdir + Part F data |
| 16–20 (governance & role) | Part G |
| 21–22 (monitoring) | Dashboard + email across all runs |

---

## 14. Open dependencies (blockers to a *complete* test bed)

These must resolve before the environment can fully exercise §13; each
maps to a plan item (manual §14):

- Globus **auth** for client-credential clients (plan §4.5) — **resolved:**
  guest collections use ACL + base Transfer scope, no `data_access`
  consent (confirmed against live Globus). No longer a gate on Parts C/D.
- Plugin **Globus Management Interface**, **outbox/inbox** project
  settings, **inbox watcher/auto-import**, **archiving poll**, and
  **remote-map reset** — all **[TARGET]** (Parts E, F, H, §11).
- Guest collections + the subscription are a firm requirement, not an open
  question. (HA collections are out of scope — data is anonymized
  source-side; see plan §10 decision 4.)
- **Path config** convention (collection-relative vs. server-local) —
  Part B/D (plan §6.6).

Until the **[TARGET]** plugin surface exists, this handbook stands up the
**infrastructure** (XNATs, GCS, collections, clients, ACLs, raw transfer)
and the **governance/HTTPS** paths, which are testable today; the Globus
transport steps come online as the plan is implemented.

---

## References

- Globus Connect Server v5 Quickstart —
  https://docs.globus.org/globus-connect-server/v5/quickstart/
- GCS CLI reference (endpoint/node setup, storage-gateway, collection) —
  https://docs.globus.org/globus-connect-server/v5/reference/
- Storage Gateway — Create POSIX —
  https://docs.globus.org/globus-connect-server/v5.4/reference/storage-gateway/create/posix/
- Automate transfers with a service account (confidential client + ACL) —
  https://docs.globus.org/guides/recipes/automate-with-service-account/
- `globus endpoint permission create` —
  https://docs.globus.org/cli/reference/endpoint_permission_create/
- Companion docs: `XSYNC_GLOBUS_ADMIN_MANUAL.md` (behavior + acceptance
  checklist), `GLOBUS_TRANSFER_PLAN.md` (the plan).
