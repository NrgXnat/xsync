# XSync + Globus — Permissions and Access Management Design

_Design thinking for the **target** authorization model for XNAT-to-XNAT
sync over Globus. The first-pass prototype (a single manually-configured
a → b route with hand-granted ACLs) does **not** require any of this; this
document is the model to grow into, not a prototype requirement._

## 1. Organizing principle: separate the two authorizations

There are two distinct "permissions" in play, and the design keeps them
apart deliberately:

- **XNAT owns user/project authorization** — *who* may send *what*, from
  *which* source project, to *which* destination project. Rich, per-user,
  per-project, governed by XNAT roles.
- **Globus owns node-to-node byte movement** — a coarse, node-level fact:
  "node A's service client may write into node B's inbox." Globus never
  knows about XNAT users or projects.

Keeping these separate is what makes the model scale. In particular we do
**not** want a Globus identity per XNAT user: that would require every user
to hold a Globus account and complete a consent flow, which is a poor fit
for unattended service transfers and an administrative burden. Globus stays
node-level; all fine-grained decisions live in XNAT.

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

## 5. Relationship to what already exists

- **Whitelist / classification** (`XsyncConfigurationService`, site
  settings) — Layer 1 gate 1, already built.
- **`RemoteAliasEntity`** (destination-issued alias tokens) — the existing
  destination-consent mechanism; the Globus ACL is its byte-movement
  parallel. A route needs *both*.
- **`GlobusEndpoint`** (site-level: client id/secret + inbox/outbox guest
  collection UUIDs) — the node's Globus binding that routes reference.
- **`GlobusClient`** (Transfer API: probe + submit/status/wait) — the client
  the ACL-automation calls would extend.

## 6. Prototype vs. target, and staged increments

The first-pass prototype needs none of this: one manually-configured route,
manually-granted ACLs. Growing toward the target model, in order:

1. A **route / destination registry** object plus the destination picker on
   the source project (Layer 1).
2. **Transfer-API ACL automation** so route approval provisions/revokes the
   Globus grant (Layer 3, step 1).
3. Optional later: **collection provisioning** via the GCS Manager API
   (Layer 3, step 2), with its privilege tradeoff addressed.

Both (1) and (2) are additive to what is already built.

## 7. Open questions

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
