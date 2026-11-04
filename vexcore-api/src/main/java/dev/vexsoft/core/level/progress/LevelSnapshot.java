package dev.vexsoft.core.level.progress;

/** Complete derived level and progress information for one experience value. */
public record LevelSnapshot(int level, double experience, double levelStartExperience, double nextLevelExperience,
                            double experienceInLevel, double experienceRequired, double progress,
                            boolean maximumLevel) {

}
