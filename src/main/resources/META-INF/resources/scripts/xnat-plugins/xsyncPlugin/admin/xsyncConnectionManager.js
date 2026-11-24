/*
 * web: xsyncConnectionManager.js
 * XNAT http://www.xnat.org
 * Copyright (c) 2005-2017, Washington University School of Medicine and Howard Hughes Medical Institute
 * All Rights Reserved
 *
 * Released under the Simplified BSD.
 */

/*!
 * Manage the backend connection by which Xsync transfers will be made.
 */

console.log('xsyncConnectionManager.js');

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
    var xsyncConnectionManager;
    XNAT.plugin.xsync.xsyncConnectionManager = xsyncConnectionManager = getObject(XNAT.plugin.xsync.xsyncConnectionManager || {});

    // The panel is data-driven: it fetches the registered transports and renders a
    // switchbox per transport, each wired to the site-wide enablement gate the
    // resolver consults (.../xsyncSitePreferences/transportEnabled/{id}). A new
    // transport plugin gets its toggle automatically, with no change here. The
    // baseline transport (HTTPS) is rendered checked and disabled.
    var TRANSPORTS_URL = '/xapi/xsyncSitePreferences/transports';
    var ASPERA_TRANSPORT_ID = 'aspera';

    function transportEnabledUrl(transportId) {
        return '/xapi/xsyncSitePreferences/transportEnabled/' + transportId;
    }

    // Aspera-specific: the Aspera Server Defaults tab is only relevant when Aspera
    // is enabled, so it is revealed/hidden with the Aspera toggle.
    xsyncConnectionManager.setAsperaDefaultsVisibility = function(enabled) {
        if (enabled) {
            $("a[title='Aspera Server Defaults']").parent().removeClass('hidden');
        } else {
            $("a[title='Aspera Server Defaults']").parent().addClass('hidden');
        }
    }

    xsyncConnectionManager.setTransportEnabled = function(transportId, enabled) {
        XNAT.xhr.put({
            url: restUrl(transportEnabledUrl(transportId)),
            async: false,
            contentType: 'application/json',
            data: JSON.stringify(enabled),
            success: function () {
                XNAT.ui.banner.top(2000, 'Updated ' + transportId + ' enabled preference.', 'success');
            },
            fail: function (e) {
                XNAT.ui.banner.top(2000, 'Could not update ' + transportId + ' enabled preference: ' + e.responseText, 'error');
            }
        });
        if (transportId === ASPERA_TRANSPORT_ID) {
            xsyncConnectionManager.setAsperaDefaultsVisibility(enabled);
        }
    }

    xsyncConnectionManager.renderTransports = function(transports) {
        var $container = $('#xsync-transports').empty();
        (transports || []).forEach(function(transport) {
            var inputId = transport.id + '-enabled';
            // $.append accepts an element or an HTML string, so this is robust to
            // what the switchbox spawner returns.
            $container.append(XNAT.ui.panel.input.switchbox({
                id: inputId,
                name: transport.id + 'Enabled',
                label: transport.displayName,
                description: transport.description,
                onText: 'Enabled',
                offText: 'Disabled'
            }));

            var $input = $('#' + inputId);
            $input.prop('checked', transport.enabled === true);
            if (transport.baseline) {
                // The baseline transport is always on and cannot be disabled.
                $input.prop('disabled', true);
            } else {
                $input.on('change', function() {
                    xsyncConnectionManager.setTransportEnabled(transport.id, this.checked);
                });
            }

            if (transport.id === ASPERA_TRANSPORT_ID) {
                xsyncConnectionManager.setAsperaDefaultsVisibility(transport.enabled === true);
            }
        });
    }

    xsyncConnectionManager.init = function() {
        XNAT.xhr.get({
            url: restUrl(TRANSPORTS_URL),
            async: false,
            success: function (transports) {
                xsyncConnectionManager.renderTransports(transports);
            },
            fail: function (e) {
                XNAT.ui.banner.top(2000, 'Could not retrieve connection information: ' + e.responseText, 'error');
            }
        });
    }

     $(document).ready(function () {
        xsyncConnectionManager.init();
    })

}));