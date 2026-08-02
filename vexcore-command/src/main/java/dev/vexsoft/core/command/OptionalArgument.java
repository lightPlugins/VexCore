package dev.vexsoft.core.command;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface OptionalArgument {

  /** Returns the matching optional argument name from the command path */
  public String value();

  /** Returns the value used when the argument was omitted */
  public String defaultValue() default "";
}
