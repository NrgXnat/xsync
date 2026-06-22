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

    xsyncConfigurationDashboard.getAddNewButton = function() {
        return spawn('button.btn.btn-sm.edit', {
            onclick: function (e) {
                e.preventDefault();
            },
            title: "Add new connection to this site"
        }, 'Add New');
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
            url: restUrl('/xapi/xsync/setup/remoteInstances/'),
            async: false,
            success: function (data) {
                xsyncConfigurationDashboard.ConfigurationData = []
                data.forEach(function (item) {
                    xsyncConfigurationDashboard.ConfigurationData.push(item);
                });
            },
            fail: function (e) {
                XNAT.ui.banner.top(2000, 'Could not retrieve remote configuration information: ' + e.responseText, 'error');
            }
        });
    }

    xsyncConfigurationDashboard.refreshTable = function() {
        if (typeof xsyncConfigurationDashboard.$table != 'undefined') {
            xsyncConfigurationDashboard.$table.remove();
        }
        let $configurationDashboardTableDiv = $('div#xsync-configuration-dashboard-panel');
        $configurationDashboardTableDiv.prepend(xsyncConfigurationDashboard.table());
    };

    xsyncConfigurationDashboard.table = function() {
        xsyncConfigurationDashboard.getConfigurationData();
        let tableData = [];
        let configurations = xsyncConfigurationDashboard.ConfigurationData;
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
                return spawn('span', {
                    title: numberProjects.toString(),
                    html: numberProjects.toString()
                });
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
                    width: '135px'
                }
            },
            apply: function (actions) {
                return [xsyncConfigurationDashboard.getAddNewButton()]
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
