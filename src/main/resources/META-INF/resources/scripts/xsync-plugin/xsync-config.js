if (typeof XSYNC === 'undefined') {
	XSYNC = {};
}
if (typeof XSYNC.xsyncconfig === 'undefined') {
	XSYNC.xsyncconfig = {};
}
if (typeof XSYNC.credentialsconfig === 'undefined') {
	XSYNC.credentialsconfig = {};
}

XSYNC.credentialsconfig.initialize = function() {
	var MUST_BE_CONFIGURED = "<h3>XSync has not been configured.  Please select the <b>XSync Configuration</b> tab.</h3>"
	var scConfigAjax = $.ajax({
		type : "GET",
 		url:serverRoot+'/data/projects/' + XNAT.data.context.project +'/resources/synchronization/files/sync_config.json',
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

XSYNC.credentialsconfig.beginConfig = function() {
	$("#xsync-credentials-div").html('<input type="button" id="xsync-begin-credentials" value="Enter or Update Remote Site Credentials">');
	$("#xsync-begin-credentials").click(function() { XSYNC.credentialsconfig.enterCredentials(); });
}

XSYNC.credentialsconfig.enterCredentials = function() {

	var modalContent =
		"<div>" +
			'<div class = "credentials-header-div credentials-div">' +
			'<h3 style="text-align:center">Enter credentials for ' + XSYNC.credentialsconfig.remoteHost + '</h3>' +
			'</div>' +
			'<input id="xsync-credentials-host" type="hidden" value="' + XSYNC.credentialsconfig.remoteHost + '">' +
			'<div class = "credentials-div">' +
			'<div style="width:100px; float:left;">Username: </div><span><input type="text" size=20 id="xsync-credentials-username">' +
			'</div>' +
			'<div class = "credentials-div">' +
			'<div style="width:100px; float:left;">Password: </div><span><input type="password" size=20 id="xsync-credentials-password">' +
			'</div>' +
		"</div>";
	var pModalOpts = {
		width: 740,
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
			var tokenData = { url:credHost + "/data/services/tokens/issue", method: "GET", user: credUser, password: credPassword };
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
									xmodal.message('Credentials saved','Successfully saved credentials for remote server ' + $("#xsync-credentials-host").val());
									modl.close();
								});
					saveCredentials.fail( function( data, textStatus, jqXHR ) {
									xmodal.message('Error','Could not save credentials for remote server ' + $("#xsync-credentials-host").val());
									modl.close();
								});

				} else {
					xmodal.message('Error','ERROR:  Could not get alias token.  Please check username and password and try again.');
				}

			});
				credentialsAjax.fail( function( data, textStatus, error ) {
					xmodal.message('Error','ERROR:  Could not get alias token.  Please check username and password and try again.');
			});

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
 		url:serverRoot+'/data/projects/' + XNAT.data.context.project +'/resources/synchronization/files/sync_config.json',
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
		XSYNC.xsyncconfig.continueInitNew();
	});
	scConfigAjax.fail( function( data, textStatus, error ) {
		XSYNC.xsyncconfig.beginConfig();
	});

}

XSYNC.xsyncconfig.beginConfig = function() {
	$("#xsync-config-div").html('<input type="button" id="xsync-begin-config" value="Begin Configuration">');
	$("#xsync-begin-config").click(function() { XSYNC.xsyncconfig.useDefaultConfig(); });
}

XSYNC.xsyncconfig.useDefaultConfig = function() {
	XSYNC.xsyncconfig.configuration = {};
	XSYNC.xsyncconfig.configuration.project = XNAT.data.context.project;
	XSYNC.xsyncconfig.configuration.sync_frequency = 'weekly';
	XSYNC.xsyncconfig.configuration.auto_sync = 'true';
	XSYNC.xsyncconfig.configuration.identifiers = 'use_local';
	XSYNC.xsyncconfig.configuration.remote_url = 'http://';
	XSYNC.xsyncconfig.configuration.remote_project_id = '';
	XSYNC.xsyncconfig.configuration.projectresources = [];
	XSYNC.xsyncconfig.configuration.subjectresources = [];
	XSYNC.xsyncconfig.configuration.subjectassessors = [];
	XSYNC.xsyncconfig.configuration.imagingsessions = [];
	XSYNC.xsyncconfig.anonymizationuploadDisabled = 'disabled';
	XSYNC.xsyncconfig.continueInit();

}

XSYNC.xsyncconfig.continueInitNew = function() {
	$("#xsync-config-div").append(
		'<input type="button" class="xsync-submit-button" id="xsync-edit-config" value="Edit Configuration">'
	);
	$("#xsync-edit-config").click( function() {
		XSYNC.xsyncconfig.editConfig();
	});
	$("#xsync-config-div").append(
		'<input type="button" id="xsync-begin-credentials" value="Remote Credentials"> <br>'
	);

	XSYNC.reporting.showHistoryTable();
}


XSYNC.xsyncconfig.editConfig = function() {

	XSYNC.xsyncconfig.modal = xmodal.open({
		title: "Xsync Configuration for " + XNAT.data.context.project,
		content: '<div id="xsync-config-dialog"></div>',
		height: 800,
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
		}
	});
};

