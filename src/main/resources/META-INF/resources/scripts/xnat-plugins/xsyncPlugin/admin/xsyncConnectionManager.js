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

    $(document).on('change','#https-enabled', function(){
        xsyncConnectionManager.toggleHttpsEnabled($(this).val());
    });

    $(document).on('change','#aspera-enabled', function(){
        xsyncConnectionManager.toggleAsperaEnabled($(this).val());
    });

    xsyncConnectionManager.toggleHttpsEnabled = function(enabled) {
        let inputPrefs = {};
        if (enabled === "true") {
            inputPrefs['httpsEnabled'] = true;
        } else {
            inputPrefs['httpsEnabled'] = false;
        }

        xsyncConnectionManager.postSitePreferencesUpdate(inputPrefs, 'https');
    }

    xsyncConnectionManager.toggleAsperaEnabled = function(enabled) {
        let inputPrefs = {};
        if (enabled === "true") {
            inputPrefs['asperaEnabled'] = true;
        } else {
            inputPrefs['asperaEnabled'] = false;
            inputPrefs['httpsEnabled'] = true;
        }

        xsyncConnectionManager.postSitePreferencesUpdate(inputPrefs, 'aspera');
        if ($('#aspera-enabled').val() === 'false') {
            $("#https-enabled").parent().parent().parent().parent().addClass('disabled');
            $("#https-enabled").prop('disabled', true);
        } else {
            $("#https-enabled").parent().parent().parent().parent().removeClass('disabled');
            $("#https-enabled").prop('disabled', false);
        }
    }

    xsyncConnectionManager.postSitePreferencesUpdate = function(inputPrefs, preferenceName) {
        XNAT.xhr.post({
            url: restUrl('/xapi/xsyncSitePreferences/'),
            async: false,
            contentType: 'application/json',
            data: JSON.stringify(inputPrefs),
            success: function () {
                console.log('Updated aspera ' + inputPrefs + ' preference.');
            },
            fail: function (e) {
                XNAT.ui.banner.top(2000, 'Could not update ' + inputPrefs + ' enabled preference: ' +  e.responseText, 'error');
            }
        });
    }

    xsyncConnectionManager.init = function() {
        XNAT.xhr.get({
            url: restUrl('/xapi/xsyncSitePreferences/'),
            async: false,
            success: function (data) {
                let httpsEnabled = data['httpsEnabled'];
                let asperaEnabled = data['asperaEnabled'];
                if (httpsEnabled == true) {
                    $('#https-enabled').prop("checked",true);
                }
                if (asperaEnabled == true) {
                    $('#aspera-enabled').prop("checked",true);
                } else {
                    $("#https-enabled").parent().parent().parent().parent().addClass('disabled');
                    $("#https-enabled").prop('disabled', true);
                }
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