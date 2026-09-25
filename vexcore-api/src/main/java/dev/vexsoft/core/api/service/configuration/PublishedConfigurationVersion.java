package dev.vexsoft.core.api.service.configuration;

/** Identifies the repository commit and database revision of a published snapshot. */
public record PublishedConfigurationVersion(String commit, long revision, boolean changed) {
}
