package dev.vexsoft.core.paper.service.localization.editor;

import dev.vexsoft.core.api.localization.LanguageContainer;
import dev.vexsoft.core.api.localization.LanguageKey;
import dev.vexsoft.core.api.localization.LocalizationOwner;
import dev.vexsoft.core.api.localization.LocalizedMessage;
import dev.vexsoft.core.api.service.localization.LocalizationService;
import dev.vexsoft.core.api.service.localization.ThemeColorService;
import dev.vexsoft.core.api.service.player.PlayerService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.common.service.localization.editor.LocalizationBrowserNode;
import dev.vexsoft.core.common.service.localization.editor.LocalizationEditorService;
import dev.vexsoft.core.common.service.localization.editor.LocalizationEntryView;
import dev.vexsoft.core.common.service.localization.editor.LocalizationValue;
import dev.vexsoft.core.paper.dialogs.TextInputDialogBuilder;
import dev.vexsoft.core.paper.inventory.InventoryContext;
import dev.vexsoft.core.paper.inventory.InventoryKey;
import dev.vexsoft.core.paper.inventory.element.StaticInventoryElement;
import dev.vexsoft.core.paper.inventory.element.RefreshableInventoryElement;
import dev.vexsoft.core.paper.inventory.page.CollectionPageSource;
import dev.vexsoft.core.paper.inventory.page.PageBounds;
import dev.vexsoft.core.paper.inventory.page.PagedInventoryView;
import dev.vexsoft.core.paper.service.dialogs.DialogService;
import dev.vexsoft.core.paper.service.inventory.InventoryService;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import dev.vexsoft.core.paper.inventory.page.PageItemRenderer;

/** Default inventory and dialog frontend for localization editing. */
@Dependencies({
    LocalizationEditorService.class,
    LocalizationService.class,
    ThemeColorService.class,
    PlayerService.class,
    InventoryService.class,
    DialogService.class
})
public final class VexLocalizationEditorUiService implements LocalizationEditorUiService {

  private static final PageBounds CONTENT = PageBounds.rectangle(1, 1, 7, 4);
  private static final int LORE_PREVIEW_WIDTH = 40;
  private static final String PREVIEW_PLACEHOLDER = "__VEX_LOCALIZATION_PREVIEW__";
  private static final Pattern MINI_MESSAGE_TAG = Pattern.compile("<[^>]+>");
  private static final Path ROOT = Path.of("");

  private final VexServiceRegistry services;
  private final LocalizationEditorService editor;
  private final LocalizationService localization;
  private final ThemeColorService themes;
  private final PlayerService players;
  private final InventoryService inventories;
  private final DialogService dialogs;
  private final Logger logger = Logger.getLogger("VexCore");

  public VexLocalizationEditorUiService(final VexServiceRegistry services) {
    this.services = Objects.requireNonNull(services, "services");
    editor = services.require(LocalizationEditorService.class);
    localization = services.require(LocalizationService.class);
    themes = services.require(ThemeColorService.class);
    players = services.require(PlayerService.class);
    inventories = services.require(InventoryService.class);
    dialogs = services.require(DialogService.class);
  }

  @Override
  public void open(final Player player) {
    inventories.openRoot(Objects.requireNonNull(player, "player"), pluginView());
  }

  private PluginView pluginView() {
    return new PluginView(List.copyOf(editor.getOwners()));
  }

  private LanguageView languageView(final String ownerName) {
    return new LanguageView(ownerName, List.copyOf(editor.getLanguages(ownerName)));
  }

  private BrowserView browserView(
      final String ownerName,
      final LanguageKey language,
      final Path directory
  ) {
    return new BrowserView(
        ownerName,
        language,
        directory,
        List.copyOf(editor.browse(ownerName, language, directory))
    );
  }

  private EntryView entryView(
      final String ownerName,
      final LanguageKey language,
      final Path file
  ) {
    return new EntryView(
        ownerName,
        language,
        file,
        List.copyOf(editor.getEntries(ownerName, language, file))
    );
  }

