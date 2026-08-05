package dev.vexsoft.core.command;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Declares the root metadata shared by every command method in a command class. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CommandRoot {

  /** Returns the primary name of the command */
  public String name();

  /** Returns the description shown by the server */
  public String description() default "";

  /** Returns the alternative names of the command */
  public String[] aliases() default {};

  /** Returns the permission required for the complete command */
  public String permission() default "";

  /** Checks whether the complete command requires a player */
  public boolean playerOnly() default false;
}
