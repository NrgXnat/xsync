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

    $('#aspera-panel').find('.btn.save').click(function(){
        xsyncProjectPreferenceManager.submitSettings();
    });

    xsyncProjectPreferenceManager.toggleEnabledElements = function(enabled) {
        var $active_pane = $('#aspera-panel');

        if (enabled === 'true') {
            $active_pane.find("input[type='text']").parent().parent().removeClass('hidden');
        } else {
            $active_pane.find("input[type='text']").parent().parent().addClass('hidden')
        }
    }

//    xsyncProjectPreferenceManager.validateSingleField = function(inputField, required, integer) {
//        let elementValue = element.val();
//
//        let valid = true
//        if (required) {
//            if (elementValue.trim()===''){
//                XNAT.app.context.validation+=name + " is a required field.</br>";
//                valid = false;
//            }
//        }
//
//        if (integer) {
//            var temp = elementValue.trim().replace(/\s+/g, "");
//            if (temp !== "" && !Number.isInteger(Number(temp))) {
//                XNAT.app.context.validation += name + " must be a valid integer.</br>";
//                valid = false;
//            }
//        }
//
//        if (elementType === "input") {
//            if (valid){
//                element.css("border-color", "");
//            } else{
//                element.css("border-color", "red");
//            }
//        }
//
//        return elementValue;
//    }

//    xsyncProjectPreferenceManager.validateNecessaryFields = function() {
//        node_url = xsyncProjectPreferenceManager.validateSingleField($('#aspera-node-url'), true, false);
//        node_url = xsyncProjectPreferenceManager.validateSingleField($('#aspera-node-user'), true, false);
//        node_url = xsyncProjectPreferenceManager.validateSingleField($('#aspera-node-url'), true, false);
//        node_url = xsyncProjectPreferenceManager.validateSingleField($('#aspera-node-url'), true, false);
//    }

    xsyncProjectPreferenceManager.submitSettings = function() {
//        let isValidPreferences = xsyncProjectPreferenceManager.validateNecessaryFields();
    }

    xsyncProjectPreferenceManager.init = function() {
        XNAT.xhr.get({
            url: restUrl('/xapi/xsyncProjectPreferences/project/' + XNAT.data.context.project + '/asperaEnabled/'),
            async: false,
            success: function (data) {
                let enabled = data;
                xsyncProjectPreferenceManager.isAsperaEnabled = true;
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