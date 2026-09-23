# XSync Plugin — Developer's Guide

_XNAT-to-XNAT data exchange plugin. Architecture and theory of
operation for developers new to the codebase._

This guide describes the `develop` branch (version `1.8.2-SNAPSHOT`,
`build.gradle:40`), which carries the migration to **Java 21 / XNAT
1.10.1** and the new **destination-site whitelist** feature
(PLUGINS-229). File and line references are given throughout; read the
cited code before relying on any behavioral claim, because several areas
carry acknowledged technical debt and workarounds.

> **What changed since 1.8.1 (`main`)** — if you already know the older
> plugin, the material differences are: (1) build/platform moved to
> Java 21 and XNAT 1.10.1-SNAPSHOT with GitHub-Actions CI (§2); (2) a
> governance layer over which destinations a project may target — a site
> **whitelist** (with an implicit local-site allowance), a project
> **blacklist**, and a URL-disable memory (§10); (3) expanded site
> preferences with explicit HTTPS/Aspera/whitelist toggles (§4.3); (4) a
> **site-admin configuration dashboard** for auditing, enabling, and
> disabling remote connections, backed by a new `XsyncConfigurationService`
> (§11.4); (5) a new **Xsync Administrator role** and an `@AuthDelegate`
> authorizer layer replacing inline permission checks on the controllers
> (§11.5); (6) new admin UI (§15). The **core sync engine — discovery,
> filtering, XAR packaging, transport, ID mapping — behaves as before**,
> though a repo-wide Lombok/cleanup pass (and a move of the history
> entities to `manifest/history/`) shifted many line numbers; the
> references here are current as of the latest `develop` revision.

---

## 1. Introduction

### 1.1 What XSync does

XSync lets a project on one XNAT ("source") push its data to a project
on a second XNAT ("destination"). It is installed **only on the source
side** — the destination is a stock XNAT that receives data through its
ordinary REST/import APIs. There is no XSync-specific code running on
the destination.

The core promise (`README.md:3`) is selective, scheduled, optionally
de-identified replication:

- **Selective** — a per-project JSON configuration decides which
  resources, subject assessors, imaging sessions, scans, and image
  assessors are eligible, by label / xsiType / scan-type.
- **Scheduled** — hourly / daily / weekly / monthly cron triggers, plus
  on-demand pushes of a project, subject, or single experiment.
- **De-identified** — DICOM can be run through a project-scoped Mizer
  anonymization script immediately before transfer.
- **Incremental** — "new data only" mode uses the database change log to
  transfer only what changed since the last successful sync.
- **Governed** — an optional site whitelist restricts which destination
  XNATs a project may be configured to target (§10, new on `develop`).

### 1.2 Mental model

Think of XSync as an **outbound replication daemon** grafted onto the
source XNAT:

```
  ┌───────────────────────── SOURCE XNAT (XSync installed) ─────────────────────────┐
  │                                                                                  │
  │  Scheduler / XAPI  ──▶  ProjectChangeDiscoverer (Callable, per project)          │
  │        │                      │                                                  │
  │        │                      ├─▶ QueryResultUtil  (SQL change discovery)        │
  │        │                      ├─▶ ProjectSyncConfiguration (what to sync)        │
  │        │                      ├─▶ SubjectDataSync ─▶ ExperimentFilter            │
  │        │                      │        │               (filter + anonymize)      │
  │        │                      │        └─▶ XsyncExperimentTransfer.storeXar()     │
  │        │                      │                 builds XAR / resource ZIPs        │
  │        │                      └─▶ SynchronizationManager (in-memory manifest)     │
  │        │                                                                          │
  │  (config save is gated by the site whitelist, §10)                               │
  │        └─▶ SyncStatusService (live progress)   RemoteConnectionManager            │
  │                                                     │                            │
  └─────────────────────────────────────────────────── │ ───────────────────────────┘
                                                        │ HTTPS (alias-token auth)
                                                        ▼
                          ┌──────────── DESTINATION XNAT (stock) ────────────┐
                          │  /data/services/import  (XAR import handler)      │
                          │  /data/archive/... (subjects, resources, delete)  │
                          │  /data/services/tokens/issue (alias refresh)      │
                          └───────────────────────────────────────────────────┘
```

Every sync run is a `Callable`/`Runnable` submitted to a bounded thread
pool. It walks the source project's changed entities, rebuilds each as a
detached in-memory XFT item with **remapped IDs/labels**, packages it as
a XAR (imaging data) or ZIP (resources), and POSTs it to the destination
using the destination's own import endpoints. Results are accumulated in
an in-memory manifest that drives email notifications, an HTML log
resource, and a Hibernate history table.

### 1.3 Key design characteristics (and consequences)

- **One-directional, source-push.** The destination is never queried for
  "what should I have"; the source decides and pushes. Deletions
  propagate only when the destination user has owner access.
- **Label-keyed, ID-remapped.** Source and destination assign different
  accession IDs. XSync maintains its own mapping table
  (`xsync_xsyncremotemapdata`) so re-syncs update rather than duplicate.
  See `IdMapper` (§7).
- **Change detection is raw SQL** against XNAT's `*_meta_data` and
  `*_history` tables, not the XFT object API. `QueryResultUtil` is
  effectively a second, hand-written data-access layer (§5).
- **Heavy use of static/in-memory state.** `SynchronizationManager`,
  `SyncStatusHolder`, and the JSESSIONID cache are process-global maps.
  This is why the plugin insists on running only on the primary node
  (`AbstractSyncService.doSync`, line 89).
- **Mixed-era code.** The build now targets **Java 21**
  (`build.gradle:56-59`) and the newest code uses modern idioms
  (records-style Lombok POJOs, `switch` expressions —
  `WhitelistXsyncSiteServiceImpl.java:85`). But the core transport still
  uses hand-rolled `while(true)` retry loops whose comments ask for
  Spring `@Retryable` "when we upgrade spring to 4"
  (`RemoteRESTServiceImpl:364`, 469) — those TODOs are now stale but the
  loops remain. Expect to see both styles.

---

## 2. Build, packaging, and bootstrap

### 2.1 Gradle build

- Single-module Gradle build; `settings.gradle` sets
  `rootProject.name = 'xsync-plugin'`.
- **Platform pin.** `vXnat = '1.10.1-SNAPSHOT'` (`build.gradle:12`) drives
  both the `org.nrg:parent` BOM (`build.gradle:62`) and the
  `xnat-data-builder` plugin version (`build.gradle:26`) — they are kept
  in lockstep by reusing `${vXnat}`.
- **Java 21.** `java { sourceCompatibility = JavaVersion.VERSION_21;
  targetCompatibility = ... }` (`build.gradle:56-59`). The plugin now
  matches the XNAT 1.10 tree's Java level (older releases compiled to
  Java 8). Because JDK 11+ dropped JSR-250, `javax.annotation:javax.annotation-api`
  is added as a `compileOnly` dependency for `@PostConstruct` /
  `@Nonnull` / `@Nullable` (`build.gradle:147-148`).
- **Lombok** is applied via `io.franzbecker.gradle-lombok` and pinned to
  `1.18.34` with a checksum (`build.gradle:65-68`). The whitelist POJOs
  and entity use Lombok `@Getter/@Setter/@AllArgsConstructor` (§10).
- **Code generation.** `org.nrg.xnat.build.xnat-data-builder`
  (`build.gradle:36`) generates XFT bean/OM classes from
  `src/main/resources/schemas/xsync/xsync.xsd` into
  `build/xnat-generated/src/main/java` (added as a source dir,
  `build.gradle:157-167`). Those generated `XsyncXsync*` classes are
  referenced throughout but do not exist in the repository — they appear
  only after a build.
- **Fat jar** via the `fatJar` task (`build.gradle:170`), bundling
  OpenCSV (the only `implementAndInclude` dependency, `build.gradle:109`).
  Nearly every XNAT dependency is `transitive = false` /
  `compileOnly`/`implementation` because the runtime is provided by the
  host XNAT.
- **Publishing.** The `XNAT_Artifactory` Maven repo now targets
  `libs-snapshot-local` / `libs-release-local` and takes credentials from
  `artifactoryUser`/`artifactoryToken` project properties or
  `ARTIFACTORY_USER`/`ARTIFACTORY_TOKEN` env vars (`build.gradle:198-211`)
  — the old `~/.m2/settings.xml`-based `maven-settings-plugin` was
  removed.
