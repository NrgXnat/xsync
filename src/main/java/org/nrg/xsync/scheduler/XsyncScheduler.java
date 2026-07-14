package org.nrg.xsync.scheduler;

import org.nrg.xsync.components.XsyncSitePreferencesBean;
import org.nrg.xsync.services.local.DailySyncService;
import org.nrg.xsync.services.local.HourlySyncService;
import org.nrg.xsync.services.local.MonthlySyncService;
import org.nrg.xsync.services.local.WeeklySyncService;
import org.nrg.xsync.services.local.XsyncAliasRefreshService;
import org.nrg.xsync.services.local.impl.XSyncAliasTokenRefresh;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
	
	private static final Logger _logger = LoggerFactory.getLogger(XsyncScheduler.class);

	@Bean(name = "xsyncThreadPoolExecutorFactoryBean")
	public ThreadPoolExecutorFactoryBean xsyncThreadPoolExecutorFactoryBean() {
		//return new ThreadPoolExecutorFactoryBean();
		ThreadPoolExecutorFactoryBean tBean = new ThreadPoolExecutorFactoryBean();
		tBean.setCorePoolSize(5);
		tBean.setThreadNamePrefix("xsync-thread-");
		return tBean;
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
    //Run Hourly sync at 30 minutes past the hour, every hour
    public TriggerTask syncProjectsMarkedAsHourlySync(HourlySyncService hourlySyncService) {
        _logger.debug("Initializing HourlySync TriggerTask:  {}", hourlySyncService);
        return new TriggerTask(hourlySyncService, new CronTrigger("0 30 * * * ?"));
    }

    
    @Bean
    //Run Daily sync every day at 00:00 hours
    public TriggerTask syncProjectsMarkedAsDailySync(final DailySyncService dailySyncService) {
        _logger.debug("Initializing DailySync TriggerTask:  {}", dailySyncService);
        return new TriggerTask(dailySyncService, new CronTrigger("0 0 0 * * *"));
    }

    @Bean
    //Run every SAT of the week  at 01:00 hours
    public TriggerTask syncProjectsMarkedAsWeeklySync(final WeeklySyncService weeklySyncService) {
        _logger.debug("Initializing WeeklySync TriggerTask:  {}", weeklySyncService);
        return new TriggerTask(weeklySyncService, new CronTrigger("0 0 1 ? * SAT"));
    }

    @Bean
    //Run every month on 1st of the month at 02:00 hours
    public TriggerTask syncProjectsMarkedAsMonthlySync(final MonthlySyncService monthlySyncService) {
        _logger.debug("Initializing MontlySync TriggerTask:  {}", monthlySyncService);
        return new TriggerTask(monthlySyncService, new CronTrigger("0 0 2 1 * *"));
    }
	
}
