package dev.vexsoft.core.paper.service.sidebar;

/** Generation-safe handle for one trigger-bound sidebar channel. */
public interface SidebarHandle extends AutoCloseable {

  /** Replaces this trigger's current frame while retaining its priority. */
  void update(SidebarFrame frame);

  /** Ends only this exact trigger generation. */
  @Override
  void close();
}
