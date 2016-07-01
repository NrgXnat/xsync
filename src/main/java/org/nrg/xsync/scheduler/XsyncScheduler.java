package org.nrg.xsync.scheduler;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import org.nrg.xsync.configuration.XsyncSitePreferencesBean;
import org.nrg.xsync.services.local.DailySyncService;
import org.nrg.xsync.services.local.MonthlySyncService;
import org.nrg.xsync.services.local.WeeklySyncService;
import org.nrg.xsync.services.local.XsyncAliasRefreshService;
import org.nrg.xsync.services.local.impl.DailySync;
import org.nrg.xsync.services.local.impl.MonthlySync;
import org.nrg.xsync.services.local.impl.WeeklySync;
import org.nrg.xsync.services.local.impl.XSyncAliasTokenRefresh;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.config.TriggerTask;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.PeriodicTrigger;

/**
 * @author Mohana Ramaratnam
 *
 */
@Configuration
@EnableScheduling
public class XsyncScheduler {
	
	@Bean
	// Request that this bean be constructed "PostConstruct" so it uses configured value
	@PostConstruct
	public TriggerTask refreshToken() {
	    return new TriggerTask(new XSyncAliasTokenRefresh(_aliasService),
	    		new PeriodicTrigger(((_prefs!=null) ? _prefs.getTokenRefreshIntervalInMillis() :
	    			XsyncSitePreferencesBean.DEFAULT_TOKEN_REFRESH_INTERVAL_MILLIS)));
	}

	@Bean 
	//Run Daily sync everyday at 00:00 hours
	public TriggerTask syncProjectsMarkedAsDailySync() {
	    return new TriggerTask(new DailySync(_dailyService), new CronTrigger("0 0 0 * * *"));
	}

	@Bean 
	//Run every month on 1st of the month at 01:00 hours
	public TriggerTask syncProjectsMarkedAsWeeklySync() {
	    return new TriggerTask(new WeeklySync(_weeklyService), new CronTrigger("0 0 1 ? * SAT"));
	}

	
	@Bean 
	//Run every month on 1st of the month at 02:00 hours
	public TriggerTask syncProjectsMarkedAsMonthlySync() {
	    return new TriggerTask(new MonthlySync(_monthlyService), new CronTrigger("0 0 2 1 * *"));
	}


	@Inject
	private  DailySyncService _dailyService;

	@Inject
	private  XsyncAliasRefreshService _aliasService;

	@Inject
	private  WeeklySyncService _weeklyService;
	
	@Inject
	private  MonthlySyncService _monthlyService;
	
	@Autowired
	private XsyncSitePreferencesBean _prefs;
}