- **CI** moved from GitLab (`.gitlab-ci.yml`, deleted) to **GitHub
  Actions**: `.github/workflows/build-publish.yml`,
  `release-cut-rc.yml`, `release-promote.yml`.

Build command (`README.md:110`): `./gradlew clean fatjar` → artifact in
`build/libs/xsync-plugin-all-*.jar`.

### 2.2 Plugin registration

`org.nrg.xsync.configuration.XsyncPlugin` (`XsyncPlugin.java:15`) is the
entry point (unchanged on `develop`):

- `@XnatPlugin(value = "xsyncPlugin", ...)` registers the plugin and its
  four XFT data models (lines 17-34): `XsyncRemoteMapData`,
  `XsyncInfoData`, `XsyncAssessorData`, `XsyncProjectData`.
- `entityPackages = {"org.nrg.xsync.remote.alias", "org.nrg.xsync.manifest"}`
  (line 35) registers the Hibernate entities. **Note:** the new
  `WhitelistSite` entity lives under `org.nrg.xsync.manifest.whitelist`,
  so it is picked up by the existing `org.nrg.xsync.manifest` package
  scan without a config change (§10).
- `logConfigurationFile` (line 36) routes XSync logging to its own file.
- `@ComponentScan` enumerates the packages Spring scans.
  **Two root packages** are in play — most code under `org.nrg.xsync.*`,
  but anonymizer/transformers/generators/report/remote-verify under
  `org.nrg.xnat.xsync.*`. The latest revision added scan entries for
  `org.nrg.xsync.security` (the authorizer beans, §11.5) and
  `org.nrg.xsync.initialization.tasks` (the upgrade role-grant task, §11.5).
- One `@Bean` is declared inline: `xsyncDataTypeSpecificTransformer`
  (line 59), the dispatcher for datatype-specific field rewriting (§13).

---

## 3. Data model

XSync stores state in two very different persistence systems.

### 3.1 XFT / XNAT-schema types (`schemas/xsync/xsync.xsd`)

These are first-class XNAT datatypes, queryable via the XFT API and
stored in generated Postgres tables. The `xdat:field` app-info
annotations map elements to SQL columns and unique composites.

| Type (`xsync.xsd`) | Table | Purpose |
|---|---|---|
| `xsyncProjectData` (line 60) | `xsync_xsyncprojectdata` | Per-project sync config: `source_project_id`, `sync_enabled`, `sync_blocked`, `notification_emails`, `no_of_retry_days`, and an embedded `syncInfo`. |
| `xsyncInfoData` (line 97) | `xsync_xsyncinfodata` | The `syncInfo` block: `remote_url`, `remote_project_id`, `sync_frequency` (enum: hourly/daily/weekly/monthly/on demand), `sync_new_only`, `identifiers` (use_local/use_remote/use_random/use_custom_local/use_custom), `custom_identifier_class`, and the all-important `sync_start_time` / `sync_end_time` watermarks. |
| `xsyncRemoteMapData` (line 154) | `xsync_xsyncremotemapdata` | The **ID mapping table**. Maps `(source_project_id, local_xnat_id, xsiType, remote_project_id, remote_host_url)` → `remote_xnat_id`, plus a `sync_status`. This is how re-syncs find the previously assigned destination accession number. Uniqueness composite `UNIQ_REMOTE`. |
| `xsyncAssessorData` (line 16) | `xsync_xsyncassessordata` | An **"OK to sync" marker** attached to an experiment. Extends `xnat:subjectAssessorData`. Fields: `synced_experiment_id`, `okToSync`, `authorized_by`, `authorized_time`, `remote_project_id`/`remote_url`, `sync_status`. Drives the QC gate. |

The two watermark columns `sync_start_time` and `sync_end_time` on
`xsyncInfoData` are the linchpin of incremental sync — nearly every
change-discovery query joins to them (§5).

### 3.2 Hibernate entities (`@ComponentScan` entity packages)

Registered via `entityPackages` and backed by
`AbstractHibernateEntityService`:

- **`RemoteAliasEntity`** (`remote/alias/RemoteAliasEntity.java`) →
  `xhbm_remote_alias_entity`. Stores the destination alias token +
  secret per `(remote_host, local_project)` (unique constraint, line 16).
  This is the **credential store** for talking to the destination (§9).
- **`WhitelistSite`** (`manifest/whitelist/WhitelistSite.java`, **new**)
  → stores an approved destination XNAT: `siteId`, `siteName`, `siteUrl`
  (all unique) and a `SiteClassification` enum
  (`PUBLIC`/`RESEARCH`/`CLINICAL`). A Lombok-annotated
  `AbstractHibernateEntity` (§10).
- **`XsyncProjectHistory`** and its children (`XsyncSubjectHistory`,
  `XsyncExperimentHistory`, `XsyncAssessorHistory`,
  `XsyncResourceHistory`) in **`manifest/history/`** (moved out of
  `manifest/` in the latest revision) → the durable sync history rendered
  in the UI (§11) and the admin dashboard (§11.4). Their API read-models
  live in `pojo/history/` (`XsyncProjectHistoryPojo`, etc.).

### 3.3 Why two persistence systems?

**There is no documented rationale.** The class-level Javadoc on the
entities is only author tags, there are no explanatory comments, and no
design doc exists. (The `xsync.xsd:11` comment "no defined data types" is
boilerplate and is actually wrong.) The reasoning below is *inferred from
how the code uses each store*, not a recorded decision.

The discriminator that actually holds is: **does this state have to live
inside the XNAT data model?**

**XFT is used when the answer is yes**, for one of three concrete reasons:

1. **It attaches to the subject/experiment hierarchy.** `xsyncAssessorData`
   extends `xnat:subjectAssessorData` (`xsync.xsd:18`). This case is
   essentially forced — to hang the "OK to sync" QC marker off an
   experiment, query it
   (`XsyncXsyncassessordata.getXsyncXsyncassessordatasByField`), scope it
   by project permissions, and save it with an XNAT event/workflow
   (`okToSyncData.save(user, false, true, c)`), it must be an XFT
   datatype. XNAT offers no other way to model an experiment-attached
   assessor.
2. **It is SQL-joined against XNAT's archive/audit tables.**
   `xsyncProjectData`, `xsyncInfoData`, and `xsyncRemoteMapData` are
   joined to `xnat_subjectdata_meta_data`, `wrk_workflowdata`,
   `xnat_experimentdata`, etc. throughout `QueryResultUtil` (the entire
   change-discovery engine turns on the `sync_start_time`/`sync_end_time`
   watermark join). Those joins need stable, predictable Postgres
   table/column names in the same schema — exactly what the
   `xdat:field` / `sqlField` / `uniqueComposite` annotations in the
   `.xsd` control (e.g. the `UNIQ_REMOTE` composite, `xsync.xsd:171`).
3. **It is project-scoped config saved through the XFT event/permission
   model** (`syncProjectConfiguration.save(user, ...)`,
   `SynchronizationManager.END_SYNC`).

**Hibernate is used for "plugin-private" operational state** that is not
part of the archive and needs none of the above:

- `RemoteAliasEntity` — a **secrets store** (alias token + secret). Making
  it an XFT datatype would expose credentials through the queryable
  REST/data model; a plain `xhbm_` table keeps them out of the archive
  surface. Keyed by `(remote_host, local_project)`, not XNAT project
  scope.
- `WhitelistSite` — site-admin config list; no hierarchy, no per-project
  security, simple CRUD; also the newest code (2024), when new
  non-datatype state defaults to Hibernate.
- `XsyncProjectHistory` + children — a write-once audit/reporting log; a
  nested project→subject→experiment→resource tree is far simpler as
  `@Entity` relations than as XFT schema types.

Two caveats for anyone reasoning about this:

- **"Uses raw SQL" is *not* the discriminator.** `RemoteAliasEntity` is
  also read by raw SQL against its own table
  (`RemoteConnectionHandler.getRemoteConnectionQuery()`:
  `select * from xhbm_remote_alias_entity ...`). The real line is
  *joined to the XNAT archive/audit tables and part of the data model*
  (→ XFT) vs *standalone plugin table* (→ Hibernate).
- **Part of the split is authorship and era, not a single policy.** The
  XFT types and the Hibernate history were written by different
  developers around the same time (2016), and the newest addition went to
  Hibernate. The forced cases (an experiment-attached assessor must be
  XFT; credentials should not be) are principled; the softer cases look
  like convention plus convenience. The absence of any justifying comment
  supports that reading.

---

## 4. Configuration subsystem

Two layers of configuration, plus site preferences.

