package dev.vexsoft.core.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.VexClassFactory;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.command.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.Value;
import lombok.Getter;
import lombok.Setter;

import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

@Dependencies
public final class VexCommandService implements CommandService {

  private final VexServiceRegistry services;
  private final Map<String, CommandTree> commands = new LinkedHashMap<>();
  private boolean bound;

  public VexCommandService(final VexServiceRegistry services) {
    this.services = Objects.requireNonNull(services, "services");
    if (!(services.getOwner() instanceof Plugin plugin)) {
      throw new IllegalArgumentException("CommandService owner must be a Bukkit plugin");
    }
    plugin.getLifecycleManager().registerEventHandler(
        LifecycleEvents.COMMANDS,
        event -> bind(event.registrar())
    );
  }

  @Override
  public synchronized <T> T register(final Class<T> commandType) {
    Objects.requireNonNull(commandType, "commandType");
    if (bound) {
      throw new IllegalStateException("Commands must be registered during plugin loading");
    }
    CommandRoot root = commandType.getAnnotation(CommandRoot.class);
    if (root == null) {
      throw new IllegalArgumentException("Missing @CommandRoot on " + commandType.getName());
    }
    T handler = instantiate(commandType);
    CommandTree tree = commands.computeIfAbsent(root.name(), ignored -> new CommandTree(root));
    tree.requireCompatible(root);
    scan(handler, tree.getRoot());
    return handler;
  }

  private synchronized void bind(final Commands registrar) {
    // Paper may rebuild its command dispatcher, so the stored model stays reusable
    for (CommandTree tree : commands.values()) {
      LiteralArgumentBuilder<CommandSourceStack> rootBuilder = literal(tree.getAnnotation().name());
      build(
          tree.getRoot(),
          rootBuilder,
          tree.getAnnotation().permission(),
          tree.getAnnotation().playerOnly()
      );
      registrar.register(
          rootBuilder.build(),
          tree.getAnnotation().description(),
          List.of(tree.getAnnotation().aliases())
      );
    }
    bound = true;
  }

  private void scan(final Object handler, final CommandNode root) {
    for (Method method : handler.getClass().getDeclaredMethods()) {
      Command command = method.getAnnotation(Command.class);
      if (command == null) {
        continue;
      }
      if (!Modifier.isPublic(method.getModifiers())) {
        throw new IllegalArgumentException("Command method must be public: " + method);
      }
      addPath(root, handler, method, command);
    }
  }

  private void addPath(
      final CommandNode root,
      final Object handler,
      final Method method,
      final Command command
  ) {
    String path = command.value().trim();
    if (path.equals(root.getName())) {
      path = "";
    } else if (path.startsWith(root.getName() + " ")) {
      path = path.substring(root.getName().length()).trim();
    }

    CommandExecution execution = new CommandExecution(handler, method, command);
    if (path.isEmpty()) {
      setExecution(root, execution, path);
      return;
    }

    String[] tokens = path.split("\\s+");
    CommandNode current = root;
    for (int index = 0; index < tokens.length; index++) {
      String token = tokens[index];
      if (isRequired(token)) {
        String name = token.substring(1, token.length() - 1).trim();
        boolean greedy = name.endsWith("...");
        if (greedy) {
          name = name.substring(0, name.length() - 3).trim();
        }
        requireArgumentName(name, token);
        if (greedy && index != tokens.length - 1) {
          throw new IllegalArgumentException("Greedy argument must be last: " + method);
        }
        current = current.argument(name, argumentSpec(method, name, false, greedy));
      } else if (isOptional(token)) {
        String name = token.substring(1, token.length() - 1).trim();
        requireArgumentName(name, token);
        if (index != tokens.length - 1) {
          throw new IllegalArgumentException("Optional argument must be last: " + method);
        }
        if (current.getExecution() == null) {
          // The parent path represents the command call where this argument was omitted
          current.setExecution(execution);
        }
        current = current.argument(name, argumentSpec(method, name, true, false));
      } else {
        current = current.literal(token);
      }
    }
    setExecution(current, execution, path);
  }

  private void setExecution(
      final CommandNode node,
      final CommandExecution execution,
      final String path
  ) {
    if (node.getExecution() != null) {
      throw new IllegalArgumentException("Duplicate command path: " + path);
    }
    node.setExecution(execution);
  }

