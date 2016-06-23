package org.nrg.xsync.configuration;

import org.apache.log4j.Logger;
import org.nrg.framework.annotations.XnatDataModel;
import org.nrg.framework.annotations.XnatPlugin;
import org.nrg.xdat.bean.XsyncXsyncremotemapdataBean;
import org.nrg.xdat.bean.XsyncXsyncinfodataBean;
import org.nrg.xdat.bean.XsyncXsyncassessordataBean;
import org.nrg.xdat.bean.XsyncXsyncprojectdataBean;
import org.springframework.context.annotation.ComponentScan;

@XnatPlugin(value = "xsyncPlugin",
			name = "XSync Plugin",
			dataModels = {
					@XnatDataModel(value = XsyncXsyncremotemapdataBean.SCHEMA_ELEMENT_NAME, 
									singular = "XSync Remote Map Data",
									plural = "XSync Remote Map Data"
									),
					@XnatDataModel(value = XsyncXsyncinfodataBean.SCHEMA_ELEMENT_NAME,
									singular = "XSync Info Data",
									plural = "XSync Info Data"
									),
					@XnatDataModel(value = XsyncXsyncassessordataBean.SCHEMA_ELEMENT_NAME,
									singular = "XSync Assessor Data",
									plural = "XSync Assessor Data"
									),
					@XnatDataModel(value = XsyncXsyncprojectdataBean.SCHEMA_ELEMENT_NAME,
									singular = "XSync Project Data",
									plural = "XSync Project Data"
									)
			},
			entityPackages = "org.nrg.xsync.remote.alias")
@ComponentScan({
	"org.nrg.xsync.component",
	"org.nrg.xsync.scheduler",
	"org.nrg.xsync.services",
	"org.nrg.xsync.tools",
	"org.nrg.xsync.remote",
	"org.nrg.xsync.xapi"
	/*
	"org.nrg.xsync.services.remote",
	"org.nrg.xsync.services.local.impl",
	"org.nrg.xsync.remote.alias",
	"org.nrg.xsync.remote.alias.services",
	"org.nrg.xsync.remote.alias.services.impl",
	*/
	})
public class XsyncPlugin {
	
	public static Logger logger = Logger.getLogger(XsyncPlugin.class);

	public XsyncPlugin() {
		logger.info("Configuring XSync plugin");
	}

}