function spawnConfig() {

	function configPanel(contents) {
		return {
			kind: 'panel',
			// kind: 'panel.form',
			// contentType: 'json',
			// action: '/xapi/xsync/projects/' + XNAT.data.context.project,

			// action: function() {
			// 	XSYNC.xsyncconfig.submitConfig()
			// },

			action: "#",

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
			kind: 'panel.element',
			element: {
				className: '',
				id: 'xsync-enebled-checkbox'
			},
			contents: {
				"enabled-checkbox": enabledElement()
			}
		}
	}

	function enabledElement() {
		return {
			id: 'xsync-config-enabled',
			kind: 'input.checkbox',
			name: 'enabled-switch',
			label: 'Enabled',
			checked: true,
			value: 'true',
			text: {
				on: "enabled",
				off: "disabled"
			}
		}
	}

	function syncNewOnly() {
		return {
			kind: 'panel.element',
			element: {
				className: '',
				id: 'xsync-newonly-checkbox'
			},
			contents: {
				"enabled-checkbox": syncNewOnlyElement()
			}
		}
	}

	function syncNewOnlyElement() {
		return {
			id: 'xsync-config-newonly',
			kind: 'input.checkbox',
			name: 'new-only-switch',
			label: 'New Data Only',
			checked: true
		}
	}

	function remoteUrl() {
		return {
			kind: 'panel.element',
			// label: 'Destination XNAT',
			element: {
				className: '',
				id: 'xsync-remote-url-textbox'
			},
			contents: {
				"Destination XNAT": remoteUrlElement()
			}
		}
	}

	function remoteUrlElement() {
		return {
			kind: 'input.text',
			id: 'xsync-config-remote-url',
			value: XSYNC.xsyncconfig.configuration.remote_url,
			element: {
				onchange: function(){
					// console.log(this.value)
				}
			}
		}
	}

	function remoteProject() {
		return {
			kind: 'panel.element',
			// label: 'Destination XNAT',
			element: {
				className: '',
				id: 'xsync-remote-project'
			},
			contents: {
				"Destination Project": remoteProjectElement()
			}
		}
	}

	function remoteProjectElement() {
		return {
			kind: 'input.text',
			id: 'xsync-config-remote-project',
			value: XSYNC.xsyncconfig.configuration.remote_project_id,
			element: {
				onchange: function(){
					// console.log(this.value)
				}
			}
		}
	}


	function frequency() {
		return {
			kind: 'panel.element',
			label: 'Sync Frequency',
			element: {
				className: '',
				id: randomID('x', false)
			},
			contents: {
				frequency: frequencyElement()
			}
		}
	}

	function frequencyElement() {
		return {
			kind: 'select.menu',
			id: 'xsync-config-frequency',
			value: XSYNC.xsyncconfig.configuration.sync_frequency,
			options: [
				{
					value: 'daily',
					text: 'Daily',
					selected: true
				},
				{
					value: 'weekly',
					text: 'Weekly'
				},
				{
					value: 'monthly',
					text: 'Monthly'
				}
			],
			element: {
				onchange: function(){
					// alert(this.value)
				}
			}
		}
	}

	function identifiers() {
		return {
			kind: 'panel.element',
			label: 'Use Identifiers',
			element: {
				className: '',
				id: 'xsync-identifiers'
			},
			contents: {
				ids: identifiersElement()
			}
		}
	}

	function identifiersElement() {
		return {
			kind: 'select.menu',
			id: 'xsync-config-identifiers',
			value: XSYNC.xsyncconfig.configuration.identifiers,
			options: [
				{
					value: 'use_local',
					text: 'Local',
					selected: true
				},
				{
					value: 'use_remote',
					text: 'Remote'
				},
				{
					value: 'use_random',
					text: 'Random'
				}
			],
			element: {
				onchange: function(){
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

	XSYNC.xsyncconfig.saveConfig();
	return;

	if (XSYNC.xsyncconfig.checkCredentials()) {

		XSYNC.xsyncconfig.saveConfig();
		return;

	}

	var modalContent =
		"<div>" +
			'<div class = "credentials-header-div credentials-div">' +
			'<h3 style="text-align:center">Enter credentials for ' +  $("#xsync-config-remote-url").val() + '</h3>' +
			'</div>' +
			'<div class = "credentials-div">' +
			'<div style="width:100px; float:left;">Username: </div><span><input type="text" size=20 id="xsync-credentials-username">' +
			'</div>' +
			'<div class = "credentials-div">' +
			'<div style="width:100px; float:left;">Password: </div><span><input type="password" size=20 id="xsync-credentials-password">' +
			'</div>' +
		"</div>";
	var pModalOpts = {
		width: 740,
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

XSYNC.xsyncconfig.checkCredentials = function() {

		this.checkCredentialsResult = false;
		var formData = {
			host: $("#xsync-config-remote-url").val(),
			localProject: XNAT.data.context.project,
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

XSYNC.xsyncconfig.submitDICOMAnonimization = function() {
	var modalContent =
		"<div>" +
			'<div class = "credentials-header-div credentials-div">' +
			'<h3 style="text-align:center">Enter Pre-Sync DICOM Anonimization  script ' + '</h3>' +
			'</div>' +
			'<div class = "credentials-div">' +
			'<textarea rows="20" cols="80" id="xsync-dicom-anonymization"></textarea>' +
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
		cancelAction: function(){ xmodal.close(XNAT.app.abu.abuConfigs.modalOpts.id); },
		closeBtn: 'hide'
	};
	xmodal.open(pModalOpts);
}

XSYNC.xsyncconfig.uploadDicomAnonymization = function() {
	var dicomScript = $("#xsync-dicom-anonymization").val();
	console.log(dicomScript);
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
				xmodal.message('Error','ERROR:  Pre-Sync DICOM Anonymization was not successfully saved (' + textStatus + ')');
			});
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
	newJson.enabled = true;
	newJson.sync_frequency = $("#xsync-config-frequency").val();
	newJson.sync_new_only = true;
	newJson.source_project_id = XNAT.data.context.project;
	newJson.remote_project_id = $("#xsync-config-remote-project").val();
	newJson.remote_url = $("#xsync-config-remote-url").val();
	newJson.identifiers = $("#xsync-config-identifiers").val();

	console.log(newJson);

	return newJson;

	/////////////////////////////////////////////////////////////////////////

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