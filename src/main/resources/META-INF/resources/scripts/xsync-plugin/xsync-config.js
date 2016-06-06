

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
	XSYNC.xsyncconfig.project = XNAT.data.context.project;
	XSYNC.xsyncconfig.sync_frequency = 'weekly';
	XSYNC.xsyncconfig.auto_sync = 'true';
	XSYNC.xsyncconfig.identifiers = 'use_local';
	XSYNC.xsyncconfig.remote_url = 'http://';
	XSYNC.xsyncconfig.remote_project_id = '';
	XSYNC.xsyncconfig.projectresources = [];
	XSYNC.xsyncconfig.subjectresources = [];
	XSYNC.xsyncconfig.subjectassessors = [];
	XSYNC.xsyncconfig.imagingsessions = [];
	XSYNC.xsyncconfig.continueInit();
	
}

XSYNC.xsyncconfig.continueInit = function() {
	$("#xsync-config-div").html('');
	$("#xsync-config-div").append('<div style="width:90%;margin-top:10px;margin-left:20px">' + 
			'<div style="width:150px;float:left;">Sync Frequency:</div><div>  <select id="xsync-config-sync-frequency">' +
				'<option value="daily">Daily</option>' +
				'<option value="weekly">Weekly</option>' +
				'<option value="monthly">Monthly</option>' +
				'<option value="on demand">On Demand</option>' +
			'</select>' +
			'<div>' +
		'<div>');
	$("#xsync-config-sync-frequency").val(XSYNC.xsyncconfig.sync_frequency);
	$("#xsync-config-div").append('<div style="width:90%;margin-top:10px;margin-left:20px">' + 
			'<div style="width:150px;float:left;">Auto-Sync:</div><div>  <select id="xsync-config-auto-sync">' +
				'<option value="true">True</option>' +
				'<option value="false">False</option>' +
			'</select>' +
			'<div>' +
		'<div>');
	$("#xsync-config-auto-sync").val(XSYNC.xsyncconfig.auto_sync);
	$("#xsync-config-div").append('<div style="width:90%;margin-top:10px;margin-left:20px">' + 
			'<div style="width:150px;float:left;">Identifiers:</div><div>  <select id="xsync-config-identifiers">' +
				'<option value="use_local">Use Local</option>' +
				'<option value="use_remote">Use Remote</option>' +
				'<option value="use_random">Use Random</option>' +
				'<option value="use_custom_local">Use Custom Local</option>' +
			'</select>' +
			'<div>' +
		'<div>');
	$("#xsync-config-identifiers").val(XSYNC.xsyncconfig.identifiers);
	$("#xsync-config-div").append('<div style="width:90%;margin-top:10px;margin-left:20px">' + 
			'<div style="width:150px;float:left;">Remote URL:</div><div>  <input type="text" id="xsync-config-remote-url" size="60">' +
			'<div>' +
		'<div>');
	$("#xsync-config-remote-url").val(XSYNC.xsyncconfig.remote_url);
}


