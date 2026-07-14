/*
 * web: xsyncWhitelistManager.js
 * XNAT http://www.xnat.org
 * Copyright (c) 2005-2017, Washington University School of Medicine and Howard Hughes Medical Institute
 * All Rights Reserved
 *
 * Released under the Simplified BSD.
 */

/*!
 * Manage optional whitelist of accepted XNAT sites for Xsync transfers
 */

console.log('xsyncWhitelistManager.js');

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
    var xsyncWhitelistManager;
    XNAT.plugin.xsync.xsyncWhitelistManager = xsyncWhitelistManager = getObject(XNAT.plugin.xsync.xsyncWhitelistManager || {});

    xsyncWhitelistManager.isWhitelistEnabledBackend = false;

    xsyncWhitelistManager.toggleWhitelistSwitch = function(enabled) {
        let $whitelistTableDiv = $('div#xsync-whitelist-table');
        let inputPrefs = {};
        if (enabled === "true") {
            inputPrefs['xsyncWhitelistEnabled'] = true;
            $whitelistTableDiv.empty().append(xsyncWhitelistManager.table());
            $whitelistTableDiv.append(xsyncWhitelistManager.getAddButton());
        } else {
            inputPrefs['xsyncWhitelistEnabled'] = false;
            $whitelistTableDiv.empty();
        }

        //checking this saved field should avoid an unnecessary API call upon page load
        if (inputPrefs['xsyncWhitelistEnabled'] != xsyncWhitelistManager.isWhitelistEnabledBackend) {
            XNAT.xhr.post({
                url: restUrl('/xapi/xsyncSitePreferences/'),
                async: false,
                contentType: 'application/json',
                data: JSON.stringify(inputPrefs),
                success: function () {
                    xsyncWhitelistManager.isWhitelistEnabledBackend = inputPrefs['xsyncWhitelistEnabled'];
                    console.log('Updated site enabled preference.');
                },
                fail: function (e) {
                    XNAT.ui.banner.top(2000, 'Could not update site enabled preference: ' +  e.responseText, 'error');
                }
            });
        }
    }

    xsyncWhitelistManager.getWhitelistedSites = function() {
        XNAT.xhr.get({
            url: restUrl('/xapi/xsyncSitePreferences/whitelistSites/'),
            async: false,
            success: function (data) {
                xsyncWhitelistManager.sites = []
                data.forEach(function (item) {
                    xsyncWhitelistManager.sites.push(item);
                });
            },
            fail: function (e) {
                XNAT.ui.banner.top(2000, 'Could not retrieve whitelist information: ' + e.responseText, 'error');
            }
        });
    }

    xsyncWhitelistManager.createOrUpdateSiteDescription = function(whitelistSiteDetails, createOrUpdate) {
        XNAT.xhr.post({
            url: restUrl('/xapi/xsyncSitePreferences/whitelistSites/add'),
            async: false,
            contentType: 'application/json',
            data: JSON.stringify(whitelistSiteDetails),
            success: function (data) {
                xsyncWhitelistManager.refreshTable();
                XNAT.ui.banner.top(2000, "Site " + createOrUpdate + " successfully", 'success');
                XNAT.ui.dialog.closeAll();

            },
            fail: function (e) {
                XNAT.ui.banner.top(2000, 'Could not ' + createOrUpdate + ' site information.' + e.responseText, 'error');
            }
        });
    }

    function createInputElement(label, id, value, inputType, tooltipTitle) {
        let elements = [
            spawn('label.element-label|class=xsync_whitelist', label),
            spawn('input', {
                addClass: 'whitelist_input required',
                type: inputType,
                id: id,
                value: value ?? ''
            })
        ];
        if (tooltipTitle) {
            elements.push(spawn('a.infoLink#render-resources-info', {
                title: tooltipTitle,
                html: '<i class="fa fa-question-circle"></i>',
                style: {position: 'relative', top: '0px', left: '5px'},
            }));
        }
        return spawn('div.element-wrapper', elements);
    }

    function renderSiteForm(item) {
        const container = document.getElementById('whitelist-site-details-body');
        container.innerHTML = '';
        const form = document.createElement('form');
        form.id = 'form';

        form.appendChild(spawn('div|class="warning"',{'style': {visibility: 'hidden'}, 'id': 'warning'}));
        form.appendChild(createInputElement('Site Id', 'site_id_input', item.siteId, 'text', 'The unique identifier for the site.'));
        form.appendChild(createInputElement('Site Name', 'site_name_input', item.siteName, 'text', 'The name given to the site so that it is clearly identifiable to users.'));
        form.appendChild(createInputElement('Site Url', 'site_url_input', item.siteUrl, 'text', 'The url of the site.'));
        let options = ['CLINICAL', 'RESEARCH', 'PUBLIC']
        let elements = [
            XNAT.ui.panel.select.menu({
                addClass: 'classification',
                id: 'classification_input',
                label: 'Classification',
                options: {
                    opt1: { label: 'CLINICAL',   value: 'CLINICAL' },
                    opt2: { label: 'RESEARCH',   value: 'RESEARCH' },
                    opt3: { label: 'PUBLIC', value: 'PUBLIC' }
                }
            })
        ];
        elements.push(spawn('a.infoLink#classification-tooltip', {
            title: 'The site\'s level of security.',
            html: '<i class="fa fa-question-circle"></i>',
            style: {position: 'relative', top: '0px', left: '5px'},
        }));
        form.appendChild(spawn('div.classification-element-wrapper', elements));
        if (item.classification) {
            $('#classification_input').val(item.classification);
        }

        container.appendChild(form);
    }

    xsyncWhitelistManager.validate = function(element, name, validators){
        elementValue = element.val();
        var valid = true;
        if(textContains(validators,"required")){
            if(elementValue.trim()===''){
                xsyncWhitelistManager.validation+=name + " is a required field.</br>";
                valid=false;
            }
        }

        if(valid){
            element.css("border-color", "");
        }else{
            element.css("border-color", "red");
        }

        return elementValue;
    }

    xsyncWhitelistManager.addOrEditModal = function(whitelistSite, createOrUpdate) {
        const tmpl = spawn('div#whitelist-site-details', [
            spawn('div#whitelist-site-details-body', [
                "Loading..."
                ])
        ]);

        let title = ''
        if (createOrUpdate === 'create') {
            title = 'Add New Site Details';
        } else {
            title = 'Edit Site Details';
        }

        this.dialog=XNAT.ui.dialog.open({
            title: title,
            width: 720,
            content:tmpl,
            isDraggable: true,
            mask: false,
            esc: true,
            buttons: [
                {
                    label: 'Save and Close',
                    isDefault: true,
                    close: false,
                    action: function() {
                        xsyncWhitelistManager.validation="";
                        if (createOrUpdate === 'create') {
                            whitelistSite['siteId'] = xsyncWhitelistManager.validate($('#site_id_input'), 'Site Name', 'required');
                        }
                        whitelistSite['siteName'] = xsyncWhitelistManager.validate($('#site_name_input'), 'Site Name', 'required');
                        whitelistSite['siteUrl'] = xsyncWhitelistManager.validate($('#site_url_input'), 'Site URL', 'required');
                        whitelistSite['classification'] = $('#classification_input').val();
                        if (xsyncWhitelistManager.validation!="") {
                            $("#warning").html(xsyncWhitelistManager.validation);
                            $("#warning").css('visibility','visible');
                            XNAT.ui.banner.top(2000, 'Failed to update whitelist.', 'error');
                        } else {
                            $("#warning").html("");
                            $("#warning").css('visibility','hidden');
                            xsyncWhitelistManager.createOrUpdateSiteDescription(whitelistSite, createOrUpdate);
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
                xsyncWhitelistManager.currentWhitelistSite = whitelistSite;
                renderSiteForm(whitelistSite);
                if (createOrUpdate != 'create') {
                    document.getElementById("site_id_input").readOnly = true;
                }
            }
        });
    }

    xsyncWhitelistManager.deleteWhitelistElement = function(whitelistSite) {
        xmodal.open({
            title: 'Confirm Listing Deletion',
            content: 'Are you sure you want to delete this listing?',
            width: 400,
            height: 200,
            overflow: 'auto',
            buttons: {
                ok: {
                    label: 'Proceed',
                    isDefault: true,
                    action: function () {
                        XNAT.xhr.delete({
                            url: restUrl('/xapi/xsyncSitePreferences/whitelistSites/delete'),
                            async: false,
                            contentType: 'application/json',
                            data: JSON.stringify(whitelistSite),
                            success: function () {
                                xmodal.closeAll();
                                XNAT.ui.banner.top(2000, "Listing deleted", 'success');
                                xsyncWhitelistManager.refreshTable();
                            },
                            fail: function (e) {
                                XNAT.ui.banner.top(2000, 'Could not delete the listing element: ' + e.responseText, 'error');
                            }
                        });
                    }
                },
                close: {
                    label: 'Cancel'
                }
            }
        });
    }

    xsyncWhitelistManager.getAddButton = function() {
        return spawn('button.btn.btn-sm.edit', {
            onclick: function (e) {
                e.preventDefault();
                whitelistSite = {};
                xsyncWhitelistManager.addOrEditModal(whitelistSite, 'create');
            },
            title: "Add new whitelist site"
        }, 'Add Site');
    }

    xsyncWhitelistManager.getEditButton = function(whitelistSite) {
        return spawn('button.btn.btn-sm.edit', {
            onclick: function (e) {
                e.preventDefault();
                xsyncWhitelistManager.addOrEditModal(whitelistSite, 'update');
            },
            title: "Edit site information"
        }, 'Edit');
    }

    xsyncWhitelistManager.getDeleteButton = function(whitelistSite) {
        return spawn('button.btn.btn-sm.edit', {
            onclick: function (e) {
                e.preventDefault();
                xsyncWhitelistManager.deleteWhitelistElement(whitelistSite);
            },
            title: "Delete site listing"
        }, 'Delete');
    }

    xsyncWhitelistManager.refreshTable = function() {
        if (typeof xsyncWhitelistManager.$table != 'undefined') {
            xsyncWhitelistManager.$table.remove();
        }
        let $whitelistTableDiv = $('div#xsync-whitelist-table');
        $whitelistTableDiv.prepend(xsyncWhitelistManager.table());
    };

    xsyncWhitelistManager.table = function() {
        xsyncWhitelistManager.getWhitelistedSites();
        let tableData = [];
        let sites = xsyncWhitelistManager.sites;
        DATA_FIELDS = "id, siteName, siteUrl, classification"

        for (let k = 0; k < sites.length; k++) {
            let site = sites[k];
            let tableDataRow = {};
            tableDataRow['id'] = site['siteId'];
            tableDataRow['siteName'] = site['siteName'];
            tableDataRow['siteUrl'] = site['siteUrl'];
            tableDataRow['classification'] = site['classification'];
            tableDataRow['actions'] = site;
            tableData.push(tableDataRow);
        }

        let columnsInTable = {
            id: {
                label: 'Site ID',
                sortable: true,
                td: {
                    style: {
                        verticalAlign: 'middle'
                    }
                },
                apply: function (id) {
                    return spawn('span', {
                        title: id,
                        html: id
                    });
                }
            },
            siteName: {
                label: 'Site Name',
                sortable: true,
                td: {
                    style: {
                        verticalAlign: 'middle'
                    }
                },
                apply: function (siteName) {
                    return spawn('span', {
                        title: siteName,
                        html: siteName
                    });
                }
            },
            siteUrl: {
                label: 'Site URL',
                sortable: true,
                td: {
                    style: {
                        verticalAlign: 'middle'
                    }
                },
                apply: function (siteUrl) {
                    return spawn('span', {
                        title: siteUrl,
                        html: siteUrl
                    });
                }
            },
            classification: {
                label: 'Classification',
                sortable: true,
                td: {
                    style: {
                        verticalAlign: 'middle'
                    }
                },
                apply: function (classification) {
                    return spawn('span', {
                        title: classification,
                        html: classification
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
                    return [xsyncWhitelistManager.getEditButton(actions), spacer(4), xsyncWhitelistManager.getDeleteButton(actions)]
                }
            }
        };

        let whitelistSiteTable = XNAT.table.dataTable(tableData, {
            header: true,
            sortable: DATA_FIELDS,
            filter: DATA_FIELDS,
            height: 'auto',
            table: {
                className: 'whitelist-site-table xnat-table selectable',
                style: {
                    width: '100%',
                    marginTop: '15px',
                    marginBottom: '15px',
                    border: '1px solid #aaa'
                }
            },
            columns: columnsInTable
        });

        xsyncWhitelistManager.$table = $(whitelistSiteTable.table);
        return whitelistSiteTable.table;
    }

    $(document).on('change','#limit-to-whitelist', function(){
        xsyncWhitelistManager.toggleWhitelistSwitch($(this).val());
    });

    xsyncWhitelistManager.init = function() {
        XNAT.xhr.get({
            url: restUrl('/xapi/xsyncSitePreferences/'),
            async: false,
            success: function (data) {
                let enabled = data['xsyncWhitelistEnabled'];
                xsyncWhitelistManager.isWhitelistEnabledBackend = true;
                if (enabled == true) {
                    $('#whitelist-site-panel').find('.switchbox.panel-switchbox').children("input").prop("checked",true);
                    xsyncWhitelistManager.toggleWhitelistSwitch("true");
                } else {
                    xsyncWhitelistManager.toggleWhitelistSwitch("false");
                }
            },
            fail: function (e) {
                XNAT.ui.banner.top(2000, 'Could not retrieve whitelist information: ' + e.responseText, 'error');
            }
        });
    }

    $(document).ready(function () {
        xsyncWhitelistManager.init();
    })

}));

function spacer(width){
    return spawn('i.spacer', {
        style: {
            display: 'inline-block',
            width: width + 'px'
        }
    })
}

function textContains(string, substring){
    return (string.indexOf(substring) !== -1);
}