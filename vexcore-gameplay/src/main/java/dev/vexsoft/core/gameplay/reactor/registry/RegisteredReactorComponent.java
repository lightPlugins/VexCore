package dev.vexsoft.core.gameplay.reactor.registry;

import dev.vexsoft.core.api.service.ServiceOwner;
import lombok.Value;

@Value
final class RegisteredReactorComponent<T> {
  String id;
  ServiceOwner owner;
  T component;
}
