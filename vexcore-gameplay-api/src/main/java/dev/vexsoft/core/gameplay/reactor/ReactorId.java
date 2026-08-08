package dev.vexsoft.core.gameplay.reactor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Declares the configuration ID of a trigger, filter, condition, or effect class. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ReactorId {

  /** Returns the lowercase, hyphen-separated component ID. */
  String value();
}
