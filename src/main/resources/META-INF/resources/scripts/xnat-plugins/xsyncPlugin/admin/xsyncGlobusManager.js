/*
 * web: xsyncGlobusManager.js
 * XNAT http://www.xnat.org
 * Copyright (c) 2005-2024, Washington University School of Medicine
 * All Rights Reserved
 *
 * Released under the Simplified BSD.
 */

/*!
 * Manage the Globus transfer destinations XSync can send to. The node's own
 * service account and outbox are configured on the Globus Node tab; a
 * destination here records a peer's inbox collection and the paths a transfer
 * writes to.
 */

console.log('xsyncGlobusManager.js');

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
    var apiBase = '/xapi/xsync/globus/endpoints';

    var xsyncGlobusManager;
    XNAT.plugin.xsync.xsyncGlobusManager = xsyncGlobusManager = getObject(XNAT.plugin.xsync.xsyncGlobusManager || {});

    xsyncGlobusManager.endpoints = [];
    xsyncGlobusManager.validation = '';

    function globusSpacer(width) {
        return spawn('i.spacer', {style: {display: 'inline-block', width: width + 'px'}});
    }

    function bannerTestResult(data) {
        if (data === true || data === 'true') {
            XNAT.ui.banner.top(3000, 'Globus connection succeeded.', 'success');
        } else {
            XNAT.ui.banner.top(4000, 'Globus connection failed. Check the node service account (Globus Node tab) and the destination inbox collection id.', 'warning');
        }
    }

    xsyncGlobusManager.getEndpoints = function() {
        XNAT.xhr.get({
            url: restUrl(apiBase),
            async: false,
            success: function(data) {
                xsyncGlobusManager.endpoints = data || [];
            },
            fail: function(e) {
                XNAT.ui.banner.top(2000, 'Could not retrieve Globus destinations: ' + e.responseText, 'error');
            }
        });
    };

    xsyncGlobusManager.createOrUpdate = function(endpoint, mode) {
        XNAT.xhr.post({
            url: restUrl(apiBase),
            async: false,
            contentType: 'application/json',
            data: JSON.stringify(endpoint),
            success: function() {
                xsyncGlobusManager.refreshTable();
                XNAT.ui.banner.top(2000, 'Destination ' + (mode === 'create' ? 'created' : 'updated') + ' successfully.', 'success');
                XNAT.ui.dialog.closeAll();
            },
            fail: function(e) {
                XNAT.ui.banner.top(3000, 'Could not save the destination: ' + e.responseText, 'error');
            }
        });
    };

    xsyncGlobusManager.testStored = function(name) {
        XNAT.xhr.post({
            url: restUrl(apiBase + '/' + encodeURIComponent(name) + '/test'),
            success: bannerTestResult,
            fail: function(e) {
                XNAT.ui.banner.top(4000, 'Connection test error: ' + e.responseText, 'error');
            }
        });
    };

    xsyncGlobusManager.deleteEndpoint = function(endpoint) {
        xmodal.open({
            title: 'Confirm Destination Deletion',
            content: 'Are you sure you want to delete the Globus destination "' + endpoint.name + '"?',
            width: 400,
            height: 200,
            overflow: 'auto',
            buttons: {
                ok: {
                    label: 'Proceed',
                    isDefault: true,
                    action: function() {
                        XNAT.xhr.delete({
                            url: restUrl(apiBase + '/' + encodeURIComponent(endpoint.name)),
                            async: false,
                            success: function() {
                                xmodal.closeAll();
                                XNAT.ui.banner.top(2000, 'Destination deleted.', 'success');
                                xsyncGlobusManager.refreshTable();
                            },
                            fail: function(e) {
                                XNAT.ui.banner.top(3000, 'Could not delete the destination: ' + e.responseText, 'error');
                            }
                        });
                    }
                },
                close: {label: 'Cancel'}
            }
        });
    };

    xsyncGlobusManager.validate = function(element, name, validators) {
        var value = element.val();
        var valid = true;
        if (validators.indexOf('required') !== -1 && value.trim() === '') {
            xsyncGlobusManager.validation += name + ' is a required field.<br/>';
            valid = false;
        }
        element.css('border-color', valid ? '' : 'red');
        return value;
    };

    function inputRow(label, id, value, type, placeholder, tooltip) {
        var elements = [
            spawn('label.element-label', label),
            spawn('input', {
                addClass: 'globus_input',
                type: type,
                id: id,
                value: value == null ? '' : value,
                placeholder: placeholder || ''
            })
        ];
        if (tooltip) {
            elements.push(spawn('a.infoLink', {
                title: tooltip,
                html: '<i class="fa fa-question-circle"></i>',
                style: {position: 'relative', top: '0px', left: '5px'}
            }));
        }
        return spawn('div.element-wrapper', elements);
    }

    function renderForm(endpoint) {
        var container = document.getElementById('globus-endpoint-details-body');
        container.innerHTML = '';
        var form = document.createElement('form');
        form.id = 'globus-endpoint-form';
        form.appendChild(spawn('div|class="warning"', {style: {visibility: 'hidden'}, id: 'globus-warning'}));
        form.appendChild(inputRow('Destination Name', 'globus_name_input', endpoint.name, 'text', '', 'A unique name for this destination.'));
        form.appendChild(inputRow('Inbox Collection ID', 'globus_inbox_input', endpoint.inboxCollectionId, 'text', '', 'UUID of the destination\'s inbox guest collection, where this node writes.'));
        form.appendChild(inputRow('Remote Inbox Path', 'globus_remotepath_input', endpoint.remoteInboxPath, 'text', '', 'Collection-relative path in the destination inbox this node writes to (its subpath, e.g. /site1).'));
        form.appendChild(inputRow('Remote Inbox Server Directory', 'globus_remoteserverdir_input', endpoint.remoteInboxServerDirectory, 'text', '', 'Destination host filesystem directory where the transferred XAR lands, for import (e.g. /opt/data/xsync-globus-inbox/site1).'));
        container.appendChild(form);
    }

    xsyncGlobusManager.addOrEditModal = function(endpoint, mode) {
        var tmpl = spawn('div#globus-endpoint-details', [
            spawn('div#globus-endpoint-details-body', ['Loading...'])
        ]);

        XNAT.ui.dialog.open({
            title: (mode === 'create') ? 'Add Globus Destination' : 'Edit Globus Destination',
            width: 720,
            content: tmpl,
            isDraggable: true,
            mask: false,
            esc: true,
            buttons: [
                {
                    label: 'Save and Close',
                    isDefault: true,
                    close: false,
                    action: function() {
                        xsyncGlobusManager.validation = '';
                        var pojo = {};
                        pojo.name = xsyncGlobusManager.validate($('#globus_name_input'), 'Destination Name', 'required');
                        pojo.inboxCollectionId = xsyncGlobusManager.validate($('#globus_inbox_input'), 'Inbox Collection ID', 'required');
                        pojo.remoteInboxPath = $('#globus_remotepath_input').val();
                        pojo.remoteInboxServerDirectory = $('#globus_remoteserverdir_input').val();
                        if (xsyncGlobusManager.validation !== '') {
                            $('#globus-warning').html(xsyncGlobusManager.validation).css('visibility', 'visible');
                            XNAT.ui.banner.top(2000, 'Failed to save destination.', 'error');
                        } else {
                            $('#globus-warning').html('').css('visibility', 'hidden');
                            xsyncGlobusManager.createOrUpdate(pojo, mode);
                        }
                    }
                },
                {
                    label: 'Close',
                    isDefault: false,
                    close: true
                }
            ],
            afterShow: function() {
                renderForm(endpoint);
                if (mode !== 'create') {
                    document.getElementById('globus_name_input').readOnly = true;
                }
            }
        });
    };

    xsyncGlobusManager.getAddButton = function() {
        return spawn('button.btn.btn-sm.edit', {
            onclick: function(e) {
                e.preventDefault();
                xsyncGlobusManager.addOrEditModal({}, 'create');
            },
            title: 'Add a Globus destination'
        }, 'Add Destination');
    };

    xsyncGlobusManager.getEditButton = function(endpoint) {
        return spawn('button.btn.btn-sm.edit', {
            onclick: function(e) {
                e.preventDefault();
                xsyncGlobusManager.addOrEditModal(endpoint, 'update');
            },
            title: 'Edit destination'
        }, 'Edit');
    };

    xsyncGlobusManager.getTestButton = function(endpoint) {
        return spawn('button.btn.btn-sm.edit', {
            onclick: function(e) {
                e.preventDefault();
                xsyncGlobusManager.testStored(endpoint.name);
            },
            title: 'Test connectivity to this destination using the node service account'
        }, 'Test');
    };

    xsyncGlobusManager.getDeleteButton = function(endpoint) {
        return spawn('button.btn.btn-sm.edit', {
            onclick: function(e) {
                e.preventDefault();
                xsyncGlobusManager.deleteEndpoint(endpoint);
            },
            title: 'Delete destination'
        }, 'Delete');
    };

    xsyncGlobusManager.refreshTable = function() {
        if (typeof xsyncGlobusManager.$table != 'undefined') {
            xsyncGlobusManager.$table.remove();
        }
        $('div#xsync-globus-endpoints-table').prepend(xsyncGlobusManager.table());
    };

    xsyncGlobusManager.table = function() {
        xsyncGlobusManager.getEndpoints();
        var DATA_FIELDS = 'name, inboxCollectionId, remoteInboxPath, remoteInboxServerDirectory';
        var tableData = [];
        (xsyncGlobusManager.endpoints || []).forEach(function(ep) {
            tableData.push({
                name: ep.name,
                inboxCollectionId: ep.inboxCollectionId,
                remoteInboxPath: ep.remoteInboxPath,
                remoteInboxServerDirectory: ep.remoteInboxServerDirectory,
                actions: ep
            });
        });

        function textCol(label) {
            return {
                label: label,
                sortable: true,
                td: {style: {verticalAlign: 'middle'}},
                apply: function(value) {
                    return spawn('span', {title: value, html: value});
                }
            };
        }

        var columns = {
            name: textCol('Name'),
            inboxCollectionId: textCol('Inbox Collection'),
            remoteInboxPath: textCol('Remote Inbox Path'),
            remoteInboxServerDirectory: textCol('Remote Inbox Server Directory'),
            actions: {
                label: 'Actions',
                td: {style: {verticalAlign: 'middle', width: '200px'}},
                apply: function(endpoint) {
                    return [
                        xsyncGlobusManager.getEditButton(endpoint), globusSpacer(4),
                        xsyncGlobusManager.getTestButton(endpoint), globusSpacer(4),
                        xsyncGlobusManager.getDeleteButton(endpoint)
                    ];
                }
            }
        };

        var globusTable = XNAT.table.dataTable(tableData, {
            header: true,
            sortable: DATA_FIELDS,
            filter: DATA_FIELDS,
            height: 'auto',
            table: {
                className: 'globus-endpoint-table xnat-table selectable',
                style: {width: '100%', marginTop: '15px', marginBottom: '15px', border: '1px solid #aaa'}
            },
            columns: columns
        });

        xsyncGlobusManager.$table = $(globusTable.table);
        return globusTable.table;
    };

    xsyncGlobusManager.init = function() {
        var $div = $('div#xsync-globus-endpoints-table');
        $div.empty().append(xsyncGlobusManager.table());
        $div.append(xsyncGlobusManager.getAddButton());
    };

    $(document).ready(function() {
        if ($('div#xsync-globus-endpoints-table').length) {
            xsyncGlobusManager.init();
        }
    });

}));
