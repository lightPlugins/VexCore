package dev.vexsoft.core.placeholder;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Declares the owner-local identifier handled by a placeholder class. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PlaceholderId {

  /** Returns the identifier below the owning plugin namespace. */
  String value();
}
