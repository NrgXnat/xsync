if (typeof XSYNC === 'undefined') {
	XSYNC = {};
}
if (typeof XSYNC.xsyncconfig === 'undefined') {
	XSYNC.xsyncconfig = {};
}
if (typeof XSYNC.credentialsconfig === 'undefined') {
	XSYNC.credentialsconfig = {};
}

/*
	Initialization
 */

XSYNC.credentialsconfig.initialize = function() {
	var MUST_BE_CONFIGURED = "<h3>XSync has not been configured.  Please select the <b>XSync Configuration</b> tab.</h3>"
	var scConfigAjax = $.ajax({
		type : "GET",
		url: serverRoot+'/data/projects/'+XNAT.data.context.project+'/config/xsync/json?contents=true',
		cache: false,
		async: false,
		context: this,
		dataType: 'json'
	 });
	scConfigAjax.done( function( data, textStatus, jqXHR ) {
		if (typeof data !== 'undefined' && typeof data.source_project_id !== 'undefined') {
			XSYNC.credentialsconfig.remoteHost = data.remote_url;
			XSYNC.credentialsconfig.beginConfig();
		} else {
			$("#xsync-credentials-div").html(MUST_BE_CONFIGURED);
		}
	});
	scConfigAjax.fail( function( data, textStatus, error ) {
		$("#xsync-credentials-div").html(MUST_BE_CONFIGURED);
	});
}

XSYNC.xsyncconfig.initialize = function() {
	var dcConfigAjax = $.ajax({
		type : "GET",
		url:serverRoot+'/data/projects/' + XNAT.data.context.project +'/resources/synchronization/files',
		cache: false,
		async: false,
		context: this,
		dataType: 'json'
	});
	dcConfigAjax.done( function( data, textStatus, jqXHR ) {
		XSYNC.xsyncconfig.anonymizationuploadBtnText = 'Add Pre Sync DICOM Anonymization Script';
		if (typeof data !== 'undefined' && typeof data.ResultSet.Result !== 'undefined') {
			$.each(data.ResultSet.Result, function(i, item) {
				if (item.Name == "DICOM_anon.das") {
					XSYNC.xsyncconfig.anonymizationuploadBtnText = 'Update Pre Sync DICOM Anonymization Script';
					return false;
				}
			});
		}
	});
	dcConfigAjax.fail( function( data, textStatus, error ) {
		XSYNC.xsyncconfig.anonymizationuploadBtnText = 'Add Pre Sync DICOM Anonymization Script';
	});

	var scConfigAjax = $.ajax({
		type : "GET",
		url: serverRoot+'/data/projects/'+XNAT.data.context.project+'/config/xsync/json?contents=true',
		cache: false,
		async: false,
		context: this,
		dataType: 'json'
	});

	scConfigAjax.done( function( data, textStatus, jqXHR ) {
		if (typeof data !== 'undefined' && typeof data.source_project_id !== 'undefined') {
			XSYNC.xsyncconfig.configuration = data;
			XSYNC.xsyncconfig.anonymizationuploadDisabled = '';
		} else {
			XSYNC.xsyncconfig.beginConfig();
		}
		XSYNC.xsyncconfig.showConfigPanel();
	});

	scConfigAjax.fail( function( data, textStatus, error ) {
		XSYNC.xsyncconfig.beginConfig();
	});
}

XSYNC.xsyncconfig.beginConfig = function() {
	$("#xsync-config-div").html(
		'<input type="button" class="btn1" id="xsync-begin-config" value="Begin Configuration">'
	);
	$("#xsync-begin-config").click(function() {
		XSYNC.xsyncconfig.useDefaultConfig();
		XSYNC.xsyncconfig.initialize();
	});
}