### 4.1 Project sync configuration JSON

The rich "what to sync" definition is stored as a JSON blob in XNAT's
`ConfigService` under tool `xsync`, config `json`, project scope
(`ProjectSyncConfiguration.setSyncConfigurationFromService`, line 214).
It deserializes into `configuration.json.SyncConfiguration`
(`SyncConfiguration.java:14`), a tree of POJOs:

- `SyncConfiguration` → `project_resources`, `subject_resources`
  (`SyncConfigurationResource`), `subject_assessors`
  (`SyncConfigurationSubjectAssessor`), `imaging_sessions`
  (`SyncConfigurationImagingSessions`).
- Each level supports a `sync-type` of `include` / `exclude` / `all` /
  `none` (`README.md:221`). The **default when a level is absent is
  "sync everything"** — see the `hasXxxConfigurationDefinition()` guards
  that return `true` when a block is null (`SyncConfiguration.java:41-118`).
- Imaging sessions additionally carry per-scan-type, per-scan-resource,
  per-assessor filters and the `needs_ok_to_sync` QC flag
  (`SyncConfigurationImagingSessionXsiType`).

`ProjectSyncConfiguration` (`configuration/ProjectSyncConfiguration.java`)
is the read-model wrapper constructed at the start of every sync. It:

- Loads both the JSON config (line 214) and the XFT
  `XsyncXsyncprojectdata` row (line 182).
- On first-ever sync, seeds `sync_start_time`/`sync_end_time` to a dummy
  1970 date so the change queries return everything
  (`setProjectSyncConfiguration`, lines 193-200; `OLD_CALENDAR`, line 229).
- Exposes the many `isXToBeSynced(...)` predicates the export pipeline
  consults (lines 58-143).

### 4.2 Config write path

`XsyncSetupController` (`xapi/XsyncSetupController.java`) is the CRUD
surface:

- `POST /xsync/setup/projects/{projectId}` (`setup`, line 72) takes a
  typed `SyncConfigurationPojo`, then applies **two governance gates
  inline** before saving: it rejects the project if it is on the
  `projectBlacklist` (lines 79-81; §10.6), and — when the whitelist is
  enabled — rejects a `remote_url` that matches no whitelisted `siteUrl`
  (lines 86-90; §10). It then persists via
  `XsyncConfigurationService.saveConfig` (line 94). Access is gated by
  `@AuthDelegate(XsyncDeleteProjectUserAuthority.class)` (§11.5).
- `GET /xsync/setup/projects/{projectId}` (`getXsyncProjectConfiguration`,
  line 109) returns the config as a `SyncConfigurationPojo`.
- `PUT/GET /xsync/setup/presyncanonymization/projects/{projectId}`
  (lines 126, 149) store/read the DICOM anonymization script as a separate
  config (`xsync` / `presyncanonymization`).

In the latest revision most of this controller's logic moved into the new
`XsyncConfigurationService` (§11.4), which owns config read/write and the
typed-POJO layer (`pojo/configuration/`). Note the whitelist *gate* is
inlined in the controller (above); the service's
`checkForWhitelistConformation` (line 146) is now used only by the
dashboard to *tag* existing destinations as conforming or not (§11.4).

### 4.3 Site preferences

`components.XsyncSitePreferencesBean` (`@NrgPreferenceBean`, tool id
`xsync`) holds site-wide knobs (`XsyncSitePreferencesBean.java`):

- `tokenRefreshInterval` (default 10h) — alias-token refresh cadence (§9).
- `syncRetryInterval` (default 2h) / `syncRetryCount` (default 2) — retry
  behavior for remote calls (§8).
- `syncMaxUncompressedZipFileSize` (default `-1`, disabled) — the chunk
  threshold for splitting large resource ZIPs (`README.md:314`).
- **`httpsEnabled`** (default `true`, line 134) / **`asperaEnabled`**
  (default `false`, line 147) — site-level transfer-method toggles
  surfaced in the "Connection Management" admin tab.
- **`xsyncWhitelistEnabled`** (default `false`, line 95) — master switch
  for the destination whitelist (§10).
- **`projectBlacklist`** (`List<String>`, default `[]`, line 121) — local
  project IDs barred from having any Xsync connection (§10.6).

Interval strings like `"10 hours"` are parsed by
`calculateIntervalInMillis` (line 297), which enforces a 5-minute floor.
The new booleans are plain `getBooleanValue`-backed preferences.

**Whitelist seeding at startup.** The bean's constructor calls
`addInitialWhitelistSitesToPreferences()` (invoked at lines 38/66,
defined at line 319), which reads the bundled
`META-INF/xnat/xsyncSiteWhitelist.json`, parses its `allowedSites`
array, and hands them to `WhitelistXsyncSiteService.addWhiteListSitesFromJson`
(§10). Missing/malformed JSON is logged and ignored, so absence of the
file is not fatal.

Preferences are read/written through `XsyncPreferencesController`
(`/xapi/xsyncSitePreferences` and friends). The pojo carrier
`XsyncSitePreferencesPojo` gained `xsyncWhitelistEnabled`, `httpsEnabled`,
`asperaEnabled` fields (`pojo/XsyncSitePreferencesPojo.java`).

---

## 5. Change-discovery engine (`QueryResultUtil`)

`utils/QueryResultUtil` (`QueryResultUtil.java`, `@Component`) is the
heart of incremental sync and the single most important file to
understand. It is a **collection of hand-written SQL strings** run
through `NamedParameterJdbcTemplate`. It bypasses XFT entirely and reads
XNAT's internal audit tables directly. (Unchanged on `develop`.)

Key patterns:

- **"Modified since last sync"** queries UNION three sources: the live
  `*_meta_data` table (rows changed since the watermark), the entity's
  own workflow rows, and the `*_history` table (for deletions). See
  `getQueryForFetchingSubjectsModifiedSinceLastSync` (line 54) — the long
  comment at lines 55-62 documents a real bug this design fixed: a
  session's row can appear modified before its files are actually
  archived, so the query keys off **`sync_start_time`** (not end time)
  and also inspects workflow modification times.
- **Watermark join.** Almost every query ends by joining
  `xsync_xsyncprojectdata` → `xsync_xsyncinfodata` and comparing
  `*_meta_data.row_last_modified` against `xsi.sync_start_time` or
  `xsi.sync_end_time`. The choice differs by query and is a known source
  of subtlety (see the TODO at line 61).
- **Retry harvesting.** Separate queries re-collect entities whose
  `xsync_xsyncremotemapdata.sync_status` is `failed` or
  `skipped_by_filter` within `no_of_retry_days`
  (`getQueryForFetchingSubjectsWithFailedOrSkippedByFilterAssessorSyncs`,
  line 90; `getQueryForFetchingSubjectExperimentsWithFailedSyncs`, line
  254). This is how transient failures self-heal on the next run.
- **OK-to-sync harvesting.**
  `getQueryForFetchingSubjectExperimentsMarkedOKSinceLastSync` (line 277)
  right-joins `xsync_xsyncassessordata` to pick up sessions a QC user has
  flagged.
- **Deleted-resource detection** uses a Postgres window function to find
  the latest history row per resource
  (`getQueryForFetchingSubjectResourcesDeletedSinceLastSync`, line 182).

The class also carries generic list/map reshaping helpers
(`separateByColumn`, `reorganizeAsPivotColumnArray`, lines 340-446).

> **For new developers:** these queries encode assumptions about XNAT's
> physical schema (`xnat_subjectdata_meta_data`, `wrk_workflowdata`,
> `xnat_abstractresource_history`, etc.). A schema change in core XNAT
> can silently break sync selection here without a compile error.

---

## 6. Scheduling and the sync task hierarchy

### 6.1 Triggers

`scheduler.XsyncScheduler` (`@Configuration @EnableScheduling`) declares
the cron triggers as Spring `TriggerTask` beans
(`XsyncScheduler.java`):

- Hourly: `0 30 * * * ?` (line 52) — 30 min past each hour.
- Daily: `0 0 0 * * *` (line 60) — midnight.
- Weekly: `0 0 1 ? * SAT` (line 67) — Saturday 01:00.
- Monthly: `0 0 2 1 * *` (line 74) — 1st of month 02:00.
- Token refresh: a `PeriodicTrigger` at the configured interval (line 42).

It also defines the shared `xsyncThreadPoolExecutorFactoryBean`
(core pool size 5, thread prefix `xsync-thread-`, lines 30-37) used by
both the scheduler and the XAPI controllers.

### 6.2 Task services

