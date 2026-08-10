package dev.vexsoft.core.paper.command.argument;

import dev.vexsoft.core.api.network.ServerId;

/** Parses validated backend server IDs. */
public final class ServerIdCommandArgument implements CommandArgumentType<ServerId> {

  @Override
  public Class<ServerId> getValueType() {
    return ServerId.class;
  }

  @Override
  public ServerId parseValue(final String nativeType) {
    return new ServerId(nativeType);
  }
}
