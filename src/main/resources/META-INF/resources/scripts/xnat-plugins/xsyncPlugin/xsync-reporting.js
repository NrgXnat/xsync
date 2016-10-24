
window.XNAT  = getObject(window.XNAT);
window.XSYNC = getObject(window.XSYNC);

// keep it private
(function(XNAT, XSYNC){

	var reporting = XSYNC.reporting = getObject(XSYNC.reporting);
	var projectContext = XNAT.data.context.project;
	var xhr = XNAT.xhr;

	function xsyncUrl(part){
		return XNAT.url.rootUrl('/xapi/xsync' + part||'');
	}

	function localDate(date){
		return date.toLocaleDateString()
	}

	function localTime(date){
		return date.toLocaleTimeString()
	}

	reporting.showHistoryTable = function() {

		// Displays overview of sync history in table format
		var xsyncHistory = XNAT.table({ className: 'xnat-table sortable' });
		xsyncHistory.tr();
		xsyncHistory.th({className:'sort', html: 'Date'})
			.th({className:'sort', html: 'Status'})
			.th('Subjects')
			.th('Subject Assessments')
			.th('Derived Assessments')
			.th('Project Resources')
			.th({className:'sort', html: 'Total'});

		var getSyncHistory = xhr.getJSON(xsyncUrl('/history/projects/' + projectContext));

		getSyncHistory.done(function(data) {

			var allHistory = data.map(function(item, i){
				var date = new Date(item.startDate);
				return [
					'<a class="show-details" title="' + item.id + '" href="#!">'+ localDate(date) + ' ' + localTime(date) +'</a>',
					item.syncStatus,
					item.totalSubjects.toString(),
					item.totalExperiments.toString(),
					item.totalAssessors.toString(),
					item.totalResources.toString(),
					item.totalDataSynced
				];
			});

			xsyncHistory.rows(allHistory.reverse());
		});

		if (xsyncHistory.rows.length > 0) {
			$("#xsync-history-header").show();
			$("#xsync-history-table").append(xsyncHistory.table);
		}

		// delegate a single event handler for all rows
		$(xsyncHistory.table).on('click', 'a.show-details', function(e){
			e.preventDefault();
			reporting.showHistoryDetailsModal(xsyncUrl('/history/'+this.title))
		});

	};

	reporting.showHistoryDetailsModal = function(uri) {
		xhr.getJSON(uri).done( function(history) {
			// Create the modal
			var startDate = new Date(history.startDate);
			xmodal.open({
				title: 'Xsync History for '+ projectContext + ' on '+ localDate(startDate) + ' ' + localTime(startDate),
				width: '80%',
				maxWidth: 1000,
				height: '95%',
				overflow: 'auto',
				maximize: true,
				content: '<div id="xsync-details-modal"></div>',
				beforeShow: function(obj){
					var container = obj.$modal.find('#xsync-details-modal');
					spawnXsyncHistoryTabs(container, history);
				},
				buttons: {
					close: {
						label: 'Close'
					}
				}
			});
		});
	};

	function spawnXsyncHistoryTabs(container, history) {
		XNAT.tabs.container = container;
		XNAT.spawner.spawn({
			myTabs: {
				kind: 'tabs',
				contains: 'tabs',
				label: 'Xsync History Detail',
				layout: 'left',
				name: 'xsyncHistoryTabs',
				tabs: {
					overview: generateOverviewTab(history),
					subjects: generateHistoryTab('Subjects', history.subjectHistories),
					experiments: generateHistoryTab('Subject Assessments', history.experimentHistories),
					assessors: generateHistoryTab('Derived Assessments', history.assessorHistories),
					resources: generateHistoryTab('Project Resources', history.resourceHistories)
				}
			}
		}).render(container, 100)
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
					label: 'History Overview',
					footer: false,
					contents: {
						syncStatus: {
							kind: 'panel.element',
							label: 'Status',
							contents: history.syncStatus
						},
						started: {
							kind: 'panel.element',
							label: 'Started',
							contents: localDate(startDate)+ ' ' + localTime(startDate)
						},
						completed: {
							kind: 'panel.element',
							label: 'Completed',
							contents: localDate(completeDate)+ ' ' + localTime(completeDate)
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
							label: 'Total Subjects',
							contents: history.totalSubjects.toString()
						},
						totalExperiments: {
							kind: 'panel.element',
							label: 'Total Subject Assessments',
							contents: history.totalExperiments.toString()
						},
						totalAssessors: {
							kind: 'panel.element',
							label: 'Total Derived Assessments',
							contents: history.totalAssessors.toString()
						},
						totalResources: {
							kind: 'panel.element',
							label: 'Total Resources',
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

		var panelContent;

		if (data.length) {
			panelContent = XNAT.table.dataTable(data, {
				id: tabType.toLowerCase() + '-table',
				items: {
					localLabel: "Label",
					syncStatus: "Status",
					syncMessage: "Message"
				}
			})
		}
		else {
			panelContent = '<i class="pad20h">Nothing synced.</i>';
		}

		var tabContent = {
			kind: 'panel',
			label: tabType + ' Sync Details',
			footer: false,
			contents: {
				history: {
					tag: 'div.pad20v',
					content: panelContent
				}
			}
		};

		return {
			kind: 'tab',
			name: tabType.toLowerCase() + ' Tab',
			label: tabType,
			contents: {
				tabTable: tabContent
			}
		}

	}

})(window.XNAT, window.XSYNC);