`services.local.AbstractSyncService` (`@XnatTask`,
`AbstractSyncService.java:30`) is the base for the four frequency
services (`DefaultDailySyncService`, `DefaultHourlySyncService`, etc.).
Each concrete service:

1. Is a `Runnable`/XnatTask fired by the matching `TriggerTask`.
2. Queries `QueryResultUtil.getProjectsTobeSyncedDaily()` (etc.) for
   projects whose `sync_frequency` matches (line 480 in `QueryResultUtil`).
3. Calls `doSync(rows)` (line 88), which — **only on the primary node**
   (`isPrimaryNode()` guard, line 89) — constructs a
   `ProjectChangeDiscoverer` per project and submits it to the executor
   (lines 98-107).

`@XnatTask(... defaultExecutionResolver = "SingleNodeExecutionResolver")`
(line 30) is the cluster-safety mechanism: only one node runs the
schedule.

---

## 7. The export pipeline

This is where a source project's changed data is transformed and pushed.
(Unchanged on `develop`.) The call graph, top to bottom:

```
ProjectChangeDiscoverer.sync()           (discoverer/ProjectChangeDiscoverer.java)
  ├─ syncProjectResources()              project-level resource files
  ├─ for each changed subject:
  │    SubjectDataSync.sync()             (local/SubjectDataSync.java)
  │      ├─ IdMapper.correctIDandLabel()  remap subject id/label
  │      ├─ ResourceFilter.select()       which subject resources
  │      ├─ ExperimentFilter.select()     which experiments (NEW/MOD/DEL/OK/FAILED)
  │      ├─ storeSubject()                PUT subject XML to destination
  │      ├─ ResourceSyncManager.syncResources()
  │      └─ BatchExperimentSync.syncExperiments()
  │            └─ XsyncExperimentTransfer.syncExperiment()
  │                 └─ storeXar()          build + POST XAR(s)
  └─ verifyProjectResources()            file-count/-size verification
```

### 7.1 Orchestrator — `ProjectChangeDiscoverer`

`discoverer/ProjectChangeDiscoverer.java` implements `Callable<Void>`.
`sync()` (line 114) is `synchronized` and:

1. Short-circuits if `sync_enabled` is false (line 125) or if
   `SyncStatusService.isCurrentlySyncing` reports an in-progress run
   (line 130) — sending a "sync skipped" email in that case.
2. Creates the HTML log resource on the source project
   (`XsyncFileUtils.createSynchronizationLogResource`, line 148).
3. Opens the run via `SynchronizationManager.BEGIN_SYNC` and registers
   with `SyncStatusService.registerSyncStart` (lines 156-157).
4. Syncs project resources, then iterates changed/failed/shared subjects
   (lines 159-214), tolerating up to `MAX_FAILURES = 5` subject failures
   before aborting (line 194).
5. Verifies project resources, then `END_SYNC` persists results
   (line 220) and `registerSyncEnd` clears live status.
6. In `finally` (line 234), closes the observer and refreshes the log
   resource catalog.

Note the class holds the sole `MAX_FAILURES` constant and a TODO that it
"should be configurable" (line 75).

### 7.2 Subject export — `SubjectDataSync`

`local/SubjectDataSync.java` `sync(boolean syncExperimentsAlso)`
(line 98):

- Copies the subject's `XFTItem`, regenerates a detached bean, sets the
  **remote project** (lines 100-104).
- Runs `IdMapper.correctIDandLabel` to remap id/label and strip shared-
  project references (line 122).
- Selects resources (`ResourceFilter`) and experiments
  (`ExperimentFilter`) to sync (lines 125-129).
- `storeSubject` PUTs the subject XML to the destination and records the
  returned remote id (lines 135-138), then syncs resources and (if
  requested) experiments (lines 147-151).

The comment at `RemoteRESTServiceImpl.importSubjectWithoutRetry`
(lines 771-775) explains a subtle correctness rule: the subject is
serialized with `subject.toXML()` rather than `getItem()...` to avoid
dragging in experiments that share an accession number and overwriting
unrelated destination sessions. A separate `syncSubject()` method
(line 170) exists for subject-only on-demand syncs; the code openly flags
the duplication as tech debt (lines 167-169).

### 7.3 Filtering — `ExperimentFilter`

`local/ExperimentFilter.java` (1165 lines) does two jobs:

1. **`select(...)`** (line 65) partitions a subject's experiments into
   `ACTIVE`/`DELETE`/`NEW`/`OK_TO_SYNC`/`FAILED` buckets by cross-
   referencing the change queries, insert/modify dates vs. the
   watermarks, OK-to-sync markers, and prior sync status (lines 244-249).
   The date arithmetic distinguishing "added" vs. "modified" is at
   lines 154-176.
2. **`prepareImagingSessionToSync(...)`** (line 610) produces a cleaned,
   filtered, ID-corrected, optionally anonymized session ready to be
   packaged. It applies, in order: assessor filter → id/label correction
   → datatype transform → resource filter → prearchive reset → scan-type
   filter → scan filters → scan-resource rewrite → reconstruction filter
   → assessor filter → **anonymize** (lines 614-674). The many
   `findAndRemoveXxx` helpers (lines 760-1037) drop non-configured
   scans/resources/assessors from the in-memory item.

The `modifyExptResource` methods (lines 517-548) rewrite resource URIs to
point at a per-sync cache directory and copy files there; when a resource
is unchanged since the last verified sync they set negative file
counts/sizes as a "skip" sentinel (lines 534-535), later honored in
`storeXar` (line 369).

### 7.4 XAR packaging and transfer — `XsyncExperimentTransfer`

`local/XsyncExperimentTransfer.java` is the workhorse.
`storeXar(...)` (line 306) is the imaging-session path:

- Strips assessors from the session item, prepares resource URIs for XAR
  layout, and builds a **session-metadata-only XAR**
  (`buildImagingSessionXar`, called line 331). Scans, resources, and
  assessors are pushed as **separate transactions** afterward — the
  comment at lines 328-329 explains why.
- Sends the XAR via `_manager.importXar(...)` (or Aspera, §14, line 338),
  extracts the destination-assigned accession id from the response
  (`XsyncURIUtils.getRemoteAssignedId`, line 343), and persists the
  mapping (`saveSyncDetails`, line 349).
- Guards against the recurring bug where a session gets the subject's
  accession number (`_S\d+$` regex, line 345).
- For each resource: `pushImagingSessionResource` (line 376). For each
  scan: builds and posts a per-scan XAR
  (`buildImagingScanXar`, line 393). For each image assessor: rebuilds,
  re-corrects ids, transforms, and posts its XAR.
- Finally `verifySync` (line 476) fetches the remote session XML and
  compares scans/resources, writes a remote workflow entry, updates the
  OK-to-sync assessor, and saves status.

`syncExperiment` (line 95) is the dispatcher deciding session vs. subject
assessor and applying the OK-to-sync gate: sessions needing OK that
haven't been flagged are recorded as `WAITING_TO_SYNC` and skipped
(lines 136-143). Image **assessors** are skipped at this level
(line 101) because they ride along with their parent session.

`lookForSessionAtDestination` (line 565) handles the common
"HTTP 500 duplicate" case: if the push fails but the session already
exists remotely, it records the existing remote id and marks the item
skipped rather than failed.

### 7.5 ID mapping and conflict handling — `IdMapper`

`local/IdMapper.java` resolves how a local entity is identified on the
destination:

- **Label assignment** (`assignRemoteLabel`, line 56) consults a custom
  `XsyncLabelGeneratorI` bean when `identifiers == use_custom`
  (§13), else uses the local label.
- **ID resolution** (`getAssignedRemoteId`, line 75) queries
  `xsync_xsyncremotemapdata` for a previously assigned `remote_xnat_id`.
  If found, the item reuses it (an update); if not, the id is blanked so
  the destination mints a new one (`correctIDandLabel`, lines 104-111).
- For experiments it can also fall back to a live REST lookup on the
  destination (`getRemoteAccessionIdFromDbOrRest`, line 158) and applies
  the same `_S\d+$` subject-accession guard (lines 173-177).
- `ConflictCheckUtil.checkForConflict` (called at line 107) verifies the
  remote id doesn't already belong to a different label before reuse.

Shared-project references are stripped so the destination doesn't inherit
source sharing (lines 114-122).

---

## 8. Remote transport layer

### 8.1 `RemoteConnectionManager` and `RemoteConnection`

