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

    // The Aspera toggle drives the site-wide transport enablement gate the resolver
    // consults: .../xsyncSitePreferences/transportEnabled/aspera. HTTPS is the always-on
    // baseline and has no toggle.
    var ASPERA_TRANSPORT_URL = '/xapi/xsyncSitePreferences/transportEnabled/aspera';

    $(document).on('change','#aspera-enabled', function(){
        xsyncConnectionManager.toggleAsperaEnabled($(this).val() === 'true');
    });

    xsyncConnectionManager.toggleAsperaEnabled = function(enabled) {
        XNAT.xhr.put({
            url: restUrl(ASPERA_TRANSPORT_URL),
            async: false,
            contentType: 'application/json',
            data: JSON.stringify(enabled),
            success: function () {
                XNAT.ui.banner.top(2000, 'Updated Aspera enabled preference.', 'success');
            },
            fail: function (e) {
                XNAT.ui.banner.top(2000, 'Could not update Aspera enabled preference: ' +  e.responseText, 'error');
            }
        });
        xsyncConnectionManager.setAsperaDefaultsVisibility(enabled);
    }

    xsyncConnectionManager.setAsperaDefaultsVisibility = function(enabled) {
        if (enabled) {
            $("a[title='Aspera Server Defaults']").parent().removeClass('hidden');
        } else {
            $("a[title='Aspera Server Defaults']").parent().addClass('hidden');
        }
    }

    xsyncConnectionManager.init = function() {
        XNAT.xhr.get({
            url: restUrl(ASPERA_TRANSPORT_URL),
            async: false,
            success: function (asperaEnabled) {
                $('#aspera-enabled').prop("checked", asperaEnabled === true);
                xsyncConnectionManager.setAsperaDefaultsVisibility(asperaEnabled === true);
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