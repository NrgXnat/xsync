package org.nrg.xsync.scheduler;

import org.nrg.prefs.services.NrgPreferenceService;
import org.nrg.framework.configuration.ConfigPaths;
import org.nrg.prefs.services.NrgPreferenceService;
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
import org.springframework.scheduling.concurrent.ThreadPoolExecutorFactoryBean;
import org.springframework.scheduling.config.TriggerTask;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.PeriodicTrigger;

/**
 * @author Mohana Ramaratnam
 */
@Configuration
@EnableScheduling
public class XsyncScheduler {
	
	@Autowired
	private NrgPreferenceService _preferenceService;
	
	
	@Bean
	public ThreadPoolExecutorFactoryBean threadPoolExecutorFactoryBean() {
		//return new ThreadPoolExecutorFactoryBean();
		ThreadPoolExecutorFactoryBean tBean = new ThreadPoolExecutorFactoryBean();
		tBean.setCorePoolSize(5);
		tBean.setThreadNamePrefix("xsync-thread-");
		return tBean;
	}
	
    @Bean
    public XsyncSitePreferencesBean xsyncSitePreferencesBean(final NrgPreferenceService preferenceService, final ConfigPaths configPaths) {
        return new XsyncSitePreferencesBean(preferenceService, configPaths);
    }

    @Bean
    // Request that this bean be constructed "PostConstruct" so it uses configured value
   // @PostConstruct
    public TriggerTask refreshToken(final XsyncAliasRefreshService aliasRefreshService, final XsyncSitePreferencesBean prefs) {
        return new TriggerTask(new XSyncAliasTokenRefresh(aliasRefreshService),
                               new PeriodicTrigger(((prefs != null) ? prefs.getTokenRefreshIntervalInMillis() :
                                                    XsyncSitePreferencesBean.DEFAULT_TOKEN_REFRESH_INTERVAL_MILLIS)));
    }

    @Bean
    //Run Daily sync everyday at 00:00 hours
    public TriggerTask syncProjectsMarkedAsDailySync(final DailySyncService dailyService) {
        return new TriggerTask(new DailySync(dailyService), new CronTrigger("0 0 0 * * *"));
    }

    @Bean
    //Run every SAT of the week  at 01:00 hours
    public TriggerTask syncProjectsMarkedAsWeeklySync(final WeeklySyncService weeklyService) {
        return new TriggerTask(new WeeklySync(weeklyService), new CronTrigger("0 0 1 ? * SAT"));
    }

    @Bean
    //Run every month on 1st of the month at 02:00 hours
    public TriggerTask syncProjectsMarkedAsMonthlySync(final MonthlySyncService monthlyService) {
        return new TriggerTask(new MonthlySync(monthlyService), new CronTrigger("0 0 2 1 * *"));
    }
}
