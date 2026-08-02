package dev.vexsoft.core.localization;

import java.util.List;
import lombok.Value;

@Value
final class MessageTemplate {
  List<String> lines;
  boolean list;
}
