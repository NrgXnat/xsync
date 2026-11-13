/*
 * web: xsyncSyncPeerGrantManager.js
 * XNAT http://www.xnat.org
 * Copyright (c) 2005-2026, Washington University School of Medicine
 * All Rights Reserved
 *
 * Released under the Simplified BSD.
 */

/*!
 * Site-level "Globus Sync Inbox Access" admin panel: list, add, and remove the
 * peer nodes allowed to send XARs to this node's Globus sync inbox. Each entry
 * grants a peer's Globus client write access to its own subpath. ACL changes run
 * server-side under the node service account.
 */

console.log('xsyncSyncPeerGrantManager.js');

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
    var apiBase = '/xapi/xsync/globus/sync-inbox-grants';

    var syncPeerGrants;
    XNAT.plugin.xsync.syncPeerGrants = syncPeerGrants = getObject(XNAT.plugin.xsync.syncPeerGrants || {});

    syncPeerGrants.grants = [];
    syncPeerGrants.validation = '';

    var STATE_COLORS = {
        ACTIVE: '#3f7f3f',
        PENDING: '#b8860b',
        ORPHANED: '#8a6d3b'
    };

    function stateTooltip(state) {
        switch (state) {
            case 'ACTIVE':   return 'The peer client has write access to its sync-inbox subpath.';
            case 'PENDING':  return 'Recorded here, but the Globus permission is missing. The peer cannot send '
                                  + 'until it is restored (remove and re-add, or check Globus).';
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

    syncPeerGrants.getGrants = function() {
        XNAT.xhr.get({
            url: restUrl(apiBase),
            async: false,
            success: function(data) {
                syncPeerGrants.grants = data || [];
            },
            fail: function(e) {
                XNAT.ui.banner.top(3000, 'Could not retrieve Globus sync-inbox grants: ' + e.responseText, 'error');
            }
        });
    };

    syncPeerGrants.add = function(grant) {
        XNAT.xhr.post({
            url: restUrl(apiBase),
            async: false,
            contentType: 'application/json',
            data: JSON.stringify(grant),
            success: function() {
                syncPeerGrants.refreshTable();
                XNAT.ui.banner.top(2000, 'Sync-inbox access granted.', 'success');
                XNAT.ui.dialog.closeAll();
            },
            fail: function(e) {
                XNAT.ui.banner.top(4000, 'Could not grant sync-inbox access: ' + e.responseText, 'error');
            }
        });
    };

    syncPeerGrants.remove = function(grant) {
        xmodal.open({
            title: 'Revoke sync-inbox access',
            content: 'Revoke Globus sync-inbox access for peer "' + (grant.peerLabel || grant.peerClientId) + '"?',
            width: 450,
            height: 200,
            overflow: 'auto',
            buttons: {
                ok: {
                    label: 'Revoke',
                    isDefault: true,
                    action: function() {
                        XNAT.xhr.delete({
                            url: restUrl(apiBase + '/' + encodeURIComponent(grant.peerClientId)),
                            async: false,
                            success: function() {
                                xmodal.closeAll();
                                XNAT.ui.banner.top(2000, 'Sync-inbox access revoked.', 'success');
                                syncPeerGrants.refreshTable();
                            },
                            fail: function(e) {
                                XNAT.ui.banner.top(3000, 'Could not revoke access: ' + e.responseText, 'error');
                            }
                        });
                    }
                },
                close: {label: 'Cancel'}
            }
        });
    };

    syncPeerGrants.validate = function(element, name) {
        var value = element.val();
        if (value == null || value.trim() === '') {
            syncPeerGrants.validation += name + ' is required.<br/>';
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
                addClass: 'sync_peer_input',
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

    function renderForm(prefill) {
        var container = document.getElementById('sync-peer-grant-details-body');
        container.innerHTML = '';
        var form = document.createElement('form');
        form.id = 'sync-peer-grant-form';
        form.appendChild(spawn('div|class="warning"', {style: {visibility: 'hidden'}, id: 'sync-peer-grant-warning'}));
        form.appendChild(inputRow('Peer label', 'sync_peer_label_input', prefill.peerLabel,
            'e.g. site1', 'The peer\'s name; also its inbox subpath segment.'));
        form.appendChild(inputRow('Peer client ID', 'sync_peer_client_input', prefill.peerClientId,
            'Globus client UUID', 'The peer node\'s Globus confidential-client id.'));
        container.appendChild(form);
    }

    syncPeerGrants.addModal = function(prefill) {
        var tmpl = spawn('div#sync-peer-grant-details', [
            spawn('div#sync-peer-grant-details-body', ['Loading...'])
        ]);
        XNAT.ui.dialog.open({
            title: 'Add sync peer',
            width: 560,
            content: tmpl,
            isDraggable: true,
            mask: false,
            esc: true,
            buttons: [
                {
                    label: 'Grant access',
                    isDefault: true,
                    close: false,
                    action: function() {
                        syncPeerGrants.validation = '';
                        var grant = {
                            peerLabel: syncPeerGrants.validate($('#sync_peer_label_input'), 'Peer label'),
                            peerClientId: syncPeerGrants.validate($('#sync_peer_client_input'), 'Peer client ID')
                        };
                        if (syncPeerGrants.validation !== '') {
                            $('#sync-peer-grant-warning').html(syncPeerGrants.validation).css('visibility', 'visible');
                        } else {
                            $('#sync-peer-grant-warning').html('').css('visibility', 'hidden');
                            syncPeerGrants.add(grant);
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

    syncPeerGrants.getAddButton = function() {
        return spawn('button.btn.btn-sm.edit', {
            onclick: function(e) {
                e.preventDefault();
                syncPeerGrants.addModal({});
            },
            title: 'Grant a peer client write access to this node\'s sync inbox'
        }, 'Add sync peer');
    };

    syncPeerGrants.getRemoveButton = function(grant) {
        return spawn('button.btn.btn-sm.edit', {
            onclick: function(e) {
                e.preventDefault();
                syncPeerGrants.remove(grant);
            },
            title: 'Revoke this peer\'s sync-inbox access'
        }, 'Revoke');
    };

    syncPeerGrants.refreshTable = function() {
        if (typeof syncPeerGrants.$table != 'undefined') {
            syncPeerGrants.$table.remove();
        }
        $('div#xsync-sync-inbox-grants-table').prepend(syncPeerGrants.table());
    };

    syncPeerGrants.table = function() {
        syncPeerGrants.getGrants();
        var tableData = [];
        (syncPeerGrants.grants || []).forEach(function(grant) {
            tableData.push({
                peer: grant.peerLabel || '',
                client: grant.peerClientId || '',
                state: grant.state || '',
                actions: grant
            });
        });

        var columns = {
            peer: {
                label: 'Peer',
                sortable: true,
                td: {style: {verticalAlign: 'middle'}},
                apply: function(value) { return spawn('span', {}, value); }
            },
            client: {
                label: 'Client ID',
                sortable: true,
                td: {style: {verticalAlign: 'middle'}},
                apply: function(value) { return spawn('span', {title: value}, value); }
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
                    // Orphaned permissions aren't managed by XNAT; shown read-only.
                    if (grant.state === 'ORPHANED') {
                        return spawn('span', {
                            style: {color: '#777', fontStyle: 'italic'},
                            title: 'This Globus permission was created outside XNAT; manage or remove it in Globus.'
                        }, 'not managed here');
                    }
                    return [syncPeerGrants.getRemoveButton(grant)];
                }
            }
        };

        var table = XNAT.table.dataTable(tableData, {
            header: true,
            sortable: 'peer, client, state',
            height: 'auto',
            table: {
                className: 'sync-peer-grant-table xnat-table selectable',
                style: {width: '100%', marginTop: '15px', marginBottom: '15px', border: '1px solid #aaa'}
            },
            columns: columns
        });

        syncPeerGrants.$table = $(table.table);
        return table.table;
    };

    syncPeerGrants.init = function() {
        var $div = $('div#xsync-sync-inbox-grants-table');
        $div.empty().append(syncPeerGrants.table());
        $div.append(syncPeerGrants.getAddButton());
    };

    $(document).ready(function() {
        if ($('div#xsync-sync-inbox-grants-table').length) {
            syncPeerGrants.init();
        }
    });

}));
