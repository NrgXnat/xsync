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
	var MUST_BE_CONFIGURED = "<h3>XSync has not been configured.  Please select the <b>XSync Plugin Configuration</b> tab.</h3>"
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
	$("#xsync-config-div").html('');

	XSYNC.xsyncconfig.showHistoryTable();

}

XSYNC.xsyncconfig.continueInit = function() {


	$("#xsync-config-div").append('<div class="row1">' +
			'<div class="col1">Sync Frequency:</div><div>  <select id="xsync-config-sync-frequency">' +
				'<option value="daily">Daily</option>' +
				'<option value="weekly">Weekly</option>' +
				'<option value="monthly">Monthly</option>' +
				'<option value="on demand">On Demand</option>' +
			'</select>' +
			'</div>' +
		'<div>');
	$("#xsync-config-sync-frequency").val(XSYNC.xsyncconfig.configuration.sync_frequency);

	$("#xsync-config-div").append('<div class="row1">' +
			'<div class="col1">Auto-Sync:</div><div>  <select id="xsync-config-auto-sync">' +
				'<option value="true">True</option>' +
				'<option value="false">False</option>' +
			'</select>' +
			'</div>' +
		'<div>');
	$("#xsync-config-auto-sync").val(XSYNC.xsyncconfig.configuration.auto_sync);

	$("#xsync-config-div").append('<div class="row1">' +
			'<div class="col1">Identifiers:</div><div>  <select id="xsync-config-identifiers">' +
				'<option value="use_local">Use Local</option>' +
				'<option value="use_remote">Use Remote</option>' +
				'<option value="use_random">Use Random</option>' +
				'<option value="use_custom_local">Use Custom Local</option>' +
			'</select>' +
			'</div>' +
		'<div>');
	$("#xsync-config-identifiers").val(XSYNC.xsyncconfig.configuration.identifiers);

	$("#xsync-config-div").append('<div class="row1">' +
			'<div class="col1">Remote URL:</div><div>  <input type="text" id="xsync-config-remote-url" size="60">' +
			'</div>' +
		'<div>');
	$("#xsync-config-remote-url").val(XSYNC.xsyncconfig.configuration.remote_url);


	$("#xsync-config-div").append('<div class="row1">' +
			'<div class="col1">Remote Project:</div><div>  <input type="text" id="xsync-config-remote-project-id" size="30">' +
			'</div>' +
		'<div>');
	$("#xsync-config-remote-project-id").val(XSYNC.xsyncconfig.configuration.remote_project_id);

	$("#xsync-config-div").append('<div class="row1">' +
			'<div class="col1">Project Resources:</div><div>' +
				'<input type="button" class="xsync-button" id="xsync-add-project-resource" value="Add Project Resource">' +
			'</div>' +
			'<div id="xsync-project-resources-div">' +
			'</div>' +
		'<div>');
	for (var i=0; i<XSYNC.xsyncconfig.configuration.projectresources.length; i++) {
		$("#xsync-project-resources-div").append('<div class="xsync-project-resource-div">' +
			'<input type="text" class="project-resource-input" name="project-resource-input" size=20>' +
			'<input type="button" class="xsync-button project-resource-remote" value="Remove" onclick="XSYNC.xsyncconfig.removeResource(this)">' +
			'</div>');
		$($(".xsync-project-resource-div")[i]).find(".project-resource-input").val(XSYNC.xsyncconfig.configuration.projectresources[i]);
	}
	$("#xsync-add-project-resource").click(function() {
		$("#xsync-project-resources-div").append('<div class="xsync-project-resource-div">' +
			'<input type="text" class="project-resource-input" name="project-resource-input" size=20>' +
			'<input type="button" class="xsync-button project-resource-remote" value="Remove" onclick="XSYNC.xsyncconfig.removeResource(this)">' +
			'</div>');
	});

	$("#xsync-config-div").append('<div class="row1">' +
			'<div class="col1">Subject Resources:</div><div>' +
				'<input type="button" class="xsync-button" id="xsync-add-subject-resource" value="Add Subject Resource">' +
			'</div>' +
			'<div id="xsync-subject-resources-div">' +
			'</div>' +
		'<div>');
	for (var i=0; i<XSYNC.xsyncconfig.configuration.subjectresources.length; i++) {
		$("#xsync-subject-resources-div").append('<div class="xsync-subject-resource-div">' +
			'<input type="text" class="subject-resource-input" name="subject-resource-input" size=20>' +
			'<input type="button" class="xsync-button subject-resource-remote" value="Remove" onclick="XSYNC.xsyncconfig.removeResource(this)">' +
			'</div>');
		$($(".xsync-subject-resource-div")[i]).find(".subject-resource-input").val(XSYNC.xsyncconfig.configuration.subjectresources[i]);
	}
	$("#xsync-add-subject-resource").click(function() {
		$("#xsync-subject-resources-div").append('<div class="xsync-subject-resource-div">' +
			'<input type="text" class="subject-resource-input" name="subject-resource-input" size=20>' +
			'<input type="button" class="xsync-button subject-resource-remote" value="Remove" onclick="XSYNC.xsyncconfig.removeResource(this)">' +
			'</div>');
	});


	$("#xsync-config-div").append('<div class="row1">' +
			'<div class="col1">Subject Assessors:</div><div>' +
				'<input type="button" class="xsync-button" id="xsync-add-subject-assessor" value="Add Subject Assessor">' +
			'</div>' +
			'<div id="xsync-subject-assessors-div">' +
			'</div>' +
		'<div>');
	for (var i=0; i<XSYNC.xsyncconfig.configuration.subjectassessors.length; i++) {
		var eles = $("#xsync-subject-assessors-div").append(
			'<div class="xsync-subject-assessor-div xsync-container-div">' +
				'<div class="col1">XSI Type:</div>' +
				'<div>' +
					'<input type="text" class="subject-assessor-xsitype-input" name="subject-assessor-xsitype-input" size=40>' +
					'<input type="button" class="xsync-button subject-assessors-remote" value="Remove" onclick="XSYNC.xsyncconfig.removeSubjectAssessor(this)">' +
				'</div>' +
				'<div class="col1">Requires OK to sync:</div>' +
				'<div>' +
					'<select class="subject-assessor-needsok-input">' +
						'<option value="true">True</option>' +
						'<option value="false">False</option>' +
					'</select>' +
				'</div>' +
				'<div class="xsync-subject-assessor-resources-div">' +
					'<div class="col1">Resources:</div><div>' +
						'<input type="button" class="xsync-button" id="xsync-add-subject-assessor-resource" value="Add Resource" onclick="XSYNC.xsyncconfig.addSubjectAssessorResource(this)">' +
					'</div>' +
				'</div>' +
			'</div>'
			);
		$($(".xsync-subject-assessor-div")[i]).find(".subject-assessor-xsitype-input").val(XSYNC.xsyncconfig.configuration.subjectassessors[i].xsiType);
		$($(".xsync-subject-assessor-div")[i]).find(".subject-assessor-needsok-input").val(XSYNC.xsyncconfig.configuration.subjectassessors[i].needs_ok_to_sync.toString());
		var resourceDiv = $($(".xsync-subject-assessor-div")[i]).find(".xsync-subject-assessor-resources-div");
		for (var j=0; j<XSYNC.xsyncconfig.configuration.subjectassessors[i].resources.length; j++) {
			$(resourceDiv).append(
				'<div class="xsync-subject-assessor-resource-div">' +
					'<input type="text" class="subject-assessor-resource-input" name="subject-assessor-resource-input" size=20>' +
					'<input type="button" class="xsync-button subject-assessor-resource-remote" value="Remove" onclick="XSYNC.xsyncconfig.removeResource(this)">' +
				'</div>');
			$($(resourceDiv).find('.xsync-subject-assessor-resource-div')[j]).find(".subject-assessor-resource-input").val(XSYNC.xsyncconfig.configuration.subjectassessors[i].resources[j]);
		}
	}
	$("#xsync-add-subject-assessor").click(function() {
		$("#xsync-subject-assessors-div").append(
			'<div class="xsync-subject-assessor-div xsync-container-div">' +
				'<div class="col1">XSI Type:</div>' +
				'<div>' +
					'<input type="text" class="subject-assessor-xsitype-input" name="subject-assessor-xsitype-input" size=40>' +
					'<input type="button" class="xsync-button subject-assessors-remote" value="Remove" onclick="XSYNC.xsyncconfig.removeSubjectAssessor(this)">' +
				'</div>' +
				'<div class="col1">Requires OK to sync:</div>' +
				'<div>' +
					'<select class="subject-assessor-needsok-input">' +
						'<option value="true">True</option>' +
						'<option value="false">False</option>' +
					'</select>' +
				'</div>' +
				'<div class="xsync-subject-assessor-resources-div">' +
					'<div class="col1">Resources:</div><div>' +
						'<input type="button" class="xsync-button" id="xsync-add-subject-assessor-resource" value="Add Resource" onclick="XSYNC.xsyncconfig.addSubjectAssessorResource(this)">' +
					'</div>' +
				'</div>' +
			'</div>'
		);
	});


	$("#xsync-config-div").append('<div class="row1">' +
			'<div class="col1">Imaging Sessions:</div><div>' +
				'<input type="button" class="xsync-button" id="xsync-add-imaging-session" value="Add Imaging Session">' +
			'</div>' +
			'<div id="xsync-imaging-sessions-div">' +
			'</div>' +
		'<div>');
	for (var i=0; i<XSYNC.xsyncconfig.configuration.imagingsessions.length; i++) {
		var eles = $("#xsync-imaging-sessions-div").append(
			'<div class="xsync-imaging-session-div xsync-container-div">' +
				'<div class="col1">XSI Type:</div>' +
				'<div>' +
					'<input type="text" class="imaging-session-xsitype-input" name="imaging-session-xsitype-input" size=40>' +
					'<input type="button" class="xsync-button imaging-sessions-remote" value="Remove" onclick="XSYNC.xsyncconfig.removeImagingSession(this)">' +
				'</div>' +
				'<div class="col1">Requires OK to sync:</div>' +
				'<div>' +
					'<select class="imaging-session-needsok-input">' +
						'<option value="true">True</option>' +
						'<option value="false">False</option>' +
					'</select>' +
				'</div>' +
				'<div class="col1">Anonymize?:</div>' +
				'<div>' +
					'<select class="imaging-session-anonymize-input">' +
						'<option value="true">True</option>' +
						'<option value="false">False</option>' +
					'</select>' +
				'</div>' +
				'<div class="xsync-imaging-session-resources-div">' +
					'<div class="col1">Resources:</div><div>' +
						'<input type="button" class="xsync-button" id="xsync-add-imaging-session-resource" value="Add Resource" onclick="XSYNC.xsyncconfig.addImagingSessionResource(this)">' +
					'</div>' +
				'</div>' +
				'<div class="xsync-imaging-session-scans-div">' +
					'<div class="col1">Scans:</div><div>' +
						'<input type="button" class="xsync-button" id="xsync-add-imaging-session-scan" value="Add Scan" onclick="XSYNC.xsyncconfig.addImagingSessionScan(this)">' +
					'</div>' +
				'</div>' +
				'<div class="xsync-imaging-session-assessors-div">' +
					'<div class="col1">Assessors:</div><div>' +
						'<input type="button" class="xsync-button" id="xsync-add-imaging-session-assessor" value="Add Assessor" onclick="XSYNC.xsyncconfig.addImagingSessionAssessor(this)">' +
					'</div>' +
				'</div>' +
			'</div>'
			);
		$($(".xsync-imaging-session-div")[i]).find(".imaging-session-xsitype-input").val(XSYNC.xsyncconfig.configuration.imagingsessions[i].xsiType);
		$($(".xsync-imaging-session-div")[i]).find(".imaging-session-needsok-input").val(XSYNC.xsyncconfig.configuration.imagingsessions[i].needs_ok_to_sync.toString());
		$($(".xsync-imaging-session-div")[i]).find(".imaging-session-anonymize-input").val(XSYNC.xsyncconfig.configuration.imagingsessions[i].anonymize.toString());
		var resourceDiv = $($(".xsync-imaging-session-div")[i]).find(".xsync-imaging-session-resources-div");
		for (var j=0; j<XSYNC.xsyncconfig.configuration.imagingsessions[i].resources.length; j++) {
			$(resourceDiv).append(
				'<div class="xsync-imaging-session-resource-div">' +
					'<input type="text" class="imaging-session-resource-input" name="imaging-session-resource-input" size=20>' +
					'<input type="button" class="xsync-button imaging-session-resource-remote" value="Remove" onclick="XSYNC.xsyncconfig.removeResource(this)">' +
				'</div>');
			$($(resourceDiv).find('.xsync-imaging-session-resource-div')[j]).find(".imaging-session-resource-input").val(XSYNC.xsyncconfig.configuration.imagingsessions[i].resources[j]);
		}
		var scanDiv = $($(".xsync-imaging-session-div")[i]).find(".xsync-imaging-session-scans-div");
		for (var j=0; j<XSYNC.xsyncconfig.configuration.imagingsessions[i].scans.length; j++) {
			$(scanDiv).append(
				'<div class="xsync-imaging-session-scan-div">' +
					'<input type="text" class="imaging-session-scan-type-input" name="imaging-session-scan-type-input" size=20>' +
					'<input type="button" class="xsync-button imaging-session-scan-remote" value="Remove" onclick="XSYNC.xsyncconfig.removeScan(this)">' +
					'<div class="xsync-imaging-scan-resources-div">' +
						'<div class="col2">Resources:</div><div>' +
							'<input type="button" class="xsync-button" id="xsync-add-imaging-scan-resource" value="Add Resource" onclick="XSYNC.xsyncconfig.addImagingScanResource(this)">' +
						'</div>' +
					'</div>' +
				'</div>');
			$($(scanDiv).find('.xsync-imaging-session-scan-div')[j]).find(".imaging-session-scan-type-input").val(XSYNC.xsyncconfig.configuration.imagingsessions[i].scans[j].type);
			var scanResDiv = $($(scanDiv).find('.xsync-imaging-session-scan-div')[j]).find(".xsync-imaging-scan-resources-div");
			for (var k=0; k<XSYNC.xsyncconfig.configuration.imagingsessions[i].scans[j].resources.length; k++) {
				scanResDiv.append(
				'<div class="xsync-imaging-scan-resource-div">' +
					'<input type="text" class="imaging-scan-resource-input" name="imaging-scan-resource-input" size=20>' +
					'<input type="button" class="xsync-button imaging-scan-resource-remote" value="Remove" onclick="XSYNC.xsyncconfig.removeResource(this)">' +
				'</div>');
				$($(scanResDiv).find('.xsync-imaging-scan-resource-div')[k]).find(".imaging-scan-resource-input").val(XSYNC.xsyncconfig.configuration.imagingsessions[i].scans[j].resources[k]);
			}
		}
		var assessorDiv = $($(".xsync-imaging-session-div")[i]).find(".xsync-imaging-session-assessors-div");
		for (var j=0; j<XSYNC.xsyncconfig.configuration.imagingsessions[i].assessors.length; j++) {
			$(assessorDiv).append(
				'<div class="xsync-imaging-session-assessor-div">' +
					'<div style="width:90%">' +
						'<input type="text" class="imaging-session-assessor-xsitype-input" name="imaging-session-assessor-xsitype-input" size=40>' +
						'<input type="button" class="xsync-button imaging-session-assessor-remote" value="Remove" onclick="XSYNC.xsyncconfig.removeImagingSessionAssessor(this)">' +
					'</div>' +
					'<div class="col2">Requires OK to sync:</div>' +
					'<div>' +
						'<select class="imaging-session-assessor-needsok-input">' +
							'<option value="true">True</option>' +
							'<option value="false">False</option>' +
						'</select>' +
					'</div>' +
					'<div class="xsync-imaging-assessor-resources-div">' +
						'<div class="col2">Resources:</div><div>' +
							'<input type="button" class="xsync-button" id="xsync-add-imaging-assessor-resource" value="Add Resource" onclick="XSYNC.xsyncconfig.addImagingAssessorResource(this)">' +
						'</div>' +
					'</div>' +
				'</div>');
			$($(assessorDiv).find('.xsync-imaging-session-assessor-div')[j]).find(".imaging-session-assessor-xsitype-input").val(XSYNC.xsyncconfig.configuration.imagingsessions[i].assessors[j].xsiType);
			$($(assessorDiv).find('.xsync-imaging-session-assessor-div')[j]).find(".imaging-session-assessor-needsok-input").val(XSYNC.xsyncconfig.configuration.imagingsessions[i].assessors[j].needs_ok_to_sync.toString());
			var assessorResDiv = $($(assessorDiv).find('.xsync-imaging-session-assessor-div')[j]).find(".xsync-imaging-assessor-resources-div");
			for (var k=0; k<XSYNC.xsyncconfig.configuration.imagingsessions[i].assessors[j].resources.length; k++) {
				assessorResDiv.append(
				'<div class="xsync-imaging-assessor-resource-div">' +
					'<input type="text" class="imaging-assessor-resource-input" name="imaging-assessor-resource-input" size=20>' +
					'<input type="button" class="xsync-button imaging-assessor-resource-remote" value="Remove" onclick="XSYNC.xsyncconfig.removeResource(this)">' +
				'</div>');
				$($(assessorResDiv).find('.xsync-imaging-assessor-resource-div')[k]).find(".imaging-assessor-resource-input").val(XSYNC.xsyncconfig.configuration.imagingsessions[i].assessors[j].resources[k]);
			}
		}
	}
	$("#xsync-add-imaging-session").click(function() {
		$("#xsync-imaging-sessions-div").append(
			'<div class="xsync-imaging-session-div xsync-container-div">' +
				'<div class="col1">XSI Type:</div>' +
				'<div>' +
					'<input type="text" class="imaging-session-xsitype-input" name="imaging-session-xsitype-input" size=40>' +
					'<input type="button" class="xsync-button imaging-sessions-remote" value="Remove" onclick="XSYNC.xsyncconfig.removeImagingSession(this)">' +
				'</div>' +
				'<div class="col1">Requires OK to sync:</div>' +
				'<div>' +
					'<select class="imaging-session-needsok-input">' +
						'<option value="true">True</option>' +
						'<option value="false">False</option>' +
					'</select>' +
				'</div>' +
				'<div class="col1">Anonymize?:</div>' +
				'<div>' +
					'<select class="imaging-session-anonymize-input">' +
						'<option value="true">True</option>' +
						'<option value="false">False</option>' +
					'</select>' +
				'</div>' +
				'<div class="xsync-imaging-session-resources-div">' +
					'<div class="col1">Resources:</div><div>' +
						'<input type="button" class="xsync-button" id="xsync-add-imaging-session-resource" value="Add Resource" onclick="XSYNC.xsyncconfig.addImagingSessionResource(this)">' +
					'</div>' +
				'</div>' +
				'<div class="xsync-imaging-session-scans-div">' +
					'<div class="col1">Scans:</div><div>' +
						'<input type="button" class="xsync-button" id="xsync-add-imaging-session-scan" value="Add Scan" onclick="XSYNC.xsyncconfig.addImagingSessionScan(this)">' +
					'</div>' +
				'</div>' +
				'<div class="xsync-imaging-session-assessors-div">' +
					'<div class="col1">Assessors:</div><div>' +
						'<input type="button" class="xsync-button" id="xsync-add-imaging-session-assessor" value="Add Assessor" onclick="XSYNC.xsyncconfig.addImagingSessionAssessor(this)">' +
					'</div>' +
				'</div>' +
			'</div>'
		);
	});
	$("#xsync-config-div").append('<input type="button" class="xsync-submit-button" id="xsync-submit-config" value="Submit Configuration">');
	$("#xsync-config-div").append('<input type="button" class="xsync-submit-button" '+ XSYNC.xsyncconfig.anonymizationuploadDisabled  +' id="xsync-annon_add-config" value="' + XSYNC.xsyncconfig.anonymizationuploadBtnText +'">');
	$("#xsync-submit-config").click(function() { XSYNC.xsyncconfig.submitConfig(); });
	$("#xsync-annon_add-config").click(function() { XSYNC.xsyncconfig.submitDICOMAnonimization(); });

}

