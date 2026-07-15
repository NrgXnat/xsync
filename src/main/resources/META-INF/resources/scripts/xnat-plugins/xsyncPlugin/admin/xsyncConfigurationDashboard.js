/*
 * web: xsyncWhitelistManager.js
 * XNAT http://www.xnat.org
 * Copyright (c) 2005-2017, Washington University School of Medicine and Howard Hughes Medical Institute
 * All Rights Reserved
 *
 * Released under the Simplified BSD.
 */

/*!
 * Provide administrators with an outline of xsync configurations
 */

console.log('xsyncConfigurationDashboard.js');

var XNAT = getObject(XNAT || {});
XNAT.plugin = getObject(XNAT.plugin || {});
XNAT.plugin.xsync = getObject(XNAT.plugin.xsync || {});

(function(factory){
    if (typeof define === 'function' && define.amd) {
        define(factory);
    }
    else if (typeof exports === 'object') {
        module.exports = factory();
    }
    else {
        return factory();
    }
}(function() {

    var restUrl = XNAT.url.restUrl;
    var xsyncConfigurationDashboard;
    XNAT.plugin.xsync.xsyncConfigurationDashboard = xsyncConfigurationDashboard = getObject(XNAT.plugin.xsync.xsyncConfigurationDashboard || {});

    xsyncConfigurationDashboard.currentRemoteUrl = '';
    xsyncConfigurationDashboard.getLocalProjectLink = function(localProject) {
        let localProjectUrl = XNAT.url.fullUrl().replace(/\/$/,'') + XNAT.url.dataUrl() + '/projects/' + localProject;
        return spawn('!', [ spawn('a.link|href='+ localProjectUrl, [['b', localProject]]),]);
    }

    xsyncConfigurationDashboard.getFailedSyncStackTrace = function(text, projectId) {
        return spawn('!', [
            spawn('a.link|href=#!', {
                onclick: function(e){
                    e.preventDefault();
                    XNAT.dialog.close();
                    xsyncConfigurationDashboard.getFailedStackTraceModal(projectId);
                }
            }, [['b', text]]),
        ]);
    }

    xsyncConfigurationDashboard.getNumberProjectsLink = function(text, remoteUrl) {
        return spawn('!', [
            spawn('a.link|href=#!', {
                onclick: function(e){
                    e.preventDefault();
                    xsyncConfigurationDashboard.getRemoteUrlListingModal(remoteUrl, false);
                }
            }, [['b', text]]),
        ]);
    }

    xsyncConfigurationDashboard.getShowHistoryButton = function(projectId) {
            return spawn('button.btn.btn-sm.edit', {
                onclick: function (e) {
                    e.preventDefault();
                    XNAT.dialog.close();
                    xsyncConfigurationDashboard.createFullHistoryModal(projectId);
                },
                title: "Show the complete history of this connection."
            }, 'History');
        }

    xsyncConfigurationDashboard.getDisableButton = function(remoteUrl) {
        let remoteUrlDisabled = xsyncConfigurationDashboard.blacklistSites.includes(remoteUrl);
        let buttonTitle = remoteUrlDisabled ? 'Enable all site connections.' : 'Disable all site connections.';
        let buttonWording = remoteUrlDisabled ? 'Enable' : 'Disable';
        return spawn('button.btn.btn-sm.edit', {
            onclick: function (e) {
                e.preventDefault();
                xsyncConfigurationDashboard.disableOrEnableSiteModal(remoteUrl, remoteUrlDisabled);
            },
            title: buttonTitle
        }, buttonWording);
    }

    xsyncConfigurationDashboard.spawnStatusColumn = function(status, localProject){
        return(XNAT.ui.panel.input.switchbox({
            title: "Connection enabled",
            checked: status === "true",
            onclick: function() {
                let enabled = this.checked;
                if (enabled === true) {
                    xsyncConfigurationDashboard.enableOrDisableProjectConfig(true, localProject, xsyncConfigurationDashboard.currentRemoteUrl, this);
                } else {
                    xsyncConfigurationDashboard.enableOrDisableProjectConfig(false, localProject, xsyncConfigurationDashboard.currentRemoteUrl, this);
                }
            }
        }));
    }

    xsyncConfigurationDashboard.getFailedStackTraceModal = function(projectId) {
        let remoteUrl = xsyncConfigurationDashboard.currentRemoteUrl;
        var stackTrace = '';
        XNAT.xhr.get({
            url: restUrl('xapi/xsync/history/' + projectId +'/failure?remoteUrl=' + remoteUrl),
            async: false,
            success: function (data) {
                stackTrace = data;
            },
            fail: function (e) {
                XNAT.ui.banner.top(2000, 'Could not retrieve failure information for project: ' + projectId + '\nError message: s' + e.responseText, 'error');
            }
        });
        var stacktraceDiv = spawn('div.stacktrace', {
            style: {
                whiteSpace: 'pre-wrap',
                fontFamily: 'monospace',
                fontSize: '12px',
                padding: '10px',
                background: '#f8f8f8',
                border: '1px solid #ccc',
                overflowX: 'auto'
            }
        }, stackTrace);
        this.dialog = XNAT.ui.dialog.open({
            title: 'Failure Stack Trace For ' + projectId,
            width: 900,
            content:stacktraceDiv,
            isDraggable: true,
            mask: false,
            esc: true,
            buttons: [
                {
                    label: 'Back',
                    isDefault: true,
                    close: true,
                    action: function(){
                        xsyncConfigurationDashboard.getRemoteUrlListingModal(xsyncConfigurationDashboard.currentRemoteUrl, true);
                    }
                }
            ]
        });
    }

    xsyncConfigurationDashboard.createFullHistoryModal = function(projectId) {
        XNAT.xhr.get({
            url: restUrl('xapi/xsync/history/projects/' + projectId),
            async: false,
            success: function (data) {
                xsyncConfigurationDashboard.currentHistoryData = []
                data.forEach(function (item) {
                    var startDate = new Date(item.startDate);
                    item.startDateAsDate = startDate;
                    item.startDate = startDate.toDateString() + ' ' + startDate.toLocaleTimeString();
                    var completeDate = new Date(item.completeDate);
                    item.completeDate = completeDate.toDateString() + ' ' + completeDate.toLocaleTimeString();
                    xsyncConfigurationDashboard.currentHistoryData.push(item);
                });
            },
            fail: function (e) {
                XNAT.ui.banner.top(2000, 'Could not project history information for: ' + projectId + '\nError message: s' + e.responseText, 'error');
            }
        });
        let historyDiv = spawn('div#history_modal_content');

        this.dialog = XNAT.ui.dialog.open({
            title: 'Full history for project: ' + projectId,
            width: 900,
            content:historyDiv,
            isDraggable: true,
            mask: false,
            esc: true,
            buttons: [
                {
                    label: 'Back',
                    isDefault: true,
                    close: true,
                    action: function(){
                        xsyncConfigurationDashboard.getRemoteUrlListingModal(xsyncConfigurationDashboard.currentRemoteUrl, true);
                    }
                }
            ],
            afterShow: function() {
                xsyncConfigurationDashboard.currentHistoryData.sort((a,b) => (a.startDateAsDate < b.startDateAsDate) ? 1 : -1);
                $('#history_modal_content').append(spawnDetailsForList(xsyncConfigurationDashboard.currentHistoryData));
            }
        });
    }

    xsyncConfigurationDashboard.getRemoteUrlListingModal = function(remoteUrl, dataAlreadyRetrieved) {
        if (!dataAlreadyRetrieved) {
            xsyncConfigurationDashboard.currentRemoteUrl = remoteUrl;
            xsyncConfigurationDashboard.getCurrentUrlTableData(remoteUrl);
        }
        let tmpl = spawn('div#modal_table_wrapper');

        this.dialog = XNAT.ui.dialog.open({
            title: 'Details for ' + remoteUrl,
            width: 900,
            content:tmpl,
            isDraggable: true,
            mask: false,
            esc: true,
            buttons: [
                {
                    label: 'Close',
                    isDefault: true,
                    close: true
                }
            ],
            afterShow: function () {
                xsyncConfigurationDashboard.refreshModalTable();
            }
        });
    }

    xsyncConfigurationDashboard.disableOrEnableSiteModal = function(remoteUrl, enable) {
        let modalTitle = enable ? 'Enable all connections for ' + remoteUrl +"?" : 'Disable all connections for ' + remoteUrl +"?";
        let modalContent = enable ? 'Are you sure you want to enable all connections associated with this remote url?' : 'Are you sure you want to disable all connections associated with this remote url?';
        xmodal.confirm({
            title: modalTitle,
            content: modalContent,
            okAction: function(){
                XNAT.xhr.put({
                    url: restUrl('/xapi/xsync/dashboard/enable?remoteUrl=' + remoteUrl + '&enabled=' + enable),
                    async: false,
                    success: function() {
                        xmodal.closeAll();
                        XNAT.dialog.closeAll();
                        xsyncConfigurationDashboard.refreshTable();
                    },
                    fail: function (e) {
                        XNAT.ui.banner.top(2000, 'Could not update configuration information for url: ' + remoteUrl + '\nError message: ' + e.responseText, 'error');
                    }
                });
            }
        });
    }

    xsyncConfigurationDashboard.enableOrDisableProjectConfig = function(enable, projectId, remoteUrl, checkbox) {
        XNAT.xhr.put({
            url: restUrl('/xapi/xsync/dashboard/' + projectId +'/enable?remoteUrl=' + remoteUrl + '&enabled=' + enable),
            async: false,
            success: function() {
                xsyncConfigurationDashboard.getCurrentUrlTableData(remoteUrl);
                xsyncConfigurationDashboard.refreshModalTable();
            },
            fail: function (e) {
                XNAT.ui.banner.top(2000, 'Could not update configuration information for project: ' + projectId + '\nError message: s' + e.responseText, 'error');
                checkbox.checked=!enable;
            }
        });
    }

    xsyncConfigurationDashboard.getCurrentUrlTableData = function(remoteUrl) {
        XNAT.xhr.get({
            url: restUrl('/xapi/xsync/dashboard/remoteUrl?remoteUrl=' + remoteUrl),
            async: false,
            success: function (data) {
                xsyncConfigurationDashboard.currentRemoteUrlData = [];
                data.forEach(function (item) {
                    xsyncConfigurationDashboard.currentRemoteUrlData.push(item);
                });
            },
            fail: function (e) {
                XNAT.ui.banner.top(2000, 'Could not retrieve configuration information for url: ' + remoteUrl + '\nError message: s' + e.responseText, 'error');
            }
        });
    }

    xsyncConfigurationDashboard.getConfigurationData = function() {
        XNAT.xhr.get({
            url: restUrl('/xapi/xsyncSitePreferences/'),
            async: false,
            success: function (data) {
                let enabled = data['xsyncWhitelistEnabled'];
                xsyncConfigurationDashboard.isWhitelistEnabledBackend = enabled;
            },
            fail: function (e) {
                XNAT.ui.banner.top(2000, 'Could not retrieve whitelist information: ' + e.responseText, 'error');
            }
        });
        XNAT.xhr.get({
            url: restUrl('/xapi/xsyncSitePreferences/blacklistSites'),
            async: false,
            success: function (data) {
                xsyncConfigurationDashboard.blacklistSites = []
                data.forEach(function (item) {
                    xsyncConfigurationDashboard.blacklistSites.push(item);
                });
            },
            fail: function (e) {
                XNAT.ui.banner.top(2000, 'Could not retrieve whitelist information: ' + e.responseText, 'error');
            }
        });
        XNAT.xhr.get({
            url: restUrl('/xapi/xsync/dashboard/'),
            async: false,
            success: function (data) {
                xsyncConfigurationDashboard.configurationData = []
                data.forEach(function (item) {
                    xsyncConfigurationDashboard.configurationData.push(item);
                });
            },
            fail: function (e) {
                XNAT.ui.banner.top(2000, 'Could not retrieve remote configuration information: ' + e.responseText, 'error');
            }
        });
    }

    xsyncConfigurationDashboard.refreshModalTable = function() {
            if (typeof xsyncConfigurationDashboard.$modalTable != 'undefined') {
                xsyncConfigurationDashboard.$modalTable.remove();
            }
            let $modalTableDiv = $('div#modal_table_wrapper');
            $modalTableDiv.prepend(xsyncConfigurationDashboard.modalTable());
        };

    xsyncConfigurationDashboard.refreshTable = function() {
        if (typeof xsyncConfigurationDashboard.$table != 'undefined') {
            xsyncConfigurationDashboard.$table.remove();
        }
        let $configurationDashboardTableDiv = $('div#xsync-configuration-dashboard-panel');
        $configurationDashboardTableDiv.prepend(xsyncConfigurationDashboard.table());
        let allRemoteUrlSpans = xsyncConfigurationDashboard.$table.find('.remoteUrl').children('span');
        allRemoteUrlSpans.each(function (span) {
            if (xsyncConfigurationDashboard.blacklistSites.includes(this.element.textContent)) {
                this.parentElement.parentElement.style.background = "#bfbfbf";
            }
        });
    };

    xsyncConfigurationDashboard.modalTable = function() {
        let tableData = [];
        let remoteUrlDetails = xsyncConfigurationDashboard.currentRemoteUrlData;
        DATA_FIELDS = "localProject, remoteProject, frequency, lastSyncStatus"

        for (let k = 0; k < remoteUrlDetails.length; k++) {
            let detail = remoteUrlDetails[k];
            let tableDataRow = {};
            tableDataRow['localProject'] = detail['localProject'];
            tableDataRow['remoteProject'] = detail['remoteProject'];
            tableDataRow['status'] = detail['status'];
            tableDataRow['frequency'] = detail['frequency'];
            tableDataRow['lastSyncStatus'] = detail['lastSyncStatus'];
            tableDataRow['actions'] = detail;
            tableData.push(tableDataRow);
        }

        let columnsInTable = {};

        columnsInTable['localProject'] = {
            label: 'Local Project',
            sortable: true,
            td: {
                style: {
                    verticalAlign: 'middle'
                }
            },
            apply: function (localProject) {
                return xsyncConfigurationDashboard.getLocalProjectLink(localProject);
            }
        }
        columnsInTable['remoteProject'] = {
            label: 'Remote Project',
            sortable: true,
            td: {
                style: {
                    verticalAlign: 'middle'
                }
            },
            apply: function (remoteProject) {
                return spawn('span', {
                    title: remoteProject,
                    html: remoteProject
                });
            }
        }
        columnsInTable['status'] = {
            label: 'Enabled',
            sortable: true,
            td: {
                style: {
                    verticalAlign: 'middle'
                }
            },
            apply: function (status) {
                return xsyncConfigurationDashboard.spawnStatusColumn(status, this.localProject);
            }
        }
        columnsInTable['frequency'] = {
            label: 'Frequency',
            sortable: true,
            td: {
                style: {
                    verticalAlign: 'middle'
                }
            },
            apply: function (frequency) {
                return spawn('span', {
                    title: frequency,
                    html: frequency
                });
            }
        }
        columnsInTable['lastSyncStatus'] =  {
            label: 'Last Sync Status',
            sortable: true,
            td: {
                style: {
                    verticalAlign: 'middle'
                }
            },
            apply: function (lastSyncStatus) {
                if (lastSyncStatus.toLowerCase().includes('fail')) {
                    return xsyncConfigurationDashboard.getFailedSyncStackTrace(lastSyncStatus, this.localProject);
                } else {
                    return spawn('span', {
                        title: lastSyncStatus,
                        html: lastSyncStatus
                    });
                }
            }
        }
        columnsInTable['actions'] = {
            label: 'Actions',
            td: {
                style: {
                    verticalAlign: 'middle',
                    width: '90px'
                }
            },
            apply: function (actions) {
                return [xsyncConfigurationDashboard.getShowHistoryButton(this.localProject)]
            }
        }

        let modalTable = XNAT.table.dataTable(tableData, {
            header: true,
            sortable: DATA_FIELDS,
            filter: DATA_FIELDS,
            height: 'auto',
            table: {
                className: 'xsync-configuration-table xnat-table selectable',
                style: {
                    width: '100%',
                    marginTop: '15px',
                    marginBottom: '15px',
                    border: '1px solid #aaa'
                }
            },
            columns: columnsInTable
        });

        xsyncConfigurationDashboard.$modalTable = $(modalTable.table);
        return modalTable.table;
    }

    xsyncConfigurationDashboard.table = function() {
        xsyncConfigurationDashboard.getConfigurationData();
        let tableData = [];
        let configurations = xsyncConfigurationDashboard.configurationData;
        DATA_FIELDS = "remoteSite, remoteUrl, securityTier, numberProjects, numberErrors"

        for (let k = 0; k < configurations.length; k++) {
            let configuration = configurations[k];
            let tableDataRow = {};
            tableDataRow['remoteSite'] = configuration['siteName'];
            tableDataRow['remoteUrl'] = configuration['remoteUrl'];
            tableDataRow['securityTier'] = configuration['classification'];
            tableDataRow['numberProjects'] = configuration['numberProjects'];
            tableDataRow['numberErrors'] = configuration['numberErrors'];
            tableDataRow['actions'] = configuration;
            tableData.push(tableDataRow);
        }

        let columnsInTable = {};

        if (xsyncConfigurationDashboard.isWhitelistEnabledBackend) {
            columnsInTable['remoteSite'] = {
                label: 'Remote Site',
                sortable: true,
                td: {
                    style: {
                        verticalAlign: 'middle'
                    }
                },
                apply: function (remoteSite) {
                    return spawn('span', {
                        title: remoteSite,
                        html: remoteSite
                    });
                }
            }
        }
        columnsInTable['remoteUrl'] = {
            label: 'Remote Url',
            sortable: true,
            td: {
                style: {
                    verticalAlign: 'middle'
                }
            },
            apply: function (remoteUrl) {
                return spawn('span', {
                    title: remoteUrl,
                    html: remoteUrl
                });
            }
        }
        if(xsyncConfigurationDashboard.isWhitelistEnabledBackend) {
            columnsInTable['securityTier'] = {
                label: 'Security Tier',
                sortable: true,
                td: {
                    style: {
                        verticalAlign: 'middle'
                    }
                },
                apply: function (securityTier) {
                    return spawn('span', {
                        title: securityTier,
                        html: securityTier
                    });
                }
            }
        }
        columnsInTable['numberProjects'] = {
            label: 'Number Projects',
            sortable: true,
            td: {
                style: {
                    verticalAlign: 'middle'
                }
            },
            apply: function (numberProjects) {
                return xsyncConfigurationDashboard.getNumberProjectsLink(numberProjects, this.remoteUrl);
            }
        }
        columnsInTable['numberErrors'] =  {
            label: 'Number Errors',
            sortable: true,
            td: {
                style: {
                    verticalAlign: 'middle'
                }
            },
            apply: function (numberErrors) {
                return spawn('span', {
                    title: numberErrors.toString(),
                    html: numberErrors.toString()
                });
            }
        }
        columnsInTable['actions'] = {
            label: 'Actions',
            td: {
                style: {
                    verticalAlign: 'middle',
                    width: '90px'
                }
            },
            apply: function (actions) {
                return [xsyncConfigurationDashboard.getDisableButton(this.remoteUrl)]
            }
        }

        let xsyncConfigurationTable = XNAT.table.dataTable(tableData, {
            header: true,
            sortable: DATA_FIELDS,
            filter: DATA_FIELDS,
            height: 'auto',
            table: {
                className: 'xsync-configuration-table xnat-table selectable',
                style: {
                    width: '100%',
                    marginTop: '15px',
                    marginBottom: '15px',
                    border: '1px solid #aaa'
                }
            },
            columns: columnsInTable
        });

        xsyncConfigurationDashboard.$table = $(xsyncConfigurationTable.table);
        return xsyncConfigurationTable.table;
    }

    xsyncConfigurationDashboard.init = function() {
        xsyncConfigurationDashboard.refreshTable();
    }

    $(document).ready(function () {
        xsyncConfigurationDashboard.init();
    })
}));

function spacer(width) {
    return spawn('i.spacer', {
        style: {
            display: 'inline-block',
            width: width + 'px'
        }
    })
}

function spawnDetailsForList(items) {
    return spawn('div.details-list', items.map(function(item, i){
        var summaryDate = new Date(item.startDate);
        var summaryText = summaryDate.toDateString() + ' ' + summaryDate.toLocaleTimeString();
        var bodyContent = ['pre.json-body', {
            style: { whiteSpace: 'pre-wrap', fontFamily: 'monospace', fontSize: '12px' }
        }, JSON.stringify(item, null, 2)];

        return ['details.json-item', { style: { marginBottom: '8px' } }, [
            ['summary', { style: { cursor: 'pointer', fontWeight: 'bold' } }, summaryText],
            bodyContent
        ]];
    }));
}
