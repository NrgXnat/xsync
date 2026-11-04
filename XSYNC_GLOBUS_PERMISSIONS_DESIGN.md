# XSync + Globus — Permissions and Access Management Design

_Design thinking for the **target** authorization model for Globus-based
data exchange: both XNAT-to-XNAT **sync** (node-to-node, §2–§4) and inbound
**DICOM upload** (users pushing to a project inbox, §5). The first-pass
prototypes (a single manually-configured a → b sync route, and a
hand-provisioned upload user) do **not** require any of this; this document
is the model to grow into, not a prototype requirement._

## 1. Organizing principle: separate the two authorizations

There are two distinct "permissions" in play, and the design keeps them
apart deliberately:

- **XNAT owns user/project authorization** — *who* may send *what*, from
  *which* source project, to *which* destination project. Rich, per-user,
  per-project, governed by XNAT roles.
- **Globus owns node-to-node byte movement** — a coarse, node-level fact:
  "node A's service client may write into node B's inbox." Globus never
  knows about XNAT users or projects.

Keeping these separate is what makes the model scale. In particular, for
**sync** we do **not** want a Globus identity per XNAT user: that would
require every user to hold a Globus account and complete a consent flow,
which is a poor fit for unattended service transfers and an administrative
burden. Globus stays node-level; all fine-grained decisions live in XNAT.

**Exception — DICOM upload (§5).** Inbound user upload deliberately relaxes
this: a user pushing DICOM from Globus Connect Personal transfers as *their
own* Globus identity, so a per-user Globus identity is here unavoidable and
appropriate (there is no service account standing in for the user). The
principle's *spirit* still holds — fine-grained authorization stays in XNAT,
since the import runs as the mapped XNAT user — so only the coarse
inbox-write ACL is pushed down to Globus.

## 2. Layer 1 — XNAT-side permissions

The list of destinations a user sees when sending from source project `a` is
the **intersection** of three gates, each with a natural home in XNAT:

1. **Site governance (source XNAT A).** The existing destination
   **whitelist** (with the PUBLIC / RESEARCH / CLINICAL classification),
   plus — for Globus — the collection-UUID allow-list already flagged as an
   open item in the transfer plan. Site-admin controlled; sets the universe
   of possible destination *hosts*.
2. **Route registry.** Approved **(destination XNAT, destination project)**
   entries, each bound to a Globus endpoint/collection and a destination
   credential. This generalizes today's per-project XSync config into a
   reusable catalog of known destinations.
3. **User authorization.** The user must hold a send-capable **role on the
   source project `a`** (owner, or member with edit — sending out is an
   export). No new per-user destination ACL is needed: if an enabled route
   from `a` exists, any user with export rights on `a` may use it.

So: **destinations offered to user U on project `a` = { routes registered
for `a` } ∩ { whitelisted and enabled }**, and U sees them because U is a
member of `a`. This yields the motivating example directly — B/b, B/b2, and
C/c1 are simply the enabled routes registered for `a`.

### 2.1 Routes are a destination-consented handshake

The crucial security property: **XNAT A cannot grant itself the right to
write into B/b — the destination must consent.** This mirrors what XSync
already does with `RemoteAliasEntity` (the destination XNAT issues an alias
token to the source). A route from A/`a` → B/`b` is established by a
handshake:

- the **source** project owner requests/proposes the destination;
- the **destination** project owner approves inbound and issues the
  credential (alias token for XNAT-level write authorization, and — see
  Layer 2 — the Globus ACL for byte movement);
- **site governance** on A must have the destination whitelisted.

A route object therefore carries: source project, destination XNAT +
project, the destination-issued alias (XNAT write auth), and the Globus
binding (endpoint + collection + path). Destination-controlled inbound is
the right posture and matches the existing trust model.

## 3. Layer 2 — the Globus IAM backing

Each XNAT route maps down to Globus **coarsely**:

- **One confidential client per XNAT node** (its service identity) — not per
  user, not per project.
- A route A/`a` → B/`b` means **A's client holds an `rw` ACL on B's inbox
  guest collection, scoped to a per-source subpath** (e.g. `/A/…`, optionally
  `/A/<source-project>/` for isolation and provenance). **One node-to-node
  ACL covers all project routes between that node pair.**
- Which destination *project* the bytes import into is an **XNAT concern
  carried in the XAR / import-by-path step** (authorized by the alias token),
  not encoded in Globus permissions. Globus only sees "A's client dropped a
  file in B's inbox."

Net effect: Globus ACLs stay few and coarse (one per node pair), while XNAT
expresses the combinatorial user × project × destination matrix. That
division is what scales.

## 4. Layer 3 — moving Globus management into the XNAT UI

Automating Globus setup from XNAT is valuable but carries a sharp privilege
tradeoff. Two distinct Globus APIs are relevant:

- **Transfer API (cloud): ACL management** on guest collections
  (add/remove access rules). This is the tractable, high-value automation:
  when a route is approved, XNAT-B calls it to grant A's client the inbox
  ACL (scoped to `/A/`), and revokes it on route removal. It directly
  retires the "XNAT↔Globus ACL drift" open item (transfer plan §14.1), and
  the plugin already has `GlobusClient` plus a Transfer token, so it is a
  natural extension (the endpoint Test could even provision the ACL).
- **GCS Manager API (node-local): collection/gateway creation** — automating
  the mapped + guest collection setup, sharing policy, and identity mapping
  we currently do by hand. Possible, but heavier, and it must encode the
  create-time gotchas already documented in the admin manual.

**The privilege tradeoff, to decide deliberately.** ACL and collection
management require the acting identity to hold admin/manage roles on the
endpoint — more privilege than a transfer-only client. If the XNAT service
client can create collections and grant ACLs, a leaked secret compromises
the endpoint's *administration*, not just data transfer. Recommended
staging:

1. **Automate ACL grant/revoke first** (bounded, high payoff).
2. Treat **collection provisioning as a later, opt-in** capability, likely
   with a separate, higher-privilege credential distinct from the transfer
   client.

### 4.1 Transfer API ACL specifics (verified)

Confirmed against the Globus Transfer API
[permissions reference](https://docs.globus.org/api/transfer/permissions/):

- **Create a grant:** `POST /endpoint/<collection_id>/access`
- **List grants:** `GET /endpoint/<collection_id>/access_list`
- **Get / update / delete one:**
  `GET|PUT|DELETE /endpoint/<collection_id>/access/<permission_id>`

The **access-rule document**:

- `DATA_TYPE`: `"access"`
- `principal_type`: one of `"identity"`, `"group"`,
  `"all_authenticated_users"`, `"anonymous"` — for us, `"identity"`.
- `principal`: the Globus **identity UUID** (for `identity`) or group UUID.
  Note this is the identity's UUID, so XNAT must resolve/store the peer
  client's identity UUID (not just the `<id>@clients.auth.globus.org`
  string) to grant it.
- `path`: absolute path, e.g. the per-source subpath `"/A/"`.
- `permissions`: `"r"` or `"rw"` — inbox grants use `"rw"`.
- optional: `expiration_date` (ISO 8601), `notify_email` (create only).

So an inbox grant for a route A/`a` → B/`b` is a `POST` to B's inbox
**guest** collection with `{DATA_TYPE: access, principal_type: identity,
principal: <A-client-identity-UUID>, path: "/A/", permissions: "rw"}`, and
revocation is the matching `DELETE`. `expiration_date` is a useful knob for
time-boxed routes.

**Authorization requirement (sharpens the privilege tradeoff — good news).**
Managing a guest collection's permissions requires an **effective role of
`access_manager`, `administrator`, or `restricted_administrator`** on that
collection. `access_manager` is the **least-privilege** option: it can
manage permissions but not otherwise administer the endpoint. So the
automation credential need not be a full endpoint admin — grant XNAT-B's
service client the **`access_manager`** role on B's inbox guest collection
and it can grant/revoke inbox ACLs and nothing more. This narrows the
blast radius of §4's tradeoff considerably for the ACL-automation step
(collection *creation* via the GCS Manager API still needs broader rights).

**Still to confirm:** the permissions reference documents the role-based
authorization but **not the OAuth token scope** these calls require. The
base Transfer scope likely suffices given role-based auth, but confirm the
required scope/consent before implementing.

## 5. DICOM upload permissions (inbound user push)

The upload service — users push a study to a project inbox over Globus and
XNAT auto-imports it (direct to archive) — is a different *flow* from
node-to-node sync, but its permission shape is a close parallel to a peer
route, with one deliberate departure (§1 exception).

### 5.1 The shape

- The uploader transfers as **their own Globus identity** (a human user on a
  workstation running Globus Connect Personal), not a node service client.
- That identity holds an **`rw` ACL on the node's upload inbox guest
  collection, scoped to its own `/<PROJECT>/<user>/` subpath** — the same
  default-deny, subpath-isolated arrangement as a peer's inbox grant, just
  keyed on project + user instead of source node.
- The import runs **as the mapped XNAT user**, so XNAT's project-edit
  permissions decide what actually archives; Globus only gates the write into
  the subpath. (No alias token: source and destination are the same node, so
  the import is local, run as the user, rather than a cross-node call.)

### 5.2 The parallel to peer routes (the symmetry)

Both cases are, at the Globus layer, the identical thing: *a default-deny
inbox guest collection with per-principal, subpath-scoped `rw` grants.* They
differ only in the principal, the subpath scheme, and the XNAT-side gate:

| | Peer sync (§2–§4) | DICOM upload |
|---|---|---|
| Globus principal | node confidential client | user's own Globus identity |
| Inbox ACL | `rw` on `/A/` (per source node) | `rw` on `/<PROJECT>/<user>/` |
| ACL cardinality | one per node pair (all projects) | one per (user, project) |
| XNAT import auth | alias token → destination project | run as the mapped user (project-edit) |
| XNAT-side gate | route registry + source-project membership + destination consent | Globus-identity→XNAT-user mapping + project-edit on the target |
| Inbox mgmt role | `access_manager` (provision ACLs) | `activity_monitor` (detect uploads) + `access_manager` (if automating user ACLs) |

The **ACL mechanic is identical** — both provision through the same Transfer
API call and access-rule document (§4.1), and both need `access_manager` on
the inbox to do so. So **Layer 3's ACL automation generalizes to both**:
"grant/revoke `rw` on an inbox subpath to a Globus identity," differing only
in the principal (client vs user identity) and how the subpath is derived.
One provisioning seam, two callers.

### 5.3 What is genuinely new for upload

- **Globus-identity ↔ XNAT-user mapping.** Sync principals are pre-registered
  machine clients; upload principals are individual humans, so XNAT must know
  that Globus identity `alice@…` is XNAT user `alice` (a user-linked identity,
  or an admin mapping). The spike stubbed this as same-name; a real build
  needs explicit linking. This is the main new design element — it has no
  peer-sync parallel.
- **Per-user provisioning at scale.** ACL count grows O(users × projects)
  rather than O(node pairs), so automating grant-on-authorize /
  revoke-on-deauthorize matters more. The authorizing event is "user U is
  granted edit on project P (and opts into Globus upload)."
- **Local, not remote.** Source and destination are the same node — no alias
  token and no cross-node handshake. "Consent" reduces to the project-edit
  grant on this node plus provisioning the user's inbox ACL.

### 5.4 Implication: a unified inbox-authorization abstraction

Given the symmetry, model both as one underlying object — an **inbox
authorization**: `(inbox collection, principal identity, subpath,
permission)` provisioned and revoked through a single seam — with two
producers: **route approval** (peer sync) and **upload authorization**
(user). The ACL layer stays uniform; all the difference lives in the
XNAT-side authorization that triggers provisioning. This is the symmetry
worth building to.

## 6. Relationship to what already exists

- **Whitelist / classification** (`XsyncConfigurationService`, site
  settings) — Layer 1 gate 1, already built.
- **`RemoteAliasEntity`** (destination-issued alias tokens) — the existing
  destination-consent mechanism; the Globus ACL is its byte-movement
  parallel. A route needs *both*.
- **`GlobusNodeConfig`** (site-level singleton: client id/secret + outbox
  guest-collection UUID + optional own-inbox UUID) — the node's Globus
  service account and outbox. **`GlobusEndpoint`** is now the per-destination
  record (peer inbox UUID + remote paths) that routes reference. This split
  matches §3's "one confidential client per node, destinations are coarse."
- **`GlobusClient`** (Transfer API: probe + submit/status/wait) — the client
  the ACL-automation calls would extend.

## 7. Prototype vs. target, and staged increments

The first-pass prototypes need none of this: one manually-configured route
and one hand-provisioned upload user, both with hand-granted ACLs. Growing
toward the target model, in order:

1. A **route / destination registry** object plus the destination picker on
   the source project (Layer 1).
2. **Transfer-API ACL automation** so route approval provisions/revokes the
   Globus grant (Layer 3, step 1) — built as the **unified inbox-authorization
   seam** (§5.4) so the same code serves upload-user ACLs.
3. **Globus-identity ↔ XNAT-user mapping** for uploads (§5.3) plus
   grant-on-authorize provisioning of per-user inbox ACLs through that seam.
4. Optional later: **collection provisioning** via the GCS Manager API
   (Layer 3, step 2), with its privilege tradeoff addressed.

(1), (2), and (3) are additive to what is already built.

## 8. Open questions

- **Route granularity:** per source-project → destination-project (assumed
  here) vs. finer per-user restrictions within a source project.
- **Registry ownership:** source-side catalog curated by site admins vs. a
  more federated discovery model (the former is recommended for the target).
- **Collection-UUID governance:** whether the whitelist must also constrain
  destination *collections*, not just destination *URLs* (transfer plan §10
  decision 6).
- **Automation credential separation:** if collection provisioning is
  automated, whether to use a distinct higher-privilege client from the
  transfer client.
- **Upload identity mapping (§5.3):** how a user's Globus identity is bound
  to their XNAT account — self-service linking vs. admin mapping — and how
  that binding is verified.
- **Upload ACL trigger (§5.2):** whether per-user inbox ACLs are
  auto-provisioned when a user gains project-edit rights, or require an
  explicit per-user opt-in to Globus upload.
- **Upload node role bundle:** whether an upload node's service account holds
  `activity_monitor` + `access_manager` separately or a single
  `administrator` role on the upload inbox (detection vs. ACL provisioning).
