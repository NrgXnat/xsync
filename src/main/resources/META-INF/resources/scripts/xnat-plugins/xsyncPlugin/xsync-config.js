if (typeof XSYNC === 'undefined') {
    XSYNC = {};
}
if (typeof XSYNC.xsyncconfig === 'undefined') {
    XSYNC.xsyncconfig = {};
    XSYNC.xsyncconfig.firsttime = false;
}
if (typeof XSYNC.credentialsconfig === 'undefined') {
    XSYNC.credentialsconfig = {};
}

(function(){

// Merge any existing settings and change any modified values
// without destroying saved 'advanced' settings.
    XSYNC.xsyncconfig.mergeConfig = function(data){
        return XSYNC.xsyncconfig.configuration = extend(true, XSYNC.xsyncconfig.configuration || {}, data);
    };


    /*
     Initialization
     */

    XSYNC.xsyncconfig.init = function(){
        XNAT.xhr.getJSON({
            url: XNAT.url.restUrl('/xapi/xsync/setup/projects/' + XNAT.data.context.project),
            success: function(data){
                XSYNC.xsyncconfig.mergeConfig(data);
                // XSYNC.xsyncconfig.configuration = data;
                XSYNC.xsyncconfig.showConfigPanel()
            },
            error: function(){
                XSYNC.xsyncconfig.initialConfig()
            }
        })
    };

    XSYNC.xsyncconfig.useDefaultConfig = function(){
        // Use the defaults to populate config dialog
        XSYNC.xsyncconfig.firsttime = true;
        XSYNC.xsyncconfig.mergeConfig({});
        // XSYNC.xsyncconfig.configuration = {};
        XSYNC.xsyncconfig.configuration.project_resources = {};
        XSYNC.xsyncconfig.configuration.subject_resources = {};
        XSYNC.xsyncconfig.configuration.subject_assessors = {};
        XSYNC.xsyncconfig.configuration.imaging_sessions = {};

        XSYNC.xsyncconfig.configuration.enabled = true;
        XSYNC.xsyncconfig.configuration.source_project_id = XNAT.data.context.project;
        XSYNC.xsyncconfig.configuration.sync_frequency = 'weekly';
        XSYNC.xsyncconfig.configuration.sync_new_only = true;
        XSYNC.xsyncconfig.configuration.identifiers = 'use_local';
        XSYNC.xsyncconfig.configuration.remote_url = 'http://';
        XSYNC.xsyncconfig.configuration.remote_project_id = '';
        XSYNC.xsyncconfig.configuration.anonymize = false;
        // XSYNC.xsyncconfig.configuration.ok_to_sync = false;

        // XSYNC.xsyncconfig.configuration.subject_assessors.xsi_types = {};
        // XSYNC.xsyncconfig.configuration.imaging_sessions.xsi_types = {};
        XSYNC.xsyncconfig.configuration.project_resources.sync_type = 'none';
        XSYNC.xsyncconfig.configuration.subject_resources.sync_type = 'none';
        XSYNC.xsyncconfig.configuration.subject_assessors.sync_type = 'none';
        XSYNC.xsyncconfig.configuration.imaging_sessions.sync_type = 'all';
        // XSYNC.xsyncconfig.configuration.imaging_sessions.xsi_types.types_list = ['xnat:mrSessionData'];
    };

    XSYNC.xsyncconfig.initialConfig = function(){
        $("#xsync-config").spawn('button#xsync-begin-config.btn1', {
            type: 'button',
            html: 'Begin Configuration',
            $: {
                click: function(){
                    XSYNC.xsyncconfig.useDefaultConfig();
                    XSYNC.xsyncconfig.editConfig();
                }
            }
        });
    };

    XSYNC.xsyncconfig.showConfigPanel = function(){

        $('#xsync-config').empty().append([
            '<button class="btn1 xsync-submit-button" id="xsync-edit-config" type="button">Edit Configuration</button>' +
            '<button class="btn1 xsync-submit-button" id="xsync-credentials" type="button">Remote Credentials</button>' +
            '<button class="btn1 xsync-submit-button" id="xsync-upload-anonymization" type="button">Configure Anonymization</button>' +
            '<h2 id="xsync-history-header" style="">Sync History</h2>' +
            '<div id="xsync-history-table" style="max-height:300px;overflow-y:auto"></div>'
        ]);

        $("#xsync-edit-config").on('click', function(){
            XSYNC.xsyncconfig.editConfig();
        });

        $("#xsync-credentials").on('click', function(){
            XSYNC.credentialsconfig.enterCredentials();
        });

        $("#xsync-upload-anonymization")
                .prop('disabled', XSYNC.xsyncconfig.configuration.anonymize === false)
                .on('click', function(){
                    XSYNC.xsyncconfig.submitDICOMAnonymization();
                });

        XSYNC.reporting.showHistoryTable();

    };


    /*
     Remote Authentication
     */


    /*
     * Get user credentials
     * @param {String} optional configuration JSON to submit if initial setup
     */

    function enterCredentials(configJson){

        var remoteProjectId =
                    XSYNC.xsyncconfig.configuration.remote_project_id ||
                    $("#xsync-config-remote-project").val() ||
                    "ERROR";

        var newOnly =
                    $("#xsync-config-newonly").val() ||
                    XSYNC.xsyncconfig.configuration.sync_new_only ||
                    true;

        var credHost =
                    XSYNC.xsyncconfig.configuration.remote_url != "http://" ?
                            XSYNC.xsyncconfig.configuration.remote_url :
                            $("#xsync-config-remote-url").val();

        var modalContent = '' +
                '<form id="xsync-remote-credentials">' +
                '<h3 class="credentials-header">Enter credentials for: <span class="remote-site">' + credHost + '</span></h3>' +
                '<input type="hidden" name="host" value="' + credHost + '">' +
                '<input type="hidden" name="method" value="GET">' +
                '<label class="credentials-input">' +
                '<b>Username: </b>' +
                '<input type="text" name="username" size="24">' +
                '</label>' +
                '<label class="credentials-input">' +
                '<b>Password: </b>' +
                '<input type="password" name="password" size="24">' +
                '</label>' +
                '</form>' +
                '';

        var pModalOpts = {
            width: 480,
            height: 360,
            id: 'xmodal-enter-credentials',
            title: "Enter remote server credentials",
            content: modalContent,
            afterShow: function(){
                this.$modal.find('#xsync-credentials-username').focus();
            },
            ok: 'show',
            okLabel: 'Continue',
            okAction: function(modl){

                var $form = modl.$modal.find('#xsync-remote-credentials');

                var credHost = $form.find('[name="host"]').val();
                var credUser = $form.find('[name="username"]').val();
                var credPassword = $form.find('[name="password"]').val();

                var tokenData = {
                    url: credHost + '/data/services/tokens/issue',
                    method: 'GET',
                    username: credUser,
                    password: credPassword
                };

                // sample tokenData JSON
                var tokenDataSample = {
                    "url": "http://10.1.100.170/data/services/tokens/issue",
                    "method": "GET",
                    "username": "bob",
                    "password": "blahblah"
                };

                var credentialsAjax = $.ajax({
                    type: 'POST',
                    url: XNAT.url.csrfUrl('/xapi/xsync/remoteREST'),
                    dataType: 'json',
                    data: JSON.stringify(tokenData),
                    processData: false,
                    contentType: 'application/json'
                });

                // sample credentials JSON
                var credentialsSample = {
                    "xdatUserId": "bob",
                    "alias": "a8174140-acb3-4b28-ba3d-006dd68be980",
                    "secret": "QuVoGllg591PTMIcYcnNoEJDcYefbfwmadlESyHFDGaec29yxpGEJb0r8cXZigRF",
                    "singleUse": false,
                    "estimatedExpirationTime": 1477178868198,
                    "enabled": true,
                    "created": 1477006068199,
                    "id": 36,
                    "disabled": 0,
                    "timestamp": 1477006068199
                };

                credentialsAjax.done(function(data){

                    if (typeof data !== 'undefined' && typeof data.secret !== 'undefined') {

                        var formData = {
                            host: credHost,
                            localProject: XNAT.data.context.project,
                            remoteProject: remoteProjectId,
                            syncNewOnly: newOnly,
                            alias: data.alias,
                            secret: data.secret,
                            username: credUser
                        };
                        try {
                            formData['estimatedExpirationTime'] = data.estimatedExpirationTime
                        }
                        catch (err) {}

                        var saveCredentials = $.ajax({
                            type: 'POST',
                            url: XNAT.url.csrfUrl('/xapi/xsync/credentials/save/projects/' + XNAT.data.context.project),
                            dataType: 'text',
                            data: JSON.stringify(formData),
                            processData: false,
                            contentType: 'text/plain'
                        });

                        saveCredentials.done(function(data, textStatus, jqXHR){
                            if (jqXHR.status == 202) {
                                xmodal.message(
                                        'Credentials saved', ' WARNING: ' + jqXHR.responseText + '\n' +
                                        'Successfully saved credentials for remote server ' +
                                        credHost
                                );
                            }
                            else {
                                if (XSYNC.xsyncconfig.firsttime === false) {
                                    xmodal.message(
                                            'Credentials saved', 'Successfully saved credentials for remote server ' +
                                            credHost
                                    );
                                }
                            }
                            modl.close();

                            // submit the config json again if this was called from submitConfig
                            if (configJson !== undefined) {
                                XSYNC.xsyncconfig.firsttime = false;
                                XSYNC.xsyncconfig.submitConfig(configJson)
                            }
                        });

                        saveCredentials.fail(function(data, textStatus, jqXHR){
                            xmodal.message(
                                    'Error', 'Could not save credentials for remote server ' +
                                    credHost + ' Cause: ' + data.statusText + " Details: " + data.responseText
                            );
                            modl.close();
                        });

                    }
                    else {
                        console.log(XNAT.url.csrfUrl('/xapi/xsync/credentials/save/projects/' + XNAT.data.context.project));
                        xmodal.message('Error', 'ERROR:  Could not get alias token.  Please check username and password and try again.');
                    }

                });
                credentialsAjax.fail(function(data, textStatus, error){
                    console.log(XNAT.url.csrfUrl('/xapi/xsync/credentials/save/projects/' + XNAT.data.context.project));
                    xmodal.message('Error', 'ERROR:  Could not get alias token.  Please check username and password and try again.');
                });

            },
            okClose: false,
            cancel: 'Cancel',
            cancelLabel: 'Cancel',
            cancelAction: function(){
                xmodal.close(XNAT.app.abu.abuConfigs.modalOpts.id);
            },
            closeBtn: 'hide'
        };

        xmodal.open(pModalOpts);

    }

    XSYNC.credentialsconfig.enterCredentials = XSYNC.xsyncconfig.enterCredentials = enterCredentials;


    function checkCredentials(callback, failure){

        this.checkCredentialsResult = false;

        var formData = {
            host: $('#xsync-config-remote-url').val(),
            localProject: XNAT.data.context.project
        };

        // sample JSON for valid credentials check
        var formDataSample = {
            "host": "http://10.1.100.170",
            "localProject": "Xa",
            "remoteProject": "Xb",
            "syncNewOnly": true,
            "alias": "a8174140-acb3-4b28-ba3d-006dd68be980",
            "secret": "QuVoGllg591PTMIcYcnNoEJDcYefbfwmadlESyHFDGaec29yxpGEJb0r8cXZigRF",
            "username": "bob",
            "estimatedExpirationTime": 1477178868198
        };

        var saveCredentials = $.ajax({
            type: "POST",
            url: XNAT.url.csrfUrl('/xapi/xsync/credentials/check/projects/' + XNAT.data.context.project),
            cache: false,
            async: false,
            dataType: 'text',
            data: JSON.stringify(formData),
            processData: false,
            contentType: "text/plain"
        });

        saveCredentials.done(function(data, textStatus, jqXHR){
            XSYNC.xsyncconfig.checkCredentialsResult = true;
            if (typeof callback === 'function') {
                callback();
            }
        });

        saveCredentials.fail(function(data, textStatus){
            console.log(textStatus + " - Failed to save credentials");
            if (typeof failure === 'function') {
                failure();
            }
        });

        return saveCredentials;

    }

    XSYNC.xsyncconfig.checkCredentials = checkCredentials;


    /*
     Configuration Settings
     */

    XSYNC.xsyncconfig.editConfig = function(){
        var $form;
        XSYNC.xsyncconfig.modal = xmodal.open({
            title: "Project Sync Settings for " + XNAT.data.context.project,
            content: '<div id="xsync-config-dialog"></div>',
            height: '90%',
            beforeShow: function(obj){
                // Spawn everything
                var spawnerConfig = spawnConfig();
                var $wrapper = obj.$modal.find('#xsync-config-dialog');
                XNAT.spawner.spawn(spawnerConfig).render($wrapper);

                $('#xsync-advanced-settings').show();

                // save <form> reference
                $form = obj.$modal.find('form');

                // Trigger changes
                $form.find('select').trigger('change');
                $form.find('checkbox').trigger('change');

                XSYNC.xsyncconfig.$form = $form;

            },
            buttons: {
                submit: {
                    label: "Submit",
                    isDefault: true,
                    action: function(obj){
                        // Only include visible fields and checkboxes, which are of type 'hidden'
                        var json = form2js($('#root-panel').find(':input').filter(':visible, [type="hidden"]')
                                .toArray());

                        // Source project not on the form
                        json.source_project_id = XNAT.data.context.project;

                        // Delete stuff we don't want serialized
                        delete json.subjectDetailsCheckbox;
                        delete json.advancedSyncCheckbox;

                        // don't trample on advanced settings that aren't defined in the UI
                        json = XSYNC.xsyncconfig.mergeConfig(json);
                        console.log('XSYNC.xsyncconfig.configuration');
                        console.log(json);

                        XSYNC.xsyncconfig.submitConfig(JSON.stringify(json));
                        // $form.triggerHandler('reload-data');
                    }
                },
                close: {
                    label: "Cancel"
                }
            }
        });
    };

    function spawnConfig(){

        ////////////////////
        // Parent element //
        ////////////////////

        function configPanel(){
            return {
                kind: 'panel.form',
                title: 'XSync Configuration',
                load: "XSYNC.xsyncconfig.configuration",
                refresh: "/xapi/xsync/setup/projects/" + XNAT.data.context.project,
                action: "#",
                contents: {
                    enabled: enabled(),
                    newOnly: syncNewOnly(),
                    destXnat: remoteUrl(),
                    destProjectId: remoteProject(),
                    frequency: frequency(),
                    identifiers: identifiers(),
                    anonymize: anonymize(),
                    // okToSync: okToSync(),

                    advancedSyncCheckbox: {
                        kind: 'panel.element',
                        name: '',
                        label: '',
                        contents: '<a href=#>Hide Advanced Settings</a>',
                        element: {
                            $: {
                                click: function(){
                                    if ($("#xsync-advanced-settings").is(":hidden")) {
                                        $('#xsync-advanced-settings').slideDown(400);
                                        $(this).find("a").text("Hide Advanced Settings")
                                    }
                                    else {
                                        $('#xsync-advanced-settings').slideUp(400);
                                        $(this).find("a").text("Show Advanced Settings")
                                    }
                                }
                            }
                        }
                    },
                    advancedSettings: {
                        tag: "div#xsync-advanced-settings",
                        contents: {
                            projectResources: {
                                tag: "div#project-resources-div",
                                contents: {
                                    projectResourcesHeading: {
                                        kind: 'panel.subhead',
                                        label: 'Project Resources'
                                    },
                                    projectResourceSelect: syncTypeSelector("project_resources.sync_type", " "),
                                    projectResourceInput: resourceInput("project_resources.resource_list")
                                }
                            },

                            subjectResources: {
                                tag: "div#subject-resources-div",
                                contents: {
                                    subjectResourcesHeading: {
                                        kind: 'panel.subhead',
                                        label: 'Subject Resources'
                                    },
                                    subjectResourceSelect: syncTypeSelector("subject_resources.sync_type", " "),
                                    subjectResourceInput: resourceInput("subject_resources.resource_list")
                                }
                            },

                            subjectAssessors: {
                                tag: "div#subject-assessors-div",
                                contents: {
                                    subjectAssessorsHeading: {
                                        kind: 'panel.subhead',
                                        label: 'Non-imaging Assessments'
                                    },
                                    subjectAssessorSelect: syncTypeSelector("subject_assessors.sync_type", " ")//,
                                    // subjectAssessorXsiTypes: xsiInput("subject_assessors.xsi_types.types_list"),
                                    // subjectDetailsCheckbox: detailsToggle("subject-assessor-advanced"),
                                    // xsiTypeAdvanced: {
                                    //     tag: "div#subject-assessor-advanced",
                                    //     contents: {
                                    //         subAssessorSelect:
                                    // syncTypeSelector("subject_assessors.advanced_options.resources.sync_type",
                                    // "Assessor Resources"), subAssessessorResources:
                                    // resourceInput("subject_assessors.advanced_options.resources.resource_list"),
                                    // okToSync: { kind: 'panel.input.checkbox', name:
                                    // "subject_assessors.advanced_options.needs_ok_to_sync", label: 'Require QC to
                                    // Sync' } } }
                                }
                            },

                            imagingSessions: {
                                tag: "div#imaging-sessions-div",
                                contents: {
                                    imagingSessionsHeading: {
                                        kind: 'panel.subhead',
                                        label: 'Imaging Sessions'
                                    },
                                    imagingSessionsSelect: syncTypeSelector("imaging_sessions.sync_type", " ")//,
                                    // imagingSessionsXsiTypes: xsiInput("imaging_sessions.xsi_types.types_list")//
                                    // sessionDetailsCheckbox: detailsToggle("imaging-sessions-advanced"),
                                    // imagingAdvanced: {
                                    //     tag: "div#imaging-sessions-advanced",
                                    //     contents: {
                                    //         okToSync: {
                                    //             kind: 'panel.input.checkbox',
                                    //             name: "imaging_sessions.advanced_options.needs_ok_to_sync",
                                    //             label: 'Require QC to Sync'
                                    //         },
                                    //         anonymize: {
                                    //             kind: 'panel.input.checkbox',
                                    //             name: "imaging_sessions.advance_options.anonymize",
                                    //             label: 'Anonymize DICOM'
                                    //         },
                                    //         imageResourceSelect:
                                    // syncTypeSelector("imaging_sessions.advanced_options.resources.sync_type",
                                    // "Session Resources"), imageResourcesInput:
                                    // resourceInput("imaging_sessions.advanced_options.resources.resource_list"),
                                    // scanTypeSelect:
                                    // syncTypeSelector("imaging_sessions.advanced_options.scan_types.sync_type", "Scan
                                    // Types"), scanTypeInput:
                                    // resourceInput("imaging_sessions.advanced_options.scan_types.sync_type_list"),
                                    // scanResourceSelect:
                                    // syncTypeSelector("imaging_sessions.advanced_options.scan_resources.sync_type",
                                    // "Scan Resources"), scanResourceInput:
                                    // resourceInput("imaging_sessions.advanced_options.scan_types.resource_list"),
                                    // imagingAssessorHeading: {kind: 'panel.subhead', label: 'Imaging Assessors'},
                                    // assessorsSelect:
                                    // syncTypeSelector("imaging_sessions.advanced_options.session_assessors.xsy_types.sync_type",
                                    // " "), assessorsInput:
                                    // xsiInput("imaging_sessions.advanced_options.session_assessors.xsi_types.types_list"),
                                    // okToSyncAss: { kind: 'panel.input.checkbox', name:
                                    // "imaging_sessions.advanced_options.session_assessors.advanced_options.needs_ok_to_sync",
                                    // label: 'Require QC to Sync' }, imageAssessorResourceSelect:
                                    // syncTypeSelector("imaging_sessions.advanced_options.session_assessors.advanced_options.sync_type", "Assessor Resources"), imageAssessorResourcesInput: resourceInput("imaging_sessions.advanced_options.session_assessors.advanced_options.resource_list") } }

                                }
                            }
                        }
                    }
                },
                footer: false
            }
        }

        ///////////////////////////
        // Basic config elements //
        ///////////////////////////

        function enabled(){
            return {
                id: 'enabled',
                kind: 'panel.input.checkbox',
                name: 'enabled',
                label: 'Enabled'
            }
        }

        function frequency(){
            return {
                kind: 'panel.select.menu',
                id: 'sync_frequency',
                name: 'sync_frequency',
                label: 'Sync Frequency',
                options: {
                    daily: 'Daily',
                    weekly: 'Weekly',
                    monthly: 'Monthly',
                    'on demand': 'On Demand'
                }
            }
        }

        function syncNewOnly(){
            return {
                id: 'xsync-config-newonly',
                kind: 'panel.input.checkbox',
                name: 'sync_new_only',
                label: 'New Data Only'
            }
        }

        function remoteUrl(){
            return {
                kind: 'panel.input.text',
                id: 'xsync-config-remote-url',
                name: 'remote_url',
                label: 'Destination XNAT'
            }
        }

        function remoteProject(){
            return {
                kind: 'panel.input.text',
                id: 'xsync-config-remote-project',
                name: 'remote_project_id',
                label: 'Destination Project'
            }
        }

        function identifiers(){
            return {
                kind: 'panel.select.menu',
                id: 'xsync-config-identifiers',
                name: 'identifiers',
                label: 'Identifiers',
                options: {
                    use_local: 'Local'//,
                    // use_remote: 'Remote'
                }
            }
        }

        function anonymize(){
            return {
                id: 'xsync-config-anonymize',
                kind: 'panel.input.checkbox',
                name: 'anonymize',
                label: 'Anonymize Images'
            }
        }

        function okToSync(_name){
            return {
                kind: 'panel.input.checkbox',
                name: _name,
                label: 'Require QC to Sync'
            }
        }


        ///////////////////////////////
        // Common config UI elements //
        ///////////////////////////////

        // Function level map to keep track of which text inputs should be visible
        var showTextInput = {};

        function syncTypeSelector(name, label){
            // imaging_sessions.advanced_options.resources.sync_type
            return {
                kind: 'panel.select.menu',
                name: name,
                id: name.replace(/\./g, '_') + '_select_menu_id',
                label: label,
                // value: 'all', // don't we want 'all' to be selected by default?
                options: {
                    all: 'All',
                    none: 'None'//,
                    // include: 'Include',
                    // exclude: 'Exclude'
                },
                element: {
                    onchange: function(){
                        // TODO - Get rid of nasty hack
                        var inputName;
                        if (name.indexOf('resource') > -1) {
                            inputName = name.replace('sync_type', 'resource_list')
                        }
                        else {
                            inputName = name.replace('sync_type', 'types_list')
                        }
                        showHideInput(this, inputName)
                    }
                }
            }
        }

        function resourceInput(name, label){
            var inputId = name.replace(/\./g, '_') + '_input_text_id';
            // Initialize show input to false
            showTextInput[name] = false;

            return {
                // kind: 'panel.input.textarea',
                kind: 'panel.textarea.arrayList',
                id: inputId,
                name: name,
                label: "Resource List",
                rows: 2,
                description: "Comma separated list of resource names, e.g., Res1,Res2. You can view your resources by selecting Manage Files in the Actions menu."
            }
        }

        function xsiInput(name){
            var inputId = name.replace(/\./g, '_') + '_input_text_id';
            // Initialize show input to false
            showTextInput[name] = false;

            return {
                kind: 'panel.textarea.arrayList',
                id: inputId,
                name: name,
                label: 'XSI Types',
                rows: 2,
                description: "" +
                "Comma separated list of XSI types, e.g., xnat:mrSessionData,hcp:subjectMetadata. You can get " +
                "a list of XSI types in the <a href=" + XNAT.url.rootUrl('app/template/XDATScreen_dataTypes.vm') +
                " target='_blank'>admin section</a>",
                element: {
                    onblur: function(){
                        var xsiTypes = $(this).val().split(',');
                        console.log(xsiTypes);
                        // split the list on comma
                        // append advanced section for each xsiType
                        // showHideAdvanced(this)
                    }
                }
            }
        }

        function detailsToggle(divId){
            return {
                kind: 'panel.input.checkbox',
                name: '',
                label: 'More details',
                element: {
                    onchange: function(){
                        if ($(this).is(':checked')) {
                            $('#' + divId).slideDown(400)
                        }
                        else {
                            $('#' + divId).slideUp(400)
                        }
                    }
                }
            }
        }



        ///////////////////////
        // Config UI Helpers //
        ///////////////////////

        function showHideInput(selector, name){
            // jquery doesn't like to select ids with periods
            var inputId = name.replace(/\./g, '_') + '_input_text_id';
            var $textInput = $('#' + inputId);

            // show or hide input text box
            if ((selector.value == "include" || selector.value == "exclude") && !showTextInput[name]) {
                $('[data-name="' + name + '"]').fadeIn(400);
                showTextInput[name] = true;
            }
            else if ((selector.value == "all" || selector.value == "none") || selector.value == "") {
                // remove input contents and hide
                $('[data-name="' + name + '"]').hide();
                $textInput.val("");
                showTextInput[name] = false;
            }
        }

        function showHideAdvanced(selector, name){}

        return {
            root: configPanel()
        };
    }

    XSYNC.xsyncconfig.submitConfig = function(jsonString){

        function doSave(){
            XSYNC.xsyncconfig.checkCredentialsResult = true;
            saveConfig(jsonString);
        }

        function tryAgain(){
            enterCredentials(jsonString);
        }

        XSYNC.xsyncconfig.checkCredentials(doSave, tryAgain);

    };

    function saveConfig(newJson){

        var saveWait = xmodal.loading.open();

        var xsyncConfigAjax = $.ajax({
            type: "POST",
            url: XNAT.url.csrfUrl('/xapi/xsync/setup/projects/' + XNAT.data.context.project),
            data: newJson,
            contentType: "text/plain"
        });

        xsyncConfigAjax.done(function(data, textStatus, jqXHR){

            $("#xsync-annon_add-config").attr("disabled", false);

            //saveWait.close();

            xmodal.message({
                title: 'Saved',
                content: 'The XSync configuration has been saved',
                // action: function(){
                //     window.location.reload();
                // },
                onClose: function(){
                    xmodal.loading.closeAll();
                    //window.location.reload();
                }
            });

            // // Reload the data on successful save
            XNAT.xhr.getJSON({
                url: XNAT.url.restUrl('/xapi/xsync/setup/projects/' + XNAT.data.context.project),
                success: function(data){
                    XSYNC.xsyncconfig.modal.close();
                    XSYNC.xsyncconfig.configuration = data;
                    // $('#root-panel').setValues(data);
                    // refresh the config panel - show the buttons
                    XSYNC.xsyncconfig.showConfigPanel();
                    $('#showSyncData').show();
                    // or just reload the whole page
                    // window.location.reload();
                },
                error: function(){
                    console.log("Failed to reload XSync config data after submission.")
                }
            })

        });

        xsyncConfigAjax.fail(function(data, textStatus, error){
            console.log("XSync config submission failed");
            console.log(newJson);
            saveWait.close();
            xmodal.message('Error', 'Configuration was not successfully saved (' + error + ')');
        });

        return xsyncConfigAjax;

    }

    XSYNC.xsyncconfig.saveConfig = saveConfig;

    /*
     DICOM Anonymization
     */

    XSYNC.xsyncconfig.submitDICOMAnonymization = function(){

        var getAnonymizationScript = $.get({
            url: XNAT.url.restUrl('/xapi/xsync/setup/presyncanonymization/projects/' + XNAT.data.context.project),
            dataType: 'text'
        });

        getAnonymizationScript.done(function(data){

            var tempDiv = spawn('div', data);

            var editorConfig = {
                //url: '/path/to/data', // PLACEHOLDER - NOT IMPLEMENTED
                // title: 'Edit',
                language: 'text'
            };

            var editor = XNAT.app.codeEditor.init(tempDiv, editorConfig);

            editor.openEditor({
                id: "xsync-dicom-anonymization",
                title: "Pre-Sync DICOM Anonymization Script",
                before: '<p>This anonymization script will be applied when Sync Anonymization is enabled.</p>',
                footerContent: 'Submit to save changes',
                //width: 680,
                height: 600,
                buttons: {
                    save: {
                        label: 'Submit',
                        isDefault: true,
                        action: function(modal){
                            var code = editor.getValue().code;
                            var upload = XSYNC.xsyncconfig.uploadDicomAnonymization(code);
                            upload.done(function(){
                                // XNAT.ui.banner.top(2000, 'Anonymization script saved.');
                                modal.close();
                            });
                        }
                    },
                    cancel: {
                        label: 'Cancel',
                        action: function(modal){
                            modal.close()
                        }
                    }
                }
                // other xmodal properties
            });
        });

        getAnonymizationScript.fail(function(data, textStatus, error){
            xmodal.message('Error', textStatus + ': Could not retrieve pre-sync DICOM anonymization script (' + error + ')');
        });

    };

    XSYNC.xsyncconfig.uploadDicomAnonymization = function(editorContents){

        var uploadDICOMscriptAjax = $.ajax({
            type: "PUT",
            url: XNAT.url.csrfUrl('/xapi/xsync/setup/presyncanonymization/projects/' + XNAT.data.context.project),
            data: editorContents
        });

        uploadDICOMscriptAjax.done(function(data, textStatus, jqXHR){
            xmodal.message('Saved', 'The Pre-Sync DICOM Anonymization has been saved');
            // XNAT.ui.banner.top(2000, 'Anonymization script saved.');
            XSYNC.xsyncconfig.anonymizationuploadBtnText = 'Update Pre Sync DICOM Anonymization Script';
            $("#xsync-annon_add-config").attr('value', XSYNC.xsyncconfig.anonymizationuploadBtnText);
        });

        uploadDICOMscriptAjax.fail(function(data, textStatus, error){
            xmodal.message('Error', textStatus + ': Pre-Sync DICOM Anonymization was not successfully saved (' + error + ')');
        });

        return uploadDICOMscriptAjax;

    };

})();
