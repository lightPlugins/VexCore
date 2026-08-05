package dev.vexsoft.core.api.service;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Declares the services that must be available before a service implementation is created. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Dependencies {

  /** Returns the services required before this implementation can be created */
  public Class<? extends VexService>[] value() default {};
}