  private ArgumentSpec argumentSpec(
      final Method method,
      final String name,
      final boolean optional,
      final boolean greedy
  ) {
    for (Parameter parameter : method.getParameters()) {
      Argument argument = parameter.getAnnotation(Argument.class);
      OptionalArgument optionalArgument = parameter.getAnnotation(OptionalArgument.class);
      boolean matchesRequired = argument != null && argument.value().equals(name);
      boolean matchesOptional = optionalArgument != null && optionalArgument.value().equals(name);
      if (matchesRequired || matchesOptional) {
        if (optional && !matchesOptional) {
          throw new IllegalArgumentException("Optional path requires @OptionalArgument: " + method);
        }
        Suggest suggest = parameter.getAnnotation(Suggest.class);
        return new ArgumentSpec(
            parameter.getType(),
            greedy || parameter.isAnnotationPresent(Greedy.class),
            suggest == null ? null : suggest.value()
        );
      }
    }
    throw new IllegalArgumentException("Missing argument parameter '" + name + "' in " + method);
  }

  private void build(
      final CommandNode node,
      final ArgumentBuilder<CommandSourceStack, ?> builder,
      final String rootPermission,
      final boolean rootPlayerOnly
  ) {
    CommandExecution execution = node.getExecution();
    builder.requires(source -> canAccessNode(source, node, rootPermission, rootPlayerOnly));
    if (execution != null) {
      builder.executes(context -> invoke(
          execution,
          context,
          rootPermission,
          rootPlayerOnly
      ));
    }
    for (CommandNode child : node.getChildren().values()) {
      ArgumentBuilder<CommandSourceStack, ?> childBuilder = child.getSpec() == null
          ? literal(child.getName())
          : argumentBuilder(child.getName(), child.getSpec());
      build(child, childBuilder, rootPermission, rootPlayerOnly);
      builder.then(childBuilder);
    }
  }

  private ArgumentBuilder<CommandSourceStack, ?> argumentBuilder(
      final String name,
      final ArgumentSpec spec
  ) {
    RequiredArgumentBuilder<CommandSourceStack, ?> builder;
    Class<?> type = spec.getType();
    if (type == int.class || type == Integer.class) {
      builder = argument(name, IntegerArgumentType.integer());
    } else if (type == long.class || type == Long.class) {
      builder = argument(name, LongArgumentType.longArg());
    } else if (type == double.class || type == Double.class) {
      builder = argument(name, DoubleArgumentType.doubleArg());
    } else if (type == float.class || type == Float.class) {
      builder = argument(name, FloatArgumentType.floatArg());
    } else if (type == boolean.class || type == Boolean.class) {
      builder = argument(name, BoolArgumentType.bool());
    } else if (supportsStringArgument(type)) {
      builder = argument(
          name,
          spec.isGreedy() ? StringArgumentType.greedyString() : StringArgumentType.word()
      );
    } else {
      throw new IllegalArgumentException("Unsupported command argument type: " + type.getName());
    }
    applySuggestions(builder, spec);
    return builder;
  }

