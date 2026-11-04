package dev.vexsoft.core.paper.vexui.render;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.renderer.TranslatableComponentRenderer;
import net.kyori.adventure.translation.Translator;

/** Immutable vanilla dictionaries and reusable formats; no disk or network access occurs while drawing a menu. */
public final class VexUITranslations implements Translator {

    private static final Pattern ARGUMENT = Pattern.compile("%%|%(?:(\\d+)\\$)?[sd]");
    private static final VexUITranslations INSTANCE = new VexUITranslations();
    private static final TranslatableComponentRenderer<Locale> RENDERER =
        TranslatableComponentRenderer.usingTranslationSource(INSTANCE);
    private final Properties english = load("en_us");
    private final Properties german = load("de_de");
    private final Map<String, MessageFormat> formats = new ConcurrentHashMap<>();

    /** Resolves vanilla translations while preserving the supplied component's styling and children. */
    public static Component render(final Component text, final Locale locale) {
        return translated(text) ? RENDERER.render(text, locale) : text;
    }

    @Override
    public Key name() {
        return Key.key("vexcore:vanilla_ui");
    }

    @Override
    public MessageFormat translate(final String key, final Locale locale) {
        boolean translated = locale.getLanguage().equals("de");
        String value = translated ? german.getProperty(key, english.getProperty(key)) : english.getProperty(key);
        if (value == null) {
            return null;
        }
        var format = formats.computeIfAbsent((translated ? "de:" : "en:") + key,
            ignored -> new MessageFormat(pattern(value), locale));
        // MessageFormat is mutable; renderers on separate entity threads must never share a working instance.
        return (MessageFormat) format.clone();
    }

    private static String pattern(final String value) {
        String escaped = value.replace("'", "''").replace("{", "'{'").replace("}", "'}'");
        int[] argument = {0};
        return ARGUMENT.matcher(escaped).replaceAll(match -> match.group().equals("%%") ? "%"
            : "{" + (match.group(1) == null ? argument[0]++ : Integer.parseInt(match.group(1)) - 1) + "}");
    }

    private static Properties load(final String locale) {
        var result = new Properties();
        try (var input = VexUITranslations.class.getResourceAsStream("vanilla/" + locale + ".properties")) {
            if (input == null) {
                throw new IllegalStateException("Missing generated vanilla UI translations: " + locale);
            }
            try (var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                result.load(reader);
            }
            return result;
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read vanilla UI translations: " + locale, failure);
        }
    }

    private static boolean translated(final Component text) {
        return text instanceof TranslatableComponent || text.children().stream().anyMatch(VexUITranslations::translated);
    }
}
