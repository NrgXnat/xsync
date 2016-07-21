if (typeof XSYNC === 'undefined') {
	XSYNC = {};
}
if (typeof XSYNC.reporting === 'undefined') {
	XSYNC.reporting = {};
}

XSYNC.reporting.showHistoryTable = function() {
	// Displays overview of sync history in table format
	var xsyncHistory = XNAT.table({ className: 'xnat-table sortable' });
	xsyncHistory.tr();
	xsyncHistory.th('Date').th('Status').th('Subjects').th('Experiments').th('Assessments').th('Resources').th('Total Data');

	var getSyncHistory = $.ajax({
		type: 'GET',
		url: '/xapi/xsync/history/project/' + XNAT.data.context.project,
		dataType: 'json'
	});

	getSyncHistory.done(function(data) {
		var allHistory = [];

		for(var i = 0; i < data.length; i++) {
			var date = new Date(data[i].startDate);
			var historyUri = '/xapi/xsync/history/'+data[i].id;
			var row = [
				'<a onclick=XSYNC.reporting.showHistoryDetailsModal("'+ historyUri +'")>'+ date.toLocaleDateString() + ' ' + date.toLocaleTimeString() +'</a>',
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
	xsyncConfigDiv.append("<h4>Sync History</h4>");
	xsyncConfigDiv.append(xsyncHistory.table);
};

XSYNC.reporting.showHistoryDetailsModal = function(uri) {
	$.ajax({
		type: 'GET',
		url: uri,
		dataType: 'json'
	}).done( function(history) {

		// Create the modal
		var startDate = new Date(history.startDate);

		xmodal.open({
			title:
                'Xsync History for '+ XSYNC.xsyncconfig.configuration.project +
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

		spawnXsyncHistoryTabs(history);
	});
};

function spawnXsyncHistoryTabs(history) {

	XNAT.tabs.container = "#xsync-details-modal";

	XNAT.spawner.spawn({
		myTabs: {
			kind: 'tabs',
			contains: 'tabs',
			label: 'Xsync History Detail',
			layout: 'left',
			name: 'xsyncHistoryTabs',
			tabs: {
				overview: generateOverviewTab(history),
				subjects: generateHistoryTab('Subject', history.subjectHistories),
				experiments: generateHistoryTab('Experiment', history.experimentHistories),
				assessors: generateHistoryTab('Assessor', history.assessorHistories),
				resources: generateHistoryTab('Resource', history.resourceHistories)
			}
		}
	}).render('#xsync-details-modal', true)
}

function generateOverviewTab(history) {
	var startDate = new Date(history.startDate);
	var completeDate = new Date(history.completeDate);

	return {
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
					}
				}
			}
		}
	}
}

// String tomfoolery to generate similarly formatted tabs
function generateHistoryTab(tabType, data) {
	var tableContent = "No " + tabType.toLowerCase() + " data synced";

	if (data.length > 0) {
		tableContent = {
			kind: 'panel.dataTable',
			name: tabType.toLowerCase() + 'Table',
			label: tabType + ' Sync Details',
			data: data,
			// sortable: true,
			id: tabType.toLowerCase() + '-table',
			items: {
				localLabel: tabType + " Label",
				syncStatus: "Status",
				syncMessage: "Message"
			}
		}
	}

	return {
		kind: 'tab',
		name: tabType.toLowerCase() + 'Tab',
		label: tabType + 's',
		contents: {
			tabTable: tableContent
		}
	}
}