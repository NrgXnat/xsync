package org.nrg.xnat.services.xsync;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @author Mohana Ramaratnam
 *
 */
public class XSyncSpringBridge  {

	    private final static ApplicationContext context = new ClassPathXmlApplicationContext("**/xsync-config.xml");


	    public static ApplicationContext getApplicationContext() {
	        return context;
	    }

}
