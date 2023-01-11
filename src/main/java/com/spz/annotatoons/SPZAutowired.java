package com.spz.annotatoons;

import java.lang.annotation.*;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SPZAutowired {
    String value() default "";
}
