# XSync + Globus — XNAT Administrator's Manual

_Draft. Describes the **target state**: an XNAT running XSync with the
completed Globus integration (client model, per `GLOBUS_TRANSFER_PLAN.md`)._

## About this manual

**What it is.** An operator's guide to configuring, running, monitoring,
and troubleshooting XNAT-to-XNAT synchronization and Globus-based data
movement once the XSync–Globus integration is complete.

**Two uses.** (1) *Now* — a yardstick for the development plan: every
behavior described here should be traceable to a plan deliverable, and
anywhere the manual has to guess is a place the plan is under-specified
(see §14). (2) *Later* — to direct QA: §7–§8 describe observable
behavior step by step, and §13 is an executable acceptance checklist.

**Status caveat.** This documents intended behavior, not yet-shipped
behavior. Items the plan leaves open or unverified are marked
**[OPEN]** and collected in §14. Concrete defaults, statuses, and paths
that already exist in XSync today are stated as fact; Globus-specific
values that depend on the build are marked where relevant.

**Audience.** XNAT **site administrators** and holders of the **Xsync
Administrator** role. Project-level steps (§6) are for project owners.

---

## 1. Overview and concepts

### 1.1 What XSync does

XSync copies data from a project on one XNAT ("source") to a project on
another XNAT ("destination"), on a schedule or on demand. It selects a
configurable subset of data, optionally de-identifies DICOM, remaps IDs
and labels for the destination, packages each imaging session as a **XAR**
(a structured zip), and transfers it. The destination imports the XAR
through its normal pipeline.

### 1.2 Transfer methods

XSync can move the packaged data three ways; the method is selectable per
project (and per destination):

| Method | Summary | Status |
|---|---|---|
| **HTTPS** | XAR streamed to the destination import service. Always available; the default and fallback. | Standard |
| **Globus** | XAR placed in a project **outbox**, transferred by Globus to a remote **inbox**, and auto-ingested. New. | This integration |
| **Aspera** | XAR sent via `ascp`. Legacy; its UI is being retired. | Deprecated (UI hidden) |

Non-Globus operation is unaffected by the Globus feature: traditional
HTTPS syncs run on the same server and projects as before.

### 1.3 Globus concepts you need

- **Endpoint / Collection** — a Globus-accessible storage location,
  identified by a UUID. Each participating XNAT is fronted by a **Globus
  Connect Server (GCS)** exposing an **inbox** and an **outbox** over
  dedicated directories (§2.1). Each is a **mapped** collection (the
  on-disk substrate) with a **guest** collection layered on top; the guest
  collection is default-deny and is what peers actually transfer against,
  so access is limited to explicitly registered service accounts. (No
  custom Globus connector is required — see
  `GLOBUS_APPROACH_COMPARISON.md`.)
- **Outbox** — holds XARs this node has staged to send. Its guest
  collection grants **read to this node's own client only**; it is
  **never** exposed to remote nodes, because it stages data bound for many
  destinations.
- **Inbox** — where XARs (or, for the upload service, DICOM) from remote
  nodes land and are auto-imported. Its guest collection grants each
  registered remote sender **read-write on its own subpath only**, so one
  peer cannot see another peer's dropped data (Globus has no write-only
  permission, so per-peer subpath isolation is required — §2.1 step 5). The
  inbox is normally the **only** collection a remote peer is given any
  access to. (Why the two are separate with asymmetric access — inbound
  vs. outbound workflows — is summarized in §2.1.)
- **Confidential client** — the credential XSync uses to drive Globus
  transfers unattended (OAuth2 client-credentials). Multiple endpoints
  may use different credentials.

### 1.4 The hub-and-spoke model

A common deployment: one central "hub" XNAT receives data from many
sources (scanners via the DICOM inbox, spokes via XSync) and redistributes
it. The features below support that topology; nothing here requires it.

### 1.5 Choosing a transport for a given network topology

The transfer method is per destination (§1.2), and the right choice depends
on where the two XNATs sit relative to each other:

- **Cross-site (different networks or organizations) → Globus.** This is
  what Globus is for: transfers across administrative and network
  boundaries, with resumable, checksum-verified movement of large datasets.
  Each node is reachable at its public address and the setup in §2.1
  applies as written.
