package dev.vexsoft.core.command;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a method as an executable path below its enclosing command root. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Command {

  /** Returns the path below the command root */
  String value();

  /** Returns the permission required for this command path */
  String permission() default "";

  /** Checks whether this command path requires a player */
  boolean playerOnly() default false;
}
