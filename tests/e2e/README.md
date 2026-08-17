# XSync end-to-end tests

Playwright coverage for the XSync administration features added in
1.8.2-SNAPSHOT. The goal is basic regression protection for each feature, not
exhaustive happy-path, negative and edge-case coverage.

## Status

**These tests have never been run.** No available XNAT instance carries
1.8.2-SNAPSHOT; the development stacks are on 1.8.1, where every endpoint under
test returns 404. The specs were written against the plugin source, so
selectors and payloads come from the code rather than from observed behaviour.
Expect to fix things on first execution.

## What is covered

| Ticket | Spec | Covers |
|---|---|---|
| PLUGINS-234, PLUGINS-235 | `admin.connectionManagement.spec.ts` | HTTPS stays locked on while Aspera is off; enabling Aspera unlocks it and reveals the Aspera Server Defaults tab; the project panel shows no Aspera notice while Aspera is off |
| PLUGINS-228, PLUGINS-229, PLUGINS-332 | `admin.whitelist.spec.ts` | Whitelist table appears with the toggle; add, edit and delete a site; required-field validation; classification is constrained; trailing slashes are stripped; this XNAT is always a permitted destination |
| PLUGINS-228, PLUGINS-310 | `admin.whitelistEnforcement.spec.ts` | A destination off the whitelist is refused and one on it is accepted; with the whitelist off anything is accepted; existing non-conforming connections are reported when the whitelist is switched on |
| PLUGINS-231 | `admin.projectBlacklist.spec.ts` | Add and remove through the admin tab; a blacklisted project cannot create a connection, has its existing connection deactivated, and shows no XSync panel; duplicates are refused |
| PLUGINS-230, PLUGINS-312, PLUGINS-314 | `admin.configurationDashboard.spec.ts` | Dashboard rows and counts; site name and security tier appear only with the whitelist on; per-project breakdown; disable a whole remote url or a single connection; recent and full history |
| PLUGINS-232 | `nonadmin.xsyncAdministratorRole.spec.ts` | Site admins hold the role after upgrade; a plain user is refused; granting the role opens the settings; revoking closes them |

## What is not covered, and why

- **PLUGINS-313**, the stack trace popup on a failed row, needs a connection
  that has genuinely failed a sync, which means a reachable second XNAT and a
  real transfer. Faking a failure would test the fake, not the feature.
- **PLUGINS-312 pagination** is exercised only to the extent that the full
  history dialog opens. Proving pagination needs a connection with more history
  entries than one page holds.
- **Actual data transfer.** Nothing here syncs a session. The suite covers
  administration and governance, which is where the recent work landed.
- The **Aspera Server Defaults** and **Remote Token** forms, which predate this
  work, are untouched.

## Two questions for review

1. `trimUrl()` in `xsyncWhitelistManager.js` strips the trailing slash when an
   admin saves through the dialog, but `WhitelistXsyncSiteServiceImpl` does no
   normalisation, so `POST /xapi/xsyncSitePreferences/whitelistSites/add`
   stores whatever it receives. The bootstrap file
   `META-INF/xnat/xsyncSiteWhitelist.json` itself contains a url with a
   trailing slash and loads through that same un-normalised path. Since
   `XsyncSetupController` matches the configured remote url against whitelist
   entries with an exact string comparison, an entry that keeps its slash can
   never be matched. The REST-level test in `admin.whitelist.spec.ts` asserts
   that the stored url is trimmed; if normalisation is meant to stay in the UI
   only, that test should be dropped.
2. Enabling the whitelist through the UI opens the non-conforming connections
   report whenever the `GET /xapi/xsync/dashboard/whitelist` call succeeds,
   including when it returns an empty list. The tests tolerate the empty-list
   dialog rather than asserting it, in case suppressing it was intended.

## Running

```bash
cd tests/e2e
npm ci
npx playwright install chromium
cp .env.example .env   # then fill in XNAT_URL and credentials
npm test
```

`NON_ADMIN_USER` must be an existing, enabled, non-site-admin account. The
suite grants and revokes `XsyncAdministrator` on it and restores whatever it
started with.

Specs run sequentially on a single worker. Every one of them writes site-wide
XSync preferences, so parallel execution would have tests overwriting each
other's setup and produce results that reflect neither. Each spec restores the
preferences it changed and deletes the projects it created.

Global setup aborts the run if the target instance predates 1.8.2. It fails
rather than skips on purpose: a suite that skipped would report green while
covering nothing.

## CI

`scripts/run-plugin-tests.sh` in the `xnat-test-automation` repository clones
this repo and runs `tests/e2e` after the core nightly suite, driven by an entry
in `plugin-test-manifest.json`. The entry mirrors the one for the Dynamic
Dashboard plugin.