- **Same internal network (e.g. two hosts in one cloud VPC/subnet) →
  HTTPS.** Two XNATs on one internal network can sync directly over HTTPS
  (the default transport) with none of the Globus NAT/firewall/guest-ACL
  machinery. Globus adds cost here for no benefit, and worse, it hits a
  networking snag: cloud providers generally **do not hairpin public/elastic
  IPs within a network**, so the two GCS nodes cannot reach each other's
  public address for the direct GridFTP data channel — each node's endpoint
  Test can pass (Globus's cloud reaches each node) while an actual
  node-to-node transfer fails.

Running Globus between same-network peers is **not a tested path here and is
not recommended** — use HTTPS. If you nonetheless need to explore it, treat
the following as an **untested hypothesis to validate with a spike, not a
procedure**:

- **What is certain (and rules out the obvious fix):** a DNS trick
  (split-horizon or `/etc/hosts`) **cannot** redirect the transfer. The
  GridFTP data channel connects to the peer node's **registered IP address**,
  not a hostname resolved at connect time
  (`globus-connect-server node setup --ip-address` = "IP Address of this Data
  Transfer Node"; `--data-interface` = "the IP Address of the network
  interface to use for GridFTP data transfers"). There is no hostname in that
  path to override.
- **The untested idea:** because addressing is by registered IP, the only
  plausible lever is registering each node's **private** data-transfer
  address at `node setup` while keeping Globus's cloud able to reach the
  control interface. **Whether a single node can serve a public control path
  and a private data path at once is unverified** — confirm it empirically
  before relying on it.

Globus itself documents same-network/NATed multi-endpoint transfer as
requiring advanced configuration with a reduced user experience
([GCS troubleshooting guide](https://docs.globus.org/globus-connect-server/v5/troubleshooting-guide/)),
so treat it as a deliberate exception, not the default.

---

## 2. Prerequisites and deployment

Before configuring anything in XNAT:

1. **Globus Connect Server** installed and set up at each participating
   site — follow the Globus
   [Quickstart](https://docs.globus.org/globus-connect-server/v5.4/quickstart/)
   through "Log into the endpoint." The endpoint must be associated with a
   **Globus subscription** — guest collections (§2.1) are a premium feature
   and this integration requires them.
2. **Inbox and outbox collections created — per §2.1 (required):** a mapped
   collection (substrate) + a **guest collection** (access layer) for each
   direction. Record the two **guest collection UUIDs**; you enter them in
   XNAT later (§5.2).
3. A **confidential client** registered in Globus (client ID + secret)
   for each set of credentials XNAT will use.
4. **ACLs**: each registered client is granted a guest-collection ACL
   (§2.1 step 5 — outbox read for the local client; inbox read-write per
   remote sender; see §2.1 for the asymmetric access model). Guest
   collections need **no `data_access` consent** — the ACL plus the base
   Transfer scope is the whole authorization story (confirmed against live
   Globus; the `data_access` dependent scope applies only to *mapped*
   collections and is rejected on guest collections).
5. **Network**: GCS transfer ports open between sites as required.
6. **Versions**: XNAT 1.10.x with the XSync plugin (with Globus support)
   installed. For Globus **inbox auto-ingest**, the *destination* XNAT
   must also run the plugin (Globus is not source-only — plan §14.1).

Deployment note: the destination whitelist and its seed data can be
provisioned at deploy time, including via Helm charts, so a fleet shares
one governance configuration. **[OPEN:** Helm propagation is a stated
requirement not yet built — Dev Guide §10.7.**]**

### 2.1 Create the inbox and outbox collections (required)

Once GCS is set up (Quickstart through step 2.4), **every XNAT+Globus node
needs an inbox and an outbox** before XNAT can use Globus: an **outbox** it
sends XARs from, and an **inbox** it receives into and auto-imports.

Each direction is built as **a mapped collection (the on-disk substrate) +
a guest collection (the access layer)**. This is deliberate:

- A **mapped** POSIX collection alone authorizes by identity *domain* +
  local-user mapping — too coarse to mean "only the service accounts we
  registered" (your service accounts share the `clients.auth.globus.org`
  domain, so a domain allow would admit *every* Globus confidential
  client).
- A **guest** collection is **default-deny**: no one has access except
  identities you explicitly grant an ACL. That is how we restrict each
  collection to exactly the registered service-account clients.

So: create restricted mapped collections owned by a single local account,
then layer a guest collection on each and grant ACLs only to the
registered clients. This replaces the generic gateway/collection the
Quickstart walks through (steps 2.5–2.6).

**Why two, with asymmetric access** (this drives the whole setup):

- *Outbound* (this node sends): XSync writes a XAR to the **outbox**, then
  this node's client submits a Globus transfer that **reads** the outbox
  and **writes** the remote's inbox. The outbox stages XARs for *many*
  destinations, so it must be readable only by *this* node's own client —
  **never** by any remote.
- *Inbound* (a remote sends): the transfer **writes** into this node's
  **inbox**; XNAT then imports it. Each remote sender needs write to *its
  own subpath* of the inbox, and nothing else.

So a peer relationship normally grants a remote access to your **inbox
only**; a pure hub may have only an inbox, a pure sender only an outbox.

**Paths** (alternative B — durable inbox, transient outbox; assumes XNAT
data root `/opt/data`):

| | Path | Rationale |
|---|---|---|
| **Inbox** | `/opt/data/xsync-globus-inbox` | A durable sibling of the archive — **not** under the cache — so inbound data awaiting import can't be swept by cache cleanup. Same volume as the archive → fast, atomic import. |
| **Outbox** | `/opt/data/cache/xsync-globus-outbox` | Under the XNAT cache: transient, written then deleted by XSync after confirmed import — matching XSync's existing cache staging. |

Adjust `/opt/data` to your data root; keep the inbox out of the cache tree.

**1. Create the directories**, owned by the account Tomcat runs as / that
owns the XNAT data — **`xnat`** in these deployments (substitute your own
if different). GCS maps the owner identity to this account (step 4b), so it
must be able to read/import and write here:

```bash
sudo mkdir -p /opt/data/xsync-globus-inbox /opt/data/cache/xsync-globus-outbox
sudo chown xnat:xnat /opt/data/xsync-globus-inbox /opt/data/cache/xsync-globus-outbox
```

**2. Create one storage gateway** with two policies: a **path restriction**
(so no collection on it can reach `/opt/data/archive`) and an **identity
mapping** (so your Globus identity maps to the local `xnat` account).

Path restriction — `xsync-restrict.json` (outbox read-only, inbox
read-write, all else denied — longest-prefix match wins):

```json
{
  "DATA_TYPE": "path_restrictions#1.0.0",
  "read":       ["/opt/data/cache/xsync-globus-outbox"],
  "read_write": ["/opt/data/xsync-globus-inbox"],
  "none":       ["*"]
}
```

Identity mapping — `idmap.json`. The **default** POSIX mapping strips the
`@domain` and uses the identity's local part as the local username, which
usually isn't a real account (you'd get a 403 "does not map to a valid
username"). Instead, map **your** Globus identity — by its identity UUID —
to the fixed local account that owns the directories (`xnat` — step 1):

```json
{
  "DATA_TYPE": "expression_identity_mapping#1.0.0",
  "mappings": [
    { "source": "{id}", "match": "<YOUR_IDENTITY_UUID>", "output": "xnat", "literal": true }
  ]
}
```

Get `<YOUR_IDENTITY_UUID>` from `globus whoami --verbose` — the identity
**ID**, a UUID (the same value as the token's `sub`). Here `"literal": true`
matches that UUID **verbatim** (this is the one place the flag belongs);
`output` is the fixed local username `xnat` (no `{…}` interpolation = a
constant). Mapping by UUID admits **exactly one identity** — least
privilege, immutable across email/username changes, and no regex to get
wrong.

*Alternative — map a whole domain.* If several admin identities must manage
these collections, match your org domain instead of one UUID (one rule
covers them all). Then `match` is a **regex** and you must **not** set
`literal: true` (that would make it a literal string that never matches);
escape dots in the domain:

```json
{
  "DATA_TYPE": "expression_identity_mapping#1.0.0",
  "mappings": [
    { "source": "{username}", "match": "(.*)@<your-org-domain>", "output": "xnat" }
  ]
}
```

```bash
globus-connect-server storage-gateway create posix "XSync Gateway" \
  --domain <your-org-domain> \
  --restrict-paths file:xsync-restrict.json \
  --identity-mapping file:idmap.json
# record the returned STORAGE_GATEWAY_ID
```

Set `--domain` to **your own org/admin domain only** — the identities that
will own the collections. `--domain` gates which identities the gateway
admits *at all*; the identity mapping above then resolves an admitted
identity to `xnat`. So even when you map by UUID, your identity's domain
must be in `--domain`. Do **not** add `clients.auth.globus.org` here:
registered service accounts get in through guest-collection ACLs (step 5),
not the gateway domain, and admitting that domain would allow every Globus
confidential client.

> **Set the identity mapping — and every gateway policy — at `create`
> time.** On GCS 5.4.x, `--identity-mapping` passed to `storage-gateway
> update` does **not** reliably take effect: the mapping silently fails to
> apply. Worse, `storage-gateway show` does **not** display identity
> mappings at all, so its output can neither confirm nor deny that one is
> in place. If you created the gateway *without* the mapping, **recreate**
> it with `--identity-mapping` in the `create` command rather than trying
> to add it afterward. Verify the mapping by **behavior** — a successful
> user-credential / guest-collection creation below — never by `show`.

**3. Create the two mapped collections (substrate), each rooted at its own
directory** (the `base_path` becomes the collection's `/`, so neither can
see above it). These are owned by the local account and are *not* what
peers transfer against. Pass **`--allow-guest-collections`** so you can
layer the guest collection on top in step 4 — a mapped collection has
sharing **disabled by default**, and without this you'll hit *"Sharing
Disabled — Guest Collection creation has been disabled on this mapped
collection"* when you try:

```bash
globus-connect-server collection create <STORAGE_GATEWAY_ID> \
  /opt/data/xsync-globus-inbox  "XNAT Inbox (mapped)"  --allow-guest-collections
globus-connect-server collection create <STORAGE_GATEWAY_ID> \
  /opt/data/cache/xsync-globus-outbox "XNAT Outbox (mapped)" --allow-guest-collections
# record both MAPPED COLLECTION UUIDs (needed to create the guest collections)
```

> Already created the mapped collection without it? Unlike the gateway
> identity mapping, **`collection update` *does* apply** here — enable
> sharing on the existing collection and reload the web app:
> ```bash
> globus-connect-server collection update <MAPPED_COLLECTION_ID> --allow-guest-collections
> ```
> This is a node-admin operation (`globus-connect-server`), independent of
> which Globus identity you transfer as.

**4. Create a guest collection on each mapped collection.** The guest
collection is the default-deny access layer; peers and the local client
transfer against *these*, never the mapped collections. Guest collections
require the endpoint's Globus **subscription**, and the mapped collection
must allow guest-collection creation — which step 3 handled with
`--allow-guest-collections` (see the
[Data Access Admin Guide](https://docs.globus.org/globus-connect-server/v5/data-access-guide/)).

Before the first guest collection on this gateway, satisfy two one-time
prerequisites for the **owner identity** (you). These are **owner-only** —
peers never need them; a peer just gets an ACL (step 5), and guest-collection
access runs as the owner's mapped local account, not the peer's.

> If your Globus **account has more than one linked identity** (for example
> a primary identity plus a separate linked identity that holds the
> subscription), these owner steps can fail in confusing ways because
> commands run as your *primary* identity, not necessarily the one with the
> subscription and user credential. See **Appendix A** for the symptoms and
> workarounds.

*4a. Consent to `data_access`* on each mapped collection (authorizes the
CLI to act on the collection as you; without it you get
`MissingLoginError: Missing 'data_access' consent`):

```bash
globus login --gcs <ENDPOINT_ID>:<INBOX_MAPPED_COLLECTION_ID>
globus login --gcs <ENDPOINT_ID>:<OUTBOX_MAPPED_COLLECTION_ID>
```

*4b. Ensure a user credential exists* for your identity. A mapped
collection acts on the filesystem *as a real Unix user*; the gateway's
identity mapping (step 2) resolves your identity to `xnat`. If GCS still
reports `No valid gcs user credentials discovered`, create one — it now
succeeds because the identity resolves to a valid account (one credential
per gateway covers both collections):

```bash
globus endpoint user-credential create posix \
  <ENDPOINT_ID> <STORAGE_GATEWAY_ID> <YOUR_IDENTITY> xnat
# <YOUR_IDENTITY>: your login identity — see `globus whoami`
```

> If the identity mapping from step 2 is in place, GCS may not require an
> explicit user credential at all; in that case skip straight to 4c. Note
> the two errors this resolves are distinct: `No valid gcs user credentials`
> means no mapping/credential for your identity; a 403 `does not map to a
> valid username` means the **mapping itself** is missing or wrong (fix
> step 2).

The account the mapping targets (`xnat`) must be a **real, enabled login
account** on the GCS host — confirm with `getent passwd xnat` (it should
show a valid uid and a login shell). Mapping to a missing, `nologin`, or
otherwise disabled account is a common cause of the 403 above.

*4c. Create the guest collections* as the mapped-collection owner, rooted at
each mapped collection's `/`:

```bash
globus collection create guest <INBOX_MAPPED_COLLECTION_ID>  / "XNAT Inbox"
globus collection create guest <OUTBOX_MAPPED_COLLECTION_ID> / "XNAT Outbox"
# or use the Globus web app: open the mapped collection -> Shares/Collections
#   -> "Add Guest Collection". Record INBOX_GUEST_COLLECTION_ID and
#   OUTBOX_GUEST_COLLECTION_ID.
```

**5. Grant ACLs on the guest collections — least-privilege and asymmetric.**
Guest collections start with no access, so only the identities you grant
here can transfer. The identity is the client's **Client ID (application
UUID, *not* a client secret)** formed into a username
`<CLIENT_ID>@clients.auth.globus.org`; use `--provision-identity` (which
creates the identity if it doesn't exist yet):

- **Outbox guest** — one ACL: *this node's own* confidential client,
  **read**. Grant no remote any access.
  ```bash
  globus endpoint permission create <OUTBOX_GUEST_COLLECTION_ID>:/ \
    --permissions r --provision-identity <LOCAL_CLIENT_ID>@clients.auth.globus.org
  ```
- **Inbox guest** — one ACL *per registered service account*, **read-write**,
  scoped to that peer's **own subpath** (`/<peer>/`); nothing broader:
  ```bash
  globus endpoint permission create <INBOX_GUEST_COLLECTION_ID>:/<peer>/ \
    --permissions rw --provision-identity <PEER_CLIENT_ID>@clients.auth.globus.org
  ```

**Per-peer inbox isolation is required, not optional.** Globus has only
`r` and `rw` permissions — there is **no write-only** — so a peer able to
write the inbox can also read whatever it has access to. To keep node B
from seeing data node C dropped, grant each peer `rw` on **only its own
subpath**; a peer then has *no* permission (not even read) on another
peer's subpath. This still works because node A imports by reading the
inbox **locally** (as the collection's owning account), so peers never need
read access to the inbox root.

Grant only clients you have registered with the local XNAT (§5.2); any
client without an ACL is denied by default.

**6. Register the GUEST collection UUIDs in XNAT.** Enter the
**guest** UUIDs (not the mapped ones) as the endpoint's inbox/outbox
collection IDs when registering the endpoint (§5.2) — the guest collections
are what transfers run against, and the ACLs you granted in step 5 are what
authorize them. A node may have only one direction (a pure hub only an
inbox, a pure sender only an outbox); at least one is required.

> **[OPEN — XNAT↔Globus ACL sync].** Registering an endpoint in XNAT (§5.2)
> and granting the matching guest-collection ACL (step 5) are today two
> manual, separate steps, so the two can drift. A future enhancement could
> have XNAT create/revoke the inbox ACL via the Transfer API when an
> endpoint is added/removed — plan §14.1.

References: Globus
[Quickstart](https://docs.globus.org/globus-connect-server/v5.4/quickstart/),
[Storage Gateway — Create POSIX](https://docs.globus.org/globus-connect-server/v5/reference/storage-gateway/create/posix/),
[PathRestrictions schema](https://docs.globus.org/globus-connect-server/v5/api/schemas/PathRestrictions_schema/),
[Collection Create](https://docs.globus.org/globus-connect-server/v5/reference/collection/),
[endpoint permission create](https://docs.globus.org/cli/reference/endpoint_permission_create/).

---

## 3. Installation and upgrade

- Install the XSync plugin JAR into each XNAT's `plugins` folder and
  restart Tomcat (source and, for Globus inbox ingest, destination).
- **Role bootstrap.** On upgrade, existing **site administrators** are
  automatically granted the new **Xsync Administrator** role, so current
  admins retain full XSync control. New grants are managed under Users.
- **Data types.** The plugin registers its XSync data types and Hibernate
  tables on first start; no manual DB steps.

---

## 4. Roles and who can do what

| Task | Site Admin | Xsync Administrator | Project Owner/Member |
|---|---|---|---|
| Site XSync/Globus settings, whitelist, blacklist, dashboard | ✅ | ✅ | ❌ |
| View all connections site-wide, enable/disable them | ✅ | ✅ | ❌ |
| Configure XSync for a project they own | ✅ | ✅ | ✅ (own project) |
| Start an on-demand sync for their project | ✅ | ✅ | ✅ (own project) |
| Mark a session "OK to sync" | ✅ | ✅ | ✅ (edit rights) |

The **Xsync Administrator** role exists so an operator can manage XSync
site-wide **without** full Site Administration privileges. API endpoints
enforce this via per-operation authorizers (project permission **or** the
role).

---

## 5. Site administration (Plugin Settings)

All site settings live under **Administer → Plugin Settings → XSync**.

### 5.1 Transfer method (Connection Management)

Toggles for the site-allowed transfer methods:

- **HTTPS** — on by default.
- **Globus** — enable to allow projects to select Globus.
- **Aspera** — legacy; its configuration UI is hidden. Existing Aspera
  connections continue to function.

Enabling Globus here does not force it; each project chooses its method.

### 5.2 Globus Management Interface

The central place to manage Globus connectivity (site level):

- **Register endpoints** — give the endpoint a display name and its
  confidential-client credentials, plus the **inbox** and **outbox guest**
  collection UUIDs from §2.1 (the guest UUIDs, not the mapped ones; at least
  one is required — leave the other blank for a receive-only or send-only
  node).
- **Credentials** — attach a confidential client (ID + secret) to an
  endpoint. **Multiple endpoints may use different credentials.** Secrets
  are stored securely and never displayed after entry. **[OPEN:** secret
  storage mechanism — plan §6.7.**]**
- **Test connection** — verifies auth + reachability for an endpoint
  (a lightweight Globus call). Use after adding or editing an endpoint.
- **Associate endpoints with projects** — make an endpoint selectable by
  particular projects.
- **Create endpoints for projects** — provision a guest collection for a
  project. **[OPEN:** endpoint provisioning via the GCS Manager API is a
  possible stretch item — plan §14.1.**]**

### 5.3 XSync site preferences

| Preference | Default | Meaning |
|---|---|---|
| Token Refresh Interval | 10 hours | How often destination alias tokens (HTTPS auth) refresh. |
| Sync Retry Interval | 2 hours | Wait between retries of a failed remote operation. |
| Sync Retry Count | 2 | Max retries. |
| Max Total Uncompressed File Size | -1 (off) | If set, large resources are split into multiple zips no larger than this many bytes. |

Globus adds transfer-task and archiving poll intervals/timeouts. **[OPEN:**
exact new preferences and defaults — plan §6.3/§14.1.**]**

### 5.4 Destination whitelist

When **enabled**, a project may only be configured to sync to an approved
destination.

- Maintain the list of approved sites (id, name, URL) with a **security
  classification**: PUBLIC, RESEARCH, or CLINICAL.
- The **local site is always allowed** implicitly, so intra-XNAT
  (project-to-project) syncs are never blocked by the whitelist.
- Enforcement is at **config-save time**: saving a project config whose
  destination URL is not whitelisted is rejected. It does **not**
  retroactively stop connections created before the whitelist was on — use
  the dashboard (§5.7) to find and disable those.
- **[OPEN:** classifications do not yet *constrain* configs (e.g., warn on
  CLINICAL→PUBLIC); requirement WS1.1a — Dev Guide §10.7. For Globus,
  decide whether a **collection-UUID** allow-list is also needed, since a
  URL match alone doesn't constrain which collection receives bytes — plan
  §10 decision 6.**]**

### 5.5 Project blacklist

A list of **local project IDs barred from any XSync connection**. A
blacklisted project cannot save an XSync config, and its connections
cannot be (re-)enabled. Managed on the **Project Blacklist** tab.

### 5.6 Configuration dashboard

**Administer → Plugin Settings → XSync → Configuration Dashboard** (the
default tab). For a site/Xsync administrator it shows:

- **All configured destinations**, each tagged as whitelist-conforming or
  not.
- **Non-conforming connections** (destinations not on the whitelist),
  which you can **disable**.
- **Projects per destination URL**.
- **Enable/disable** all connections for a URL, or a single project's
  connection.
- **Sync history** (paginated) and **failure stack traces** for
  diagnosis.

With Globus, the dashboard also surfaces **Globus transfer and ingest
status/history** per connection.

---

## 6. Project configuration

Project owners configure XSync from the project's **Manage → XSync
Configuration** page (or via XAPI). An Xsync Administrator can do this for
any project.

### 6.1 Basic setup

1. **Enable** XSync for the project.
2. Choose **New Data Only** (incremental) or full sync.
3. Enter the **destination XNAT URL** and **destination project**. (If the
   whitelist is on, the URL must be approved — §5.4.)
4. Provide **destination credentials** (used to import at the destination
   and, for deletions, requires owner-level access there).
5. Choose a **sync frequency** (§6.5).

### 6.2 Choosing Globus as the transfer method

If the site allows Globus (§5.1) and an endpoint is associated with the
project (§5.2):

1. Select **Globus** as the transfer method for the destination.
2. Choose the **Globus endpoint** (credentials come from the endpoint).
3. Configure the project **outbox** (source collection path) and the
   remote **inbox** (destination collection path).
4. Save. Use **Test connection** (§5.2) to confirm before relying on it.

### 6.3 Multiple destinations

A project may sync to **more than one destination**. Each destination
carries its own transfer method, endpoint, and inbox; XSync sends to each
on the schedule. HTTPS and Globus destinations can coexist on one project.

### 6.4 Selecting what to sync (filters)

XSync can limit what moves, so only necessary data is transferred:

- Project / subject / subject-assessor **resources** by label.
- **Imaging sessions** by xsiType; per-session, **scans** by type, **scan
  resources** by label, and **image assessors** by xsiType.
- A per-level `sync-type` of `include` / `exclude` / `all` / `none`.
- Absent configuration defaults to "sync everything" at that level.

Shared (into-project) subjects are recognized and handled to avoid
duplicating data. **[OPEN:** confirm "only necessary / shared data"
coverage vs. requirement WS1.5 — Dev Guide §10.7.**]**

### 6.5 Frequency and QC gate

- **Frequencies:** Hourly (30 min past the hour), Daily (00:00), Weekly
  (Sat 01:00), Monthly (1st, 02:00), or **On Demand**.
- **OK to Sync (QC gate):** if a session type is configured to require it,
  a session syncs only after a user marks it **OK to Sync** on the
  session's Synchronization tab. Useful for curation before release.

### 6.6 Anonymization

If **Anonymize** is enabled, DICOM is run through the project's Mizer
anonymization script **before** transfer. Enter/maintain the script via
the project's XSync setup. De-identification runs source-side, so data is
already anonymized before it reaches any Globus endpoint.

---

## 7. Operation: how a Globus sync runs

When a scheduled or on-demand sync runs for a Globus destination, XSync
performs the following. Each step has an observable result useful for QA.

1. **Discover changes.** XSync selects new/changed/failed entities since
   the last successful sync (per the filters). *Observe:* candidate
   subjects/experiments in the run.
2. **Curate + package.** For each session it filters, remaps IDs/labels,
   optionally anonymizes, and builds a XAR. *Observe:* XAR built in the
   project cache.
3. **Stage to outbox with obfuscated name.** The XAR is placed in the
   project's Globus **outbox** under an **opaque (hashed) filename**, so
   file names are not visible to the transfer service. *Observe:*
   opaque-named file in the outbox; real label recorded internally.
4. **Submit Globus transfer.** XSync submits an endpoint-to-endpoint
   transfer (checksum-verified) to the destination **inbox**. *Observe:* a
   Globus task id; task visible in the dashboard and in Globus.
5. **Await transfer.** XSync tracks the task to success/failure. *Observe:*
   task reaches SUCCEEDED; the XAR appears in the destination inbox.
6. **Destination auto-ingests.** The destination XNAT detects the inbox
   arrival and imports the XAR through its pipeline
   (prearchive/archive as configured). *Observe:* session appears in the
   destination prearchive/archive.
7. **Poll archiving status.** The sync-confirmation step polls the
   destination until the session is **archived** (not merely transferred).
   *Observe:* status advances to synced/verified.
8. **Verify + record.** XSync compares source vs. destination and records
   a per-item status (§12.3) and history. *Observe:* dashboard/history
   entry; email to the requester.
9. **Clean up.** On confirmed import, the XAR is **deleted from the
   local outbox** (if Globus has not already removed it). *Observe:*
   outbox no longer holds the sent XAR.

On any Globus failure, XSync retries per site preferences and, if
configured, **falls back to HTTPS** for that item so a sync is not lost.
*Observe:* retry entries in the log; fallback transfer if enabled.

---

## 8. Globus Upload Service (DICOM inbox)

Independently of XSync-to-XNAT, a project can receive data pushed to it
over Globus and have XNAT import it automatically — a "DICOM inbox over
Globus."

### 8.1 Configuration

- Enable an **upload inbox** for the project and expose it through a Globus
  collection.
- The inbox uses **expected subdirectories** — e.g. a `DICOM/` folder acts
  like XNAT's filesystem DICOM Inbox.
- Grant the appropriate Globus identities write access to the collection.

### 8.2 Behavior

- A user or peer site transfers imaging data into the project's inbox
  subdirectory over Globus (resumable, checksum-verified).
- XNAT detects the arrival and **auto-imports**: files flow through the
  normal routing → anonymization → prearchive → archive pipeline, exactly
  as SCP-received DICOM would.
- **Access permissions** for imported data are comparable to XNAT's
  prearchive: who may review/archive follows project prearchive rules;
  who may write to the inbox is governed by Globus collection ACLs.
- **Custom import handlers** may be registered to ingest new data formats
  beyond DICOM. **[OPEN:** the import-handler SPI is a planned extension —
  plan §14.2.**]**

### 8.3 Monitoring

A management view shows data **available / in transit / imported** for the
inbox, so an operator can see what is arriving and manage the import.

---

## 9. Monitoring and reporting

- **Configuration dashboard** (§5.6) — the primary operator view: all
  connections, conformance, per-URL projects, Globus transfer + ingest
  status/history, enable/disable, failure stack traces.
- **Per-project history** — paginated sync history with per-item results.
- **Live status** — a project's current sync progress (current subject/
  experiment, completed/failed lists).
- **Email notifications** — the requester (and any configured notification
  addresses) receives a per-run summary.
- **Logs** — each run writes a log resource on the source project; the
  in-progress log is retrievable while a sync runs.
- **Cluster note** — scheduled syncs run on the **primary node only**;
  live in-memory status is per-node.

---

## 10. Security and compliance

- **Roles** — site-wide XSync control requires Site Admin or the Xsync
  Administrator role; project actions require project permissions (§4).
- **Governance** — the whitelist constrains destinations; the project
  blacklist bars specific projects; classifications tag destination
  sensitivity (§5.4–§5.5).
- **De-identification** — DICOM anonymization runs **source-side before
  transfer** (§6.6); data on Globus is already anonymized for XSync flows.
  (For the upload service, incoming DICOM may be identifiable until XNAT
  anonymizes it on receive — treat the inbox as PHI-bearing.)
- **Filename obfuscation** — XAR file names are opaque in transit (§7.3),
  so the transfer service sees no identifiers in names.
- **Credentials/tokens** — Globus client secrets are stored securely;
  destination XNAT access uses short-lived alias tokens refreshed on a
  schedule. Client-credential tokens are re-fetched on expiry.
- **Transport security** — Globus transfers are encrypted and
  checksum-verified end to end.
- **Audit** — sync history records who configured and ran each sync and
  the outcome per item.

---

## 11. Troubleshooting

| Symptom | Likely cause | Action |
|---|---|---|
| Project config save rejected ("not an allowed destination") | Whitelist on; destination URL not approved | Add the site to the whitelist (§5.4) or correct the URL. |
| Config save rejected ("project not allowed") | Project is blacklisted | Remove from the project blacklist (§5.5) if appropriate. |
| Globus **Test connection** fails | Bad client credentials (auth), a wrong collection UUID, or Globus can't reach the GCS node | Read the logged error. Auth failure → re-enter the client ID/secret (the failing client ID is logged) and confirm outbound HTTPS to `auth.globus.org`. `404`/`NotFound` → a wrong/nonexistent collection UUID (register the **guest** UUID). `502`/connect timeout → Globus can't reach the node (next row). A `403` on the inbox is *expected* and tolerated (the sender's ACL is scoped to a subpath, so the root isn't listable). Test proves the client authenticates and the UUIDs resolve; a subpath-scoped ACL still can't be fully verified until a real transfer. |
| Test/transfer fails with `502 ExternalError.DirListingFailed` / "Error (connect) … timed out" | Globus's servers can't open a connection to the GCS node — a firewall/security-group or Network ACL is dropping it | Open inbound **443** and **50000–51000** to **`0.0.0.0/0`** on the node. Globus's transfer servers use **dynamic** source IPs, so these ports cannot be scoped to specific addresses — a *connect timeout* (vs. a protocol error) means the SYN is being dropped by a source-scoped rule. A node reachable from your workstation can still be blocked for Globus if 443 is IP-scoped. Also check the subnet's stateless **Network ACL**, not just the security group. |
| `UNKNOWN_SCOPE_ERROR` on a token request | A `data_access` scope was requested for a *guest* collection (only valid for mapped collections) | Should not occur in the shipped build (guest collections use the base Transfer scope only); if seen, the collection registered is a mapped, not guest, UUID — register the guest UUID. |
| Transfer between two nodes on the **same internal network** (e.g. one VPC) never completes, though each node's Test passes | Cloud networks don't hairpin public/elastic IPs: the two GCS nodes can't reach each other's *public* address for the direct data channel | Use **HTTPS** for same-network peers — Globus here is untested/not recommended (see §1.5). Note a DNS/`/etc/hosts` fix *can't* work: the data channel uses each node's **registered IP** (`node setup --ip-address`/`--data-interface`), not a resolved hostname. |
| Transfer submitted but never completes (cross-site) | Collection down, path wrong, quota, or a missing/incorrect guest-collection ACL | Check the Globus task in the dashboard/Globus; verify the inbox path exists and is writable and that the sender's ACL covers its subpath. |
| XAR arrives in inbox but is not imported | Destination plugin/inbox watcher not running; inbox path mismatch | Confirm the destination XNAT runs the plugin and watches the configured inbox; check destination logs. |
| Session transferred but status stuck pre-archive | Archiving pipeline stalled at destination | Check destination prearchive/pipeline; the archiving poll will report failure after timeout. |
| Sync falls back to HTTPS unexpectedly | Globus misconfigured or unreachable; fallback enabled | Investigate the Globus error in the log; fix config, then retry. |
| Deletions not propagating to destination | Destination user lacks owner access | Provide owner-level destination credentials. |
| Sync "skipped" for a session | Requires OK-to-sync and not marked; or excluded by filter | Mark OK to Sync, or adjust filters (§6.4–§6.5). |
| Sync not running at all | Not primary node; sync disabled; already running | Confirm primary node; check enable flag and in-progress lock. |
| Old XARs left in outbox | Import not confirmed, or cleanup failed | Confirm destination import; check archiving-poll result and cleanup step. |

---

## 12. Reference

### 12.1 Sync frequencies

Hourly `:30` · Daily `00:00` · Weekly Sat `01:00` · Monthly 1st `02:00` ·
On Demand.

### 12.2 Site preferences (defaults)

Token Refresh 10 h · Retry Interval 2 h · Retry Count 2 · Max Uncompressed
Zip -1 (off). Transfer-method toggles: HTTPS on, Globus off, Aspera hidden.
Globus task/archiving poll settings: **[OPEN]**.

### 12.3 Per-item sync statuses

`WAITING_TO_SYNC`, `SYNC_REQUESTED`, `IN_PROGRESS`,
`SYNCED_AND_NOT_VERIFIED`, `SYNCED_AND_VERIFIED`, `SKIPPED`,
`SKIPPED_BY_FILTER`, `INVALID_FILTER`, `DELETED`, `FAILED`.

### 12.4 Key XAPI areas

`/xapi/xsync/setup/...` (project config) · `/xapi/xsync/...` (operations:
start sync, OK-to-sync, progress) · `/xapi/xsync/dashboard/...` (admin
dashboard) · `/xapi/xsyncSitePreferences/...` and
`/xapi/xsyncProjectPreferences/...` (site/project prefs, whitelist,
blacklist, Globus endpoints). Exact Globus endpoint/transfer routes:
**[OPEN]** until built.

### 12.5 Locations

Source XSync cache/outbox and per-run logs live under the XNAT cache;
destination inbox under the configured collection path; whitelist seed
JSON is bundled and may be provisioned at deploy time.

---

## 13. Acceptance / verification checklist (QA)

Run against a two-site test bed (source + destination) each with GCS and a
POSIX collection. Each item is pass/fail.

**Foundation (WS2)**
1. Register two Globus endpoints with **different** credentials; both save.
2. **Test connection** passes for a good endpoint and fails clearly for a
   bad credential / wrong UUID.
3. A project can select an associated endpoint; an unassociated endpoint
   is not offered.

**XSync over Globus (WS3)**
4. With Globus enabled and configured, a scheduled sync builds a XAR and
   places it in the outbox under an **opaque filename**.
5. A Globus transfer is submitted and reaches SUCCEEDED; the XAR appears in
   the destination inbox.
6. The destination **auto-imports**; the session reaches the destination
   archive.
7. Sync status advances only **after archiving** (archiving poll), not at
   transfer completion.
8. The local XAR is **deleted** after confirmed import.
9. A project configured with **two destinations** sends to both.
10. Inducing a Globus failure triggers retry and (if enabled) **HTTPS
    fallback**; the sync still completes.
11. A traditional **HTTPS** sync on another project is unaffected
    (regression).
12. Transferred file names expose **no identifiers** to the transfer
    service.

**Upload service / DICOM inbox (WS4)**
13. Data pushed to a project's inbox `DICOM/` subdir is **auto-imported**
    through routing → anonymization → prearchive.
14. Inbox review/archive permissions behave **comparably to prearchive**.
15. The management view shows data available / in transit / imported.

**Governance & security (WS1)**
16. With the whitelist on, saving a non-whitelisted destination is
    **rejected**; the local site is always allowed.
17. A **blacklisted** project cannot save/enable an XSync config.
18. An **Xsync Administrator** (not a Site Admin) can manage site XSync
    settings and the dashboard, but cannot exceed the role elsewhere.
19. The dashboard lists **non-conforming** connections and can **disable**
    them.
20. On upgrade, existing site admins received the Xsync Administrator role.

**Monitoring**
21. Dashboard shows Globus transfer + ingest status/history per
    connection; failures show a stack trace.
22. The requester receives an email summary; history records per-item
    status.

---

## 14. Known assumptions and open items (for plan evaluation)

Where this manual had to assume, the plan is under-specified. Each maps to
a plan decision or `[VERIFY]`; resolving them lets QA write exact tests.

- ~~**Globus auth consents** for client-credential clients~~ — **resolved.**
  XSync uses guest collections, authorized by ACL + the base Transfer
  scope; no `data_access` consent is involved (confirmed against live
  Globus, endpoint Test). See plan §4.3/§4.5.
- **Secret storage** for Globus client secrets (plan §6.7). §5.2.
- **New Globus preferences and defaults** — task poll interval, archiving
  poll interval/timeout, per-endpoint settings (plan §6.3/§14.1). §5.3,
  §12.2.
- **Endpoint provisioning** ("create endpoints for projects") via the GCS
  Manager API — possibly out of first scope (plan §14.1). §5.2.
- **Destination-side deployment** — Globus inbox auto-ingest requires the
  plugin on the destination; confirm packaging/versioning (plan §14.1).
  §2, §3, §7 step 6.
- **Collection-UUID governance** — whether the whitelist must also
  constrain destination collections, not just URLs (plan §10 decision 6).
  §5.4.
- **Sync/async task handling** — block-poll vs. async tracking affects how
  status appears while a transfer is in flight (plan §6.2/§6.5). §7, §9.
- **Import-handler SPI** for the upload service (plan §14.2). §8.2.
- **WS1 governance gaps** not yet built — Helm/AIS whitelist propagation,
  hiding Aspera UI, classification-driven constraints, filtered/shared-data
  coverage (Dev Guide §10.7). §2, §5.1, §5.4, §6.4.

---

## Appendix A — When your Globus account has multiple linked identities

The setup in §2.1 assumes you operate Globus as a **single identity**:
your identity's domain goes in the gateway `--domain`, the identity
mapping matches that domain, and the user credential and guest
collections are all created as that same identity. If your Globus
**account** instead has **several linked identities** — for example a
primary identity from one provider plus a second linked identity that
actually holds the subscription — the §2.1 owner steps can fail in
confusing ways. This appendix documents that edge case; skip it if you
have one identity.

### Why it happens

Globus commands (`globus …`) and the Globus web app act as your account's
**primary** identity — the one `globus whoami` reports — regardless of
which linked identity holds the subscription, belongs to the gateway
`--domain`, or owns the user credential. `globus login` authenticates the
*account*; it does **not** let you pick which identity is primary. So if
your primary identity isn't the one the gateway accepts, GCS can't resolve
it to the local `xnat` account, and the owner steps fail even though a
perfectly good credential exists on the *other* identity.

### Symptoms

- `globus whoami` shows the "wrong" identity — the one without the
  subscription, or not in your gateway `--domain`.
- `globus collection create guest` fails with **`No valid gcs user
  credentials discovered`** even though `globus endpoint user-credential
  list` shows a valid, non-expired credential — because that credential is
  on the *linked* identity, not the primary one the command runs as.
- Web-app guest-collection creation stops with a scope/consent error
  naming `…:manage_collections[… data_access]`.

### Workarounds (in order of preference)

1. **Make the subscription/gateway identity your primary.** In the Globus
   web app, open account settings and set the linked identity that holds
   the subscription as primary, then `globus logout && globus login` and
   confirm `globus whoami` now reports it. (If the web app doesn't expose a
   "make primary" control for your account, use option 2.)
2. **Do the guest-collection steps in the web app as the subscription
   identity.** Open the mapped collection → **Add Guest Collection**. When
   it reports the `manage_collections` scope/consent problem, **click
   through to reauthenticate** — granting that consent completes the
   creation. This is what succeeded in our test bed when the primary
   identity could not be changed.
3. **On the CLI, align everything to the subscription identity.** Ensure
   the gateway `--domain`, the identity mapping (§2.1 step 2), and the user
   credential (step 4b) all reference the linked identity that holds the
   subscription, and run the collection-management commands only after
   `globus whoami` reports that identity. The **`{id}` mapping form** (§2.1
   step 2) is the precise tool here: set `match` to the subscription
   identity's UUID so *that* identity — and not your shadowing primary — is
   the one mapped to `xnat`.

### What is *not* affected

Granting the guest-collection **ACLs** to your XSync service-account
clients (`<CLIENT_ID>@clients.auth.globus.org`, §2.1 step 5) is
independent of your personal linked-identity situation — those clients are
their own identities. Once the guest collections exist, the rest of the
setup proceeds exactly as the main text describes.

---

Related documents: `GLOBUS_TRANSFER_PLAN.md` (the plan this manual
projects), `GLOBUS_APPROACH_COMPARISON.md` (why the client model),
`DEVELOPERS_GUIDE.md` (current XSync internals).
