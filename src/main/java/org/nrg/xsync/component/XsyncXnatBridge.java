package org.nrg.xsync.component;

import org.nrg.framework.annotations.XnatPlugin;
import org.nrg.xsync.tools.XsyncXnatInfo;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

/**
 * @author Mohana Ramaratnam
 *
 */
@XnatPlugin(value="XsyncXnatBridge")
@ComponentScan(basePackages = "org.nrg.xsync")

public class XsyncXnatBridge {
	
    @Bean
    public XsyncXnatInfo getXsyncXnatInfo() {
       return new XsyncXnatInfo();
    }
}