  private void applySuggestions(
      final RequiredArgumentBuilder<CommandSourceStack, ?> builder,
      final ArgumentSpec spec
  ) {
    if (spec.getSuggestionType() != null) {
      SuggestionProvider provider = instantiate(spec.getSuggestionType());
      builder.suggests((context, suggestions) ->
          provider.suggest(new VexCommandSource(context.getSource()), suggestions));
    } else if (spec.getType() == Player.class) {
      builder.suggests((context, suggestions) -> {
        String remaining = suggestions.getRemainingLowerCase();
        Bukkit.getOnlinePlayers().stream()
            .map(Player::getName)
            .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(remaining))
            .forEach(suggestions::suggest);
        return suggestions.buildFuture();
      });
    } else if (spec.getType().isEnum()) {
      builder.suggests((context, suggestions) -> {
        String remaining = suggestions.getRemainingLowerCase();
        Arrays.stream(spec.getType().getEnumConstants())
            .map(value -> ((Enum<?>) value).name().toLowerCase(Locale.ROOT))
            .filter(value -> value.startsWith(remaining))
            .forEach(suggestions::suggest);
        return suggestions.buildFuture();
      });
    }
  }

  private int invoke(
      final CommandExecution execution,
      final CommandContext<CommandSourceStack> context,
      final String rootPermission,
      final boolean rootPlayerOnly
  ) throws CommandSyntaxException {
    if (!canExecute(
        context.getSource(),
        execution,
        rootPermission,
        rootPlayerOnly
    )) {
      throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().create();
    }
    try {
      Object result = execution.getMethod().invoke(
          execution.getHandler(),
          invocationArguments(execution.getMethod(), context)
      );
      return result instanceof Integer value ? value : 1;
    } catch (IllegalAccessException exception) {
      throw new IllegalStateException("Unable to invoke command method", exception);
    } catch (InvocationTargetException exception) {
      Throwable cause = exception.getCause();
      if (cause instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      throw new IllegalStateException("Command execution failed", cause);
    }
  }

  private Object[] invocationArguments(
      final Method method,
      final CommandContext<CommandSourceStack> context
  ) {
    Parameter[] parameters = method.getParameters();
    Object[] values = new Object[parameters.length];
    for (int index = 0; index < parameters.length; index++) {
      Parameter parameter = parameters[index];
      if (parameter.getType() == VexCommandSource.class) {
        values[index] = new VexCommandSource(context.getSource());
        continue;
      }
      if (parameter.getType() == CommandSourceStack.class) {
        values[index] = context.getSource();
        continue;
      }
      Argument argument = parameter.getAnnotation(Argument.class);
      OptionalArgument optional = parameter.getAnnotation(OptionalArgument.class);
      if (argument == null && optional == null) {
        throw new IllegalArgumentException("Missing argument annotation on " + parameter);
      }
      String name = argument == null ? optional.value() : argument.value();
      try {
        values[index] = readArgument(context, name, parameter.getType());
      } catch (IllegalArgumentException exception) {
        if (optional == null) {
          throw exception;
        }
        values[index] = defaultValue(parameter.getType(), optional.defaultValue());
      }
    }
    return values;
  }

  private Object readArgument(
      final CommandContext<CommandSourceStack> context,
      final String name,
      final Class<?> type
  ) {
    if (type == String.class) {
      return StringArgumentType.getString(context, name);
    }
    if (type == int.class || type == Integer.class) {
      return IntegerArgumentType.getInteger(context, name);
    }
    if (type == long.class || type == Long.class) {
      return LongArgumentType.getLong(context, name);
    }
    if (type == double.class || type == Double.class) {
      return DoubleArgumentType.getDouble(context, name);
    }
    if (type == float.class || type == Float.class) {
      return FloatArgumentType.getFloat(context, name);
    }
    if (type == boolean.class || type == Boolean.class) {
      return BoolArgumentType.getBool(context, name);
    }
    String raw = StringArgumentType.getString(context, name);
    if (type == Player.class) {
      Player player = Bukkit.getPlayerExact(raw);
      if (player == null) {
        throw new IllegalArgumentException("Player not found: " + raw);
      }
      return player;
    }
    if (type == UUID.class) {
      return UUID.fromString(raw);
    }
    if (type == Duration.class) {
      return parseDuration(raw);
    }
    if (type.isEnum()) {
      return enumValue(type, raw);
    }
    throw new IllegalArgumentException("Unsupported command argument type: " + type.getName());
  }

  private Object defaultValue(final Class<?> type, final String value) {
    if (value.isEmpty()) {
      if (type == boolean.class || type == Boolean.class) {
        return false;
      }
      if (type == int.class || type == Integer.class) {
        return 0;
      }
      if (type == long.class || type == Long.class) {
        return 0L;
      }
      if (type == double.class || type == Double.class) {
        return 0D;
      }
      if (type == float.class || type == Float.class) {
        return 0F;
      }
      return null;
    }
    if (type == String.class) {
      return value;
    }
    if (type == boolean.class || type == Boolean.class) {
      return Boolean.parseBoolean(value);
    }
    if (type == int.class || type == Integer.class) {
      return Integer.parseInt(value);
    }
    if (type == long.class || type == Long.class) {
      return Long.parseLong(value);
    }
    if (type == double.class || type == Double.class) {
      return Double.parseDouble(value);
    }
    if (type == float.class || type == Float.class) {
      return Float.parseFloat(value);
    }
    if (type == UUID.class) {
      return UUID.fromString(value);
    }
    if (type == Duration.class) {
      return parseDuration(value);
    }
    if (type.isEnum()) {
      return enumValue(type, value);
    }
    throw new IllegalArgumentException("Unsupported optional argument type: " + type.getName());
  }

  private Duration parseDuration(final String input) {
    String value = input.trim().toLowerCase(Locale.ROOT);
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Duration must not be empty");
    }
    long multiplier;
    String number;
    if (value.endsWith("ms")) {
      multiplier = 1L;
      number = value.substring(0, value.length() - 2);
    } else {
      char unit = value.charAt(value.length() - 1);
      number = value.substring(0, value.length() - 1);
      multiplier = switch (unit) {
        case 's' -> 1_000L;
        case 'm' -> 60_000L;
        case 'h' -> 3_600_000L;
        case 'd' -> 86_400_000L;
        default -> throw new IllegalArgumentException("Unknown duration unit: " + unit);
      };
    }
    return Duration.ofMillis(Math.multiplyExact(Long.parseLong(number), multiplier));
  }

  private Object enumValue(final Class<?> type, final String value) {
    @SuppressWarnings({"rawtypes", "unchecked"})
    Object result = Enum.valueOf(
        (Class<? extends Enum>) type.asSubclass(Enum.class),
        value.toUpperCase(Locale.ROOT)
    );
    return result;
  }

  private boolean canAccessNode(
      final CommandSourceStack source,
      final CommandNode node,
      final String rootPermission,
      final boolean rootPlayerOnly
  ) {
    if (!canUseRoot(source, rootPermission, rootPlayerOnly)) {
      return false;
    }
    if (node.getExecution() != null
        && canExecute(source, node.getExecution(), rootPermission, rootPlayerOnly)) {
      return true;
    }
    for (CommandNode child : node.getChildren().values()) {
      if (canAccessNode(source, child, rootPermission, rootPlayerOnly)) {
        return true;
      }
    }
    return false;
  }

  private boolean canExecute(
      final CommandSourceStack source,
      final CommandExecution execution,
      final String rootPermission,
      final boolean rootPlayerOnly
  ) {
    if (!canUseRoot(source, rootPermission, rootPlayerOnly)) {
      return false;
    }
    Command command = execution.getAnnotation();
    if (command.playerOnly() && !(source.getSender() instanceof Player)) {
      return false;
    }
    return command.permission().isBlank()
        || source.getSender().hasPermission(command.permission());
  }

  private boolean canUseRoot(
      final CommandSourceStack source,
      final String rootPermission,
      final boolean rootPlayerOnly
  ) {
    if (rootPlayerOnly && !(source.getSender() instanceof Player)) {
      return false;
    }
    return rootPermission.isBlank()
        || source.getSender().hasPermission(rootPermission);
  }

  private <T> T instantiate(final Class<T> type) {
    return VexClassFactory.create(type, services, "Registered command component");
  }

  private boolean supportsStringArgument(final Class<?> type) {
    return type == String.class
        || type == Player.class
        || type == UUID.class
        || type == Duration.class
        || type.isEnum();
  }

  private boolean isRequired(final String token) {
    return token.length() > 2 && token.startsWith("<") && token.endsWith(">");
  }

  private boolean isOptional(final String token) {
    return token.length() > 2 && token.startsWith("[") && token.endsWith("]");
  }

  private void requireArgumentName(final String name, final String token) {
    if (name.isEmpty()) {
      throw new IllegalArgumentException("Invalid command argument: " + token);
    }
  }

  @Value
  private static class CommandTree {
    CommandRoot annotation;
    CommandNode root;

    private CommandTree(final CommandRoot annotation) {
      this.annotation = annotation;
      this.root = new CommandNode(annotation.name(), null);
    }

    private void requireCompatible(final CommandRoot other) {
      if (!annotation.description().equals(other.description())
          || !annotation.permission().equals(other.permission())
          || annotation.playerOnly() != other.playerOnly()
          || !Arrays.equals(annotation.aliases(), other.aliases())) {
        throw new IllegalArgumentException("Conflicting command root: " + annotation.name());
      }
    }
  }

  @Getter
  private static final class CommandNode {
    private final String name;
    private final ArgumentSpec spec;
    private final Map<String, CommandNode> children = new LinkedHashMap<>();
    @Setter
    private CommandExecution execution;

    private CommandNode(final String name, final ArgumentSpec spec) {
      this.name = name;
      this.spec = spec;
    }

    public CommandNode literal(final String literal) {
      return children.computeIfAbsent("literal:" + literal, ignored -> new CommandNode(literal, null));
    }

    public CommandNode argument(final String argument, final ArgumentSpec argumentSpec) {
      String key = "argument:" + argument;
      CommandNode existing = children.get(key);
      if (existing != null) {
        if (!existing.getSpec().equals(argumentSpec)) {
          throw new IllegalArgumentException("Conflicting command argument: " + argument);
        }
        return existing;
      }
      CommandNode created = new CommandNode(argument, argumentSpec);
      children.put(key, created);
      return created;
    }
  }

  @Value
  private static class ArgumentSpec {
    Class<?> type;
    boolean greedy;
    Class<? extends SuggestionProvider> suggestionType;
  }

  @Value
  private static class CommandExecution {
    Object handler;
    Method method;
    Command annotation;
  }
}
