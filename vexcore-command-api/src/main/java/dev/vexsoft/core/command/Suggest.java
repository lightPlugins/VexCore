package dev.vexsoft.core.command;

import dev.vexsoft.core.command.suggestion.SuggestionProvider;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Selects the suggestion provider used for a command argument. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Suggest {

  /** Returns the provider used to suggest values for this argument */
  public Class<? extends SuggestionProvider> value();
}
