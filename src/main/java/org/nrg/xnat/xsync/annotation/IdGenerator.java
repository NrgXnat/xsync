/**
 * 
 */

package org.nrg.xnat.xsync.annotation;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * This annotation will be used for user defined label generation of session and subjects.
 * @author Atul
 *
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RUNTIME)
public @interface IdGenerator {
	String ID_GENERATOR = "idGenerator";
	
	String label() default "";
}
