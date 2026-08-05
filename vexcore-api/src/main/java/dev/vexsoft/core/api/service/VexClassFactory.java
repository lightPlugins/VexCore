package dev.vexsoft.core.api.service;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Objects;
import lombok.experimental.UtilityClass;

/** Creates dependency-checked extension classes through their service-registry constructor. */
@UtilityClass
public class VexClassFactory {

  /** Creates a public class through its VexServiceRegistry constructor */
  public static <T> T create(
      final Class<T> type,
      final VexServiceRegistry services,
      final String role
  ) {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(services, "services");
    String checkedRole = Objects.requireNonNull(role, "role");
    if (!Modifier.isPublic(type.getModifiers()) || Modifier.isAbstract(type.getModifiers())) {
      throw new IllegalArgumentException(
          checkedRole + " class must be public and concrete: " + type.getName()
      );
    }
    Dependencies dependencies = type.getAnnotation(Dependencies.class);
    if (dependencies == null) {
      throw new IllegalArgumentException(
          checkedRole + " is missing @Dependencies: " + type.getName()
      );
    }
    for (Class<? extends VexService> dependency : dependencies.value()) {
      if (!services.isAvailable(dependency)) {
        throw new ServiceNotFoundException(dependency);
      }
    }
    try {
      Constructor<T> constructor = type.getConstructor(VexServiceRegistry.class);
      return constructor.newInstance(services);
    } catch (NoSuchMethodException exception) {
      throw new IllegalArgumentException(
          checkedRole + " requires a public VexServiceRegistry constructor: " + type.getName(),
          exception
      );
    } catch (InstantiationException | IllegalAccessException exception) {
      throw new IllegalStateException(
          "Unable to create " + checkedRole.toLowerCase() + " " + type.getName(),
          exception
      );
    } catch (InvocationTargetException exception) {
      Throwable cause = exception.getCause();
      if (cause instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      if (cause instanceof Error error) {
        throw error;
      }
      throw new IllegalStateException(checkedRole + " constructor failed: " + type.getName(), cause);
    }
  }
}
