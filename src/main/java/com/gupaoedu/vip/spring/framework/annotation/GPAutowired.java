package com.gupaoedu.vip.spring.framework.annotation;

import java.lang.annotation.*;

/**
 * @author 孟祥骞
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface GPAutowired {
    String value() default "";
}
