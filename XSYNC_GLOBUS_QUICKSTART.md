# XSync + Globus — Quick Start

_The **do-this** runbook for standing up one XNAT+Globus node and a first
Globus sync. For the reasoning behind each step (why two collections, why
guest + ACL, path choices, security model), see the detailed
**`XSYNC_GLOBUS_ADMIN_MANUAL.md`** — section pointers are given as
"→ Manual §X"._

Placeholders in `<ANGLE_BRACKETS>` are values you supply or record as you
go. Assumes XNAT data root `/opt/data`; adjust to yours.

---

## Prerequisites

- XNAT **1.10.x on Java 21**, with the **XSync plugin (Globus build)** in
  `plugins/` and Tomcat restarted — on **both** source and destination
  (Globus inbox auto-ingest runs on the destination). → Manual §2, §3
- **Globus Connect Server** installed and set up through "Log into the
  endpoint" ([GCS Quickstart](https://docs.globus.org/globus-connect-server/v5.4/quickstart/)),
  on an endpoint associated with a **Globus subscription** (required —
  guest collections are a premium feature). → Manual §2
- A **Globus confidential client** (client ID + secret) for this node. Its
  **Client ID** is the application UUID (not a client secret). → Manual §1.3
- Outbound HTTPS from the XNAT host to `https://auth.globus.org`.
- **Firewall/security group:** inbound **443** and **50000–51000** open to
  **`0.0.0.0/0`** on the GCS node — Globus's transfer servers use dynamic
  source IPs, so these can't be scoped to specific addresses. (A node
  reachable from your workstation can still be unreachable for Globus if 443
  is IP-scoped; the endpoint Test then fails with a `502`/connect timeout.)
  → Manual §11
- **Topology:** use Globus for **cross-site** peers. For two XNATs on the
  **same internal network**, prefer **HTTPS** — same-network Globus hits a
  public-IP hairpin and needs advanced split-horizon setup. → Manual §1.5
- You hold the **XsyncAdministrator** role in XNAT. → Manual §4

---

## Part 1 — Create the Globus collections

Each direction = a **mapped** collection (on-disk substrate) + a **guest**
collection (default-deny access layer). → Manual §2.1

**1. Directories** (owned by the account Tomcat runs as — `xnat` here;
substitute yours):
```bash
sudo mkdir -p /opt/data/xsync-globus-inbox /opt/data/cache/xsync-globus-outbox
sudo chown xnat:xnat /opt/data/xsync-globus-inbox /opt/data/cache/xsync-globus-outbox
```