`connection.RemoteConnectionManager` (`@Service`) is the façade the
pipeline calls (`importSubject`, `importXar`, `deleteSubject`,
`importProjectResource`, etc., lines 211-383). It owns:

- **Auth header construction** (`GetAuthHeaders`/`addAuthHeaders`,
  lines 63-108): HTTP Basic (`alias:secret`, base64) or a cached
  `JSESSIONID`.
- **A static JSESSIONID cache** keyed by `url + localProject`
  (`sessionCache`, line 46; `getJsessionId`, line 129).

`RemoteConnection` (`connection/RemoteConnection.java`) is a mutable
credential holder tied to a `RemoteAliasEntity` id;
`useRefreshedAliasToken()` (line 83) re-reads a fresh token/secret after
an auth failure.

`RemoteConnectionHandler` (`connection/RemoteConnectionHandler.java`)
builds a `RemoteConnection` from the alias table via SQL and implements a
**process-global lock** (`LOCKED_CONNECTIONS`, line 117) so a sync waits
(up to a minute, then errors) if a token refresh is mid-flight for that
`(project,host)` (lines 77-94).

### 8.2 `RemoteRESTServiceImpl`

`services.remote.RemoteRESTServiceImpl` (`@Service`) is the actual HTTP
client:

- **XAR import** (`importXar`, line 143) chooses sync vs. async by file
  size: files > 5 MB upload to the destination user cache and then import
  with an `http-session-listener`, polled via
  `/xapi/event_tracking/{listener}` in `monitorAsyncImport` (line 298,
  2-hour cap). Small files POST directly to `/data/services/import`.
- **Resource ZIP import** (`importZipWithoutRetry`, line 366) POSTs a
  multipart ZIP to the appropriate `/files?overwrite=true&extract=true&update-stats=`
  endpoint (project/subject/experiment/assessor variants, lines 654-757).
- **Retry.** Every public method is a hand-rolled `while(true)` loop that
  retries up to `maxTries` (`syncRetryCount`) with `sleep`
  (`syncRetryInterval`) between attempts, calling `useRefreshedAliasToken`
  on auth failures (e.g. `importXar`, lines 143-166). Certain HTTP 400/500
  responses are treated as terminal (`ERROR_STATUSES`, line 935).
- **Auth-failure fallback.** Try with cached JSESSIONID, catch
  `XsyncHttpAuthenticationException`, retry with fresh basic-auth headers
  (e.g. `importSubjectWithoutRetry`, lines 782-804).
- On failed subject/workflow stores it dumps the item XML to the sync
  cache directory for post-mortem (lines 796-802).

`RemoteConnectionResponse` wraps the `ResponseEntity` and exposes
`wasSuccessful()`; `XsyncResponseErrorHandler` prevents Spring from
throwing on non-2xx so the code can inspect status itself.

---

## 9. Authentication and alias-token lifecycle

XSync never stores the destination user's real password long-term.
Instead it uses XNAT **alias tokens**.

- **Credential entry.** `XsyncRemoteCredentialsController`
  (`/xsync/credentials`) `saveRemoteCredentials` (line 77) accepts host,
  alias, secret, local/remote project, verifies the user actually has the
  needed access at the destination
  (`userHasRequiredAccessAtRemoteProject`, line 164 — an owner-vs-member
  distinction that governs whether deletes will propagate), then upserts
  a `RemoteAliasEntity`. `checkRemoteCredentials` (line 229) validates an
  existing connection. (On `develop` this class was modernized —
  `StandardCharsets.UTF_8`, `isEmpty()`, tidied logging — with no
  behavioral change.)
- **Storage.** Tokens live in `xhbm_remote_alias_entity`, one row per
  `(remote_host, local_project)` (unique constraint,
  `RemoteAliasEntity.java:16`).
- **Refresh.** A `PeriodicTrigger` (`XsyncScheduler.refreshToken`,
  line 42) runs `XSyncAliasTokenRefresh`, which per project runs
  `DefaultXsyncAliasRefresherForAProject`
  (`services/local/impl/DefaultXsyncAliasRefresherForAProject.java`).
  That `Runnable`:
  - Takes the `RemoteConnectionHandler` lock (line 49) to block syncs
    during refresh.
  - Calls the destination's `/data/services/tokens/issue/{alias}/{secret}`
    (line 55), parses `alias`/`secret`/`estimatedExpirationTime`
    (lines 73-83), and updates the entity.
  - Retries up to 4 times at 15-minute intervals (lines 34-35), emailing
    the admin on final failure with the offending entity id (lines 88-95).

---

## 10. Destination governance: whitelist, blacklist, transfer control

The `develop` branch grew a governance layer over *which destinations a
project may sync to* and *which projects may sync at all*: a destination
**whitelist** (§10.1–10.4), an implicit **local-site allowance**
(§10.5), and a project **blacklist** (§10.6).

PLUGINS-229 adds an optional, site-admin-managed **whitelist of
destination XNATs**. When enabled, a project owner can only configure
XSync to push to one of the approved sites.

### 10.1 Domain and persistence

- `manifest/whitelist/WhitelistSite.java` — the Hibernate entity
  (`@Entity`, Lombok `@Getter/@Setter/@AllArgsConstructor/@NoArgsConstructor`).
  Columns `siteId`, `siteName`, `siteUrl` are each `unique = true,
  nullable = false`; plus a `SiteClassification`.
- `manifest/whitelist/SiteClassification.java` — enum
  `{ PUBLIC, RESEARCH, CLINICAL }`.
- `manifest/whitelist/WhitelistSiteRepository.java` —
  `AbstractHibernateDAO<WhitelistSite>` with a
  `findWhitelistSiteBySiteId` finder.
- `pojo/WhitelistSitePojo.java` — the API/transport carrier (classification
  as a plain `String`).

### 10.2 Service

`services.local.WhitelistXsyncSiteService` /
`impl.WhitelistXsyncSiteServiceImpl`
(`AbstractHibernateEntityService`, `@Service @Transactional`) provides:

- `getAllWhitelistedSites()` (line 24) — list as pojos.
- `addWhiteListSitesFromJson(list)` (line 36) — **idempotent seeding**:
  inserts each site only if its `siteId` is not already present; a bad
  `classification` is logged and skipped, not fatal.
- `addOrUpdateWhitelistSiteFromSiteAdmin(pojo)` (line 53) — upsert by
  `siteId`.
- `deleteWhitelistSiteFromSiteAdmin(pojo)` (line 73) — delete by `siteId`,
  throwing `NotFoundException` if absent.
- `getSiteClassification(pojo)` (line 83) parses the classification via a
  Java 21 `switch` expression, throwing `DataFormatException` on unknown
  values.

### 10.3 Seeding and enforcement

- **Seed at startup.** `XsyncSitePreferencesBean`'s constructor calls
  `addInitialWhitelistSitesToPreferences()`
  (`XsyncSitePreferencesBean.java:319`), reading the bundled
  `META-INF/xnat/xsyncSiteWhitelist.json` (`allowedSites` array) and
  feeding it to `addWhiteListSitesFromJson`. The shipped file contains
  three example sites (research/public/clinical) — replace or empty it
  for real deployments.
- **Master switch.** `xsyncWhitelistEnabled` preference (default `false`,
  `XsyncSitePreferencesBean.java:95`).
- **The gate.** Enforcement happens at **config-save time only**, and in
  the latest revision the check is **inlined in
  `XsyncSetupController.setup`** (`XsyncSetupController.java:86-90`): when
  the whitelist is enabled, the incoming config's `remote_url` must match
  a whitelisted `siteUrl`, or the setup is rejected. (The service's
  `checkForWhitelistConformation`, line 146, is now used only by the
  dashboard for reporting, not the gate.) There is still **no re-check
  inside the sync execution path** — a project configured before the
  whitelist was enabled keeps syncing to its existing destination.
- **Visibility of pre-existing non-conforming connections (PLUGINS-310).**
  Because the gate is config-time only, the admin dashboard (§11.4) adds
  a report of already-configured destinations whose URL is *not* on the
  whitelist (`getAllNonWhitelistRemoteUrls`; exposed at
  `GET /xsync/dashboard/whitelist`), plus the ability to disable them.
  This gives admins a way to *find* and *turn off* non-conforming
  connections even though the whitelist does not retroactively block them.

### 10.4 XAPI

On `XsyncPreferencesController` (all `@AuthDelegate`-gated, §11.5):

