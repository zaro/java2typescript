package org.bsc.java2typescript.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 *  
 * @author bsorrentino
 *
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface TsType {
	String value();
}
