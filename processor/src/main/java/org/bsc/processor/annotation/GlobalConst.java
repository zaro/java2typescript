package org.bsc.processor.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 *  
 * @author bsorrentino
 *
 */
@Retention(RetentionPolicy.SOURCE)
@Target( {ElementType.ANNOTATION_TYPE} )
public @interface GlobalConst {
	String name();
	Class<?> type();
}
