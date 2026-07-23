if (typeof XSYNC === 'undefined') {
    XSYNC = {};
}
if (typeof XSYNC.xsyncConfig === 'undefined') {
    XSYNC.xsyncConfig = {};
    XSYNC.xsyncConfig.firstTime = false;
}
if (typeof XSYNC.credentialsConfig === 'undefined') {
    XSYNC.credentialsConfig = {};
}

(function(){

    var restUrl = XNAT.url.restUrl;

    // Merge any existing settings and change any modified values
    // without destroying saved 'advanced' settings.
    XSYNC.xsyncConfig.mergeConfig = function(data){
        return XSYNC.xsyncConfig.configuration = extend(true, XSYNC.xsyncConfig.configuration || {}, data);
    };

    /*
     Initialization
    */
    XSYNC.xsyncConfig.init = function(){
        XNAT.xhr.getJSON({
            url: restUrl('/xapi/xsync/setup/projects/' + XNAT.data.context.project),
            success: function(data){
                XSYNC.xsyncConfig.mergeConfig(data);
                XSYNC.xsyncConfig.showConfigPanel()
            },
            error: function(){
                XSYNC.xsyncConfig.initialConfig()
            }
        })
    };

    XSYNC.xsyncConfig.useDefaultConfig = function(){
        // Use the defaults to populate config dialog
        XSYNC.xsyncConfig.firstTime = true;
        XSYNC.xsyncConfig.mergeConfig({});
        // XSYNC.xsyncConfig.configuration = {};
        XSYNC.xsyncConfig.configuration.project_resources = {};
        XSYNC.xsyncConfig.configuration.subject_resources = {};
        XSYNC.xsyncConfig.configuration.subject_assessors = {};
        XSYNC.xsyncConfig.configuration.imaging_sessions = {};

        XSYNC.xsyncConfig.configuration.enabled = true;
        XSYNC.xsyncConfig.configuration.source_project_id = XNAT.data.context.project;
        XSYNC.xsyncConfig.configuration.sync_frequency = 'weekly';
        XSYNC.xsyncConfig.configuration.sync_new_only = true;
        XSYNC.xsyncConfig.configuration.identifiers = 'use_local';
        XSYNC.xsyncConfig.configuration.remote_url = 'https://';
        XSYNC.xsyncConfig.configuration.remote_project_id = '';
        XSYNC.xsyncConfig.configuration.notification_emails = '';
        XSYNC.xsyncConfig.configuration.customIdentifiers = 'dateTimeLabelGenerator';
        XSYNC.xsyncConfig.configuration.anonymize = false;
		XSYNC.xsyncConfig.configuration.no_of_retry_days = 3;
        // XSYNC.xsyncConfig.configuration.ok_to_sync = false;

        // XSYNC.xsyncConfig.configuration.subject_assessors.xsi_types = {};
        // XSYNC.xsyncConfig.configuration.imaging_sessions.xsi_types = {};
        XSYNC.xsyncConfig.configuration.project_resources.sync_type = 'none';
        XSYNC.xsyncConfig.configuration.subject_resources.sync_type = 'none';
        XSYNC.xsyncConfig.configuration.subject_assessors.sync_type = 'none';
        XSYNC.xsyncConfig.configuration.imaging_sessions.sync_type = 'all';
        // XSYNC.xsyncConfig.configuration.imaging_sessions.xsi_types.types_list = ['xnat:mrSessionData'];

        XSYNC.xsyncConfig.isProjectAsperaEnabled = false;
        XSYNC.xsyncConfig.isSiteWideAsperaEnabled = false;
    };

    XSYNC.xsyncConfig.initialConfig = function(){
        $("#xsync-config").spawn('button#xsync-begin-config.btn1', {
            type: 'button',
            html: 'Begin Configuration',
            onclick: function(){
                XSYNC.xsyncConfig.useDefaultConfig();
                XSYNC.xsyncConfig.editConfig();
            }
        });
    };

    XSYNC.xsyncConfig.showConfigPanel = function(){

        $('#xsync-config').empty().append([
            '<button class="btn1 xsync-submit-button" id="xsync-edit-config" type="button">Edit Configuration</button>' +
            '<button class="btn1 xsync-submit-button" id="xsync-credentials" type="button">Remote Credentials</button>' +
            '<button class="btn1 xsync-submit-button" id="xsync-upload-anonymization" type="button">Configure Anonymization</button>' +
            '<h2 id="xsync-history-header" style="">Sync History</h2>' +
            '<div id="xsync-history-table" style="max-height:300px;overflow-y:auto"></div>'
        ]);

        $("#xsync-edit-config").on('click', function() {
            XSYNC.xsyncConfig.editConfig();
        });

        $("#xsync-credentials").on('click', function() {
            XSYNC.credentialsConfig.enterCredentials();
        });

        $("#xsync-upload-anonymization").prop('disabled', XSYNC.xsyncConfig.configuration.anonymize === false)
            .on('click', function(){
                XSYNC.xsyncConfig.submitDICOMAnonymization();
        });

        XSYNC.reporting.showHistoryTable();
    };

    /////////////////////////
    //Remote Authentication//
    /////////////////////////

    /*
     * Get user credentials
     * @param {String} optional configuration JSON to submit if initial setup
     */
    XSYNC.credentialsConfig.enterCredentials = function(configJson){

        var remoteProjectId = XSYNC.xsyncConfig.configuration.remote_project_id ||
            $("#xsync-config-remote-project").val() || "ERROR";

        var newOnly = $("#xsync-config-new-only").val() ||
            XSYNC.xsyncConfig.configuration.sync_new_only || true;

        var credHost = XSYNC.xsyncConfig.configuration.remote_url != "https://" ?
            XSYNC.xsyncConfig.configuration.remote_url : $("#xsync-config-remote-url").val();

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
            afterShow: function() {
                this.$modal.find('#xsync-credentials-username').focus();
            },
            ok: 'show',
            okLabel: 'Continue',
            okAction: function(modl) {
                var $form = modl.$modal.find('#xsync-remote-credentials');

                var credHost = $form.find('[name="host"]').val();
                var credUser = $form.find('[name="username"]').val();
                var credPassword = $form.find('[name="password"]').val();

                xmodal.loading.open({ title: 'Checking xsync credentials...'});

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
                    "host": "http://10.1.100.170",
                    "localProject": "Xa",
                    "remoteProject": "Xb",
                    "syncNewOnly": true,
                    "alias": "a8174140-acb3-4b28-ba3d-006dd68be980",
                    "secret": "QuVoGllg591PTMIcYcnNoEJDcYefbfwmadlESyHFDGaec29yxpGEJb0r8cXZigRF",
                    "username": "bob",
                    "estimatedExpirationTime": 1477178868198
                };

                credentialsAjax.done(function(data) {
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

                        if (Object.hasOwn(data, 'estimatedExpirationTime')) {
                            formData['estimatedExpirationTime'] = data.estimatedExpirationTime
                        }

                        var saveCredentials = $.ajax({
                            type: 'POST',
                            url: XNAT.url.csrfUrl('/xapi/xsync/credentials/save/projects/' + XNAT.data.context.project),
                            data: JSON.stringify(formData),
                            processData: false,
                            contentType: 'application/json'
                        });

                        saveCredentials.done(function(data, textStatus, jqXHR){
                            xmodal.loading.close();
                            if (jqXHR.status == 202) {
                                xmodal.message(
                                    'Credentials saved', ' WARNING: ' + jqXHR.responseText + '\n' +
                                    'Successfully saved credentials for remote server ' + credHost
                                );
                            }
                            else {
                                if (XSYNC.xsyncConfig.firstTime === false) {
                                    xmodal.message(
                                        'Credentials saved', 'Successfully saved credentials for remote server ' +
                                        credHost
                                    );
                                }
                            }
                            modl.close();

                            // submit the config json again if this was called from submitConfig
                            if (configJson !== undefined) {
                                XSYNC.xsyncConfig.firstTime = false;
				                if(XSYNC.xsyncConfig.isWhitelistEnabledBackend === true) {
                                    XSYNC.xsyncConfig.newRemoteUrl = $('#site_select_menu').val();
                                } else{
                                    XSYNC.xsyncConfig.newRemoteUrl = trimUrl($('#xsync-config-remote-url').val());
                                }
                                XSYNC.xsyncConfig.submitConfig(configJson);
                            }
                        });

                        saveCredentials.fail(function(data, textStatus, jqXHR) {
                            xmodal.message(
                                'Error', 'Could not save credentials for remote server ' +
                                credHost + ' Cause: ' + data.statusText + " Details: " + data.responseText
                            );
                            xmodal.loading.close();
                            modl.close();
                        });
                    }
                    else {
                        console.log(XNAT.url.csrfUrl('/xapi/xsync/credentials/save/projects/' + XNAT.data.context.project));
                        xmodal.loading.close();
                        xmodal.message('Error', 'ERROR:  Could not get alias token.  Please check username and password and try again.');
                    }
                });
                credentialsAjax.fail(function(data, textStatus, error) {
                    console.log(XNAT.url.csrfUrl('/xapi/xsync/credentials/save/projects/' + XNAT.data.context.project));
                    xmodal.loading.close();
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

    XSYNC.xsyncConfig.checkCredentials = function (successCallback, failureCallback) {
        this.checkCredentialsResult = false;

        let host_url = $('#xsync-config-remote-url').val();
        if(XSYNC.xsyncConfig.isWhitelistEnabledBackend === true) {
            host_url = $('#site_select_menu').val();
        }
        var formData = {
            host: trimUrl(host_url),
            localProject: XNAT.data.context.project
        };

        // sample JSON for valid credentials check
        var formDataSample = {
            "host": "http://10.1.100.170",
            "localProject": "Xa"
        };

        var checkCredentialsAjax = $.ajax({
            type: "POST",
            url: XNAT.url.csrfUrl('/xapi/xsync/credentials/check/projects/' + XNAT.data.context.project),
            cache: false,
            async: false,
            data: JSON.stringify(formData),
            processData: false,
            contentType: 'application/json'
        });

        checkCredentialsAjax.done(function(data, textStatus, jqXHR){
            XSYNC.xsyncConfig.checkCredentialsResult = true;
            if (typeof successCallback === 'function') {
                successCallback();
            }
        });

        checkCredentialsAjax.fail(function(data, textStatus){
            console.log(textStatus + " - Failed to save credentials");
            if (typeof failureCallback === 'function') {
                failureCallback();
            }
        });

        return checkCredentialsAjax;
    }

	function isValidEmailAddress(emailAddress) {
        var pattern = /^([a-z\d!#$%&'*+\-\/=?^_`{|}~\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF]+(\.[a-z\d!#$%&'*+\-\/=?^_`{|}~\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF]+)*|"((([ \t]*\r\n)?[ \t]+)?([\x01-\x08\x0b\x0c\x0e-\x1f\x7f\x21\x23-\x5b\x5d-\x7e\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF]|\\[\x01-\x09\x0b\x0c\x0d-\x7f\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF]))*(([ \t]*\r\n)?[ \t]+)?")@(([a-z\d\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF]|[a-z\d\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF][a-z\d\-._~\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF]*[a-z\d\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])\.)+([a-z\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF]|[a-z\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF][a-z\d\-._~\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF]*[a-z\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])\.?$/i;
        return pattern.test(emailAddress);
    };
	
	function validateEmailAddresses() {
        var allAddressesValid=true;
        var emails=$('#xsync-config-notification_emails').val();

        if (emails != null && emails.length > 0) {
            var emailArr=emails.split(',');
            for (i=0; i<emailArr.length; i++) {
                if (!isValidEmailAddress(emailArr[i].trim())) {
                    errorWindow('<b>'+emailArr[i] +'</b> is not a valid email address. Please check the email to make sure it is correct.');
                    allAddressesValid=false;
                    break;
                }
            }
        }
        return allAddressesValid;
	}
		
    function validateNoOfRetryDays() {
        var valid=true;
        var retryVal = $('#xsync-config-no_of_retry_days').val();

        if (isNaN(retryVal) || retryVal.includes('.') || retryVal > 999 || retryVal < 0) {
            errorWindow('<b>'+retryVal +'</b> is not a valid value for number of retries. The number of retries must be a positive integer.');
            valid=false;
        }
        return valid;
    }

    function errorWindow(message) {
        errorModal=xmodal.open({
            title: "Error",
            content:'<div>'+message+'</div>',
            height: '20%',
            width: '25%',
            ok: 'hide',
            cancel: 'show',
            cancelLabel: 'Close',
            closeBtn: 'hide'
        });
        xModalOpenNew(errorModal);
    }

    //////////////////////////
    //Configuration Settings//
    //////////////////////////

    XSYNC.xsyncConfig.destinationChanged = false;
    XSYNC.xsyncConfig.oldRemoteUrl = "";

    XSYNC.xsyncConfig.displayNewDataWarning = function() {
        xmodal.message({
            title: 'Warning',
            content: '<p><b>WARNING:</b>  Configuring XSync with <em>New Data Only</em> unchecked, can lead to some undesirable results, including data loss, on the destination side and should only be used under certain circumstances.</p><p>When <em>New Data Only</em> is unchecked, XSync will check for source-side changes prior to a project sync.  When there are changes to a session at the source-side, it will resend that session, <b><em>completely replacing the session at the destination</em></b>.</p><p>This option should not be used if project workflows require editing the destination session, as those edits will be lost.  It should only be used when all session changes are made on the source-side and the destination session is expected always reflect what is at the source.</p>',
            width: '600px',
            height: '350px'
        });
    }

    XSYNC.xsyncConfig.editConfig = function() {
        XSYNC.xsyncConfig.modal = xmodal.open({
            title: "Project Sync Settings for " + XNAT.data.context.project,
            content: '<div id="xsync-config-dialog"></div>',
            height: '90%',
            beforeShow: function(obj) {
                // Spawn everything
                var spawnerConfig = spawnConfig();
                setTimeout(function() {
                    var newOnlyVal = $("#xsync-config-new-only").val();
                    XSYNC.xsyncConfig.oldRemoteUrl = $('#xsync-config-remote-url').val();
                    if (newOnlyVal == "false") {
                        XSYNC.xsyncConfig.displayNewDataWarning();
                    }
                    XSYNC.xsyncConfig.oldValue = $('#xsync-config-remote-url').val();
                        $('#xsync-config-new-only').change(function() {
                        var newOnlyVal = $("#xsync-config-new-only").val();
                        if (newOnlyVal == "false") {
                            XSYNC.xsyncConfig.displayNewDataWarning();
                        }
                    });
                    $('#xsync-config-remote-url').change(function() {
                            XSYNC.xsyncConfig.destinationChanged = true;
                    });
                }, 500);
                var $wrapper = obj.$modal.find('#xsync-config-dialog');
                XNAT.spawner.spawn(spawnerConfig).render($wrapper);
                getCustomIdentifiers(function() {
                    var xConfig = XSYNC.xsyncConfig.configuration;
                    for (var key in xConfig) {
                        if (xConfig.hasOwnProperty(key)) {
                            var configObj = xConfig[key];
                            if (typeof configObj === 'object') {
                                var configName = key;
                                for (var key2 in configObj) {
                                    if (xConfig.hasOwnProperty(key)) {
                                        var configName2 = configName + "." + key2;
                                        $('[name="' + configName2 + '"]').val(configObj[key2]);
                                    }
                                }
                            } else if (typeof configObj === 'boolean') {
                                $('[type="checkbox"][title="' + key + '"]').val(xConfig[key]);
                                $('[type="checkbox"][title="' + key + '"]').attr('checked', xConfig[key]);
                                $('[name="' + key + '"]').val(xConfig[key]);
                            } else {
                                $('[name="' + key + '"]').val(xConfig[key]);
                            }
                        }
                    }

                    $('#xsync-advanced-settings').show();
                    var $form = obj.$modal.find('form');

                    // Trigger changes
                    $form.find('select').trigger('change');
                    $form.find('checkbox').trigger('change');
                    XSYNC.xsyncConfig.$form = $form;
                });
            },
            buttons: {
                submit: {
                    label: "Submit",
                    isDefault: true,
                    action: function(obj) {
						if (validateEmailAddresses() && validateNoOfRetryDays()) {
							// Only include visible fields and checkboxes, which are of type 'hidden'
							const stringValues = ["remote_project_id"];
                            var json = form2js($('#root.xnat-form-panel').find(':input').filter(':visible, [type="hidden"]').toArray(), undefined, undefined, undefined, undefined, undefined, stringValues);

							// Source project not on the form
							json.source_project_id = XNAT.data.context.project;

							if (XSYNC.xsyncConfig.isWhitelistEnabledBackend === true) {
                                json.remote_url = trimUrl(json.site_select_menu);
							} else {
							    // Strip trailing slashes
                                json.remote_url = trimUrl(json.remote_url);
							}

							// Delete stuff we don't want serialized
							delete json.subjectDetailsCheckbox;
							delete json.advancedSyncCheckbox;

							// don't trample on advanced settings that aren't defined in the UI
							json = XSYNC.xsyncConfig.mergeConfig(json);
							XSYNC.xsyncConfig.newRemoteUrl = json.remote_url;
                            XSYNC.xsyncConfig.submitConfig(JSON.stringify(json));
						}
                    }
                },
                close: {
                    label: "Cancel"
                }
            }
        });
    };

    function getCustomIdentifiers(callback) {
        function removeCustomOption() {
            $('#xsync-config-custom-identifiers option[value="use_custom"]').remove();
        }
        let $customIdentifiersSelect = $("#xsync-config-custom-identifiers");
        if ($customIdentifiersSelect.find('option').length === 0) {
            var getCustomIdGeneratorsAjax = $.ajax({
                type: "GET",
                url: XNAT.url.csrfUrl('/xapi/xsync/getXsyncCustomIdGenerators')
            });

            getCustomIdGeneratorsAjax.done(function(data, textStatus, jqXHR) {
                if (data.length === 0) {
                    removeCustomOption();
                } else if (data.length === 1) {
                    $customIdentifiersSelect.after('<input type="hidden" name="' +
                        $customIdentifiersSelect.attr('name') + '" value="' + data[0] + '"/>');
                    $customIdentifiersSelect.after('<span id="' + $customIdentifiersSelect.prop('id') +
                        '">' + data[0] + '</span>');
                    $customIdentifiersSelect.remove();
                } else {
                    $.each(data, function(idx, value) {
                        var option = new Option(value, value);
                        $customIdentifiersSelect.append($(option));
                    });
                }
                callback();
            });

            getCustomIdGeneratorsAjax.fail(function(data, textStatus, error) {
                xmodal.message('Error', 'Failed to fetch custom identifiers, see console for details');
                console.error(error);
                removeCustomOption();
                callback();
            });
        }
    }

    function spawnConfig() {

        ////////////////////
        // Parent element //
        ////////////////////

        function configPanel() {
            XNAT.xhr.get({
                url: restUrl('/xapi/xsyncSitePreferences/asperaEnabled/'),
                async: false,
                success: function (data) {
                    XSYNC.xsyncConfig.isSiteWideAsperaEnabled = data;
                },
                fail: function (e) {
                    XNAT.ui.banner.top(2000, 'Could not retrieve aspera information: ' + e.responseText, 'error');
                }
            });
             XNAT.xhr.get({
                url: restUrl('/xapi/xsyncProjectPreferences/project/' + XNAT.data.context.project + '/asperaEnabled/'),
                async: false,
                success: function (data) {
                    XSYNC.xsyncConfig.isProjectAsperaEnabled = data;
                },
                fail: function (e) {
                    XNAT.ui.banner.top(2000, 'Could not retrieve aspera information: ' + e.responseText, 'error');
                }
            });
            XNAT.xhr.get({
                url: restUrl('/xapi/xsyncProjectPreferences/whitelistEnabled/'),
                async: false,
                success: function (data) {
                    XSYNC.xsyncConfig.isWhitelistEnabledBackend = data;
                    if (XSYNC.xsyncConfig.isWhitelistEnabledBackend == true) {
                        XNAT.xhr.get({
                            url: restUrl('/xapi/xsyncSitePreferences/whitelistSites/'),
                            async: false,
                            success: function (data) {
                                XSYNC.xsyncConfig.whitelistSites = {}
                                data.forEach(function (item) {
                                    XSYNC.xsyncConfig.whitelistSites[item['siteUrl']]  = {
                                        label: item['siteUrl'],
                                        value: item['siteUrl']
                                    };
                                });
                            },
                            fail: function (e) {
                                XNAT.ui.banner.top(2000, 'There was an error obtaining xsync preferences: ' + e.responseText, 'error');
                            }
                        });
                    }
                },
                fail: function (e) {
                    XNAT.ui.banner.top(2000, 'There was an error obtaining xsync preferences: ' + e.responseText, 'error');
                }
            });
            return {
                kind: 'panel.form',
                title: 'XSync Configuration',
                load: XSYNC.xsyncConfig.configuration,
                refresh: "/xapi/xsync/setup/projects/" + XNAT.data.context.project,
                action: "#!",
                contents: {
                    message: aspera(),
                    enabled: enabled(),
                    newOnly: syncNewOnly(),
                    destXnat: remoteUrl(),
                    destProjectId: remoteProject(),
                    frequency: frequency(),
                    identifiers: identifiers(),
					customIdentifiers: customIdentifiers(),
                    anonymize: anonymize(),
					no_of_retry_days:numberRetryDays(),
                    notification_emails:notificationEmails(),
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

        function aspera() {
            if (XSYNC.xsyncConfig.isProjectAsperaEnabled === true && XSYNC.xsyncConfig.isSiteWideAsperaEnabled) {
                return {
                    tag:  "div.message.bold",
                    content: "NOTICE: Aspera transfers are now supported, if your destination site supports them.  Please see project settings, in the actions menu, to configure Aspera settings."
                }
            } else {
                return '';
            }
        }

        function enabled() {
            return {
                id: 'enabled',
                kind: 'panel.input.checkbox',
                name: 'enabled',
                label: 'Enabled'
            }
        }

        function frequency() {
            return {
                kind: 'panel.select.menu',
                id: 'sync_frequency',
                name: 'sync_frequency',
                label: 'Sync Frequency',
                options: {
                	hourly: 'Hourly',
                    daily: 'Daily',
                    weekly: 'Weekly',
                    monthly: 'Monthly',
                    'on demand': 'On Demand'
                }
            }
        }

        function syncNewOnly() {
            return {
                id: 'xsync-config-new-only',
                kind: 'panel.input.checkbox',
                name: 'sync_new_only',
                label: 'New Data Only'
            }
        }

        function remoteUrl() {
            if (XSYNC.xsyncConfig.isWhitelistEnabledBackend === true) {
                XSYNC.xsyncConfig.configuration.site_select_menu = XSYNC.xsyncConfig.configuration.remote_url;
                return remoteUrlWhitelist();
            } else {
                return remoteUrlNoWhitelist();
            }
        }

        function remoteUrlNoWhitelist() {
            return {
                kind: 'panel.input.text',
                id: 'xsync-config-remote-url',
                name: 'remote_url',
                label: 'Destination XNAT'
            }
        }

        function remoteUrlWhitelist() {
            return {
                kind: 'panel.select.menu',
                id: 'site_select_menu',
                name: 'site_select_menu',
                label: 'Destination XNAT',
                options: XSYNC.xsyncConfig.whitelistSites
            }
        }

        function remoteProject() {
            return {
                kind: 'panel.input.text',
                id: 'xsync-config-remote-project',
                name: 'remote_project_id',
                label: 'Destination Project'
            }
        }

        function identifiers() {
            return {
                kind: 'panel.select.menu',
                id: 'xsync-config-identifiers',
                name: 'identifiers',
                label: 'Identifiers',
                description: 'Use the source XNAT\'s labels for subject and session (Local) or perform remapping (Custom)',
                options: {
                    use_local: 'Local',
                    use_custom: 'Custom'
                },
				element: {
                    onchange: function() {
						XSYNC.xsyncConfig.showHideCustomIdentifiers($(this).val());
					}
				}
			}
        }
		
		XSYNC.xsyncConfig.showHideCustomIdentifiers = function(identifiers) {
            const target = $('.panel-element[data-name="customIdentifiers"]');
			if (identifiers === 'use_custom') {
                target.show();
			} else {
                target.hide();
			}
		};
		
		function customIdentifiers() {
            return {
                kind: 'panel.select.menu',
                id: 'xsync-config-custom-identifiers',
                name: 'customIdentifiers',
                label: 'Custom Identifiers',
                description: 'The label re-mapping class (see your admin if you need something other than what you see offered)'
            }
        }

        function anonymize() {
            return {
                id: 'xsync-config-anonymize',
                kind: 'panel.input.checkbox',
                name: 'anonymize',
                label: 'Anonymize Images'
            }
        }

        function okToSync(_name) {
            return {
                kind: 'panel.input.checkbox',
                name: _name,
                label: 'Require QC to Sync'
            }
        }
        
        function notificationEmails() {
            return {
                id: 'xsync-config-notification_emails',
                kind: 'panel.input.textarea',
                name: 'notification_emails',
                label: 'Notification Emails',
				rows: 2,
				description: 'By default, Xsync sends emails to Site Admin and Xsync user. If you want to notify more people, kindly provide comma separated email addresses.'
            }
        }

        function numberRetryDays() {
            return {
                id: 'xsync-config-no_of_retry_days',
                kind: 'panel.input.text',
                name: 'no_of_retry_days',
                label: 'Number of days for retry',
				size: '3',
				description: 'By default, Xsync would not re-sync once session/subject is skipped due to filters. If you want to retry skipped sessions for x number of days, kindly select appropriate value.\n 0 = no retry.'
            }
        }
        ///////////////////////////////
        // Common config UI elements //
        ///////////////////////////////

        // Function level map to keep track of which text inputs should be visible
        var showTextInput = {};

        function syncTypeSelector(name, label) {
            // imaging_sessions.advanced_options.resources.sync_type
            return {
                kind: 'panel.select.menu',
                name: name,
                id: name.replace(/\./g, '_') + '_select_menu_id',
                label: label,
                options: {
                    all: 'All',
                    none: 'None'//,
                    // include: 'Include',
                    // exclude: 'Exclude'
                },
                element: {
                    onchange: function() {
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

        function resourceInput(name, label) {
            var inputId = name.replace(/\./g, '_') + '_input_text_id';
            showTextInput[name] = false;

            return {
                kind: 'panel.textarea.arrayList',
                id: inputId,
                name: name,
                label: "Resource List",
                rows: 2,
                description: "Comma separated list of resource names, e.g., Res1,Res2. You can view your resources by selecting Manage Files in the Actions menu."
            }
        }

        function xsiInput(name) {
            var inputId = name.replace(/\./g, '_') + '_input_text_id';
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
                    }
                }
            }
        }

        function detailsToggle(divId) {
            return {
                kind: 'panel.input.checkbox',
                name: '',
                label: 'More details',
                element: {
                    onchange: function() {
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

        function showHideInput(selector, name) {
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

        return {
            root: configPanel()
        };
    }

    XSYNC.xsyncConfig.submitConfig = function(jsonString) {

        function doSave() {
            XSYNC.xsyncConfig.checkCredentialsResult = true;
            XSYNC.xsyncConfig.saveConfig(jsonString);
        }

        function tryAgain() {
            XSYNC.credentialsConfig.enterCredentials(jsonString);
        }

        XSYNC.xsyncConfig.checkCredentials(doSave, tryAgain);
    };

    XSYNC.xsyncConfig.saveConfig = function(newJson) {
        var xsyncConfigAjax = $.ajax({
            type: "POST",
            url: XNAT.url.csrfUrl('/xapi/xsync/setup/projects/' + XNAT.data.context.project),
            data: newJson,
            contentType: "application/json"
        });

        xsyncConfigAjax.done(function(data, textStatus, jqXHR){
            $("#xsync-annon_add-config").attr("disabled", false);

            //In certain cases, system does not recognize that configuration saved message will show and shows
            //credentials saved on first. This will make sure that is removed so we don't make the user close both.
            xmodal.closeAll();

            xmodal.message({
                title: 'Saved',
                content: 'The XSync configuration has been saved',
                // action: function(){
                //     window.location.reload();
                // },
                onClose: function() {
                    if (XSYNC.xsyncConfig.destinationChanged == true && XSYNC.xsyncConfig.oldRemoteUrl != "" &&
                        XSYNC.xsyncConfig.oldRemoteUrl != "https://" &&
                        XSYNC.xsyncConfig.oldRemoteUrl != XSYNC.xsyncConfig.newRemoteUrl) {

                        var pModalOpts = {
                            width: 720,
                            height: 380,
                            id: 'xmodal-remote-url',
                            title: "Remote URL Modified",
                            content: "<p><b>IMPORTANT:</b>  You have modified the <em>Destination XNAT</em> URL.  It is important to indicate whether or not this URL points to the same host/XNAT server as the previous configuration.</p><p>If the modified URL points to the same host/XNAT, the sync history should be updated to reflect this new URL.  Otherwise, the sync process will see this as a new host, which could result in XSync sending old data that should not be sent.</p><p>While normally XSync will skip sessions/subjects that already exist at the destination, if a subject/session has been renamed at the destination, then source/subjects with the old label may be resent.</p><p><b>Please indicate how to treat this new <em>Destination XNAT</em> URL:</b></p><p><b>OLD XNAT URL: &nbsp;" + XSYNC.xsyncConfig.oldRemoteUrl + "</b></p><p><b>NEW XNAT URL: &nbsp;" + XSYNC.xsyncConfig.newRemoteUrl + "</b></p>",
                            ok: 'show',
                            okLabel: 'Same Host - Update URL History',
                            okAction: function(modl){
                                XNAT.xhr.post({
                                    url: restUrl('/xapi/xsync/projects/' + XNAT.data.context.project + "/updateRemoteHostUrl"),
                                    success: function(data){
                                        xmodal.message("Success","Remote Host URL information has been updated.");
                                    },
                                    error: function(){
                                        xmodal.message("Error","There was an error.  The remote host url could not be updated.");
                                    }
                                })
                            },
                            okClose: true,
                            cancel: 'Cancel',
                            cancelLabel: "Different Host - Don't modify history.",
                            cancelAction: function(){
                                xmodal.closeAll();
                            },
                            closeBtn: 'hide'
                        };

                        xmodal.open(pModalOpts);
                    }
                }
            });

            // // Reload the data on successful save
            XNAT.xhr.getJSON({
                url: restUrl('/xapi/xsync/setup/projects/' + XNAT.data.context.project),
                success: function(data) {
                    XSYNC.xsyncConfig.modal.close();
                    XSYNC.xsyncConfig.configuration = data;
                    // $('#root-panel').setValues(data);
                    // refresh the config panel - show the buttons
                    XSYNC.xsyncConfig.showConfigPanel();
                    $('#showSyncData').show();
                    // or just reload the whole page
                    // window.location.reload();
                },
                error: function() {
                    console.log("Failed to reload XSync config data after submission.")
                }
            })
        });

        xsyncConfigAjax.fail(function(data, textStatus, error) {
            console.log("XSync config submission failed");
            console.log(newJson);
            xmodal.loading.close();
            xmodal.message('Error', 'Configuration was not successfully saved (' + error + ')');
        });

        return xsyncConfigAjax;
    }

    /*
     DICOM Anonymization
     */
    XSYNC.xsyncConfig.submitDICOMAnonymization = function() {

        var getAnonymizationScript = $.get({
            url: restUrl('/xapi/xsync/setup/presyncanonymization/projects/' + XNAT.data.context.project),
            dataType: 'text'
        });

        getAnonymizationScript.done(function(data) {
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
                        action: function(modal) {
                            var code = editor.getValue().code;
                            var upload = XSYNC.xsyncConfig.uploadDicomAnonymization(code);
                            upload.done(function() {
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
            });
        });

        getAnonymizationScript.fail(function(data, textStatus, error){
            xmodal.message('Error', textStatus + ': Could not retrieve pre-sync DICOM anonymization script (' + error + ')');
        });
    };

    XSYNC.xsyncConfig.uploadDicomAnonymization = function(editorContents){

        var uploadDicomScriptAjax = $.ajax({
            type: "PUT",
            url: XNAT.url.csrfUrl('/xapi/xsync/setup/presyncanonymization/projects/' + XNAT.data.context.project),
            data: editorContents
        });

        uploadDicomScriptAjax.done(function(data, textStatus, jqXHR) {
            xmodal.message('Saved', 'The Pre-Sync DICOM Anonymization has been saved');
            XSYNC.xsyncConfig.anonymizationUploadBtnText = 'Update Pre Sync DICOM Anonymization Script';
            $("#xsync-annon_add-config").attr('value', XSYNC.xsyncConfig.anonymizationUploadBtnText);
        });

        uploadDicomScriptAjax.fail(function(data, textStatus, error) {
            xmodal.message('Error', textStatus + ': Pre-Sync DICOM Anonymization was not successfully saved (' + error + ')');
        });

        return uploadDicomScriptAjax;
    };

})();

//strip urls of whitespace and trailing slashes
function trimUrl(x) {
  return x.replace(/\/$/,'');
}