XSYNC.xsyncconfig.useDefaultConfig = function() {
	// Use the defaults to populate config dialog
	XSYNC.xsyncconfig.configuration = {};
	XSYNC.xsyncconfig.configuration.source_project_id = XNAT.data.context.project;
	XSYNC.xsyncconfig.configuration.sync_frequency = 'weekly';
	XSYNC.xsyncconfig.configuration.sync_new_only = true;
	XSYNC.xsyncconfig.configuration.identifiers = 'use_local';
	XSYNC.xsyncconfig.configuration.remote_url = 'http://';
	XSYNC.xsyncconfig.configuration.remote_project_id = '';
	XSYNC.xsyncconfig.configuration.projectresources = [];
	XSYNC.xsyncconfig.configuration.subjectresources = [];
	XSYNC.xsyncconfig.configuration.subjectassessors = [];
	XSYNC.xsyncconfig.configuration.imagingsessions = [];
	XSYNC.xsyncconfig.anonymizationuploadDisabled = 'disabled';
	// XSYNC.xsyncconfig.submitConfig();
	XSYNC.xsyncconfig.editConfig();
}

XSYNC.xsyncconfig.showConfigPanel = function() {
	var xsyncConfigDiv = $("#xsync-config-div");

	xsyncConfigDiv.append(
		'<div>' +
			'<input type="button" class="btn1 xsync-submit-button" id="xsync-edit-config" value="Edit Configuration">' +
			'<input type="button" class="btn1 xsync-submit-button" id="xsync-credentials" value="Remote Credentials">' +
			'<input type="button" class="btn1 xsync-submit-button" id="xsync-upload-anonymization" value="Configure Anonymization">' +
		'</div> ' +
		'<br>'
	);

	$("#xsync-edit-config").click( function() {
		XSYNC.xsyncconfig.editConfig();
	});
	$("#xsync-credentials").click( function() {
		XSYNC.credentialsconfig.enterCredentials();
	});
	$("#xsync-upload-anonymization").click( function() {
		XSYNC.xsyncconfig.submitDICOMAnonymization();
	});

	XSYNC.reporting.showHistoryTable();
}

/*
	Remote Authentication
 */

XSYNC.credentialsconfig.beginConfig = function() {
	$("#xsync-credentials-div").html(
		'<input type="button" id="xsync-begin-credentials" value="Enter or Update Remote Site Credentials">'
	);
	$("#xsync-begin-credentials").click(function() {
		// XSYNC.credentialsconfig.enterCredentials();
		XSYNC.xsyncconfig.editConfig();
	});
}