- `GET  /xsyncProjectPreferences/whitelistEnabled` (line 203)
- `GET  /xsyncSitePreferences/whitelistSites` (line 212)
- `POST /xsyncSitePreferences/whitelistSites/add` (line 222)
- `DELETE /xsyncSitePreferences/whitelistSites/delete` (line 232)
- `GET/POST/DELETE /xsyncSitePreferences/blacklistProjects[/{projectId}]`
  (lines 252/271/290, plus a `GET .../{projectId}` membership check at
  line 261) — the project blacklist (§10.6).

`DataFormatException`/`NotFoundException` map to HTTP 400 via the
controller's `@ExceptionHandler` methods.

### 10.5 Implicit local-site allowance (PLUGINS-228)

XSync can sync between two projects **on the same XNAT**. So the whitelist
must not block local (source→same-site) syncs even when enabled. This is
handled in `WhitelistXsyncSiteServiceImpl.getAllWhitelistedSites()`: if no
whitelist row matches this XNAT's own URL, it **auto-inserts the local
site** (`XDAT.getSiteUrl()`, classified `PUBLIC`) before returning the
list. Because the setup gate checks `remote_url` against that list, a
`remote_url` equal to the local site URL always passes.

### 10.6 Project blacklist (PLUGINS-231)

A complementary control naming **local projects that may not have any
Xsync connection**, regardless of destination.

- **Storage.** The `projectBlacklist` site preference — a `List<String>`
  of project IDs (`XsyncSitePreferencesBean.java:121`, default `[]`).
- **Enforcement, two places.** (1) At config-save,
  `XsyncSetupController.setup` rejects a blacklisted project up front
  (`XsyncSetupController.java:79-81`). (2) When (re)enabling a connection,
  `XsyncConfigurationServiceImpl.enableOrDisableSingleConnection`
  (line 256) throws `IllegalArgumentException` if the project is
  blacklisted and `enabled == true` — so an admin cannot re-enable a
  blacklisted project's sync.
- **XAPI.** GET list / GET membership / POST add / DELETE remove under
  `xsyncSitePreferences/blacklistProjects` (§10.4).
- **UI.** A "Project Blacklist" site tab wired by
  `admin/xsyncProjectBlacklistManager.js`.

> **Two different "blacklists."** `projectBlacklist` (above) bars *local
> projects*. Separately, `sitesBlacklist` is a *URL-disable memory*: when
> an admin disables all connections for a destination URL via
> `changeEnabledForUrl` (`XsyncConfigurationServiceImpl.java`), that URL is
> added to `sitesBlacklist` (and removed when re-enabled). Don't conflate
> them.

### 10.7 Governance requirements not yet in the code

The *XNAT Data Import & Synchronization* proposal (WS1) asks for
governance the current code does not yet fully provide; tracked in
`GLOBUS_TRANSFER_PLAN.md` §14.3:

- **Whitelist propagation across the AIS, including via Helm charts**
  (WS1.1) — deploy-time provisioning of the whitelist + seed JSON.
- **Security classifications drive sensitive-data handling** (WS1.1a) —
  `SiteClassification` (§10.1) exists on whitelist entries but does not
  yet *constrain* configs or warn on sensitive combinations.
- **Filtered configs + sync only necessary/shared data** (WS1.5) — build
  on the existing filtering (§7.3); confirm shared-data coverage.

---

## 11. Status tracking, manifest, and history

Three parallel mechanisms record what happened.

### 11.1 Live in-memory status — `SyncStatusService` / `SyncStatusHolder`

- `components.SyncStatusHolder` (`@Component`) holds a
  `Map<projectId, ProjectSyncStatus>` (line 13) — pure in-memory,
  per-node.
- `remote.alias.services.SyncStatusService` (`@Service`) is the write API
  (`registerSyncStart/End`, `registerCurrent/Completed/FailedSubject`,
  `...Experiment`). `isCurrentlySyncing` (line 100) is the **in-flight
  lock** consulted before starting a project/experiment sync.
- Exposed to the UI by `XsyncStatusController`
  (`/xsync/syncStatus/projects/{projectId}`).

### 11.2 In-flight manifest — `SynchronizationManager` + `SyncManifest`

- `manager.SynchronizationManager` is an all-static coordinator with two
  process-global maps: `projectSyncStartTime` and `syncManifests`
  (`SynchronizationManager.java:37-39`). `BEGIN_SYNC` (line 41) seeds a
  `SyncManifest`; `UPDATE_MANIFEST` (line 52) appends items;
  `END_SYNC` (line 75) writes status back to `xsyncInfoData`, emails the
  user, writes the HTML file, and persists history.
- It owns the **cache path** conventions under
  `<cache>/SYNCHRONIZATION/<project>/<timestamp>/...`
  (`GET_SYNC_FILE_PATH`, line 153) and cleans them up on success
  (line 122).
- `manifest.SyncManifest` accumulates `ResourceSyncItem` /
  `SubjectSyncItem` (with nested experiments/scans/assessors), computes
  `wasSyncSuccessfull()` / `getOverAllSyncStatusWhenSucessfull()`
  (lines 150-190), renders the notification HTML (`syncInfoAsHTML`,
  line 250), and triggers persistence (`syncInfoToDatabase`, line 381).

### 11.3 Durable history — `HibernateSyncHistoryService`

`services.local.impl.HibernateSyncHistoryService` (`@Service`,
implements `SyncManifestService`) converts a completed `SyncManifest`
into `manifest/history/XsyncProjectHistory` + child rows
(`persistHistory`) and computes rollups. `getAll()` (inherited from
`AbstractHibernateEntityService`) is what the dashboard (§11.4) uses to
enumerate every recorded connection. Surfaced through
`XsyncHistoryController` (`/xsync/history`), which in the latest revision
returns **typed `pojo/history/` read-models** (`XsyncProjectHistoryPojo`,
etc.) and adds paginated (`/projects/{id}/recentHistory`, line 125) and
failure-stack-trace (`/{projectId}/failure`, line 176) endpoints, each
gated by an `@AuthDelegate` authorizer (§11.5).

### 11.4 Admin configuration dashboard (new)

A site-admin dashboard (PLUGINS-230/312/313/314) audits and controls all
configured remote connections across the site. It is **read-mostly** —
it reports on existing config and toggles the `sync_enabled` flag; it does
not itself move data.

- **Controller.** `xapi/XsyncConfigurationDashboardController`
  (`@XapiRestController`, `/xsync/dashboard`, `restrictTo = Admin` except
  the per-project toggle):
  - `GET /` (line 72) — every configured remote destination, each tagged
    with whitelist conformance (`getAllXsyncConfigInformation`).
  - `GET /whitelist` (line 95) — destinations **not** on the whitelist
    (only valid when the whitelist is enabled, else HTTP 400); see §10.3.
  - `GET /remoteUrl?remoteUrl=` (line 115) — the local projects connected
    to a given destination URL.
  - `PUT /enable?remoteUrl=&enabled=` (line 130) — enable/disable **all**
    configs pointing at a URL.
  - `PUT /{projectId}/enable?remoteUrl=&enabled=` (line 145,
    `restrictTo = Delete`) — enable/disable a **single** project's config.
- **Service.** `services.local.XsyncConfigurationService` /
  `impl.XsyncConfigurationServiceImpl` is the new backend. Besides the
  dashboard read-models it centralizes logic previously scattered across
  the setup controller, `XsyncUtils`, and `QueryResultUtil`:
  `checkForWhitelistConformation` (line 146, used to *tag* destinations for
  the dashboard — the actual gate is inlined in the setup controller,
  §10.3), `getAll{Daily,Weekly,Monthly,OnDemand}...` project queries,
  `getSyncConfiguration`/`saveConfig` (typed `SyncConfigurationPojo`), and
  the enable/disable methods `changeEnabledForUrl` /
  `changeConnectionEnabled` / `changeConnectionEnabledForProject`
  (line 215) — all routed through `enableOrDisableSingleConnection`, which
  flips `XsyncXsyncprojectdata.setSyncEnabled` (line 259) and **refuses to
  re-enable a blacklisted project** (line 256; §10.6).
- **Read-models.** `pojo/XsyncRemoteUrlDetailsPojo`,
  `pojo/XsyncDashboardProjectConfigurationPojo`,
  `pojo/configuration/SyncConfigurationPojo` (+ assessor/resource pojos),
  `pojo/history/*`, and `pojo/XsyncRemoteCredentialsPojo`.
- **UI.** A new "Configuration Dashboard" site tab (now the default active
  tab, `site-settings.yaml`) rendered by
  `admin/xsyncConfigurationDashboard.js` — includes history pagination
  (PLUGINS-312), failure **stack-trace visualization** (PLUGINS-313), and
  per-connection **disable** (PLUGINS-314).

