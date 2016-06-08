

if (typeof XSYNC === 'undefined') {
	XSYNC = {};
}
if (typeof XSYNC.xsyncconfig === 'undefined') {
	XSYNC.xsyncconfig = { };
}

XSYNC.xsyncconfig.initialize = function() {

	var scConfigAjax = $.ajax({
		type : "GET",
 		url:serverRoot+'/data/projects/' + XNAT.data.context.project +'/resources/synchronization/files/sync_config.json',
		cache: false,
		async: false,
		context: this,
		dataType: 'json'
	 });
	scConfigAjax.done( function( data, textStatus, jqXHR ) {

		if (typeof data !== 'undefined' && typeof data.project !== 'undefined') {
			XSYNC.xsyncconfig.configuration = data;
		} else {
			XSYNC.xsyncconfig.beginConfig();
			
		}
		XSYNC.xsyncconfig.continueInit();
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
	XSYNC.xsyncconfig.continueInit();
	
}

XSYNC.xsyncconfig.continueInit = function() {
	$("#xsync-config-div").html('');
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
			'<div class="xsync-container-div">' + 
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
						'<input type="button" class="xsync-button imaging-session-assessor-remote" value="Remove" onclick="XSYNC.xsyncconfig.removeScan(this)">' +
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
			'<div class="xsync-container-div">' + 
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
	$("#xsync-submit-config").click(function() { XSYNC.xsyncconfig.submitConfig(); });
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

XSYNC.xsyncconfig.removeResource = function(ele) {
	$(ele).parent().remove();
}

XSYNC.xsyncconfig.submitConfig = function() {

	var modalContent = 
		"<div>" + 
			'<div class = "credentials-header-div credentials-div">' + 
			'<h3 style="text-align:center">Enter credentials for XSync to use for transfers</h3>' +
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
		height: 480,  
		id: 'xmodal-enter-credentials',  
		title: "Information required",
		content: modalContent,
		ok: 'show',
		okLabel: 'Continue',
		okAction: function(modl){ 
					XSYNC.xsyncconfig.continueConfig();
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

XSYNC.xsyncconfig.continueConfig = function() {

	var credHost = $("#xsync-config-remote-url").val();
	var credUser = $("#xsync-credentials-username").val();
	var credPassword = $("#xsync-credentials-password").val();
	var tokenData = { host: credHost, user: credUser, password: credPassword };

	var credentialsAjax = $.ajax({
		type : "POST",
 		url: serverRoot + '/data/xsync/remoteToken?XNAT_CSRF=' + window.csrfToken,
		cache: false,
		async: true,
		dataType: 'json',
		data:  JSON.stringify(tokenData),
		contentType: "application/json; charset=utf-8"
	 });
	credentialsAjax.done( function( data, textStatus, jqXHR ) {

		if (typeof data !== 'undefined' && typeof data.secret !== 'undefined') {

			var newJson = XSYNC.xsyncconfig.constructNewJson(data);

			var xsyncConfigAjax = $.ajax({
				type : "POST",
		 		url:serverRoot+'/data/xsync/setup?project=' + XNAT.data.context.project + '&XNAT_CSRF=' + window.csrfToken,
				cache: false,
				async: true,
				data:  JSON.stringify(newJson),
				contentType: "application/json; charset=utf-8"
			 });
			xsyncConfigAjax.done( function( data, textStatus, jqXHR ) {
				xmodal.message('Saved','The XSync configuration has been saved');
			});
			xsyncConfigAjax.fail( function( data, textStatus, error ) {
				console.log(newJson);
				console.log(JSON.stringify(newJson));
				xmodal.message('Error','ERROR:  Configuration was not successfully saved (' + textStatus + ')');
			});

		} else {
			xmodal.message('Error','ERROR:  Could not get alias token.  Please check username and password and try again.');
		}
		XSYNC.xsyncconfig.continueInit();
	});
		credentialsAjax.fail( function( data, textStatus, error ) {
			xmodal.message('Error','ERROR:  Could not get alias token');
	});

}

XSYNC.xsyncconfig.constructNewJson = function(data) {
	var newJson = {};
	newJson.project = XSYNC.xsyncconfig.configuration.project;
	newJson.sync_frequency = $("#xsync-config-sync-frequency").val();
	newJson.auto_sync = $("#xsync-config-auto-sync").val();
	newJson.identifiers = $("#xsync-config-identifiers").val();
	newJson.remote_url = $("#xsync-config-remote-url").val();
	newJson.remote_project_id = $("#xsync-config-remote-project-id").val();
	newJson.remote_token = data.alias;
	newJson.remote_secret = data.secret;
	newJson.projectresources = [];
	$(".project-resource-input").each(function() {
		newJson.projectresources.push($(this).val());
	});
	newJson.subjectresources = [];
	$(".subject-resource-input").each(function() {
		newJson.subjectresources.push($(this).val());
	});
	newJson.subjectassessors = [];
	$(".xsync-subject-assessor-div").each(function() {
		var subject_assessor = {};
		subject_assessor.xsiType = $(this).find(".subject-assessor-xsitype-input").val();
		subject_assessor.needs_ok_to_sync = JSON.parse($(this).find(".subject-assessor-needsok-input").val().toLowerCase());
		subject_assessor.resources = [];
		$(".subject-assessor-resource-input").each(function() {
			subject_assessor.resources.push($(this).val());
		});
		newJson.subjectassessors.push(subject_assessor);
	});
	newJson.imagingsessions = [];
	$(".xsync-imaging-session-div").each(function() {
		var imaging_session = {};
		imaging_session.xsiType = $(this).find(".imaging-session-xsitype-input").val();
		imaging_session.needs_ok_to_sync = JSON.parse($(this).find(".imaging-session-needsok-input").val().toLowerCase());
		imaging_session.anonymize = JSON.parse($(this).find(".imaging-session-anonymize-input").val().toLowerCase());
		imaging_session.resources = [];
		$(this).find(".imaging-session-resource-input").each(function() {
			imaging_session.resources.push($(this).val());
		});
		imaging_session.scans = [];
		$(this).find(".xsync-imaging-session-scan-div").each(function() {
			var scan = {};
			scan.type = $(this).find(".imaging-session-scan-type-input").val();
			scan.resources = [];
			$(this).find(".imaging-scan-resource-input").each(function() {
				scan.resources.push($(this).val());
			});
			imaging_session.scans.push(scan);
		});
		imaging_session.assessors = [];
		$(this).find(".xsync-imaging-session-assessor-div").each(function() {
			var assessor = {};
			assessor.xsiType = $(this).find(".imaging-session-assessor-xsitype-input").val();
			assessor.needs_ok_to_sync = JSON.parse($(this).find(".imaging-session-assessor-needsok-input").val().toLowerCase());
			assessor.resources = [];
			$(this).find(".imaging-assessor-resource-input").each(function() {
				assessor.resources.push($(this).val());
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
					'<input type="button" class="xsync-button imaging-session-assessor-remote" value="Remove" onclick="XSYNC.xsyncconfig.removeAssessor(this)">' +
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