XSYNC.credentialsconfig.enterCredentials = function() {

	var modalContent =
		"<div>" +
			'<div class = "credentials-header-div credentials-div">' +
				'<h3 style="text-align:center">Enter credentials for ' + XSYNC.xsyncconfig.configuration.remote_url + '</h3>' +
			'</div>' +
			'<input id="xsync-credentials-host" type="hidden" value="' + XSYNC.xsyncconfig.configuration.remote_url + '">' +
			'<div class = "credentials-div">' +
				'<div style="width:100px; float:left;">Username: </div><span><input type="text" size=20 id="xsync-credentials-username">' +
			'</div>' +
			'<div class = "credentials-div">' +
			'	<div style="width:100px; float:left;">Password: </div><span><input type="password" size=20 id="xsync-credentials-password">' +
			'</div>' +
		"</div>";

	var pModalOpts = {
		width: 600,
		height: 380,
		id: 'xmodal-enter-credentials',
		title: "Enter credentials to be used for XSync transfers for this project",
		content: modalContent,
		ok: 'show',
		okLabel: 'Continue',
		okAction: function(modl){
			var credHost = $("#xsync-credentials-host").val();
			var credUser = $("#xsync-credentials-username").val();
			var credPassword = $("#xsync-credentials-password").val();
			var remote
			var tokenData = {
				url: credHost + "/data/services/tokens/issue",
				method: "GET",
				username: credUser,
				password: credPassword
			};

			var credentialsAjax = $.ajax({
				type : "POST",
		 		url: serverRoot + '/xapi/xsync/remoteREST?XNAT_CSRF=' + window.csrfToken,
				cache: false,
				async: true,
				dataType: 'json',
				data:  JSON.stringify(tokenData),
				contentType: "application/json; charset=utf-8"
			 });

			credentialsAjax.done( function( data, textStatus, jqXHR ) {

				if (typeof data !== 'undefined' && typeof data.secret !== 'undefined') {

					var formData = {
						host: $("#xsync-credentials-host").val(),
						localProject: XNAT.data.context.project,
						alias: data.alias,
						secret: data.secret,
						username:credUser
					};
					var saveCredentials = $.ajax({
						type : "POST",
				 		url: serverRoot+'/xapi/xsync/projects/' + XNAT.data.context.project + '/saveRemoteCredentials?XNAT_CSRF=' + window.csrfToken,
						cache: false,
						async: true,
						dataType: 'text',
						data:  JSON.stringify(formData),
						contentType: "application/json; charset=utf-8"
					 });
					saveCredentials.done( function( data, textStatus, jqXHR ) {
						if (jqXHR.status == 202) {
							xmodal.message(
									'Credentials saved',' WARNING: ' + jqXHR.responseText + '\n' + 
									'Successfully saved credentials for remote server ' +
									$("#xsync-credentials-host").val() 
								);
						}else {
							xmodal.message(
								'Credentials saved','Successfully saved credentials for remote server ' +
								$("#xsync-credentials-host").val()
							);
						}
						modl.close();
					});
					saveCredentials.fail( function( data, textStatus, jqXHR ) {
						xmodal.message(
							'Error','Could not save credentials for remote server '  +
							$("#xsync-credentials-host").val() + ' Cause: ' + data.statusText + " Details: " + data.responseText
						);
						modl.close();
					});

				} else {
					console.log(serverRoot+'/xapi/xsync/projects/' + XNAT.data.context.project + '/saveRemoteCredentials?XNAT_CSRF=' + window.csrfToken)
					xmodal.message('Error','ERROR:  Could not get alias token.  Please check username and password and try again.');
				}

			});
				credentialsAjax.fail( function( data, textStatus, error ) {
					console.log(serverRoot+'/xapi/xsync/projects/' + XNAT.data.context.project + '/saveRemoteCredentials?XNAT_CSRF=' + window.csrfToken)
					xmodal.message('Error','ERROR:  Could not get alias token.  Please check username and password and try again.');
			});

		 },
		okClose: false,
		cancel: 'Cancel',
		cancelLabel: 'Cancel',
		cancelAction: function() {
		 	xmodal.close(XNAT.app.abu.abuConfigs.modalOpts.id);
		 },
		closeBtn: 'hide'
	};
	xmodal.open(pModalOpts);
	$('#xsync-credentials-username').focus();
}

XSYNC.xsyncconfig.checkCredentials = function() {

	this.checkCredentialsResult = false;
	var formData = {
		host: XSYNC.xsyncconfig.configuration.remote_url,
		localProject: XNAT.data.context.project
	};
	var saveCredentials = $.ajax({
		type : "POST",
		url: serverRoot+'/xapi/xsync/projects/' + XNAT.data.context.project + '/checkRemoteCredentials?XNAT_CSRF=' + window.csrfToken,
		cache: false,
		async: false,
		dataType: 'text',
		data:  JSON.stringify(formData),
		contentType: "application/json; charset=utf-8"
	});
	saveCredentials.done( function( data, textStatus, jqXHR ) {
		XSYNC.xsyncconfig.checkCredentialsResult = true;
	});
	return this.checkCredentialsResult;
}

/*
	Configuration Settings
 */

