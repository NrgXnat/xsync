package org.nrg.xsync.configuration;

import org.nrg.framework.annotations.XnatDataModel;
import org.nrg.framework.annotations.XnatPlugin;
import org.nrg.xdat.bean.XsyncXsyncassessordataBean;
import org.nrg.xdat.bean.XsyncXsyncinfodataBean;
import org.nrg.xdat.bean.XsyncXsyncprojectdataBean;
import org.nrg.xdat.bean.XsyncXsyncremotemapdataBean;
import org.nrg.xnat.xsync.transformer.XsyncDataTypeSpecificTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
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
			entityPackages = {"org.nrg.xsync.remote.alias", "org.nrg.xsync.manifest"},
			log4jPropertiesFile="/META-INF/resources/xsyncLog4j.properties")
@ComponentScan({
	"org.nrg.xsync.aspera",
	"org.nrg.xsync.components",
	"org.nrg.xsync.scheduler",
	"org.nrg.xsync.services",
	"org.nrg.xsync.services.local",
	"org.nrg.xsync.tools",
	"org.nrg.xsync.remote",
	"org.nrg.xsync.xapi",
	"org.nrg.xsync.utils",	
	"org.nrg.xsync.connection",
	"org.nrg.xnat.xsync.transformer",
	"org.nrg.xsync.manifest"})
public class XsyncPlugin {
	
	public static Logger logger = LoggerFactory.getLogger(XsyncPlugin.class);

	public XsyncPlugin() {
		logger.info("Configuring XSync plugin");
	}
	
	@Bean
	XsyncDataTypeSpecificTransformer xsyncDataTypeSpecificTransformer() {
		XsyncDataTypeSpecificTransformer dataTypeSpecificTransformer = new XsyncDataTypeSpecificTransformer();
		return dataTypeSpecificTransformer;
	}

}
