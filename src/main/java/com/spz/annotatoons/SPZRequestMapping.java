package com.spz.annotatoons;

import java.lang.annotation.*;

@Target({ElementType.TYPE,ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SPZRequestMapping {
    String value() default "";
}
