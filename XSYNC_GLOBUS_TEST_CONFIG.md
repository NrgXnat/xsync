# Configuring a Globus Sync Test via the XAPI

How to configure a source project to sync to a destination over Globus, using
the XAPIs directly. Written for the two-node test bed (source **site1** →
destination **site2**); substitute your own ids/paths.

Configuration is in three layers:

- **Node** (site, once per XNAT): this node's own Globus service account and
  outbox. Shared by every transfer the node sends.
- **Destination** (site, one per peer): a peer's inbox collection and the paths
  a transfer writes to.
- **Project** (per project): whether Globus is on, and which destination to use.

All calls are against the **source** XNAT (site1) unless noted. Examples use
basic auth; a session cookie works too.

---

## What must already be in place

1. **Guest collections + ACLs** on both nodes, per the admin manual: site1's
   outbox + inbox, site2's outbox + inbox, with **site1's service-account client**
   granted `rw` on site2's inbox guest collection (scoped to its subpath, e.g.
   `/site1`).
2. **The XSync plugin (Globus build)** deployed on both nodes.
3. **A standard XSync destination configured for the project** (via
   `/xapi/xsync/setup/projects/{projectId}`): destination URL, destination
   project, and destination credentials. This is still required — Globus moves
   the bytes, but the final **import-by-path** call uses the normal
   destination connection (alias token). Globus is the transport, not a
   replacement for the destination connection.

---

## Step 1 — Configure this node's service account + outbox (site level)

One record per node, holding **site1's own client credentials** and **site1's
outbox**. Requires the **Xsync Administrator** role.

**Do this in the admin UI** — **Administer → Plugin Settings → XSync → Site
Level Configuration → Globus Node**. The UI is a thin front-end over the XAPI
below (identical operations), so if you have already saved the node config
there, skip to Step 2.

The equivalent XAPI, for scripting:

```bash
curl -u admin:PASS -X POST https://site1.example/xapi/xsync/globus/node \
  -H 'Content-Type: application/json' \
  -d '{
        "clientId": "<SITE1_CLIENT_ID>",
        "clientSecret": "<SITE1_CLIENT_SECRET>",
        "outboxCollectionId": "<SITE1_OUTBOX_GUEST_UUID>",
        "outboxDirectory": "/opt/data/cache/xsync-globus-outbox",
        "inboxCollectionId": "<SITE1_INBOX_GUEST_UUID>"
      }'
```

| Field | Meaning | Example |
|---|---|---|
| `clientId` | site1's confidential-client (application) id | `<SITE1_CLIENT_ID>` |
| `clientSecret` | the client secret (write-only; blank on update keeps the stored value) | `<SITE1_CLIENT_SECRET>` |
| `outboxCollectionId` | UUID of site1's outbox guest collection | `<SITE1_OUTBOX_GUEST_UUID>` |
| `outboxDirectory` | collection-relative path in the outbox where XSync stages XARs | `"/opt/data/cache/xsync-globus-outbox"` |
| `inboxCollectionId` | UUID of site1's inbox guest collection (optional for a send-only node) | `<SITE1_INBOX_GUEST_UUID>` |

Read it back (the secret is never returned):

```bash
curl -u admin:PASS https://site1.example/xapi/xsync/globus/node
```

---

## Step 2 — Register the destination (site level)

One record per peer. The destination "site2" holds **site2's inbox**
guest-collection UUID and the paths a transfer to site2 writes to. It carries no
credentials — those come from the node service account in Step 1. Requires the
**Xsync Administrator** role.

**Do this in the admin UI** — **Administer → Plugin Settings → XSync → Site
Level Configuration → Globus Endpoints** — add the destination and click
**Test**. If Test passed there, skip to Step 3.

The equivalent XAPI:

