package dev.vexsoft.core.paper.platform;

public final class PlatformDetector {
  private static final String FOLIA_SERVER_CLASS = "io.papermc.paper.threadedregions.RegionizedServer";
  private static final ServerPlatform PLATFORM = detect();

  private PlatformDetector() {
  }

  public static ServerPlatform platform() {
    return PLATFORM;
  }

  public static boolean isFolia() {
    return PLATFORM == ServerPlatform.FOLIA;
  }

  private static ServerPlatform detect() {
    try {
      Class.forName(FOLIA_SERVER_CLASS, false, PlatformDetector.class.getClassLoader());
      return ServerPlatform.FOLIA;
    } catch (ClassNotFoundException exception) {
      return ServerPlatform.PAPER;
    }
  }
}