**2. Restricted storage gateway** with a path restriction + an identity
mapping (the mapping sends your org identities to the local `xnat` account;
without it the default strips the domain and you get a 403). `xsync-restrict.json`:
```json
{
  "DATA_TYPE": "path_restrictions#1.0.0",
  "read":       ["/opt/data/cache/xsync-globus-outbox"],
  "read_write": ["/opt/data/xsync-globus-inbox"],
  "none":       ["*"]
}
```
`idmap.json` (escape dots in the domain):
```json
{
  "DATA_TYPE": "expression_identity_mapping#1.0.0",
  "mappings": [
    { "source": "{username}", "match": "(.*)@<your-org-domain>", "output": "xnat" }
  ]
}
```
(`match` is a regex — don't set `literal: true`; `output` "xnat" is a constant.)
```bash
globus-connect-server storage-gateway create posix "XSync Gateway" \
  --domain <your-org-domain> \
  --restrict-paths file:xsync-restrict.json \
  --identity-mapping file:idmap.json
# record STORAGE_GATEWAY_ID   (--domain = your org/admin domain ONLY, not clients.auth.globus.org)
```

**3. Two mapped collections** (substrate; not what peers transfer against):
```bash
globus-connect-server collection create <STORAGE_GATEWAY_ID> \
  /opt/data/xsync-globus-inbox  "XNAT Inbox (mapped)"
globus-connect-server collection create <STORAGE_GATEWAY_ID> \
  /opt/data/cache/xsync-globus-outbox "XNAT Outbox (mapped)"
# record both MAPPED_COLLECTION_IDs
```

**4. A guest collection on each mapped collection** (needs the
subscription; the mapped collection must allow sharing). Two one-time
owner-only prerequisites first (peers need neither) → Manual §2.1 step 4:
```bash
# 4a. data_access consent on each mapped collection
globus login --gcs <ENDPOINT_ID>:<INBOX_MAPPED_COLLECTION_ID>
globus login --gcs <ENDPOINT_ID>:<OUTBOX_MAPPED_COLLECTION_ID>
# 4b. user credential — only if GCS still asks ("No valid gcs user credentials");
#     the step-2 identity mapping usually resolves this on its own
globus endpoint user-credential create posix \
  <ENDPOINT_ID> <STORAGE_GATEWAY_ID> <YOUR_IDENTITY> xnat
```
Then create (or via web app: mapped collection → Shares → Add Guest Collection):
```bash
globus collection create guest <INBOX_MAPPED_COLLECTION_ID>  / "XNAT Inbox"
globus collection create guest <OUTBOX_MAPPED_COLLECTION_ID> / "XNAT Outbox"
# record INBOX_GUEST_COLLECTION_ID and OUTBOX_GUEST_COLLECTION_ID
```

**5. Grant ACLs on the guest collections** (default-deny; grant only
clients registered in XNAT). Identity = the **Client ID** (app UUID, not a
secret) as `<CLIENT_ID>@clients.auth.globus.org`; `--provision-identity`
creates it if new:
```bash
# outbox: this node's own client, read only
globus endpoint permission create <OUTBOX_GUEST_COLLECTION_ID>:/ \
  --permissions r --provision-identity <LOCAL_CLIENT_ID>@clients.auth.globus.org
# inbox: each registered sender, rw, scoped to ITS OWN subpath (per-peer
# isolation is required — no write-only permission exists, so a shared inbox
# path would let one peer read another's data)
globus endpoint permission create <INBOX_GUEST_COLLECTION_ID>:/<peer>/ \
  --permissions rw --provision-identity <PEER_CLIENT_ID>@clients.auth.globus.org
```

Record the two **guest** UUIDs — they go into XNAT next.

---

## Part 2 — Register the endpoint in XNAT

**Admin → Plugin Settings → XSync** (→ Manual §5):

1. **Connection Management** — enable **Globus** as a transfer method. (§5.1)
2. **Globus Endpoints** — add an endpoint: display name, the confidential
   client ID + secret, and the **inbox** and **outbox _guest_ collection
   UUIDs** (at least one; leave the other blank for a receive-only or
   send-only node). (§5.2)
3. Click **Test** on the endpoint to confirm auth + reachability.

---

## Part 3 — Configure a project to sync over Globus

On the project's **Manage → XSync Configuration** (→ Manual §6):

1. Enable XSync; set the **destination XNAT URL + project** and
   **destination credentials**. (§6.1)
2. Transfer method = **Globus**; pick the registered **endpoint**. (§6.2)
3. Set the project **outbox** (this node) and remote **inbox** paths.
4. Pick a **sync frequency** (or On Demand); enable **Anonymize** if
   needed. (§6.5–§6.6)

---

## Part 4 — Smoke test

1. Start an **on-demand** sync (or wait for the schedule). → Manual §7
2. Watch: XAR built → appears in the outbox under an opaque name → Globus
   task **SUCCEEDED** → XAR in the destination inbox → session
   **auto-imported** → status advances after archiving → XAR deleted from
   the outbox.
3. Confirm the **Configuration Dashboard** shows the transfer + ingest
   status and the requester got an email. → Manual §5.6, §9

---

## Notes

- **Auth model (confirmed):** guest collections are authorized by their
  **ACLs** plus the **base Transfer scope** — no `data_access` dependent
  scope or consent (that scope is mapped-collection-only and is rejected on
  guest collections). The endpoint **Test** is therefore a pure auth check:
  green means the client authenticated to Globus; it does **not** prove the
  collection UUIDs or ACLs are correct. → Manual §14
- Other open items (task/archiving poll settings, endpoint provisioning) —
  → Manual §14.

_Migrating a node that was already set up with mapped-only collections?
That's an access-layer change (add guest collections + ACLs, re-point XNAT
to the guest UUIDs, re-consent, coordinate peers) — see the migration
notes accompanying the Admin Manual._
