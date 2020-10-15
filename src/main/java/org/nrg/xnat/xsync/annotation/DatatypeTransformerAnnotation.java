package org.nrg.xnat.xsync.annotation;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * @author Mohana Ramaratnam(mohanakannan9@gmail.com)
 * 
 * Each Datatype that needs custom transformation before data is sent over to the
 * Remote XNAT should annotate the class with xsiType.
 *
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RUNTIME)
public @interface DatatypeTransformerAnnotation {
		String xsiType();
}