XSYNC.xsyncconfig.editConfig = function() {
	XSYNC.xsyncconfig.modal = xmodal.open({
		title: "Project Sync Settings for " + XNAT.data.context.project,
		content: '<div id="xsync-config-dialog"></div>',
		height: '75%',
		buttons: {
			submit: {
				label: "Submit",
				action: function(obj) {
					var form = obj.$modal.find('form')[0];
					var jsonObject = form2js(form);
					jsonObject.source_project_id = XNAT.data.context.project;
					XSYNC.xsyncconfig.submitConfig(JSON.stringify(jsonObject));
					$(form).triggerHandler('reload-data')
				}
			},
			close: {
				label: "Cancel"
			}
		},
		beforeShow: function(obj){
			var spawnerConfig = spawnConfig();
			var $wrapper = obj.$modal.find('#xsync-config-dialog');
			XNAT.spawner.spawn(spawnerConfig).render($wrapper);
		}
	});
};

function spawnConfig() {

	// Basic Config Elements
	

	function configPanel() {
		return {
			kind: 'panel.form',
			title: 'XSync Configuration',
			load: "XSYNC.xsyncconfig.configuration",
			refresh: "/xapi/xsync/projects/" + XNAT.data.context.project,
			action: "#",
			contents: {
				enabled: enabled(),
				newOnly: syncNewOnly(),
				destXnat: remoteUrl(),
				destProjectId: remoteProject(),
				frequency: frequency(),
				identifiers: identifiers(),
				advancedConfigHeading: {
					kind: 'panel.element',
					label: '<h3>Advanced Settings</h3>'
				},
				projectResources: {
					tag: "div",
					contents: {
						projectResourceSelect:
							syncTypeSelector("project_resources.sync_type", "Project Resources"),
						projectResourceInput:
							resourceInput("project_resources.sync_type")
					}
				},
				subjectResources: {
					tag: "div",
					contents: {
						subjectResourceSelect:
							syncTypeSelector("subject_resources.sync_type", "Subject Resources"),
						subjectResourceInput:
							resourceInput("subject_resources.sync_type")
					}
				}

			},
			footer: false
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
				daily: 'Daily',
				weekly: 'Weekly',
				monthly: 'Monthly'
			}
		}
	}

	function syncNewOnly() {
		return {
			id: 'xsync-config-newonly',
			kind: 'panel.input.checkbox',
			name: 'sync_new_only',
			label: 'New Data Only'
		}
	}

	function remoteUrl() {
		return {
			kind: 'panel.input.text',
			id: 'xsync-config-remote-url',
			name: 'remote_url',
			label: 'Destination XNAT',
			// value: XSYNC.xsyncconfig.configuration.remote_url
		}
	}

	function remoteProject() {
		return {
			kind: 'panel.input.text',
			id: 'remote_project_id',
			name: 'remote_project_id',
			label: 'Destination Project',
			// value: XSYNC.xsyncconfig.configuration.remote_project_id
		}
	}

	function identifiers() {
		return {
			kind: 'panel.select.menu',
			name: 'identifiers',
			id: 'xsync-config-identifiers',
			label: 'Identifiers',
			// value: XSYNC.xsyncconfig.configuration.identifiers,
			options: {
				use_local: 'Local',
				use_remote: 'Remote'
			}
		}
	}

	// Config UI Common Widgets

	function syncTypeSelector(name, label) {
		return {
			kind: 'panel.select.menu',
			name: name,
			id: name.replace(/\./g, '') + '_select_menu_id',
			label: label,
			options: {
				all: 'All',
				none: 'None',
				include: 'Include',
				exclude: 'Exclude'
			},
			element: {
				onchange: function() {
					showHideInput(this, name)
				}
			}
		}
	}

	// Function level map to keep track of which text inputs should be visible
	var showTextInput = {};

	function resourceInput(name) {
		showTextInput[name] = false;

		return {
			kind: 'input.text',
			name: name + '.resource_list',
			id: name.replace(/\./g, '') + '_input_text_id',
			size: 100
		}
	}

	function xsiTypeInput() {}


	// Config UI Helpers

	function showHideInput(selector, name) {
		// jquery doesn't like to select ids with periods
		var inputId = name.replace(/\./g, '') + '_input_text_id';
		var $textInput = $('#'+inputId);

		// show or hide input text box
		if ((selector.value == "include" || selector.value == "exclude") && !showTextInput[name]) {
			$textInput.show();
			showTextInput[name] = true;
		} else if ((selector.value == "all" || selector.value == "none" && showTextInput[name])) {
			// remove input contents and hide
			$textInput.hide();
			$textInput.empty();
			showTextInput[name] = false;
		}
	}

	return {
		root: configPanel()
	};
}

