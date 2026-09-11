package dev.vexsoft.core.number;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses and formats exact whole amounts with incremental-game suffixes. */
public final class WholeAmountFormatter {

    private static final Pattern COMPACT = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*([a-zA-Z]*)");

    private WholeAmountFormatter() {
    }

    /**
     * Formats an amount with at most three significant digits using k, m, b, t, aa, ab and beyond.
     */
    public static String format(final WholeAmount amount) {
        return formatDecimal(new BigDecimal(Objects.requireNonNull(amount, "amount").toBigInteger()));
    }

    /** Formats a non-negative decimal value with the same incremental suffix sequence. */
    public static String formatDecimal(final BigDecimal amount) {
        BigDecimal value = Objects.requireNonNull(amount, "amount");

        if (value.signum() < 0) {
            throw new IllegalArgumentException("Compact amount must not be negative");
        }

        if (value.compareTo(BigDecimal.valueOf(1_000L)) < 0) {
            return value.stripTrailingZeros().toPlainString();
        }

        int integerDigits = value.precision() - value.scale();
        int group = (integerDigits - 1) / 3;
        BigDecimal compact = scaled(value, group);
        int scale = Math.max(0, 3 - compact.precision() + compact.scale());

        compact = compact.setScale(scale, RoundingMode.HALF_UP);

        if (compact.compareTo(BigDecimal.valueOf(1_000L)) >= 0) {
            group++;
            compact = scaled(value, group);
            scale = Math.max(0, 3 - compact.precision() + compact.scale());
            compact = compact.setScale(scale, RoundingMode.HALF_UP);
        }

        return compact.stripTrailingZeros().toPlainString() + suffix(group);
    }

    /** Formats a signed decimal value while preserving the common suffix sequence. */
    public static String formatSignedDecimal(final BigDecimal amount) {
        BigDecimal checked = Objects.requireNonNull(amount, "amount");

        return checked.signum() < 0 ? '-' + formatDecimal(checked.abs()) : formatDecimal(checked);
    }

    /** Parses either a complete integer or a compact value such as {@code 35ab}. */
    public static WholeAmount parse(final String input) {
        try {
            return WholeAmount.of(parseDecimal(input).toBigIntegerExact());
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Compact whole amount has a fractional result: " + input, exception);
        }
    }

    /** Parses a non-negative decimal with an optional compact suffix without rounding. */
    public static BigDecimal parseDecimal(final String input) {
        String checked = Objects.requireNonNull(input, "input").trim();
        Matcher matcher = COMPACT.matcher(checked);

        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid whole amount: " + input);
        }

        String suffix = matcher.group(2).toLowerCase(Locale.ROOT);

        if (suffix.isEmpty()) {
            if (matcher.group(1).indexOf('.') >= 0) {
                return new BigDecimal(matcher.group(1));
            }

            return new BigDecimal(matcher.group(1));
        }

        int group = group(suffix);
        BigDecimal expanded =
            new BigDecimal(matcher.group(1)).multiply(new BigDecimal(BigInteger.TEN.pow(Math.multiplyExact(group, 3))));

        return expanded;
    }

    private static BigDecimal scaled(final BigDecimal value, final int group) {
        return value.movePointLeft(Math.multiplyExact(group, 3));
    }

    private static String suffix(final int group) {
        return switch (group) {
            case 1 -> "k";
            case 2 -> "m";
            case 3 -> "b";
            case 4 -> "t";
            default -> alphabetic(group - 5);
        };
    }

    private static int group(final String suffix) {
        return switch (suffix) {
            case "k" -> 1;
            case "m" -> 2;
            case "b" -> 3;
            case "t" -> 4;
            default -> 5 + alphabeticIndex(suffix);
        };
    }

    private static String alphabetic(final int rawIndex) {
        if (rawIndex < 0) {
            throw new IllegalArgumentException("Invalid compact suffix group");
        }

        int index = rawIndex;
        int length = 2;
        int capacity = 26 * 26;

        while (index >= capacity) {
            index -= capacity;
            length++;
            capacity = Math.multiplyExact(capacity, 26);
        }

        char[] result = new char[length];

        for (int position = length - 1; position >= 0; position--) {
            result[position] = (char) ('a' + index % 26);
            index /= 26;
        }

        return new String(result);
    }

    private static int alphabeticIndex(final String suffix) {
        if (suffix.length() < 2 || !suffix.chars().allMatch(character -> character >= 'a' && character <= 'z')) {
            throw new IllegalArgumentException("Invalid compact whole-amount suffix: " + suffix);
        }

        int index = 0;
        int capacity = 26 * 26;

        for (int length = 2; length < suffix.length(); length++) {
            index = Math.addExact(index, capacity);
            capacity = Math.multiplyExact(capacity, 26);
        }

        int local = 0;

        for (int position = 0; position < suffix.length(); position++) {
            local = Math.addExact(Math.multiplyExact(local, 26), suffix.charAt(position) - 'a');
        }

        return Math.addExact(index, local);
    }
}