  private void reopenEntry(
      final Player player,
      final String ownerName,
      final LanguageKey language,
      final Path file
  ) {
    inventories.openRoot(player, pluginView());
    inventories.open(player, languageView(ownerName));
    inventories.open(player, browserView(ownerName, language, ROOT));
    Path directory = ROOT;
    Path parent = file.getParent();
    if (parent != null) {
      for (Path segment : parent) {
        directory = directory.resolve(segment);
        inventories.open(player, browserView(ownerName, language, directory));
      }
    }
    inventories.open(player, entryView(ownerName, language, file));
  }

  private void edit(
      final Player player,
      final String ownerName,
      final LanguageKey language,
      final Path file,
      final LocalizationEntryView entry,
      final String initialValue,
      final String error
  ) {
    TextInputDialogBuilder dialog = dialogs.textInput(player)
        .title(text(player, "localization-editor.dialog.title", Map.of(
            "plugin", ownerName,
            "language", language.getValue()
        )))
        .label(text(player, "localization-editor.dialog.label", Map.of("key", entry.key())))
        .initialValue(initialValue)
        .maxLength(16_384)
        .submitButton(text(player, "localization-editor.dialog.save", Map.of()))
        .cancelButton(text(player, "localization-editor.dialog.cancel", Map.of()))
        .canCloseWithEscape(true)
        .timeout(Duration.ofMinutes(10));
    dialog.message(text(player, "localization-editor.dialog.location", Map.of(
        "file", file.toString().replace('\\', '/'),
        "key", entry.key()
    )));
    if (entry.inherited()) {
      dialog.message(text(player, "localization-editor.dialog.inherited", Map.of()));
    }
    if (error != null) {
      dialog.message(text(player, "localization-editor.dialog.error", Map.of("error", error)));
    }
    dialog.message(text(player, "localization-editor.dialog.preview", Map.of()));
    preview(initialValue, entry.value().list()).forEach(dialog::message);
    if (entry.value().list()) {
      dialog.multiline(50, 180);
    }
    dialog.open().whenComplete((result, throwable) -> {
      if (throwable != null) {
        logger.log(Level.WARNING, "Localization editor dialog failed", throwable);
        return;
      }
      if (!result.isConfirmed()) {
        reopenEntry(player, ownerName, language, file);
        return;
      }
      String submitted = result.getValue().orElse("");
      try {
        LocalizationValue value = entry.value().list()
            ? LocalizationValue.lines(lines(submitted))
            : LocalizationValue.text(submitted);
        value.lines().forEach(themes::deserialize);
        editor.update(ownerName, language, file, entry.key(), value);
        reopenEntry(player, ownerName, language, file);
      } catch (RuntimeException exception) {
        logger.log(Level.WARNING, "Unable to update localization " + ownerName + ':' + entry.key(), exception);
        edit(
            player,
            ownerName,
            language,
            file,
            entry,
            submitted,
            rootMessage(exception)
        );
      }
    });
  }

  private List<Component> preview(final String value, final boolean list) {
    try {
      return (list ? lines(value) : List.of(value)).stream().map(themes::deserialize).toList();
    } catch (RuntimeException exception) {
      return List.of(Component.text(value));
    }
  }

  private List<Component> entryLore(
      final Player player,
      final LocalizationEntryView entry,
      final Map<String, String> replacements
  ) {
    List<Component> previewLines = new ArrayList<>();
    List<String> values = entry.value().lines();
    for (String value : values) {
      List<Component> wrapped = value.isEmpty()
          ? localizedLines(player, "localization-editor.entries.item.preview.empty", replacements)
          : wrapPreview(value);
      for (int index = 0; index < wrapped.size(); index++) {
        Component line = wrapped.get(index);
        if (entry.value().list() && index == 0) {
          line = Component.text("- ").append(line);
        }
        previewLines.add(line);
      }
    }
    List<Component> lore = new ArrayList<>();
    for (Component line : localizedLines(
        player, "localization-editor.entries.item.lore", replacements
    )) {
      if (PlainTextComponentSerializer.plainText().serialize(line).equals(PREVIEW_PLACEHOLDER)) {
        lore.addAll(previewLines);
      } else {
        lore.add(line);
      }
    }
    return List.copyOf(lore);
  }

