package com.stockforecast.identity;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects the {@link AuthenticatedUser} into a controller method parameter. Ownership must always be derived
 * from this principal rather than from ids supplied in paths or bodies.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@AuthenticationPrincipal(errorOnInvalidType = true)
public @interface CurrentUser {
}