The dashboard is the practical answer to the "whitelist is a config-time
gate, not a runtime one" limitation (§10.3, §16): it lets an admin *find*
non-conforming or unwanted connections after the fact and switch them off.

### 11.5 Authorization and the Xsync Administrator role (new)

PLUGINS-232 introduces a dedicated **Xsync Administrator** role plus an
XAPI **authorizer** layer, replacing the older pattern of inline
`canEditProject`/`canReadProject` checks inside each controller method.

- **The role.** `XsyncAdministrator`, defined in
  `config/roles/xsync-role-definition.properties` and referenced via the
  constant `XsyncUtils.XSYNC_ADMINISTRATOR_ROLE` (line 83). A holder "can
  manage all Xsync connections throughout the site."
- **Authorizers** (`org.nrg.xsync.security`, all `@Component` extending
  `AbstractXapiAuthorization`):
  - `XsyncAdministratorUserAuthorization` — pure role check
    (`Roles.checkRole(user, XSYNC_ADMINISTRATOR_ROLE)`); used for
    site-level admin endpoints (site prefs, whitelist, blacklist).
  - `XsyncRead/Edit/DeleteProjectUserAuthority` — pass if the user has the
    matching **project** permission **or** the Xsync Administrator role.
    They read the **project id from the first method argument**
    (`joinPoint.getArgs()[0]`), so annotated methods must take `projectId`
    first.
  - `XsyncEdit/DeleteExperimentUserAuthorizer`,
    `XsyncDeleteSubjectUserAuthorizer` — the same "permission OR role"
    pattern for experiment/subject-scoped operations.
- **Wiring.** Controller methods declare
  `@AuthDelegate(SomeAuthorizer.class)` together with
  `@XapiRequestMapping(... restrictTo = AccessLevel.Authorizer)`. XNAT's
  XAPI aspect runs the delegate's `checkImpl` before the method. Examples:
  `XsyncOperationsController` uses `XsyncDeleteProjectUserAuthority` for
  project sync, `XsyncEditExperimentUserAuthorizer` for the QC/sync-now
  experiment endpoints, and `XsyncDeleteSubjectUserAuthorizer` for subject
  sync; `XsyncSetupController.setup` uses
  `XsyncDeleteProjectUserAuthority`; `XsyncPreferencesController` uses
  `XsyncAdministratorUserAuthorization` for site-level prefs and
  `XsyncReadProjectUserAuthority` for project-scoped reads.
- **Upgrade grant.**
  `initialization.tasks.AssignXsyncAdministratorRoleToSiteAdminOnUpgrade`
  (an `AbstractInitializingTask`) runs once after init and, **if no user
  yet holds the role**, grants `XsyncAdministrator` to every enabled site
  administrator (`ROLE_ADMINISTRATOR`) — so existing admins keep working
  after the upgrade. Both `org.nrg.xsync.security` and
  `org.nrg.xsync.initialization.tasks` were added to the plugin's
  `@ComponentScan` (§2.2).

Practical consequence for new code: to protect a new XSync endpoint, add
the appropriate `@AuthDelegate` authorizer and `restrictTo = Authorizer`
rather than calling permission helpers by hand — and put `projectId`
first if you use a project-scoped authorizer.

---

## 12. XAPI surface

All controllers extend `AbstractXapiProjectRestController` (project
permission checks) or `AbstractXapiRestController`, under
`/xapi/xsync*`.

| Controller | Base path | Notable endpoints |
|---|---|---|
| `XsyncOperationsController` | `/xsync` | `POST /projects/{id}` (start project sync, `exportProject` line 146); `POST /syncexperiment/{id}` (line 238) and `POST /syncsubject/{id}` (line 295) on-demand; `POST /experiments/{id}?okToSync=` (QC gate, `exportExperiment` line 329); `POST /requestSync/{id}` (mark for next cycle, `markForSync` line 467); `GET /progress/{id}` (stream log, `getSyncProgress` line 549); `GET /getSubjectMappingFile/{id}` (mapping report, line 437). |
| `XsyncSetupController` | `/xsync/setup` | GET/POST project config as typed `SyncConfigurationPojo` (POST inlines the blacklist + whitelist gates, §10); GET/PUT pre-sync anonymization script (lines 126/149). |
| `XsyncRemoteCredentialsController` | `/xsync/credentials` | `POST /save/projects/{id}` (store alias token, `saveRemoteCredentials` line 80 — now `projectId` path var + typed `XsyncRemoteCredentialsPojo`); `POST /check/projects/{id}` (validate, line 211). |
| `XsyncPreferencesController` | `/xapi` | site prefs; site/project Aspera prefs; `httpsEnabled`/`asperaEnabled` getters (lines 122/131/141); whitelist CRUD + `whitelistEnabled` and **project blacklist** CRUD (§10.4). |
| `XsyncConfigurationDashboardController` | `/xsync/dashboard` | admin dashboard: list destinations, non-whitelisted destinations, projects-per-URL, and enable/disable connections (§11.4). |
| `XsyncStatusController` | `/xsync/syncStatus` | live `ProjectSyncStatus` JSON. |
| `XsyncEntityStateController` | `/xsync/information` | per-project entity sync state. |
| `XsyncHistoryController` | `/xsync/history` | durable history (typed `pojo/history/` models); adds paginated `recentHistory` and a `failure` stack-trace endpoint. |
| `XsyncControllerAdvice` | — | `@ControllerAdvice` exception mapping. |

Most methods are now guarded by an `@AuthDelegate(...)` authorizer with
`restrictTo = AccessLevel.Authorizer` rather than inline permission checks
(§11.5).

On-demand endpoints construct the relevant task (`ProjectChangeDiscoverer`
/ `SingleExperimentTransfer` / `SingleSubjectTransfer`) and submit it to
the shared `xsyncThreadPoolExecutorFactoryBean` executor, after checking
`isCurrentlySyncing` (e.g. `exportProject`, line 146).

---

## 13. Extension points

Two SPI-style extension mechanisms, both bean-based and discovered from
the Spring context. (Unchanged on `develop`.)

### 13.1 Datatype transformers

For datatypes that embed subject/session IDs in custom fields (canonical
example `icr:roiCollectionData`, `README.md:267`), register a
`@Component` implementing `transformer.SyncTransformerI` annotated with
`@DatatypeTransformerAnnotation(xsiType=...)`. At transfer time,
`ExperimentFilter.transformOtherItemFieldsBeforeSync` (line 1118) looks
up the `XsyncDataTypeSpecificTransformer` bean, populates a
`TransformerHelper` attribute map, and calls `transform(...)`; dispatch
by xsiType is via `SyncTransformerManagerI`. Reference implementation:
`transformer.datatype.ICRRoiCollectionPreSyncTransformer`.

### 13.2 Label generators

When `identifiers == use_custom`, `IdMapper.assignRemoteLabel`
(`IdMapper.java:56`) resolves a `generator.XsyncLabelGeneratorI` bean by
name (`custom_identifier_class`) and calls `generateId`. Shipped:
`HashIdLabelGenerator` (MD5 of the local id, `HashIdLabelGenerator.java:44`)
and `DateTimeLabelGenerator`. Available generator names are enumerated
for the UI via `XsyncOperationsController.getCustomIdentifers`.

---

## 14. Optional Aspera transport

XSync can offload the byte transfer of XARs to IBM Aspera (`ascp`)
instead of HTTPS, per project.

- `aspera.AsperaClient` (`@Component`) shells out to `/usr/local/bin/ascp`
  with project-scoped SSH key/port/destination settings and streams
  process output into an `AsperaStatus` (`AsperaClient.java:28`).
- `XsyncExperimentTransfer.shouldUseAspera` (line 512) gates on
  `AsperaProjectPrefs.getAsperaEnabled`; if enabled and configured,
  `asperaXarSend` (line 529) uploads the XAR and triggers a **server-side
  XAR import by path**, falling back to HTTPS on failure. This
  stage-then-import-by-path pattern is the template the planned Globus
  transport follows (see `GLOBUS_TRANSFER_PLAN.md`).
- Site-level and project-level enablement is now also visible through the
  new `asperaEnabled` site preference and the "Connection Management"
  admin tab (§4.3, §15); project Aspera settings still come from the
  Spawner form `project-settings.yaml` →
  `/xapi/xsyncProjectPreferences/project/{id}/aspera`.