  private List<Component> wrapPreview(final String value) {
    List<String> wrapped = new ArrayList<>();
    StringBuilder line = new StringBuilder();
    for (String word : value.strip().split("\\s+")) {
      if (!line.isEmpty()
          && visibleLength(line.toString()) + 1 + visibleLength(word) > LORE_PREVIEW_WIDTH) {
        wrapped.add(line.toString());
        line.setLength(0);
      }
      if (!line.isEmpty()) {
        line.append(' ');
      }
      line.append(word);
    }
    if (!line.isEmpty()) {
      wrapped.add(line.toString());
    }
    if (wrapped.isEmpty()) {
      wrapped.add("");
    }
    Map<String, String> activeTags = new LinkedHashMap<>();
    List<Component> components = new ArrayList<>(wrapped.size());
    try {
      for (String wrappedLine : wrapped) {
        String prefix = String.join("", activeTags.values());
        components.add(themes.deserialize(prefix + wrappedLine));
        updateActiveTags(activeTags, wrappedLine);
      }
      return List.copyOf(components);
    } catch (RuntimeException exception) {
      return wrapped.stream().<Component>map(Component::text).toList();
    }
  }

  private void updateActiveTags(final Map<String, String> activeTags, final String value) {
    Matcher matcher = MINI_MESSAGE_TAG.matcher(value);
    while (matcher.find()) {
      String tag = matcher.group();
      String content = tag.substring(1, tag.length() - 1);
      if (content.equalsIgnoreCase("reset")) {
        activeTags.clear();
        continue;
      }
      boolean closing = content.startsWith("/");
      String normalized = closing ? content.substring(1) : content;
      int separator = normalized.indexOf(':');
      String name = (separator < 0 ? normalized : normalized.substring(0, separator)).toLowerCase();
      if (closing) {
        activeTags.remove(name);
      } else {
        activeTags.put(name, tag);
      }
    }
  }

  private int visibleLength(final String value) {
    String visible = MINI_MESSAGE_TAG.matcher(value).replaceAll("");
    return visible.codePointCount(0, visible.length());
  }

  private String localizedText(final Player player, final String key) {
    return PlainTextComponentSerializer.plainText().serialize(text(player, key, Map.of()));
  }

  private List<String> lines(final String value) {
    String[] raw = value.split("\\R", -1);
    int length = raw.length;
    while (length > 1 && raw[length - 1].isEmpty()) {
      length--;
    }
    return List.of(raw).subList(0, length);
  }

  private String rootMessage(final Throwable throwable) {
    Throwable root = throwable;
    while (root.getCause() != null) {
      root = root.getCause();
    }
    String message = root.getMessage();
    return message == null || message.isBlank() ? root.getClass().getSimpleName() : message;
  }

  private Component text(
      final Player player,
      final String key,
      final Map<String, String> replacements
  ) {
    LanguageKey language = players.require(player.getUniqueId())
        .getContainer(LanguageContainer.class)
        .getLanguage()
        .getKey();
    LocalizedMessage message = localization.resolve(language, key, replacements);
    return join(message.getLines());
  }

  private List<Component> localizedLines(
      final Player player,
      final String key,
      final Map<String, String> replacements
  ) {
    LanguageKey language = players.require(player.getUniqueId())
        .getContainer(LanguageContainer.class)
        .getLanguage()
        .getKey();
    return List.copyOf(localization.resolve(language, key, replacements).getLines());
  }

  private Component join(final Collection<Component> lines) {
    Component result = Component.empty();
    boolean first = true;
    for (Component line : lines) {
      if (!first) {
        result = result.append(Component.newline());
      }
      result = result.append(line);
      first = false;
    }
    return result;
  }