XSYNC.xsyncconfig.removeResource = function(ele) {
	$(ele).parent().remove();
}

XSYNC.xsyncconfig.removeScan = function(ele) {
	$(ele).parent().remove();
}

XSYNC.xsyncconfig.removeAssessor = function(ele) {
	$(ele).parent().remove();
}

XSYNC.xsyncconfig.removeSubjectAssessor = function(ele) {
	$(ele).parent().parent().remove();
}

XSYNC.xsyncconfig.removeImagingSession = function(ele) {
	$(ele).parent().parent().remove();
}

XSYNC.xsyncconfig.removeImagingSessionAssessor = function(ele) {
	$(ele).parent().parent().remove();
}

XSYNC.xsyncconfig.removeResource = function(ele) {
	$(ele).parent().remove();
}



XSYNC.xsyncconfig.submitConfig = function() {

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
			'</div>'
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
	newJson.project = XSYNC.xsyncconfig.configuration.project;
	newJson.sync_frequency = $("#xsync-config-sync-frequency").val();
	newJson.auto_sync = $("#xsync-config-auto-sync").val();
	newJson.identifiers = $("#xsync-config-identifiers").val();
	newJson.remote_url = $("#xsync-config-remote-url").val();
	newJson.remote_project_id = $("#xsync-config-remote-project-id").val();
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

XSYNC.xsyncconfig.addSubjectAssessorResource = function(ele) {
	$(ele).parent().parent().append(
				'<div class="xsync-subject-assessor-resource-div">' +
					'<input type="text" class="subject-assessor-resource-input" name="subject-assessor-resource-input" size=20>' +
					'<input type="button" class="xsync-button subject-assessor-resource-remote" value="Remove" onclick="XSYNC.xsyncconfig.removeResource(this)">' +
				'</div>');
}

XSYNC.xsyncconfig.addImagingSessionResource = function(ele) {
	$(ele).parent().parent().append(
				'<div class="xsync-imaging-session-resource-div">' +
					'<input type="text" class="imaging-session-resource-input" name="imaging-session-resource-input" size=20>' +
					'<input type="button" class="xsync-button imaging-session-resource-remote" value="Remove" onclick="XSYNC.xsyncconfig.removeResource(this)">' +
				'</div>');
}

XSYNC.xsyncconfig.addImagingSessionScan = function(ele) {
	$(ele).parent().parent().append(
				'<div class="xsync-imaging-session-scan-div">' +
					'<input type="text" class="imaging-session-scan-type-input" name="imaging-session-scan-type-input" size=20>' +
					'<input type="button" class="xsync-button imaging-session-scan-remote" value="Remove" onclick="XSYNC.xsyncconfig.removeScan(this)">' +
					'<div class="xsync-imaging-scan-resources-div">' +
						'<div class="col2">Resources:</div><div>' +
							'<input type="button" class="xsync-button" id="xsync-add-imaging-scan-resource" value="Add Resource" onclick="XSYNC.xsyncconfig.addImagingScanResource(this)">' +
						'</div>' +
					'</div>' +
				'</div>'
				);
}

XSYNC.xsyncconfig.addImagingSessionAssessor = function(ele) {
	$(ele).parent().parent().append(
				'<div class="xsync-imaging-session-assessor-div">' +
					'<div style="width:90%">' +
					'<input type="text" class="imaging-session-assessor-xsitype-input" name="imaging-session-assessor-xsitype-input" size=40>' +
					'<input type="button" class="xsync-button imaging-session-assessor-remote" value="Remove" onclick="XSYNC.xsyncconfig.removeImagingSessionAssessor(this)">' +
					'</div>' +
					'<div class="col2">Requires OK to sync:</div>' +
					'<div>' +
						'<select class="imaging-session-assessor-needsok-input">' +
							'<option value="true">True</option>' +
							'<option value="false">False</option>' +
						'</select>' +
					'</div>' +
					'<div class="xsync-imaging-assessor-resources-div">' +
						'<div class="col2">Resources:</div><div>' +
							'<input type="button" class="xsync-button" id="xsync-add-imaging-assessor-resource" value="Add Resource" onclick="XSYNC.xsyncconfig.addImagingAssessorResource(this)">' +
						'</div>' +
					'</div>' +
				'</div>'
				);
}

XSYNC.xsyncconfig.addImagingScanResource = function(ele) {
	$(ele).parent().parent().append(
				'<div class="xsync-imaging-scan-resource-div">' +
					'<input type="text" class="imaging-scan-resource-input" name="imaging-scan-resource-input" size=20>' +
					'<input type="button" class="xsync-button imaging-scan-resource-remote" value="Remove" onclick="XSYNC.xsyncconfig.removeResource(this)">' +
				'</div>');
}

XSYNC.xsyncconfig.addImagingAssessorResource = function(ele) {
	$(ele).parent().parent().append(
				'<div class="xsync-imaging-assessor-resource-div">' +
					'<input type="text" class="imaging-assessor-resource-input" name="imaging-assessor-resource-input" size=20>' +
					'<input type="button" class="xsync-button imaging-assessor-resource-remote" value="Remove" onclick="XSYNC.xsyncconfig.removeResource(this)">' +
				'</div>');
}

/////////////////////
// Xsync reporting //
/////////////////////

XSYNC.xsyncconfig.showHistoryTable = function() {
	// Displays overview of sync history in table format
	var xsyncHistory = XNAT.table({ className: 'xnat-table sortable' });
	xsyncHistory.tr();
	xsyncHistory.th('Date').th('Status').th('Subjects').th('Experiments').th('Assessments').th('Resources').th('Total Data');

	var getSyncHistory = $.ajax({
		type: 'GET',
		url: serverRoot + '/xapi/xsync/history',
		dataType: 'json'
	});

	getSyncHistory.done(function(data) {
		console.log('Got data ' + data.length);
		var allHistory = [];

		for(var i = 0; i < data.length; i++) {
			var date = new Date(data[i].startDate);
			var historyUri = serverRoot + '/xapi/xsync/history/'+data[i].id;
			var row = [
				'<a onclick=XSYNC.xsyncconfig.showHistoryDetailsModal("'+ historyUri +'")>'+ date.toLocaleDateString() + ' ' + date.toLocaleTimeString() +'</a>',
				data[i].syncStatus,
				data[i].totalSubjects.toString(),
				data[i].totalExperiments.toString(),
				data[i].totalAssessors.toString(),
				data[i].totalResources.toString(),
				data[i].totalDataSynced
			];
			allHistory.push(row);
		}
		xsyncHistory.rows(allHistory);
	});

	var xsyncConfigDiv = $("#xsync-config-div");
	xsyncConfigDiv.append("<h2>Sync History</h2>");
	xsyncConfigDiv.append('<a onclick=showTabModal()>Tab Modal</a>');
	xsyncConfigDiv.append(xsyncHistory.table);
};

XSYNC.xsyncconfig.showHistoryDetailsModal = function(uri) {
	$.ajax({
		type: 'GET',
		url: uri,
		dataType: 'json'
	}).done( function(history) {

		// Create the modal
		var startDate = new Date(history.startDate);

		xmodal.open({
			title:
				'Xsync History for '+ XSYNC.xsyncconfig.configuration.source_project_id +
				' on '+ startDate.toLocaleDateString() + ' ' + startDate.toLocaleTimeString(),
			width: 800,
			height: '95%',
			overflow: 'auto',
			content: '<div id="xsync-details-modal"></div>',
			buttons: {
				close: {
					label: 'Close'
				}
			}
		});

		// Render content into modal
		spawnXsyncHistoryTabs(history);

		// var detailsDiv = $("#xsync-details-modal");
// 		detailsTable.render(detailsDiv);
		// renderXsyncHistoryDiv($('#xsync-subject-details'), subjectRows);
		// renderXsyncHistoryDiv($('#xsync-experiment-details'), experimentRows);
		// renderXsyncHistoryDiv($('#xsync-assessor-details'), assessorRows);
		// renderXsyncHistoryDiv($('#xsync-resource-details'), resourceRows);
	});
};

function spawnXsyncHistoryTabs(history) {

	var startDate = new Date(history.startDate);
	var completeDate = new Date(history.completeDate);

	var overviewTable = XNAT.table({
		className: 'xnat-table xsync-details-table',
		style: {'border': 'none'}
	});
	overviewTable.tr();

	// Collect all the data
	overviewTable.rows([
		['<b>Started</b>', startDate.toLocaleDateString() + ' ' + startDate.toLocaleTimeString()],
		['<b>Completed</b>', completeDate.toLocaleDateString() + ' ' + completeDate.toLocaleTimeString()],
		['<b>Destination XNAT</b>', history.remoteHost],
		['<b>Destination Project</b>', history.remoteProject],
		['<b>Subjects Synced</b>', history.totalSubjects.toString()],
		['<b>Experiments Synced</b>', history.totalExperiments.toString()],
		['<b>Assessors Synced</b>', history.totalAssessors.toString()],
		['<b>Resources Synced</b>', history.totalResources.toString()],
		['<b>Total Data</b>', history.totalDataSynced],
		['<b>Sync User</b>', history.syncUser],
		['<b>Sync Status</b>', history.syncStatus]
	]);

	var overviewTab = {
		kind: 'tab',
		name: 'overviewTab',
		label: 'Overview',
		group: 'xsyncGroup',
		active: 'true',
		contents: {
			overview: {
				kind: 'panel',
				contents: {
					syncStatus: {
						kind: 'panel.element',
						label: 'Status',
						contents: history.syncStatus
					},
					started: {
						kind: 'panel.element',
						label: 'Started',
						contents: startDate.toLocaleDateString()+ ' ' + startDate.toLocaleTimeString()
					},
					completed: {
						kind: 'panel.element',
						label: 'Completed',
						contents: completeDate.toLocaleDateString()+ ' ' + completeDate.toLocaleTimeString()
					},
					destinationXnat: {
						kind: 'panel.element',
						label: 'Destination XNAT',
						contents: history.remoteHost
					},
					remoteProject: {
						kind: 'panel.element',
						label: 'Destination Project',
						contents: history.remoteProject
					},
					totalSubjects: {
						kind: 'panel.element',
						label: 'Total Subjects Synced',
						contents: history.totalSubjects.toString()
					},
					totalExperiments: {
						kind: 'panel.element',
						label: 'Total Experiments Synced',
						contents: history.totalExperiments.toString()
					},
					totalAssessors: {
						kind: 'panel.element',
						label: 'Total Assessors Synced',
						contents: history.totalAssessors.toString()
					},
					totalResources: {
						kind: 'panel.element',
						label: 'Total Resources Synced',
						contents: history.totalResources.toString()
					},
					totalDataSynced: {
						kind: 'panel.element',
						label: 'Total Data',
						contents: history.totalDataSynced
					},
					syncUser: {
						kind: 'panel.element',
						label: 'Sync User',
						contents: history.syncUser
					},
				}
			}
		}
	};

	// var subjectRows = getHistoryList(history.subjectHistories);
	// var experimentRows = getHistoryList(history.experimentHistories);
	// var assessorRows = getHistoryList(history.assessorHistories);
	// var resourceRows = getHistoryList(history.resourceHistories);

	var subjectRows = [
		{first: "First", second: "First2"},
		{first: "Second", second: "stuff"}
	];

	var subjectTab = {
		kind: 'tab',
		name: 'subjectTab',
		label: 'Subjects',
		contents: {
			subjectTable: {
				kind: 'panel.dataTable',
				name: 'subjectTable',
				label: 'Subject Sync Details',
				data: subjectRows,
				// load: '/data/projects',
				sortable: true,
				id: 'subject-table',
				items: {
					first: "FIRST",
					second: "SEC"
				}
			}
		}
	};

	var experimentTab = {
		kind: 'tab',
		label: 'Experiments',
	};
	var assessorTab = {
		kind: 'tab',
		label: 'Assessors',
	};
	var resourceTab = {
		kind: 'tab',
		label: 'Resources',
	};

	XNAT.tabs.container = "#xsync-details-modal";

	XNAT.spawner.spawn({
		myTabs: {
			kind: 'tabs',
			contains: 'tabs',
			label: 'Xsync History Detail',
			layout: 'left',
			name: 'xsyncHistoryTabs',
			tabs: {
				overview: overviewTab,
				subjects: subjectTab,
				experiments: experimentTab,
				assessors: assessorTab,
				resources: resourceTab
			}
		}
	}).render('#xsync-details-modal', true)
}


// function getHistoryList(data) {
// 	var records = [];
// 	for (var i = 0; i < data.length; i++) {
// 		var elem = data[i];
// 		records.push([elem.localLabel, elem.syncStatus])
// 	}
// 	return records;
// }

// function renderXsyncHistoryDiv(element, data) {
// 	if (data.length === 0) {
// 		element.append('<div class="col1">None</div>');
// 		return;
// 	}
// 	for (var i = 0; i < data.length; i++) {
// 		element.append(
// 			'<div class="col1">'+data[i][0]+'</div><div>'+data[i][1]+'</div>'
// 		)
// 	}
// }




// function showTabModal() {
// 	xmodal.open({
// 		title: "modal tab demo",
// 		width: 600,
// 		height: 400,
// 		overflow: 'auto',
// 		content: '<div id="modal-tab"></div>',
// 		buttons: {
// 			close: {
// 				label: 'Close'
// 			}
// 		}
// 	});
//
// 	XNAT.tabs.container = "#modal-tab";
//
// 	var tab1 = {
// 		kind: 'tab',
// 		name: 'atab',
// 		label: 'A Tab',
// 		group: 'tabGroup1',
// 		active: true,
// 		contents: '<div>Hi</div>'
// 	};
// 	var tab2 = {
// 		kind: 'tab',
// 		name: 'anothertab',
// 		label: 'A Tab',
// 		group: 'tabGroup1',
// 		active: true,
// 		contents: '<div>Hi yourself</div>'
// 	};
//
// 	XNAT.spawner.spawn({
// 		myTabs: {
// 			kind: 'tabs',
// 			contains: 'tabs',
// 			label: 'My Tabs',
// 			layout: 'top',
// 			meta: {
// 				tabGroups: {
// 					tabGroup1: 'Group One',
// 					tabGroup2: 'Group Two'
// 				}
// 			},
// 			name: 'myTabs',
// 			tabs: {
// 				myTab: tab1,
// 				otherTab: tab2
// 			}
// 		}
// 	}).render('#modal-tab', true)
// }
