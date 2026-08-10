package dev.vexsoft.core.paper.command.argument;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.vexsoft.core.paper.command.VexCommandSource;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;
import java.util.function.Consumer;

/** Defines a reusable typed parser for annotated Vex commands. */
public interface CommandArgumentType<T> extends CustomArgumentType.Converted<T, String> {

  /** Returns the Java parameter type handled by this parser. */
  Class<T> getValueType();

  /** Returns the native Brigadier argument used to read the raw value. */
  @Override
  default ArgumentType<String> getNativeType() {
    return StringArgumentType.word();
  }

  /** Converts the raw input into the command parameter value. */
  @Override
  default T convert(final String nativeType) {
    return parseValue(nativeType);
  }

  /** Parses a raw command value. */
  T parseValue(String value);

  /** Reads this argument from an execution context. */
  default T read(
      final CommandContext<CommandSourceStack> context,
      final String name
  ) {
    return context.getArgument(name, getValueType());
  }

  /** Parses a configured optional default value. */
  default T parseDefault(final String value) {
    return parseValue(value);
  }

  /** Adds suggestions for the current command source and raw input. */
  default void suggest(
      final VexCommandSource source,
      final String input,
      final Consumer<String> suggestion
  ) { }
}
