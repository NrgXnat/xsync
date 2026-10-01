# Configuring a Globus Sync Test via the XAPI

How to configure a source project to sync to a destination over Globus, using
the XAPIs directly (there is no project UI yet). Written for the two-node test
bed (source **cb1** → destination **cb2**); substitute your own ids/paths.

All calls are against the **source** XNAT (cb1) unless noted. Examples use
basic auth; a session cookie works too.

---

## What must already be in place

1. **Guest collections + ACLs** on both nodes, per the admin manual: cb1's
   outbox + inbox, cb2's outbox + inbox, with cb1's client granted `rw` on
   cb2's inbox guest collection (scoped to its subpath, e.g. `/cb1`).
2. **The XSync plugin (Globus build)** deployed on both nodes.
3. **A standard XSync destination configured for the project** (via
   `/xapi/xsync/setup/projects/{projectId}`): destination URL, destination
   project, and destination credentials. This is still required — Globus moves
   the bytes, but the final **import-by-path** call uses the normal
   destination connection (alias token). Globus is the transport, not a
   replacement for the destination connection.

---

## Step 1 — Register the Globus endpoint (site level)

The endpoint record for "cb1 sends to cb2" holds **cb1's own client
credentials**, **cb1's outbox** guest-collection UUID, and **cb2's inbox**
guest-collection UUID. Requires the **Xsync Administrator** role.

**Do this in the admin UI** — **Administer → Plugin Settings → XSync → Site
Level Configuration → Globus Endpoints** — add the endpoint and click
**Test**. The UI is a thin front-end over the XAPI below (identical
operations), so **if you have already registered the endpoint there and Test
passed, Step 1 is complete** — skip to Step 2.

The equivalent XAPI, for scripting:

```bash
curl -u admin:PASS -X POST https://cb1.example/xapi/xsync/globus/endpoints \
  -H 'Content-Type: application/json' \
  -d '{
        "name": "cb2",
        "clientId": "<CB1_CLIENT_ID>",
        "clientSecret": "<CB1_CLIENT_SECRET>",
        "outboxCollectionId": "<CB1_OUTBOX_GUEST_UUID>",
        "inboxCollectionId":  "<CB2_INBOX_GUEST_UUID>"
      }'

# Test (same as the UI's Test action; does an ls against the collections):
curl -u admin:PASS -X POST https://cb1.example/xapi/xsync/globus/endpoints/cb2/test
# -> true
```

A green Test confirms the client authenticates and the collections resolve;
the inbox **ACL subpath** is only fully exercised by an actual transfer
(Step 3).

---

## Step 2 — Set the project's Globus configuration (new XAPI)

Requires project **edit** access. The five fields:

| Field | Meaning | Example |
|---|---|---|
| `globusEnabled` | turn on Globus for this project | `true` |
| `globusEndpointName` | the endpoint registered in Step 1 | `"cb2"` |
| `outboxDirectory` | local dir backing cb1's outbox collection (where the XAR is staged) | `"/opt/data/cache/xsync-globus-outbox"` |
| `remoteInboxPath` | collection-relative path in cb2's inbox guest collection that cb1 writes to | `"/cb1"` |
| `remoteInboxServerDirectory` | cb2's server-local dir where the file lands, used for import-by-path | `"/opt/data/xsync-globus-inbox/cb1"` |

```bash
curl -u owner:PASS -X PUT \
  https://cb1.example/xapi/xsync/globus/projects/PROJECT_A/config \
  -H 'Content-Type: application/json' \
  -d '{
        "globusEnabled": true,
        "globusEndpointName": "cb2",
        "outboxDirectory": "/opt/data/cache/xsync-globus-outbox",
        "remoteInboxPath": "/cb1",
        "remoteInboxServerDirectory": "/opt/data/xsync-globus-inbox/cb1"
      }'
```

Read it back:

```bash
curl -u owner:PASS \
  https://cb1.example/xapi/xsync/globus/projects/PROJECT_A/config
```

**Why two destination paths?** A transfer and the import use different
addressings: `remoteInboxPath` is where Globus writes *within cb2's inbox
collection*; `remoteInboxServerDirectory` is cb2's *filesystem* path the
import service reads. They must point at the same place — the collection is
just the Globus view of that directory.

---

## Step 3 — Run a sync and watch

Start an on-demand sync for the project the normal way (XSync operations
API / project XSync page). With the config above, `XarSenderResolver` selects
`GlobusXarSender` (it reports "Using Globus for the data transfer method." in
`xsync.log`).

Expected sequence in `xsync.log` and on disk:

1. XAR built in the project cache.
2. Staged into `outboxDirectory` under an **opaque** name (e.g.
   `3f9c…​.xar`).
3. Globus transfer submitted (a `task_id` is logged) and polled to
   **SUCCEEDED**; the file appears in cb2 at
   `remoteInboxServerDirectory/<opaque>.xar`.
4. `importXar` is triggered against cb2 (`localFilePath=<that path>`,
   `removeLocalFileAfterImport=true`); the session imports on cb2.
5. The staged outbox file on cb1 is deleted.

---

## If it falls back to HTTPS

`GlobusXarSender` **falls back to HTTPS on any Globus failure** (bad config,
transfer failure, unreachable node) so a sync is not lost. If you see an HTTPS
transfer where you expected Globus, check `xsync.log` for the Globus warning
that preceded the fallback, then:

- `globusEnabled` true and all five fields set? (`supports()` needs a complete
  config — a blank field silently disables Globus.)
- endpoint name matches a registered endpoint whose **Test** passes?
- the guest-collection ACL for cb1's client present on cb2's inbox (scoped to
  `remoteInboxPath`)?
- node reachability / firewall (see the admin manual troubleshooting).

---

## Teardown

```bash
curl -u owner:PASS -X PUT \
  https://cb1.example/xapi/xsync/globus/projects/PROJECT_A/config \
  -H 'Content-Type: application/json' -d '{"globusEnabled": false}'
```

Disabling drops the project back to HTTPS; the endpoint registration and
collections remain for the next test.