XSYNC.xsyncconfig.submitConfig = function(jsonString) {

	if (XSYNC.xsyncconfig.checkCredentials()) {
		XSYNC.xsyncconfig.saveConfig(jsonString);
		return;
	}

	var modalContent =
		"<div>" +
			'<div class = "credentials-header-div credentials-div">' +
				'<h3 style="text-align:center">Enter credentials for ' +  $('#xsync-config-remote-url').val() + '</h3>' +
			'</div>' +
			'<div class = "credentials-div">' +
				'<div style="width:100px; float:left;">Username: </div><span><input type="text" size=20 id="xsync-credentials-username">' +
			'</div>' +
			'<div class = "credentials-div">' +
				'<div style="width:100px; float:left;">Password: </div><span><input type="password" size=20 id="xsync-credentials-password">' +
			'</div>' +
		"</div>";

	var pModalOpts = {
		width: 600,
		height: 380,
		id: 'xmodal-enter-credentials',
		title: "Credentials required for remote server",
		content: modalContent,
		ok: 'show',
		okLabel: 'Continue',
		okAction: function(modl){
			XSYNC.xsyncconfig.updateCredentialsAndSaveConfig();
			modl.close();
		 },
		okClose: false,
		cancel: 'Cancel',
		cancelLabel: 'Cancel',
		cancelAction: function(){ xmodal.close(XNAT.app.abu.abuConfigs.modalOpts.id); },
		closeBtn: 'hide'
	};

	xmodal.open(pModalOpts);
	$('#xsync-credentials-username').focus();
}

XSYNC.xsyncconfig.saveConfig = function(newJson) {
	var xsyncConfigAjax = $.ajax({
		type: "POST",
 		url: serverRoot+'/xapi/xsync/projects/' + XNAT.data.context.project + '?XNAT_CSRF=' + window.csrfToken,
		cache: false,
		async: true,
		// dataType: 'json',
		data: newJson,
		contentType: "application/json; charset=utf-8"
	 });

	xsyncConfigAjax.done( function( data, textStatus, jqXHR ) {
		$("#xsync-annon_add-config").attr("disabled", false);
		xmodal.message('Saved','The XSync configuration has been saved');
		XSYNC.xsyncconfig.modal.close();
		console.log(newJson);
	});

	xsyncConfigAjax.fail( function( data, textStatus, error ) {
		console.log(newJson);
		xmodal.message('Error',  'Configuration was not successfully saved (' + error + ')');
	});
}

