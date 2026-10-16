package net.rcetech.meta.user;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasAnyRole(T(net.rcetech.meta.user.Role).ADMIN.name(), T(net.rcetech.meta.user.Role).OPERATOR.name())")
public @interface SupportAccess {
}
