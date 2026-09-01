# XSync end-to-end tests

Playwright coverage for XSync's site and project administration: connection
management, the destination whitelist, the project blacklist, the
configuration dashboard, the `XsyncAdministrator` role, and transferring data
between two XNAT instances.

## Requirements

- Node 18+
- An XNAT instance running xsyncPlugin 1.8.2 or later. Global setup checks
  this and fails the run if the endpoints under test are missing.
- A site admin account and a non-site-admin account on that instance.
- Optionally, a second XNAT instance to receive a real transfer (see
  [Cross-instance transfer](#cross-instance-transfer)).

## Setup

```bash
cd tests/e2e
npm ci
npx playwright install chromium
cp .env.example .env
```

Fill in `.env`:

| Variable | Meaning |
|---|---|
| `XNAT_URL` | The instance under test. |
| `ADMIN_USER` / `ADMIN_PASS` | A site administrator account. |
| `NON_ADMIN_USER` / `NON_ADMIN_PASS` | An existing, enabled, non-site-admin account. The role spec grants and revokes `XsyncAdministrator` on it and restores whatever it started with. |
| `REMOTE_XNAT_URL` | Optional. A second, receiving XNAT instance. Unset skips the cross-instance spec; everything else still runs. |
| `REMOTE_ADMIN_USER` / `REMOTE_ADMIN_PASS` | Required together with `REMOTE_XNAT_URL`. An account on the receiver that can create a project. |

## Running

```bash
npm test                 # full suite
npm run test:admin       # admin-only specs
npm run test:nonadmin    # the XsyncAdministrator role spec
npx playwright test admin.whitelist.spec.ts   # a single spec
npx playwright show-report                    # the last HTML report
```

Specs run sequentially on a single worker (`fullyParallel: false, workers: 1`
in `playwright.config.ts`). Every spec reads and writes site-wide XSync
preferences (the whitelist toggle, connection types, the project blacklist),
so running specs concurrently would have them overwrite each other's setup.

## What is covered

| Spec | Covers |
|---|---|
| `admin.connectionManagement.spec.ts` | HTTPS/Aspera toggles; HTTPS stays enabled while it is the only transport; Aspera UI (tab and in-dialog notice) appears only when Aspera is enabled |
| `admin.whitelist.spec.ts` | The destination whitelist: enabling/disabling the table, add/edit/delete a site, required-field validation, classification values, trailing-slash normalisation, and that the local XNAT is always a permitted destination |
| `admin.whitelistEnforcement.spec.ts` | Project sync setup is refused for a destination not on the whitelist and accepted for one that is; anything is accepted with the whitelist off; non-conforming existing connections are reported when the whitelist is enabled; the project config dialog offers a dropdown of whitelisted destinations instead of free text |
| `admin.projectBlacklist.spec.ts` | Adding/removing a project from the blacklist; a blacklisted project cannot create a sync configuration, has its existing configuration disabled, and shows no XSync panel on its Manage page; duplicate entries are refused |
| `admin.configurationDashboard.spec.ts` | The site-wide dashboard: rows and counts, the Remote Site/Security Tier columns (whitelist-dependent), the per-project breakdown, disabling a whole remote url or a single project connection, and recent/full sync history |
| `nonadmin.xsyncAdministratorRole.spec.ts` | The `XsyncAdministrator` role gates every site-wide XSync endpoint independent of site-admin status |
| `admin.sitePreferences.spec.ts` | Interval, retry and max-file-size preferences persist through a save and reject malformed values |
| `admin.projectSetup.spec.ts` | A stored project sync configuration round-trips correctly; malformed or missing project references are refused |
| `admin.crossXnatTransfer.spec.ts` | An actual transfer between two XNAT instances: a subject is synced to a second instance, its arrival is verified there, and the source instance's history and dashboard reflect the outcome |

### What is not covered

- The failure stack-trace popup on a row with a failed sync (needs a sync
  that fails mid-transfer against a real receiver).
- Pagination of sync history beyond confirming the dialog opens.
- The pre-existing Aspera Server Defaults and Remote Token preference forms.

## Cross-instance transfer

`admin.crossXnatTransfer.spec.ts` needs a second, reachable XNAT instance.
Nothing about the setup assumes a particular hostname, network, or CI system —
the same test runs against two stacks in any environment where both are
reachable from the machine running Playwright.

The spec creates its own uniquely named project on the receiver and deletes
it afterward; existing data on the receiver is never read or modified. The
sender authenticates to the receiver with an alias token issued for the
configured account, not a raw password, matching how XSync authenticates in
production.

If `REMOTE_XNAT_URL` is unset, this spec is skipped and every other spec still
runs. If it is set, `REMOTE_ADMIN_USER` and `REMOTE_ADMIN_PASS` are required;
a partially configured remote fails the run rather than skipping silently.

## Structure

```
tests/e2e/
  playwright.config.ts     projects, timeouts, worker/parallelism settings
  .env.example
  lib/
    auth.ts                login and CSRF token handling
    api.ts                 REST client for XSync and core XNAT endpoints
    pages.ts               selectors and navigation helpers for the admin UI
    remote.ts              REST client for the receiving instance
    run.ts                 per-run unique ids, so re-running the suite
                            never collides with a previous run's projects
  tests/
    global-setup.ts         authenticates both accounts; verifies the
                            target instance exposes the endpoints under test
    admin.*.spec.ts         site-admin-authenticated specs
    nonadmin.*.spec.ts      non-admin-authenticated specs
```

Every spec cleans up the projects and preference changes it makes, using
`beforeAll`/`afterAll` hooks so a failure partway through a spec still leaves
the instance in its original state.

## CI

`scripts/run-plugin-tests.sh` in the `xnat-test-automation` repository clones
this repo and runs `tests/e2e` after the core nightly regression suite, driven
by an entry in that repository's `plugin-test-manifest.json`.
