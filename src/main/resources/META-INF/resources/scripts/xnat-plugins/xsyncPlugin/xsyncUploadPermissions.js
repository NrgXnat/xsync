/*
 * web: xsyncUploadPermissions.js
 * XNAT http://www.xnat.org
 * Copyright (c) 2005-2026, Washington University School of Medicine
 * All Rights Reserved
 *
 * Released under the Simplified BSD.
 */

/*!
 * Project "Globus Upload" management tab: list, add, and remove the per-user
 * Globus upload permissions for a project. Each entry maps a Globus identity to
 * the XNAT user its uploads import as. ACL changes run server-side under the node
 * service account, so no admin Globus login is needed.
 */

console.log('xsyncUploadPermissions.js');

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

    var uploadPermissions;
    XNAT.plugin.xsync.uploadPermissions = uploadPermissions =
        getObject(XNAT.plugin.xsync.uploadPermissions || {});

    uploadPermissions.grants = [];
    uploadPermissions.validation = '';

    function projectId() {
        return XNAT.data.context.project;
    }

    function projectBase() {
        return '/xapi/xsync/globus/projects/' + encodeURIComponent(projectId());
    }

    function apiBase() {
        return projectBase() + '/upload-grants';
    }

    function spacer(width) {
        return spawn('i.spacer', {style: {display: 'inline-block', width: width + 'px'}});
    }

    // Colour cues for the reconciliation state.
    var STATE_COLORS = {
        ACTIVE: '#3f7f3f',
        PENDING: '#b8860b',
        ORPHANED: '#8a6d3b'
    };

    // Plain-language meaning of each state, shown as the badge tooltip and in the
    // legend under the table.
    function stateTooltip(state) {
        switch (state) {
            case 'ACTIVE':   return 'Upload access is set up and active.';
            case 'PENDING':  return 'Recorded here, but the Globus permission is missing. '
                                  + 'Uploads will not work until it is restored (remove and re-add, or check Globus).';
            case 'ORPHANED': return 'A Globus permission exists that XNAT is not managing '
                                  + '(created outside XNAT, e.g. via the Globus CLI).';
            default:         return '';
        }
    }

    function stateBadge(state) {
        return spawn('span', {
            style: {
                color: '#fff',
                backgroundColor: STATE_COLORS[state] || '#777',
                padding: '2px 8px',
                borderRadius: '3px',
                fontSize: '11px',
                fontWeight: 'bold'
            },
            title: stateTooltip(state)
        }, state || 'UNKNOWN');
    }

    uploadPermissions.getGrants = function() {
        XNAT.xhr.get({
            url: restUrl(apiBase()),
            async: false,
            success: function(data) {
                uploadPermissions.grants = data || [];
            },
            fail: function(e) {
                XNAT.ui.banner.top(3000, 'Could not retrieve Globus upload permissions: ' + e.responseText, 'error');
            }
        });
    };

    uploadPermissions.getEligibleUsers = function() {
        var users = [];
        XNAT.xhr.get({
            url: restUrl(projectBase() + '/eligible-users'),
            async: false,
            success: function(data) {
                users = data || [];
            },
            fail: function(e) {
                XNAT.ui.banner.top(3000, 'Could not load the project user list: ' + e.responseText, 'error');
            }
        });
        return users;
    };

    uploadPermissions.add = function(grant) {
        XNAT.xhr.post({
            url: restUrl(apiBase()),
            async: false,
            contentType: 'application/json',
            data: JSON.stringify(grant),
            success: function() {
                uploadPermissions.refreshTable();
                XNAT.ui.banner.top(2000, 'Upload permission granted.', 'success');
                XNAT.ui.dialog.closeAll();
            },
            fail: function(e) {
                XNAT.ui.banner.top(4000, 'Could not grant upload permission: ' + e.responseText, 'error');
            }
        });
    };

    uploadPermissions.remove = function(grant) {
        xmodal.open({
            title: 'Revoke upload permission',
            content: 'Revoke Globus upload access for "' + (grant.globusUsername || grant.globusIdentityId)
                + '" (imports as XNAT user "' + grant.xnatUsername + '")? '
                + 'This removes future access only; it does not affect already-imported data.',
            width: 450,
            height: 220,
            overflow: 'auto',
            buttons: {
                ok: {
                    label: 'Revoke',
                    isDefault: true,
                    action: function() {
                        XNAT.xhr.delete({
                            url: restUrl(apiBase() + '/' + encodeURIComponent(grant.globusIdentityId)),
                            async: false,
                            success: function() {
                                xmodal.closeAll();
                                XNAT.ui.banner.top(2000, 'Upload permission revoked.', 'success');
                                uploadPermissions.refreshTable();
                            },
                            fail: function(e) {
                                XNAT.ui.banner.top(3000, 'Could not revoke the permission: ' + e.responseText, 'error');
                            }
                        });
                    }
                },
                close: {label: 'Cancel'}
            }
        });
    };

    uploadPermissions.validate = function(element, name) {
        var value = element.val();
        if (value == null || value.trim() === '') {
            uploadPermissions.validation += name + ' is required.<br/>';
            element.css('border-color', 'red');
            return '';
        }
        element.css('border-color', '');
        return value.trim();
    };

    function inputRow(label, id, value, placeholder, tooltip) {
        var elements = [
            spawn('label.element-label', {style: {display: 'block', marginBottom: '5px'}}, label),
            spawn('input', {
                addClass: 'upload_grant_input',
                type: 'text',
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

    function selectRow(label, id, options, selected, tooltip) {
        var optionEls;
        if (!options || !options.length) {
            optionEls = [spawn('option', {value: ''}, 'No eligible users')];
        } else {
            optionEls = [spawn('option', {value: ''}, 'Select a project user')];
            options.slice().sort(function(a, b) {
                return a.toLowerCase().localeCompare(b.toLowerCase());
            }).forEach(function(u) {
                var attrs = {value: u};
                if (u === selected) {
                    attrs.selected = true;
                }
                optionEls.push(spawn('option', attrs, u));
            });
        }
        var elements = [
            spawn('label.element-label', {style: {display: 'block', marginBottom: '5px'}}, label),
            spawn('select', {addClass: 'upload_grant_input', id: id}, optionEls)
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

    function renderForm(prefill) {
        var container = document.getElementById('upload-grant-details-body');
        container.innerHTML = '';
        var form = document.createElement('form');
        form.id = 'upload-grant-form';
        form.appendChild(spawn('div|class="warning"', {style: {visibility: 'hidden'}, id: 'upload-grant-warning'}));
        form.appendChild(inputRow('Globus identity', 'upload_globus_input', prefill.globusUsername,
            'username or email', null));
        form.appendChild(selectRow('XNAT user', 'upload_xnat_input', uploadPermissions.getEligibleUsers(),
            prefill.xnatUsername, 'Must be a project member with edit access.'));
        container.appendChild(form);
    }

    uploadPermissions.addModal = function(prefill) {
        var tmpl = spawn('div#upload-grant-details', [
            spawn('div#upload-grant-details-body', ['Loading...'])
        ]);
        XNAT.ui.dialog.open({
            title: 'Add upload user',
            width: 560,
            content: tmpl,
            isDraggable: true,
            mask: false,
            esc: true,
            buttons: [
                {
                    label: 'Grant upload',
                    isDefault: true,
                    close: false,
                    action: function() {
                        uploadPermissions.validation = '';
                        var grant = {
                            globusUsername: uploadPermissions.validate($('#upload_globus_input'), 'Globus identity'),
                            xnatUsername: uploadPermissions.validate($('#upload_xnat_input'), 'XNAT user')
                        };
                        if (uploadPermissions.validation !== '') {
                            $('#upload-grant-warning').html(uploadPermissions.validation).css('visibility', 'visible');
                        } else {
                            $('#upload-grant-warning').html('').css('visibility', 'hidden');
                            uploadPermissions.add(grant);
                        }
                    }
                },
                {label: 'Cancel', close: true}
            ],
            afterShow: function() {
                renderForm(prefill || {});
            }
        });
    };

    uploadPermissions.getAddButton = function() {
        return spawn('button.btn.btn-sm.edit', {
            onclick: function(e) {
                e.preventDefault();
                uploadPermissions.addModal({});
            },
            title: 'Grant a Globus identity upload access to this project'
        }, 'Add upload user');
    };

    uploadPermissions.getRemoveButton = function(grant) {
        return spawn('button.btn.btn-sm.edit', {
            onclick: function(e) {
                e.preventDefault();
                uploadPermissions.remove(grant);
            },
            title: 'Revoke this upload permission'
        }, 'Revoke');
    };

    function identityCell(grant) {
        var label = grant.globusUsername || grant.globusIdentityId || '(unknown)';
        return spawn('span', {title: grant.globusIdentityId || '', html: label});
    }

    uploadPermissions.refreshTable = function() {
        if (typeof uploadPermissions.$table != 'undefined') {
            uploadPermissions.$table.remove();
        }
        $('div#xsync-globus-upload-table').prepend(uploadPermissions.table());
    };

    uploadPermissions.table = function() {
        uploadPermissions.getGrants();
        var tableData = [];
        (uploadPermissions.grants || []).forEach(function(grant) {
            tableData.push({
                identity: grant,
                xnatUsername: grant.xnatUsername || '',
                state: grant.state || '',
                actions: grant
            });
        });

        var columns = {
            identity: {
                label: 'Globus identity',
                sortable: true,
                td: {style: {verticalAlign: 'middle'}},
                apply: function(grant) { return identityCell(grant); }
            },
            xnatUsername: {
                label: 'XNAT user',
                sortable: true,
                td: {style: {verticalAlign: 'middle'}},
                apply: function(value) { return spawn('span', {html: value}); }
            },
            state: {
                label: 'State',
                sortable: true,
                td: {style: {verticalAlign: 'middle'}},
                apply: function(value) { return stateBadge(value); }
            },
            actions: {
                label: 'Actions',
                td: {style: {verticalAlign: 'middle', width: '160px'}},
                apply: function(grant) {
                    // Orphaned permissions aren't managed by XNAT; shown read-only for now.
                    if (grant.state === 'ORPHANED') {
                        return spawn('span', {
                            style: {color: '#777', fontStyle: 'italic'},
                            title: 'This Globus permission was created outside XNAT; manage or remove it in Globus.'
                        }, 'not managed here');
                    }
                    return [uploadPermissions.getRemoveButton(grant)];
                }
            }
        };

        var table = XNAT.table.dataTable(tableData, {
            header: true,
            sortable: 'identity, xnatUsername, state',
            height: 'auto',
            table: {
                className: 'upload-grant-table xnat-table selectable',
                style: {width: '100%', marginTop: '15px', marginBottom: '15px', border: '1px solid #aaa'}
            },
            columns: columns
        });

        uploadPermissions.$table = $(table.table);
        return table.table;
    };

    uploadPermissions.init = function() {
        var $div = $('div#xsync-globus-upload-table');
        $div.empty().append(uploadPermissions.table());
        $div.append(uploadPermissions.getAddButton());
    };

    $(document).ready(function() {
        if ($('div#xsync-globus-upload-table').length) {
            uploadPermissions.init();
        }
    });

}));
