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
			var tokenData = {
				url: credHost + "/data/services/tokens/issue",
				method: "GET",
				user: credUser,
				password: credPassword
			};

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
						host: $("#xsync-credentials-host").val(),
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
						xmodal.message(
							'Credentials saved','Successfully saved credentials for remote server ' +
							$("#xsync-credentials-host").val()
						);
						modl.close();
					});
					saveCredentials.fail( function( data, textStatus, jqXHR ) {
						xmodal.message(
							'Error','Could not save credentials for remote server ' +
							$("#xsync-credentials-host").val()
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

function advConfig(){

	var $container = $('#xsync-config-dialog');

	XSYNC.xsyncconfig.render = XNAT.xhr.getJSON({
		url: XNAT.url.rootUrl('/xapi/spawner/resolve/xsync/config'),
		success: function(json){
			var xsyncUI = XNAT.spawner.spawn(json);
			$container.append(xsyncUI.get());
			xsyncUI.render($container);
		}
	});

	XSYNC.xsyncconfig.render.done(function(){

		function addResource(container, resourceType){
			var $container = $$(container);
			var $lastInput = $container.find('input').last();
			var lastIndex = $lastInput.dataAttr('index');
			var $newInput = $lastInput.clone();
			$newInput.val('').dataAttr('index', lastIndex + 1);
			$newInput.attr('name', resourceType + '[' + (lastIndex + 1) + ']');
			$container.append($newInput);
		}

		$('#project-sync-config').on('click', 'button.add-resource', function(){
			var resourceType = $(this).dataAttr('resourceType');
			var container = $(this).dataAttr('resourceList');
			addResource(container, resourceType);
		});
	});
}

XSYNC.xsyncconfig.editConfig = function() {
	XSYNC.xsyncconfig.modal = xmodal.open({
		title: "Project Sync Settings for " + XNAT.data.context.project,
		content: '<div id="xsync-config-dialog"></div>',
		height: '75%',
		buttons: {
			submit: {
				label: "Submit",
				action: function() {
					XSYNC.xsyncconfig.submitConfig()
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
			// advConfig();
		}
	});
};

function spawnConfig() {
	function configPanel(contents) {
		return {
			kind: 'panel',
			contents: {
				"Enabled": enabled(),
				"New Data Only": syncNewOnly(),
				"Destination XNAT": remoteUrl(),
				"Destination Project ID": remoteProject(),
				frequency: frequency(),
				identifiers: identifiers()
			}
		}
	}

	function enabled() {
		return {
			id: 'xsync-config-enabled',
			kind: 'panel.input.checkbox',
			name: 'enabled-switch',
			label: 'Enabled',
			checked: XSYNC.xsyncconfig.configuration.enabled,
			value: 'true',
			text: {
				on: "enabled",
				off: "disabled"
			}
		}
	}

	function syncNewOnly() {
		return {
			id: 'xsync-config-newonly',
			kind: 'panel.input.checkbox',
			name: 'new-only-switch',
			label: 'New Data Only',
			checked: XSYNC.xsyncconfig.configuration.sync_new_only
		}
	}

	function remoteUrl() {
		return {
			kind: 'panel.input.text',
			id: 'xsync-config-remote-url',
			//name: '',
			label: 'Destination XNAT',
			value: XSYNC.xsyncconfig.configuration.remote_url,
			element: {
				onchange: function () {
					// console.log(this.value)
				}
			}
		}
	}

	function remoteProject() {
		return {
			kind: 'panel.input.text',
			id: 'xsync-config-remote-project',
			//name: '',
			label: 'Destination Project',
			value: XSYNC.xsyncconfig.configuration.remote_project_id,
			element: {
				onchange: function () {
					// console.log(this.value)
				}
			}
		}
	}

	function frequency() {
		return {
			kind: 'panel.select.menu',
			id: 'xsync-config-frequency',
			// name: '',
			label: 'Sync Frequency',
			value: XSYNC.xsyncconfig.configuration.sync_frequency,
			options: {
				daily: 'Daily',
				weekly: 'Weekly',
				monthly: 'Monthly'
			},
			element: {
				onchange: function () {
					// alert(this.value)
				}
			}
		}
	}

	function identifiers() {
		return {
			kind: 'panel.select.menu',
			// name: '',
			id: 'xsync-config-identifiers',
			label: 'Use Identifiers',
			value: XSYNC.xsyncconfig.configuration.identifiers,
			options: {
				use_local: 'Local',
				use_remote: 'Remote',
				use_random: 'Random'
			},
			element: {
				onchange: function () {
					// alert(this.value)
				}
			}
		}
	}

	return {
		root: configPanel()
	};
}

XSYNC.xsyncconfig.submitConfig = function() {

	if (XSYNC.xsyncconfig.checkCredentials()) {
		XSYNC.xsyncconfig.saveConfig();
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

XSYNC.xsyncconfig.saveConfig = function() {
	var newJson = XSYNC.xsyncconfig.constructNewJson();

	var xsyncConfigAjax = $.ajax({
		type : "POST",
 		url:serverRoot+'/xapi/xsync/projects/' + XNAT.data.context.project + '?XNAT_CSRF=' + window.csrfToken,
		cache: false,
		async: true,
		data:  JSON.stringify(newJson),
		contentType: "application/json; charset=utf-8"
	 });

	xsyncConfigAjax.done( function( data, textStatus, jqXHR ) {
		$("#xsync-annon_add-config").attr("disabled", false);
		xmodal.message('Saved','The XSync configuration has been saved');
		XSYNC.xsyncconfig.modal.close();
		console.log(JSON.stringify(newJson));
	});

	xsyncConfigAjax.fail( function( data, textStatus, error ) {
		console.log(newJson);
		console.log(JSON.stringify(newJson));
		xmodal.message('Error','ERROR:  Configuration was not successfully saved (' + textStatus + ')');
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

XSYNC.xsyncconfig.constructNewJson = function() {
	var newJson = {};
	newJson.enabled = $("#xsync-config-enabled").val();
	newJson.sync_frequency = $("#xsync-config-frequency").val();
	newJson.sync_new_only =  $("#xsync-config-newonly").val();
	newJson.source_project_id = XNAT.data.context.project;
	newJson.remote_project_id = $("#xsync-config-remote-project").val();
	newJson.remote_url = $("#xsync-config-remote-url").val();
	newJson.identifiers = $("#xsync-config-identifiers").val();

	console.log(newJson);

	return newJson;

	////////////////////////////////////////////
	// TODO
	////////////////////////////////////////////

	newJson.projectresources = $(".project-resource-input").map(function(){
		return this.value;
	});

	newJson.projectresources = [];
	$(".project-resource-input").each(function() {
		var newresource = $(this).val();
		if (typeof newresource == undefined || newresource.length<1) {
			return true;
		}
		newJson.projectresources.push(newresource);
	});
	newJson.subjectresources = [];
	$(".subject-resource-input").each(function() {
		var newresource = $(this).val();
		if (typeof newresource == undefined || newresource.length<1) {
			return true;
		}
		newJson.subjectresources.push(newresource);
	});
	newJson.subjectassessors = [];
	$(".xsync-subject-assessor-div").each(function() {
		var checkval = $(this).find(".subject-assessor-xsitype-input").val();
		if (typeof checkval == undefined || checkval.length<1) {
			return true;
		}
		var subject_assessor = {};
		subject_assessor.xsiType = $(this).find(".subject-assessor-xsitype-input").val();
		subject_assessor.needs_ok_to_sync = JSON.parse($(this).find(".subject-assessor-needsok-input").val().toLowerCase());
		subject_assessor.resources = [];
		$(this).find(".subject-assessor-resource-input").each(function() {
			var newresource = $(this).val();
			if (typeof newresource == undefined || newresource.length<1) {
				return true;
			}
			subject_assessor.resources.push(newresource);
		});
		newJson.subjectassessors.push(subject_assessor);
	});
	newJson.imagingsessions = [];
	$(".xsync-imaging-session-div").each(function() {
		var checkval = $(this).find(".imaging-session-xsitype-input").val();
		if (typeof checkval == undefined || checkval.length<1) {
			return true;
		}
		var imaging_session = {};
		imaging_session.xsiType = $(this).find(".imaging-session-xsitype-input").val();
		imaging_session.needs_ok_to_sync = JSON.parse($(this).find(".imaging-session-needsok-input").val().toLowerCase());
		imaging_session.anonymize = JSON.parse($(this).find(".imaging-session-anonymize-input").val().toLowerCase());
		imaging_session.resources = [];
		$(this).find(".imaging-session-resource-input").each(function() {
			var newresource = $(this).val();
			if (typeof newresource == undefined || newresource.length<1) {
				return true;
			}
			imaging_session.resources.push(newresource);
		});
		imaging_session.scans = [];
		$(this).find(".xsync-imaging-session-scan-div").each(function() {
			var checkval = $(this).find(".imaging-session-scan-type-input").val();
			if (typeof checkval == undefined || checkval.length<1) {
				return true;
			}
			var scan = {};
			scan.type = $(this).find(".imaging-session-scan-type-input").val();
			scan.resources = [];
			$(this).find(".imaging-scan-resource-input").each(function() {
				var newresource = $(this).val();
				if (typeof newresource == undefined || newresource.length<1) {
					return true;
				}
				scan.resources.push(newresource);
			});
			imaging_session.scans.push(scan);
		});
		imaging_session.assessors = [];
		$(this).find(".xsync-imaging-session-assessor-div").each(function() {
			var checkval = $(this).find(".imaging-session-assessor-xsitype-input").val();
			if (typeof checkval == undefined || checkval.length<1) {
				return true;
			}
			var assessor = {};
			assessor.xsiType = $(this).find(".imaging-session-assessor-xsitype-input").val();
			assessor.needs_ok_to_sync = JSON.parse($(this).find(".imaging-session-assessor-needsok-input").val().toLowerCase());
			assessor.resources = [];
			$(this).find(".imaging-assessor-resource-input").each(function() {
				var newresource = $(this).val();
				if (typeof newresource == undefined || newresource.length<1) {
					return true;
				}
				assessor.resources.push(newresource);
			});
			imaging_session.assessors.push(assessor);
		});
		newJson.imagingsessions.push(imaging_session);
	});
	return newJson;
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