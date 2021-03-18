
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
		xsyncHistory
				.th({className:'sort', html: 'Date <i>&nbsp;</i>'})
				.th({className:'sort', html: 'Status <i>&nbsp;</i>'})
				.th('Subjects')
				.th('Subject Assessments')
				.th('Derived Assessments')
				.th('Project Resources')
				.th({className:'sort', html: 'Total <i>&nbsp;</i>'});

		var getSyncHistory = xhr.getJSON(xsyncUrl('/history/projects/' + projectContext));

		getSyncHistory.done(function(data) {

			var allHistory = data.map(function(item, i){

				var date = new Date(item.startDate);

				return [
					'<div class="mono">' +
						'<i class="hidden start-time">' + item.startDate + '</i>' +
						'<a class="show-details link" title="' + item.id + '" href="#!">'+ localDate(date) + '<br>' + localTime(date) +'</a>' +
					'</div>',

					item.syncStatus,

					'<div class="mono centered">' + item.totalSubjects + '</div>',

					'<div class="mono centered">' + item.totalExperiments + '</div>',

					'<div class="mono centered">' + item.totalAssessors + '</div>',

					'<div class="mono centered">' + item.totalResources + '</div>',

					'<div class="mono align-right">' +
						'<i class="hidden total-data-synced">' + item.totalDataSynced + '</i>' +
						sizeFormat(item.totalDataSynced, 2) +
					'</div>'

				];
			});

			xsyncHistory.rows(allHistory.reverse());

			if (data.length) {
				$("#xsync-history-header").show();
				$("#xsync-history-table").append(xsyncHistory.table);
			}
			else {
				$("#xsync-history-table").spawn('p', 'No sync history.')
			}

		});

		// delegate a single event handler for all rows
		$(xsyncHistory.table).on('click', 'a.show-details', function(e){
			e.preventDefault();
			reporting.showHistoryDetailsModal(xsyncUrl('/history/projects/'+ projectContext + '/' +this.title))
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
		}).render(container, 100);
		setActiveTab();
	}

	/*
	 * This is a quick fix. This is function is written to make display Overview Tab.  
	 * Bug# https://issues.humanconnectome.org/browse/CCF-99
	 * 
	 */
	function setActiveTab()
	{
	   var objects=document.getElementsByClassName('tab-pane');
	   if(objects!=null && objects.length>0)
	   {
		   for(i=0; i<objects.length;i++)
		   {
			   var obj=objects[i];
			   if(obj.data.name=="overviewTab")
			   {
				   obj.className='tab-pane active';
			   }
		   }
	   } 
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
					syncMessage: {
						label: "Message",
						apply: function(){
							return window.escapeHtml(this.syncMessage);
						}
					}
				}
			}).get();
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


