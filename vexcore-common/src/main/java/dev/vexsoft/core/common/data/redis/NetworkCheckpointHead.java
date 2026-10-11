package dev.vexsoft.core.common.data.redis;

/** Publication cursor without loading the stored player and profile data. */
public record NetworkCheckpointHead(long generation, String position) {
}
