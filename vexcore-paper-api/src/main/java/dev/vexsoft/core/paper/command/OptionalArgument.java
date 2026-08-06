package dev.vexsoft.core.paper.command;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Binds a method parameter to an optional named command argument */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface OptionalArgument {

  /** Returns the matching optional argument name from the command path */
  String value();

  /** Returns the value used when the argument was omitted */
  String defaultValue() default "";
}