This path assumes a hard-coded `ascp` location and an out-of-band Aspera
receiver — environment-specific and off by default.

> **Roadmap note.** The *XNAT Data Import & Synchronization* proposal
> (WS1.4) calls for **hiding Aspera
> references throughout the XSync UI** as Globus becomes the preferred
> alternate backend. The Globus work is planned in `GLOBUS_TRANSFER_PLAN.md`
> (client model) and `GLOBUS_CONNECTOR_PLAN.md` (connector), compared in
> `GLOBUS_APPROACH_COMPARISON.md`.

---

## 15. User interface

The plugin ships JSP/Velocity/JS assets under
`src/main/resources/META-INF/resources`.

**Project-facing:**

- `templates/screens/xnat_projectData/report/manage_tab/XsyncConfiguration.vm`
  + `scripts/.../xsyncPlugin/xsync-config.js` — the Manage-tab config
  form (heavily refactored on `develop`). New:
  `xsync-setupProjectPreference.js` for project preference wiring.
- `xnat_experimentData/report/tabs/Synchronization.vm` and
  `xnat_subjectData/report/tabs/Synchronization.vm` — the "OK to Sync" /
  "Sync This Session Now" controls.
- `xnat_projectData/actionsBox/Syncnow.vm` — the "Sync Data" action.

**Site-admin (expanded on `develop`, see `site-settings.yaml`):**

- **Configuration Dashboard** tab (now the default active tab) — the
  audit/enable/disable UI for all remote connections (§11.4), rendered by
  `scripts/.../xsyncPlugin/admin/xsyncConfigurationDashboard.js` into
  `div#xsync-configuration-dashboard-panel`.
- **Connection Management** tab with `httpsEnabled` / `asperaEnabled`
  switchboxes, wired by
  `scripts/.../xsyncPlugin/admin/xsyncConnectionManager.js`.
- **Whitelist Of Accepted Receiving XNATs** tab with an enable switch and
  a managed table, wired by
  `scripts/.../xsyncPlugin/admin/xsyncWhitelistManager.js` and styled by
  `style/.../xsyncPlugin/xsync-whitelist.css` (§10).
- **Project Blacklist** tab — projects barred from any Xsync connection —
  rendered by `scripts/.../xsyncPlugin/admin/xsyncProjectBlacklistManager.js`
  into `div#xsync-project-blacklist-panel` (§10.6).
- The existing Remote-Token, retry, max-zip-size, and Aspera-defaults
  panels remain.

These are thin clients over the XAPI (§12); there is no server-side
rendering of sync logic.

---

## 16. Cross-cutting concerns and pitfalls for new developers

- **Primary-node only.** Because so much coordination state is static and
  in-memory (`SynchronizationManager`, `SyncStatusHolder`, JSESSIONID
  cache, connection lock), scheduled syncs run only on the primary node
  (`AbstractSyncService.doSync`, line 89). The whitelist table, however,
  is DB-backed and cluster-consistent.
- **Two data-access layers.** XFT/OM for writes and item building; raw
  SQL (`QueryResultUtil`) for change discovery. They can disagree if
  XNAT's physical schema changes. Search **both** when touching data flow.
- **Watermark semantics.** `sync_start_time` vs. `sync_end_time` is used
  inconsistently by design (TODO, `QueryResultUtil.java:61`). Failed syncs
  deliberately do **not** advance `sync_end_time`
  (`SynchronizationManager.END_SYNC`, lines 87-93) so the next run retries
  the same window.
- **Whitelist is a config-time gate, not a runtime one.** It blocks new
  project configs pointing at non-approved destinations (via
  `XsyncConfigurationService.checkForWhitelistConformation`,
  `XsyncConfigurationServiceImpl.java:146`, called from
  `XsyncSetupController.java:81-82`) but does not re-validate existing
  configs at sync time (§10.3). Enabling the whitelist does not
  retroactively stop in-flight destinations — instead the admin dashboard
  (§11.4) surfaces non-conforming connections
  (`GET /xsync/dashboard/whitelist`) so an admin can disable them.
- **Status vocabulary.** The `XsyncUtils.SYNC_STATUS_*` constants
  (`SYNCED_AND_NOT_VERIFIED`, `SYNCED_AND_VERIFIED`, `SKIPPED`,
  `SKIPPED_BY_FILTER`, `WAITING_TO_SYNC`, `SYNC_REQUESTED`, `FAILED`,
  `DELETED`, `IN_PROGRESS`, `INVALID_FILTER`) are shared between the
  pipeline, the retry queries, `SyncManifest.wasSyncSuccessfull` (line
  150), and the QC UI. Adding a status means touching all four.
- **The `_S\d+$` guard.** Appears in `IdMapper.java:173`,
  `XsyncExperimentTransfer.java:345`, and `ExperimentFilter.java:436` as a
  workaround for sessions being assigned a subject accession number,
  explicitly marked "TEMPORARY???" — root cause never found.
- **Verification is best-effort.** It compares source file counts/sizes
  against destination `?format=JSON` listings (`XsyncProjectVerifier`). A
  connection failure downgrades to `SYNCED_AND_NOT_VERIFIED` rather than
  failing the sync.
- **Mixed-era idioms.** The build is Java 21 and the newest code is clean
  (switch expressions, Lombok POJOs), but the transport layer keeps
  hand-rolled retry loops with stale "upgrade Spring" TODOs
  (`RemoteRESTServiceImpl:364`) and the anonymizer still has
  `System.out.println` debugging (`XsyncAnonymizer.java:253`, 336, 345).
  Match the surrounding style when patching; don't treat the legacy code
  as exemplary.
- **Authorizer arg-order footgun.** The project/experiment/subject
  authorizers (§11.5) read the id from **`joinPoint.getArgs()[0]`** — the
  *first* method argument. A method annotated with a project-scoped
  `@AuthDelegate` whose first parameter is not the `projectId` will
  authorize against the wrong value (or throw). Keep the id first.
- **The local site is implicitly whitelisted.** `getAllWhitelistedSites`
  auto-adds this XNAT's own URL (§10.5), so a whitelist that "contains only
  remote sites" still silently permits local→local syncs. And there are
  **two distinct "blacklists"** — `projectBlacklist` (local projects) vs.
  `sitesBlacklist` (a disabled-URL memory); see §10.6.

---

## 17. Quick reference — where to start reading

| To understand... | Start at |
|---|---|
| Plugin wiring / beans | `configuration/XsyncPlugin.java` |
| Build / platform (Java 21, XNAT 1.10.1) | `build.gradle` |
| What gets synced (config) | `configuration/json/SyncConfiguration.java`, `configuration/ProjectSyncConfiguration.java` |
| When syncs run | `scheduler/XsyncScheduler.java`, `services/local/AbstractSyncService.java` |
| How change is detected | `utils/QueryResultUtil.java` |
| The main sync loop | `discoverer/ProjectChangeDiscoverer.java` |
| Building/pushing a session | `local/XsyncExperimentTransfer.java` (`storeXar`) |
| Filtering a session | `local/ExperimentFilter.java` |
| ID/label remapping | `local/IdMapper.java` |
| Anonymization | `anonymize/XsyncAnonymizer.java`, `anonymize/ExportAnonymizer.java` |
| HTTP to the destination | `services/remote/RemoteRESTServiceImpl.java`, `connection/RemoteConnectionManager.java` |
| Credentials / tokens | `xapi/XsyncRemoteCredentialsController.java`, `services/local/impl/DefaultXsyncAliasRefresherForAProject.java` |
| Destination governance (whitelist / blacklist) | `manifest/whitelist/*`, `services/local/impl/WhitelistXsyncSiteServiceImpl.java`; gates inlined in `xapi/XsyncSetupController.java` (`setup`); blacklist pref in `components/XsyncSitePreferencesBean.java` |
| Authorization / Xsync Admin role | `security/*`, `initialization/tasks/AssignXsyncAdministratorRoleToSiteAdminOnUpgrade.java`, `config/roles/xsync-role-definition.properties` |
| Admin config dashboard | `xapi/XsyncConfigurationDashboardController.java`, `services/local/impl/XsyncConfigurationServiceImpl.java`, `admin/xsyncConfigurationDashboard.js` |
| Results / notifications / history | `manager/SynchronizationManager.java`, `manifest/SyncManifest.java`, `manifest/history/*`, `services/local/impl/HibernateSyncHistoryService.java` |
| REST API | `xapi/*Controller.java` |
| Extension SPIs | `transformer/`, `generator/` |