function intializeXsyncMapping(projectId) {
	var confirmModal = xModalConfirm;
	confirmModal.okClose = false,
	confirmModal.okAction = function(){ 
		var reportFormat = $("input[name=xsyncMappingReportType]:checked").val();
		var objectType=$("input[name=xsyncDataType]:checked").val();
		var confirmUrl = serverRoot+'/xapi/xsync/getSubjectMappingFile/' + projectId+"?reportFormat="+reportFormat+"&objectType="+objectType;
			
		$("#report-modal-overlay").show();
		$("#xsyncMappingReportConfirmModal-cancel-button").hide();

		$.ajax({
			type : "GET",
			url:confirmUrl,
			cache: false,
			async: true,
			context: this,
			dataType: 'text'
		 })
		.done( function( data, textStatus, jqXHR ) {
			if (reportFormat=="CSV") {
				xmodal.close('xsyncMappingReportConfirmModal');
				XNAT.ui.banner.top(2000, "Building report file.  Your download should begin shortly");
				window.location.href=confirmUrl;
			} else {
				var dataJSON = JSON.parse(data);
				var tableStr = "<div style='height:500px; width:930px; overflow:auto;'><table style='font-size: 11px; border-width:1px; border-style:solid; border-collapse: collapse'>";
				for (var i=0; i<dataJSON.length; i++) {
					tableStr+="<tr style='border-width:1px; border-style:solid; border-collapse: collapse'>";
					for (var j=0; j<dataJSON[i].length; j++) {
						if (i>0) {
							tableStr+="<td style='padding-left:5px;padding-right:5px;border-width:1px; border-style:solid; border-collapse: collapse'>" + dataJSON[i][j] + "</td>";
						} else {
							tableStr+="<th style='padding-left:5px;padding-right:5px;background-color:#EEEEEE;border-width:1px; border-style:solid; border-collapse: collapse'>" + dataJSON[i][j] + "</th>";
						}
					}
					tableStr+="</tr>";
				}
				tableStr+="</table></div>";
	 			var msg = "<h2 style='color:#228822'>Xsync Id Report (Project=" + projectId +")</h2>" + "<br><br>" + tableStr;
				
				$("#report-modal-overlay").hide();
				xmodal.message({
					title:  "Xsync Id Report",
					width:  '1000px',
					height:  '800px',
					content:  msg
				});
				xmodal.close('xsyncMappingReportConfirmModal');
			}
		})
		.fail( function( data, textStatus, error ) {
			var responseText = data.responseText;
			try {
				var parseResponseText = JSON.parse(responseText);
				if (parseResponseText.constructor === Array) {
					responseText = "";
					for (var i=0;i<parseResponseText.length;i++) {
						responseText = responseText + "<li>" + parseResponseText[i] + "</li>";
					}
				}
			} catch (e) {
				// Do nothing
			}
	 		var errMsg = "<h2 style='color:#AA1111'>Could not generate report</h2><em>REASON:</em> &nbsp;" +  error + "<br><br><em>DETAILS:</em><br><br>" + responseText;
			
			$("#report-modal-overlay").hide();
			xmodal.message({
				title:  "Combined Session Building Results",
				width:  '800px',
				height:  '630px',
				content:  errMsg
			});
			xmodal.close('xsyncMappingReportConfirmModal');
		});
	 }

	confirmModal.id  = 'xsyncMappingReportConfirmModal';
	confirmModal.content  = '<h2 style="margin-bottom:10px;margin-top:10px">View/Download Xsync Id Data (Project=' + projectId + ')?</h2>';
	confirmModal.cancelAction = function(){ return; };
	confirmModal.title = "View/Download Xsync Id Data";
	confirmModal.width = 650;
	confirmModal.height = 300;
	xModalOpenNew(confirmModal);
 	XNAT.spawner.spawn(spawnReport()).render($("#xsyncMappingReportConfirmModal").find(".body").find(".inner"));
 	$("#xsyncMappingReportConfirmModal").find(".body").find(".inner").append(
		"<div id='report-modal-overlay' style='width:100%;height:100%;display:none;background-color:#FFFFFF;" +
		"z-index:10;opacity:0.9;margin:0px;position:absolute;top:0px;left:0px;padding:0px'>" + 
		"<div style='color:#22AA22;width:100%'><h3 style='text-align:center'>Generating id report &nbsp;Please wait...</h3</div>" + 
		"<div style='top:50%;left:50%;margin-right: -50%;transform: translate(-50%, -50%);position:absolute;opacity:1.0;'><img src='/images/loading.gif'/></div>" +
		"</div><iframe id='download_iframe' style='display:none;'></iframe>");
 	$($("#xsyncDataType")[0]).attr('checked', true);
	$($("#xsyncMappingReportType")[0]).attr('checked', true); 

}



	
	function spawnReport() {
		function configPanel(contents) {
		return {
			id: 'xsyncMappingReportPanel',
			kind: 'panel.form',
			width: '600px',
			height: '300px',
			label: 'Xsync Mapping Report',
			header: false,
			footer: false,
			contents: {
				"Data Type": dataType(),
				"Report Type": reportFormat()
			}
		}
	}
	function reportFormat() {
		return {
			id: 'xsyncMappingReport',
			kind: 'panel.element',
			name: 'xsyncMappingReport',
			value: 'CSV',
			label: 'Report Format',
			contents: {
				"CSV": reportFormatCSV(),
				"JSON": reportFormatJSON()
			}
		}
	}
	function reportFormatCSV() {
		return {
			id: 'xsyncMappingReportType',
			kind: 'input.radio',
			name: 'xsyncMappingReportType',
			value: 'CSV',
			label: 'Report FormatXX',
			after: "<span style='margin-right:15px'>Download</span>"
		}
	}
	function reportFormatJSON() {
		return {
			id: 'xsyncMappingReportType',
			kind: 'input.radio',
			name: 'xsyncMappingReportType',
			value: 'JSON',
			after: "<span>Display</span>",
		}
	}
	
	function dataType() {
		return {
			id: 'xsyncMappingData',
			kind: 'panel.element',
			name: 'xsyncMappingData',
			value: 'CSV',
			label: 'Data',
			contents: {
				"Subject": subjectDataType(),
				"Experiment": experimentDataType()
			}
		}
	}
	
	function subjectDataType() {
		return {
			id: 'xsyncDataType',
			kind: 'input.radio',
			name: 'xsyncDataType',
			value: 'subject',
			label: 'subject',
			after: "<span style='margin-right:15px'>Subject</span>"
		}
	}
	function experimentDataType() {
		return {
			id: 'xsyncDataType',
			kind: 'input.radio',
			name: 'xsyncDataType',
			value: 'experiment',
			after: "<span>Experiment</span>",
		}
	}
		return {
		root: configPanel()
	};
}