XSYNC.xsyncconfig.updateCredentialsAndSaveConfig = function() {

	var credHost = $("#xsync-config-remote-url").val();
	var credUser = $("#xsync-credentials-username").val();
	var credPassword = $("#xsync-credentials-password").val();
	var tokenData = { url:credHost + "/data/services/tokens/issue/user/" + credUser, method: "GET", user: credUser, password: credPassword };

	var credentialsAjax = $.ajax({
		type : "POST",
 		url: serverRoot + '/data/xsync/remoteREST?XNAT_CSRF=' + window.csrfToken,
		cache: false,
		async: true,
		dataType: 'json',
		data:  JSON.stringify(tokenData),
		contentType: "application/json; charset=utf-8"
	 });
	credentialsAjax.done( function( data, textStatus, jqXHR ) {

		if (typeof data !== 'undefined' && typeof data.secret !== 'undefined') {

			var formData = {
				host: $("#xsync-config-remote-url").val(),
				localProject: XNAT.data.context.project,
				alias: data.alias,
				secret: data.secret
			};
			var saveCredentials = $.ajax({
				type : "POST",
		 		url: serverRoot+'/xapi/xsync/projects/' + XNAT.data.context.project + '/saveRemoteCredentials?XNAT_CSRF=' + window.csrfToken,
				cache: false,
				async: true,
				dataType: 'text',
				data:  JSON.stringify(formData),
				contentType: "application/json; charset=utf-8"
			 });
			saveCredentials.done( function( data, textStatus, jqXHR ) {
					XSYNC.xsyncconfig.saveConfig();
			});
			saveCredentials.fail( function( data, textStatus, jqXHR ) {
							xmodal.message('Error','Could not save credentials for remote server ' + $("#xsync-config-remote-url").val());
							modl.close();
			});


		} else {
			xmodal.message('Error','ERROR:  Could not get alias token.  Please check username and password and try again.');
		}
	});
		credentialsAjax.fail( function( data, textStatus, error ) {
			xmodal.message('Error','ERROR:  Could not get alias token');
	});
}

/*
	DICOM Anonymization
 */

XSYNC.xsyncconfig.submitDICOMAnonymization = function() {
	var getAnonymizationScript = $.ajax({
		type : "GET",
		url: serverRoot+'/xapi/xsync/projects/'+XNAT.data.context.project+'/presyncanonymization',
		dataType: 'text'
	});

	getAnonymizationScript.done(function(text) {
		if (text == undefined) {
			text = '';
		}

		var modalContent =
			"<div>" +
				'<div class = "credentials-header-div credentials-div">' +
					'<h3 style="text-align:center">Pre-Sync DICOM Anonymization script ' + '</h3>' +
				'</div>' +
				'<div class = "credentials-div">' +
					'<textarea rows="20" cols="80" id="xsync-dicom-anonymization">'+ text +'</textarea>' +
				'</div>' +
			"</div>";

		var pModalOpts = {
			width: 680,
			height: 580,
			id: 'xmodal-enter-dicom-anonymization',
			title: "DICOM Anonymization script",
			content: modalContent,
			ok: 'show',
			okLabel: 'Save',
			okAction: function(modl){
				XSYNC.xsyncconfig.uploadDicomAnonymization();
				modl.close();
			},
			okClose: false,
			cancel: 'Cancel',
			cancelLabel: 'Cancel',
			enter: false,
			cancelAction: function(){
				xmodal.close(XNAT.app.abu.abuConfigs.modalOpts.id);
			},
			closeBtn: 'hide'
		};
		xmodal.open(pModalOpts);
	});

	getAnonymizationScript.fail( function( data, textStatus, error ) {
		xmodal.message('Error', textStatus + ': Could not retrieve pre-sync DICOM anonymization script (' + error + ')');
	});
}

XSYNC.xsyncconfig.uploadDicomAnonymization = function() {
	var dicomScript = $("#xsync-dicom-anonymization").val();

	var uploadDICOMscriptAjax = $.ajax({
		type : "PUT",
		url:serverRoot+'/xapi/xsync/projects/' + XNAT.data.context.project + '/presyncanonymization?XNAT_CSRF=' + window.csrfToken,
		data:  dicomScript
	});

	uploadDICOMscriptAjax.done( function( data, textStatus, jqXHR ) {
		xmodal.message('Saved','The Pre-Sync DICOM Anonymization has been saved');
		XSYNC.xsyncconfig.anonymizationuploadBtnText = 'Update Pre Sync DICOM Anonymization Script';
		$("#xsync-annon_add-config").attr('value', XSYNC.xsyncconfig.anonymizationuploadBtnText);
	});

	uploadDICOMscriptAjax.fail( function( data, textStatus, error ) {
		xmodal.message('Error', textStatus + ': Pre-Sync DICOM Anonymization was not successfully saved (' + error + ')');
	});
}