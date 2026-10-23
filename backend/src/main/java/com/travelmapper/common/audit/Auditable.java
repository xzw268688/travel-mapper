package com.travelmapper.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

    String action();

    String targetType() default "";

    /**
     * Optional SpEL expression resolving to a numeric target id, e.g. "#id".
     */
    String targetIdSpel() default "";
}
