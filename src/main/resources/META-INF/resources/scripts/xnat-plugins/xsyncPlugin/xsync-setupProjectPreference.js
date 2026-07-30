console.log('xsync-setupProjectPreference.js');

var XNAT = getObject(XNAT || {});
XNAT.plugin = getObject(XNAT.plugin || {});
XNAT.plugin.xsync = getObject(XNAT.plugin.xsync || {});

(function(factory) {
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

    var xsyncProjectPreferenceManager;
    XNAT.plugin.xsync.xsyncProjectPreferenceManager = xsyncProjectPreferenceManager = getObject(XNAT.plugin.xsync.xsyncProjectPreferenceManager || {});

    $(document).on('change','#aspera-enabled', function(){
        xsyncProjectPreferenceManager.toggleEnabledElements($(this).val());
    });

    xsyncProjectPreferenceManager.toggleEnabledElements = function(enabled) {
        var $active_pane = $('#aspera-panel');

        if (enabled === 'true') {
            $active_pane.find("input[type='text']").parent().parent().removeClass('hidden');
        } else {
            $active_pane.find("input[type='text']").parent().parent().addClass('hidden')
        }
    }

    xsyncProjectPreferenceManager.init = function() {
        XNAT.xhr.get({
            url: restUrl('/xapi/xsyncSitePreferences/asperaEnabled/'),
            async: false,
            success: function (data) {
                xsyncProjectPreferenceManager.siteWideAsperaEnabled = data;
            },
            fail: function (e) {
                XNAT.ui.banner.top(2000, 'Could not retrieve aspera information: ' + e.responseText, 'error');
            }
        });
        XNAT.xhr.get({
            url: restUrl('/xapi/xsyncSitePreferences/blacklistProjects/' + XNAT.data.context.project),
            async: false,
            success: function (data) {
                xsyncProjectPreferenceManager.isOnBlacklist = data;
            },
            fail: function (e) {
                XNAT.ui.banner.top(2000, 'Could not retrieve aspera information: ' + e.responseText, 'error');
            }
        });
        if (xsyncProjectPreferenceManager.siteWideAsperaEnabled == true && xsyncProjectPreferenceManager.isOnBlacklist === false) {
            $("#xsync-config").removeClass('hidden');
        } else {
            $("#xsync-config").addClass('hidden');
        }
        XNAT.xhr.get({
            url: restUrl('/xapi/xsyncProjectPreferences/project/' + XNAT.data.context.project + '/asperaEnabled/'),
            async: false,
            success: function (data) {
                let enabled = data;
                if (enabled == true) {
                    $('#aspera-panel').find('.switchbox.panel-switchbox').children("input").prop("checked",true);
                }
                xsyncProjectPreferenceManager.toggleEnabledElements($('#aspera-enabled').val());
            },
            fail: function (e) {
                XNAT.ui.banner.top(2000, 'Could not retrieve aspera information: ' + e.responseText, 'error');
            }
        });
        xsyncProjectPreferenceManager.toggleEnabledElements($('#aspera-enabled').val());
    }

    $(document).ready(function () {
        xsyncProjectPreferenceManager.init();
    })

}));