  private ItemStack item(
      final Material material,
      final Component name,
      final List<Component> lore
  ) {
    ItemStack item = new ItemStack(material);
    ItemMeta meta = item.getItemMeta();
    meta.displayName(nonItalic(name));
    meta.lore(lore.stream().map(this::nonItalic).toList());
    if (!item.setItemMeta(meta)) {
      throw new IllegalStateException("Unable to apply localization editor item metadata");
    }
    return item;
  }

  private ItemStack decoration() {
    ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
    ItemMeta meta = item.getItemMeta();
    meta.setHideTooltip(true);
    if (!item.setItemMeta(meta)) {
      throw new IllegalStateException("Unable to apply localization editor decoration metadata");
    }
    return item;
  }

  private Component nonItalic(final Component component) {
    return component.decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE);
  }

  private class EditorPage<T> extends PagedInventoryView<T> {

    EditorPage(
        final InventoryKey key,
        final List<T> values,
        final String translationPrefix,
        final Function<InventoryContext, Component> title,
        final PageItemRenderer<T> renderer
    ) {
      super(
          services,
          key,
          54,
          CONTENT,
          new CollectionPageSource<>(values),
          renderer
      );
      setTitle(title);
      fill(decoration());
      setPreviousButton(48, context -> item(
          Material.ARROW,
          text(context.getViewer(), translationPrefix + ".navigation.previous.name", Map.of()),
          localizedLines(
              context.getViewer(), translationPrefix + ".navigation.previous.lore", Map.of()
          )
      ));
      setNextButton(50, context -> item(
          Material.ARROW,
          text(context.getViewer(), translationPrefix + ".navigation.next.name", Map.of()),
          localizedLines(context.getViewer(), translationPrefix + ".navigation.next.lore", Map.of())
      ));
      setBackButtonElement(45, new RefreshableInventoryElement(
          context -> item(
              Material.BARRIER,
              text(context.getViewer(), translationPrefix + ".navigation.back.name", Map.of()),
              localizedLines(context.getViewer(), translationPrefix + ".navigation.back.lore", Map.of())
          ),
          (context, event) -> context.getInventoryService().back(context.getViewer())
      ));
      setPageIndicator(53, context -> item(
          Material.MAP,
          text(context.getViewer(), translationPrefix + ".navigation.page.name", Map.of(
              "page", Integer.toString(getPage() + 1),
              "pages", Integer.toString(getPageCount(context))
          )),
          localizedLines(context.getViewer(), translationPrefix + ".navigation.page.lore", Map.of(
              "page", Integer.toString(getPage() + 1),
              "pages", Integer.toString(getPageCount(context))
          ))
      ));
    }

  }

  private final class PluginView extends EditorPage<LocalizationOwner> {

    PluginView(final List<LocalizationOwner> owners) {
      super(
          InventoryKey.of("vexcore:localization/plugins"),
          owners,
          "localization-editor.plugins",
          context -> text(context.getViewer(), "localization-editor.plugins.title", Map.of()),
          (context, owner, index) -> {
            String name = owner.getServiceOwnerName();
            return new StaticInventoryElement(
                item(
                    Material.BOOKSHELF,
                    Component.text(name),
                    localizedLines(
                        context.getViewer(), "localization-editor.plugins.item.lore", Map.of(
                            "plugin", name
                        )
                    )
                ),
                (clicked, event) -> inventories.open(clicked.getViewer(), languageView(name))
            );
          }
      );
    }
  }

  private final class LanguageView extends EditorPage<LanguageKey> {

    private final String ownerName;

    LanguageView(final String ownerName, final List<LanguageKey> languages) {
      super(
          InventoryKey.of("vexcore:localization/languages"),
          languages,
          "localization-editor.languages",
          context -> text(context.getViewer(), "localization-editor.languages.title", Map.of(
              "plugin", ownerName
          )),
          (context, language, index) -> new StaticInventoryElement(
              item(
                  Material.WRITABLE_BOOK,
                  text(context.getViewer(), "localization-editor.languages.item.name", Map.of(
                      "language", language.getValue()
                  )),
                  localizedLines(
                      context.getViewer(), "localization-editor.languages.item.lore", Map.of(
                          "plugin", ownerName,
                          "language", language.getValue()
                      )
                  )
              ),
              (clicked, event) -> inventories.open(
                  clicked.getViewer(),
                  browserView(ownerName, language, ROOT)
              )
          )
      );
      this.ownerName = ownerName;
    }
  }

  private final class BrowserView extends EditorPage<LocalizationBrowserNode> {

    private final String ownerName;
    private final LanguageKey language;

    BrowserView(
        final String ownerName,
        final LanguageKey language,
        final Path directory,
        final List<LocalizationBrowserNode> nodes
    ) {
      super(
          InventoryKey.of("vexcore:localization/browser"),
          nodes,
          "localization-editor.browser",
          context -> text(context.getViewer(), "localization-editor.browser.title", Map.of(
              "plugin", ownerName,
              "language", language.getValue(),
              "path", displayPath(directory)
          )),
          (context, node, index) -> {
            Material material = node.directory() ? Material.CHEST : Material.PAPER;
            String itemKey = node.directory()
                ? "localization-editor.browser.item.directory"
                : "localization-editor.browser.item.file";
            String replacementKey = node.directory() ? "directory" : "file";
            Map<String, String> replacements = Map.of(
                replacementKey, node.name(),
                "path", node.relativePath().toString().replace('\\', '/'),
                "source", localizedText(
                    context.getViewer(),
                    "localization-editor.browser.placeholder.source."
                        + (node.inherited() ? "inherited" : "local")
                ),
                "action", localizedText(
                    context.getViewer(),
                    "localization-editor.browser.placeholder.action."
                        + (node.directory() ? "directory" : "file")
                )
            );
            return new StaticInventoryElement(
                item(
                    material,
                    text(context.getViewer(), itemKey + ".name", replacements),
                    localizedLines(context.getViewer(), itemKey + ".lore", replacements)
                ),
                (clicked, event) -> inventories.open(
                    clicked.getViewer(),
                    node.directory()
                        ? browserView(ownerName, language, node.relativePath())
                        : entryView(ownerName, language, node.relativePath())
                )
            );
          }
      );
      this.ownerName = ownerName;
      this.language = language;
    }

  }

  private final class EntryView extends EditorPage<LocalizationEntryView> {

    private final String ownerName;
    private final LanguageKey language;
    private final Path file;

    EntryView(
        final String ownerName,
        final LanguageKey language,
        final Path file,
        final List<LocalizationEntryView> entries
    ) {
      super(
          InventoryKey.of("vexcore:localization/entries"),
          entries,
          "localization-editor.entries",
          context -> text(context.getViewer(), "localization-editor.entries.title", Map.of(
              "file", file.toString().replace('\\', '/')
          )),
          (context, entry, index) -> {
            Map<String, String> replacements = Map.of(
                "key", entry.key(),
                "lines", Integer.toString(entry.value().lines().size()),
                "type", localizedText(
                    context.getViewer(),
                    "localization-editor.entries.placeholder.type."
                        + (entry.value().list() ? "list" : "string")
                ),
                "source", localizedText(
                    context.getViewer(),
                    "localization-editor.entries.placeholder.source."
                        + (entry.inherited() ? "inherited" : "local")
                ),
                "action", localizedText(
                    context.getViewer(),
                    "localization-editor.entries.placeholder.action."
                        + (entry.inherited() ? "create" : "edit")
                ),
                "preview", PREVIEW_PLACEHOLDER
            );
            return new StaticInventoryElement(
                item(
                    Material.NAME_TAG,
                    text(context.getViewer(), "localization-editor.entries.item.name", replacements),
                    entryLore(context.getViewer(), entry, replacements)
                ),
                (clicked, event) -> edit(
                    clicked.getViewer(),
                    ownerName,
                    language,
                    file,
                    entry,
                    String.join("\n", entry.value().lines()),
                    null
                )
            );
          }
      );
      this.ownerName = ownerName;
      this.language = language;
      this.file = file;
    }

  }

  private String displayPath(final Path path) {
    return path.toString().isEmpty() ? "/" : path.toString().replace('\\', '/');
  }
}
