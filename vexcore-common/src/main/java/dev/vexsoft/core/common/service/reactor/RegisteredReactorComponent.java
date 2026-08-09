package dev.vexsoft.core.common.service.reactor;

import dev.vexsoft.core.api.service.registry.ServiceOwner;
import lombok.Value;

@Value
final class RegisteredReactorComponent<T> {
  String id;
  ServiceOwner owner;
  T component;
}
