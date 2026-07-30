/*
 * web: xsyncProjectBlacklistManager.js
 * XNAT http://www.xnat.org
 * Copyright (c) 2005-2017, Washington University School of Medicine and Howard Hughes Medical Institute
 * All Rights Reserved
 *
 * Released under the Simplified BSD.
 */

/*!
 * Administration tab to manage which project are not allowed to have XSYNC connections attached to them.
 */

 console.log('xsyncProjectBlacklistManager.js');

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
    var xsyncProjectBlacklistManager;
    XNAT.plugin.xsync.xsyncProjectBlacklistManager = xsyncProjectBlacklistManager = getObject(XNAT.plugin.xsync.xsyncProjectBlacklistManager || {});

    xsyncProjectBlacklistManager.getAddButton = function() {
        return spawn('button.btn.btn-sm.edit', {
            onclick: function (e) {
                e.preventDefault();
                whitelistSite = {};
                xsyncProjectBlacklistManager.addModal();
            },
            title: "Add project to blacklist"
        }, 'Add Project');
    }

    xsyncProjectBlacklistManager.getRemoveButton = function(blacklistProject) {
        return spawn('button.btn.btn-sm.edit', {
            onclick: function (e) {
                e.preventDefault();
                xsyncProjectBlacklistManager.removeProjectFromBlacklist(blacklistProject);
            },
            title: "Remove project"
        }, 'Remove');
    }

    xsyncProjectBlacklistManager.removeProjectFromBlacklist = function(blacklistProject) {
        XNAT.xhr.delete({
            url: restUrl('/xapi/xsyncSitePreferences/blacklistProjects/' + blacklistProject),
            async: false,
            contentType: 'application/json',
            success: function (data) {
                XNAT.ui.banner.top(2000, "Project removed from blacklist", 'success');
                xsyncProjectBlacklistManager.projects = data;
                xsyncProjectBlacklistManager.refreshTable();
            },
            fail: function (e) {
                XNAT.ui.banner.top(2000, 'Could not remove project from blacklist: ' + e.responseText, 'error');
            }
        });
    }

    xsyncProjectBlacklistManager.addModal = function() {
        const tmpl = spawn('div#add-project-blacklist', [
            spawn('div#add-project-blacklist-body', [
                "Loading..."
                ])
        ]);

        XNAT.xhr.get({
            url: restUrl('xapi/role/projects?format=json'),
            dataType: 'json',
            async: false,
            success: function(data) {
                xsyncProjectBlacklistManager.allXnatProjects = [];
                data.forEach(function(item) {
                    if (!xsyncProjectBlacklistManager.projects.includes(item.id)) {
                        xsyncProjectBlacklistManager.allXnatProjects.push(item.id);
                    }
                });
                console.log(xsyncProjectBlacklistManager.allXnatProjects)
            }
        });

        this.dialog=XNAT.ui.dialog.open({
            title: 'Add Project(s) To Blacklist',
            width: 720,
            content:tmpl,
            isDraggable: true,
            mask: false,
            esc: true,
            buttons: [
                {
                    label: 'Add',
                    isDefault: true,
                    close: true,
                    action: function() {
                        let newBlacklistProject = $("#projects_dropdown").val();
                        if (newBlacklistProject != "") {
                            XNAT.xhr.post({
                                url: restUrl('/xapi/xsyncSitePreferences/blacklistProjects/' + newBlacklistProject),
                                async: false,
                                contentType: 'application/json',
                                success: function (data) {
                                    XNAT.ui.banner.top(2000, "Project added from blacklist", 'success');
                                    xsyncProjectBlacklistManager.projects = data;
                                    xsyncProjectBlacklistManager.refreshTable();
                                },
                                fail: function (e) {
                                    XNAT.ui.banner.top(2000, 'Could not remove project from blacklist: ' + e.responseText, 'error');
                                }
                            });
                        }
                    }
                },
                {
                    label: 'Close',
                    isDefault: false,
                    close: true
                }
            ],
            afterShow: function () {
                const container = document.getElementById('add-project-blacklist-body');
                container.innerHTML = '';
                const form = document.createElement('form');
                form.id = 'form';
                let allProjects = {};
                allProjects['opt0'] = {label: "Select", value: ""};
                xsyncProjectBlacklistManager.allXnatProjects.forEach(function(item) {
                    allProjects['opt'+item] = { label: item, value: item };
                });
                let elements = [
                    XNAT.ui.panel.select.menu({
                        id: 'projects_dropdown',
                        label: 'Select Project',
                        options: allProjects
                    })
                ];
                form.appendChild(spawn('div.projects-element-wrapper', elements));
                container.appendChild(form);
            }
        });
    }

    xsyncProjectBlacklistManager.refreshTable = function() {
        let $blacklistTableDiv = $('div#xsync-project-blacklist-panel');
        $blacklistTableDiv.empty().append(xsyncProjectBlacklistManager.table());
        $blacklistTableDiv.append(xsyncProjectBlacklistManager.getAddButton());
    };

    xsyncProjectBlacklistManager.table = function() {
        let tableData = [];
        let projects = xsyncProjectBlacklistManager.projects;
        DATA_FIELDS = "projectId"

        for (let k = 0; k < projects.length; k++) {
            let project = projects[k];
            let tableDataRow = {};
            tableDataRow['projectId'] = project;
            tableDataRow['actions'] = project;
            tableData.push(tableDataRow);
        }

        let columnsInTable = {
            projectId: {
                label: 'Project ID',
                sortable: true,
                td: {
                    style: {
                        verticalAlign: 'middle'
                    }
                },
                apply: function (projectId) {
                    return spawn('span', {
                        title: projectId,
                        html: projectId
                    });
                }
            },
            actions: {
                label: 'Actions',
                td: {
                    style: {
                        verticalAlign: 'middle',
                        width: '135px'
                    }
                },
                apply: function (actions) {
                    return [xsyncProjectBlacklistManager.getRemoveButton(actions)]
                }
            }
        };

        let blacklistProjectsTable = XNAT.table.dataTable(tableData, {
            header: true,
            sortable: DATA_FIELDS,
            filter: DATA_FIELDS,
            height: 'auto',
            table: {
                className: 'blacklist-table xnat-table selectable',
                style: {
                    width: '50%',
                    marginTop: '15px',
                    marginBottom: '15px',
                    border: '1px solid #aaa'
                }
            },
            columns: columnsInTable
        });

        xsyncProjectBlacklistManager.$table = $(blacklistProjectsTable.table);
        return blacklistProjectsTable.table;
    }

    xsyncProjectBlacklistManager.init = function() {
        XNAT.xhr.get({
            url: restUrl('/xapi/xsyncSitePreferences/blacklistProjects/'),
            async: false,
            success: function (data) {
                xsyncProjectBlacklistManager.projects = data;
                console.log(xsyncProjectBlacklistManager.projects);
                xsyncProjectBlacklistManager.refreshTable();
            },
            fail: function (e) {
                XNAT.ui.banner.top(2000, 'Could not retrieve blacklist information: ' + e.responseText, 'error');
            }
        });
    }

    $(document).ready(function () {
        xsyncProjectBlacklistManager.init();
    })

}));