```bash
curl -u admin:PASS -X POST https://site1.example/xapi/xsync/globus/endpoints \
  -H 'Content-Type: application/json' \
  -d '{
        "name": "site2",
        "inboxCollectionId": "<SITE2_INBOX_GUEST_UUID>",
        "remoteInboxPath": "/site1",
        "remoteInboxServerDirectory": "/opt/data/xsync-globus-inbox/site1"
      }'

# Test (same as the UI's Test action; obtains a token with the node service
# account and does an ls against the destination inbox):
curl -u admin:PASS -X POST https://site1.example/xapi/xsync/globus/endpoints/site2/test
# -> true
```

| Field | Meaning | Example |
|---|---|---|
| `name` | unique name for this destination | `"site2"` |
| `inboxCollectionId` | UUID of site2's inbox guest collection | `<SITE2_INBOX_GUEST_UUID>` |
| `remoteInboxPath` | collection-relative path in site2's inbox that site1 writes to | `"/site1"` |
| `remoteInboxServerDirectory` | site2's server-local dir where the file lands, for import-by-path | `"/opt/data/xsync-globus-inbox/site1"` |

**Why two destination paths?** A transfer and the import use different
addressings: `remoteInboxPath` is where Globus writes *within site2's inbox
collection*; `remoteInboxServerDirectory` is site2's *filesystem* path the
import service reads. They must point at the same place — the collection is
just the Globus view of that directory.

A green Test confirms the node service account authenticates and the
destination inbox resolves; the inbox **ACL subpath** is only fully exercised
by an actual transfer (Step 4).

---

## Step 3 — Set the project's Globus configuration

Requires project **edit** access. The project layer is just an on/off switch
and a choice of destination:

| Field | Meaning | Example |
|---|---|---|
| `globusEnabled` | turn on Globus for this project | `true` |
| `globusEndpointName` | the destination registered in Step 2 | `"site2"` |

```bash
curl -u owner:PASS -X PUT \
  https://site1.example/xapi/xsync/globus/projects/PROJECT_A/config \
  -H 'Content-Type: application/json' \
  -d '{
        "globusEnabled": true,
        "globusEndpointName": "site2"
      }'
```

Read it back:

```bash
curl -u owner:PASS \
  https://site1.example/xapi/xsync/globus/projects/PROJECT_A/config
```

In the project UI this is the **Configure Globus** button (enable + a
destination dropdown).

---

## Step 4 — Run a sync and watch

Start an on-demand sync for the project the normal way (XSync operations
API / project XSync page). With the config above, `XarSenderResolver` selects
`GlobusXarSender` (it reports "Using Globus for the data transfer method." in
`xsync.log`).

Expected sequence in `xsync.log` and on disk:

1. XAR built in the project cache.
2. Staged into the node's `outboxDirectory` under an **opaque** name (e.g.
   `3f9c…​.xar`).
3. Globus transfer submitted (a `task_id` is logged) and polled to
   **SUCCEEDED**; the file appears in site2 at
   `remoteInboxServerDirectory/<opaque>.xar`.
4. `importXar` is triggered against site2 (`localFilePath=<that path>`,
   `removeLocalFileAfterImport=true`); the session imports on site2.
5. The staged outbox file on site1 is deleted.

---

## If it falls back to HTTPS

`GlobusXarSender` **falls back to HTTPS on any Globus failure** (bad config,
transfer failure, unreachable node) so a sync is not lost. If you see an HTTPS
transfer where you expected Globus, check `xsync.log` for the Globus warning
that preceded the fallback, then:

- node config complete? (service account + outbox set — a blank field
  silently disables Globus.)
- `globusEnabled` true and `globusEndpointName` naming a registered
  destination whose **Test** passes?
- the guest-collection ACL for site1's service-account client present on site2's
  inbox (scoped to `remoteInboxPath`)?
- node reachability / firewall (see the admin manual troubleshooting).

---

## Teardown

```bash
curl -u owner:PASS -X PUT \
  https://site1.example/xapi/xsync/globus/projects/PROJECT_A/config \
  -H 'Content-Type: application/json' -d '{"globusEnabled": false}'
```

Disabling drops the project back to HTTPS; the node config, destination
registration, and collections remain for the next test.
