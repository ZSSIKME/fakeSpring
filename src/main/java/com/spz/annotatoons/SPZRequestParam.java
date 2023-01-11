package com.spz.annotatoons;

import java.lang.annotation.*;

@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SPZRequestParam {
    String value() default "";
}
