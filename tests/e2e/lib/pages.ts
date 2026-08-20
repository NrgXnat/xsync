/**
 * Navigation and element helpers for the XSync administration UI.
 *
 * Selectors here are taken from the plugin's own source rather than guessed:
 * the tab labels come from
 *   META-INF/xnat/spawner/xsync-plugin/site-settings.yaml
 * and the element ids from the admin scripts under
 *   META-INF/resources/scripts/xnat-plugins/xsyncPlugin/admin/
 */
import { Page, Locator, expect } from '@playwright/test';

/** Tab labels defined in site-settings.yaml, in the order they are declared. */
export const XSYNC_TABS = {
    connectionManagement: 'Connection Management',
    configurationDashboard: 'Configuration Dashboard',
    remoteToken: 'Remote Token',
    syncRetry: 'Sync Retry',
    maxUncompressedFileSize: 'Maximum Total Uncompressed File Size',
    whitelist: 'Whitelist Of Accepted Receiving XNATs',
    projectBlacklist: 'Project Blacklist',
    asperaDefaults: 'Aspera Server Defaults',
} as const;

export const PLUGIN_SETTINGS_URL = '/app/template/Page.vm?view=admin/plugins';

/** Load the site-wide Plugin Settings page and wait for the XSync tabs to render. */
export async function gotoPluginSettings(page: Page): Promise<void> {
    await page.goto(PLUGIN_SETTINGS_URL);
    await page.waitForLoadState('domcontentloaded');
    // The XSync tab group is spawned by the admin scripts after their init
    // XHRs resolve, so wait on the group rather than on a load event.
    await xsyncTabLink(page, XSYNC_TABS.connectionManagement).waitFor({ state: 'attached', timeout: 30_000 });
}

/** The anchor for one XSync tab. Tab anchors carry the label as their title. */
export function xsyncTabLink(page: Page, label: string): Locator {
    return page.locator(`a[title="${label}"]`);
}

/** Click an XSync tab and wait for its panel to become visible. */
export async function openXsyncTab(page: Page, label: string, panelSelector?: string): Promise<void> {
    const link = xsyncTabLink(page, label);
    await link.waitFor({ state: 'visible', timeout: 30_000 });
    await link.click();
    if (panelSelector) {
        await page.locator(panelSelector).waitFor({ state: 'visible', timeout: 30_000 });
    }
}

// ------------------------------------------------------------------ panels

export const WHITELIST_PANEL = 'div#xsync-whitelist-table';
export const BLACKLIST_PANEL = 'div#xsync-project-blacklist-panel';
export const DASHBOARD_PANEL = 'div#xsync-configuration-dashboard-panel';

// Wait targets for tabs whose panel content is conditional. The whitelist
// table div is emptied while whitelisting is off, and an empty div has no
// bounding box, so Playwright treats it as hidden; the checkbox inputs are
// display:none always. The switchbox labels are rendered unconditionally, so
// they are what openXsyncTab should wait for on these two tabs.
export const CONNECTION_TAB_READY = 'label.switchbox:has(#https-enabled)';
export const WHITELIST_TAB_READY = 'label.switchbox:has(#limit-to-whitelist)';

export function whitelistTable(page: Page): Locator {
    return page.locator('table.whitelist-site-table');
}

export function blacklistTable(page: Page): Locator {
    return page.locator('table.blacklist-table');
}

/** The dashboard table inside the Configuration Dashboard tab, not a modal copy. */
export function dashboardTable(page: Page): Locator {
    return page.locator(`${DASHBOARD_PANEL} table.xsync-configuration-table`);
}

/** Row of any XNAT data table whose text contains the given value. */
export function rowContaining(table: Locator, value: string): Locator {
    return table.locator('tbody tr').filter({ hasText: value });
}

/** Column header labels of an XNAT data table, in display order. */
export async function columnLabels(table: Locator): Promise<string[]> {
    const headers = await table.locator('thead th').allTextContents();
    return headers.map(h => h.trim()).filter(h => h.length > 0);
}

// ------------------------------------------------------------------ dialogs

/** The XNAT.ui.dialog currently open. Used by the whitelist and blacklist forms. */
export function openDialog(page: Page): Locator {
    return page.locator('.xnat-dialog.open').last();
}

/** The xmodal currently open. Used by the delete and disable confirmations. */
export function openXmodal(page: Page): Locator {
    return page.locator('.xmodal.open').last();
}

/** Click a dialog footer button by its label. */
export async function clickDialogButton(dialog: Locator, label: string): Promise<void> {
    await dialog.locator('button', { hasText: new RegExp(`^\\s*${label}\\s*$`) }).first().click();
}

// ------------------------------------------------------------------ switchboxes

/**
 * XNAT switchboxes render as a checkbox whose id is the kebab-case form of the
 * preference name, wrapped in a label that draws the visible toggle. Core CSS
 * hides the checkbox itself (label.switchbox > input { display: none; } in
 * app.css), so state is read from the input but clicks must land on the
 * sibling span.switchbox-outer. Toggling fires a change handler that POSTs
 * the new value immediately; callers wait on the resulting state, not on a
 * save button.
 */
export function switchbox(page: Page, id: string): Locator {
    return page.locator(`#${id}`);
}

/** The visible toggle for a switchbox. This is the element a user clicks. */
export function switchboxToggle(page: Page, id: string): Locator {
    return page.locator(`label.switchbox:has(#${id}) span.switchbox-outer`);
}

export async function setSwitchbox(page: Page, id: string, on: boolean): Promise<void> {
    const box = switchbox(page, id);
    await box.waitFor({ state: 'attached', timeout: 15_000 });
    if ((await box.isChecked()) !== on) {
        await switchboxToggle(page, id).click();
        await expect(box, `switchbox #${id} did not reach checked=${on}`).toBeChecked({ checked: on });
    }
}

// ------------------------------------------------------------------ project pages

/**
 * The Manage tab of a project report page, where the XSync Configuration
 * panel renders. The tab is a client-side YUI tab; the selector matches the
 * one the xnat-test-automation page objects use for the same element.
 */
export async function gotoProjectXsyncTab(page: Page, projectId: string): Promise<void> {
    await page.goto(`/data/projects/${encodeURIComponent(projectId)}?format=html`);
    await page.waitForLoadState('domcontentloaded');
    const manageTab = page.locator('#projectSummary ul.yui-nav li a em', { hasText: 'Manage' }).first();
    await manageTab.waitFor({ state: 'visible', timeout: 30_000 });
    await manageTab.click();
}

/**
 * Expand the XSync Configuration section of the Manage tab. The Manage tab is
 * an accordion of collapsed sections; the section header is visible from the
 * start, but the configuration form inside only renders visibly after the
 * section is expanded.
 */
export async function expandProjectXsyncSection(page: Page): Promise<void> {
    const header = page.locator('#xsync_panel_header');
    await header.waitFor({ state: 'visible', timeout: 30_000 });
    await header.click();
    await page.locator('#xsync-config').waitFor({ state: 'visible', timeout: 30_000 });
}
