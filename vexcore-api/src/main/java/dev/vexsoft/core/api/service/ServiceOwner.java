package dev.vexsoft.core.api.service;

/**
 * Identifies the plugin or module that owns registered services
 */
public interface ServiceOwner {

  /**
   * Returns the human-readable name of the service owner
   *
   * @return the owner name
   */
  String getServiceOwnerName();
}
