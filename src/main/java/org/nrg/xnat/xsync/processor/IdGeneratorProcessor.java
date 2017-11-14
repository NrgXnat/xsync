package org.nrg.xnat.xsync.processor;

import java.util.Map;

import javax.annotation.processing.Processor;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.TypeElement;

import org.kohsuke.MetaInfServices;
import org.nrg.framework.processors.NrgAbstractAnnotationProcessor;
import org.nrg.xnat.xsync.annotation.IdGenerator;

import com.google.common.collect.Maps;


/**
 * The Class IdGeneratorProcessor.
 * This class will generate properties file for all the classes using @idGenerator annotation.
 */
@MetaInfServices(Processor.class)
@SupportedAnnotationTypes("org.nrg.xnat.xsync.annotation.IdGenerator")
public class IdGeneratorProcessor extends NrgAbstractAnnotationProcessor<IdGenerator> {
	
	/* (non-Javadoc)
	 * @see org.nrg.framework.processors.NrgAbstractAnnotationProcessor#processAnnotation(javax.lang.model.element.TypeElement, java.lang.annotation.Annotation)
	 */
	@Override
	protected Map<String, String> processAnnotation(TypeElement element, IdGenerator annotation) {
		final Map<String, String> properties = Maps.newLinkedHashMap();
		properties.put(IdGenerator.ID_GENERATOR, element.getQualifiedName().toString());
		return properties;
	}

	/* (non-Javadoc)
	 * @see org.nrg.framework.processors.NrgAbstractAnnotationProcessor#getPropertiesName(javax.lang.model.element.TypeElement, java.lang.annotation.Annotation)
	 */
	@Override
	protected String getPropertiesName(TypeElement element, IdGenerator annotation) {
        return String.format("xsync/%s-xsync.properties", element.getSimpleName());
	}

}
