/*
 * web: xsyncGlobusManager.js
 * XNAT http://www.xnat.org
 * Copyright (c) 2005-2024, Washington University School of Medicine
 * All Rights Reserved
 *
 * Released under the Simplified BSD.
 */

/*!
 * Manage the Globus endpoints (and their credentials) XSync can transfer with.
 * Client secrets are write-only: stored on save, never returned; leave the
 * secret blank when editing to keep the stored value.
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
            XNAT.ui.banner.top(4000, 'Globus connection failed. Check the client id, secret, and collection ids.', 'warning');
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
                XNAT.ui.banner.top(2000, 'Could not retrieve Globus endpoints: ' + e.responseText, 'error');
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
                XNAT.ui.banner.top(2000, 'Endpoint ' + (mode === 'create' ? 'created' : 'updated') + ' successfully.', 'success');
                XNAT.ui.dialog.closeAll();
            },
            fail: function(e) {
                XNAT.ui.banner.top(3000, 'Could not save the endpoint: ' + e.responseText, 'error');
            }
        });
    };

    xsyncGlobusManager.testCredentials = function(endpoint) {
        XNAT.xhr.post({
            url: restUrl(apiBase + '/test'),
            contentType: 'application/json',
            data: JSON.stringify(endpoint),
            success: bannerTestResult,
            fail: function(e) {
                XNAT.ui.banner.top(4000, 'Connection test error: ' + e.responseText, 'error');
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
            title: 'Confirm Endpoint Deletion',
            content: 'Are you sure you want to delete the Globus endpoint "' + endpoint.name + '"?',
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
                                XNAT.ui.banner.top(2000, 'Endpoint deleted.', 'success');
                                xsyncGlobusManager.refreshTable();
                            },
                            fail: function(e) {
                                XNAT.ui.banner.top(3000, 'Could not delete the endpoint: ' + e.responseText, 'error');
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

    function renderForm(endpoint, mode) {
        var container = document.getElementById('globus-endpoint-details-body');
        container.innerHTML = '';
        var form = document.createElement('form');
        form.id = 'globus-endpoint-form';
        form.appendChild(spawn('div|class="warning"', {style: {visibility: 'hidden'}, id: 'globus-warning'}));
        form.appendChild(inputRow('Endpoint Name', 'globus_name_input', endpoint.name, 'text', '', 'A unique name for this endpoint configuration.'));
        form.appendChild(inputRow('Client ID', 'globus_clientid_input', endpoint.clientId, 'text', '', 'The Globus confidential-client (application) id.'));
        var secretPlaceholder = (mode === 'create') ? '' : 'Leave blank to keep the stored secret';
        form.appendChild(inputRow('Client Secret', 'globus_secret_input', '', 'password', secretPlaceholder, 'The client secret. Stored on save but never displayed again.'));
        form.appendChild(inputRow('Inbox Collection ID', 'globus_inbox_input', endpoint.inboxCollectionId, 'text', '', 'UUID of this node\'s inbox collection (receives XARs/DICOM). Leave blank for a send-only node.'));
        form.appendChild(inputRow('Outbox Collection ID', 'globus_outbox_input', endpoint.outboxCollectionId, 'text', '', 'UUID of this node\'s outbox collection (sends staged XARs). Leave blank for a receive-only node.'));
        container.appendChild(form);
    }

    function gatherForm() {
        return {
            name: $('#globus_name_input').val(),
            clientId: $('#globus_clientid_input').val(),
            clientSecret: $('#globus_secret_input').val(),
            inboxCollectionId: $('#globus_inbox_input').val(),
            outboxCollectionId: $('#globus_outbox_input').val()
        };
    }

    xsyncGlobusManager.addOrEditModal = function(endpoint, mode) {
        var tmpl = spawn('div#globus-endpoint-details', [
            spawn('div#globus-endpoint-details-body', ['Loading...'])
        ]);

        XNAT.ui.dialog.open({
            title: (mode === 'create') ? 'Add Globus Endpoint' : 'Edit Globus Endpoint',
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
                        pojo.name = xsyncGlobusManager.validate($('#globus_name_input'), 'Endpoint Name', 'required');
                        pojo.clientId = xsyncGlobusManager.validate($('#globus_clientid_input'), 'Client ID', 'required');
                        pojo.inboxCollectionId = $('#globus_inbox_input').val();
                        pojo.outboxCollectionId = $('#globus_outbox_input').val();
                        if ((!pojo.inboxCollectionId || pojo.inboxCollectionId.trim() === '') &&
                            (!pojo.outboxCollectionId || pojo.outboxCollectionId.trim() === '')) {
                            xsyncGlobusManager.validation += 'An inbox and/or outbox collection ID is required.<br/>';
                            $('#globus_inbox_input').css('border-color', 'red');
                            $('#globus_outbox_input').css('border-color', 'red');
                        }
                        var secret = $('#globus_secret_input').val();
                        if (mode === 'create') {
                            pojo.clientSecret = xsyncGlobusManager.validate($('#globus_secret_input'), 'Client Secret', 'required');
                        } else if (secret && secret.trim() !== '') {
                            pojo.clientSecret = secret;
                        }
                        if (xsyncGlobusManager.validation !== '') {
                            $('#globus-warning').html(xsyncGlobusManager.validation).css('visibility', 'visible');
                            XNAT.ui.banner.top(2000, 'Failed to save endpoint.', 'error');
                        } else {
                            $('#globus-warning').html('').css('visibility', 'hidden');
                            xsyncGlobusManager.createOrUpdate(pojo, mode);
                        }
                    }
                },
                {
                    label: 'Test',
                    isDefault: false,
                    close: false,
                    action: function() {
                        var pojo = gatherForm();
                        if ((!pojo.clientSecret || pojo.clientSecret.trim() === '') && mode !== 'create') {
                            XNAT.ui.banner.top(5000, 'Enter the secret to test it here, or use Test on the endpoint row to test the stored secret.', 'warning');
                            return;
                        }
                        xsyncGlobusManager.testCredentials(pojo);
                    }
                },
                {
                    label: 'Close',
                    isDefault: false,
                    close: true
                }
            ],
            afterShow: function() {
                renderForm(endpoint, mode);
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
            title: 'Add a Globus endpoint'
        }, 'Add Endpoint');
    };

    xsyncGlobusManager.getEditButton = function(endpoint) {
        return spawn('button.btn.btn-sm.edit', {
            onclick: function(e) {
                e.preventDefault();
                xsyncGlobusManager.addOrEditModal(endpoint, 'update');
            },
            title: 'Edit endpoint'
        }, 'Edit');
    };

    xsyncGlobusManager.getTestButton = function(endpoint) {
        return spawn('button.btn.btn-sm.edit', {
            onclick: function(e) {
                e.preventDefault();
                xsyncGlobusManager.testStored(endpoint.name);
            },
            title: 'Test connectivity using the stored credentials'
        }, 'Test');
    };

    xsyncGlobusManager.getDeleteButton = function(endpoint) {
        return spawn('button.btn.btn-sm.edit', {
            onclick: function(e) {
                e.preventDefault();
                xsyncGlobusManager.deleteEndpoint(endpoint);
            },
            title: 'Delete endpoint'
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
        var DATA_FIELDS = 'name, clientId, inboxCollectionId, outboxCollectionId';
        var tableData = [];
        (xsyncGlobusManager.endpoints || []).forEach(function(ep) {
            tableData.push({
                name: ep.name,
                clientId: ep.clientId,
                inboxCollectionId: ep.inboxCollectionId,
                outboxCollectionId: ep.outboxCollectionId,
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
            clientId: textCol('Client ID'),
            inboxCollectionId: textCol('Inbox Collection'),
            outboxCollectionId: textCol('Outbox Collection'),
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
