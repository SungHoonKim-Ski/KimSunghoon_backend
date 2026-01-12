package barley.wire.wirebarley.infrastructure.aop;

import static barley.wire.wirebarley.common.constants.IdempotencyConstants.TTL_MINUTES;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {
    int ttl() default TTL_MINUTES;

    boolean required() default true;
}
