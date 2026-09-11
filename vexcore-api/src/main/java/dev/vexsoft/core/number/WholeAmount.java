package dev.vexsoft.core.number;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Objects;

/** Exact, non-negative, arbitrarily large whole-number amount used by scalable economies. */
public final class WholeAmount implements Comparable<WholeAmount> {

    /** Shared zero amount. */
    public static final WholeAmount ZERO = new WholeAmount(BigInteger.ZERO);
    /** Shared unit amount. */
    public static final WholeAmount ONE = new WholeAmount(BigInteger.ONE);

    private final BigInteger value;

    private WholeAmount(final BigInteger value) {
        this.value = requireNonNegative(value);
    }

    /** Creates an amount from a non-negative {@code long}. */
    public static WholeAmount of(final long value) {
        if (value == 0L) {
            return ZERO;
        }

        if (value == 1L) {
            return ONE;
        }

        return new WholeAmount(BigInteger.valueOf(value));
    }

    /** Creates an amount from a non-negative arbitrary-precision integer. */
    public static WholeAmount of(final BigInteger value) {
        BigInteger checked = requireNonNegative(value);

        if (checked.signum() == 0) {
            return ZERO;
        }

        if (BigInteger.ONE.equals(checked)) {
            return ONE;
        }

        return new WholeAmount(checked);
    }

    /** Parses an exact unsigned base-ten integer without compact suffixes. */
    public static WholeAmount parse(final String value) {
        String checked = Objects.requireNonNull(value, "value").trim();

        if (checked.isEmpty() || !checked.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("Whole amount must be an unsigned base-ten integer");
        }

        return of(new BigInteger(checked));
    }

    /** Returns this amount plus another amount. */
    public WholeAmount add(final WholeAmount other) {
        return of(value.add(require(other).value));
    }

    /** Returns this amount minus another amount, rejecting a negative result. */
    public WholeAmount subtract(final WholeAmount other) {
        return of(value.subtract(require(other).value));
    }

    /** Returns this amount multiplied by a non-negative whole-number factor. */
    public WholeAmount multiply(final long factor) {
        if (factor < 0L) {
            throw new IllegalArgumentException("Whole amount factor must not be negative");
        }

        return of(value.multiply(BigInteger.valueOf(factor)));
    }

    /** Returns this amount multiplied by a non-negative arbitrary-precision factor. */
    public WholeAmount multiply(final BigInteger factor) {
        return of(value.multiply(requireNonNegative(factor)));
    }

    /** Returns the quotient after integer division by a strictly positive amount. */
    public WholeAmount divide(final WholeAmount divisor) {
        WholeAmount checked = require(divisor);

        if (checked.isZero()) {
            throw new ArithmeticException("Division by zero");
        }

        return of(value.divide(checked.value));
    }

    /** Returns the quotient after integer division by a strictly positive factor. */
    public WholeAmount divide(final long divisor) {
        if (divisor <= 0L) {
            throw new IllegalArgumentException("Whole amount divisor must be positive");
        }

        return of(value.divide(BigInteger.valueOf(divisor)));
    }

    /**
     * Multiplies by a non-negative decimal factor and rounds to a whole amount as requested.
     */
    public WholeAmount multiply(final BigDecimal factor, final RoundingMode roundingMode) {
        BigDecimal checked = Objects.requireNonNull(factor, "factor");

        if (checked.signum() < 0) {
            throw new IllegalArgumentException("Whole amount factor must not be negative");
        }

        return of(new BigDecimal(value).multiply(checked)
            .setScale(
                0,
                Objects.requireNonNull(roundingMode, "roundingMode")
            )
            .toBigIntegerExact());
    }

    /** Returns the smaller of this amount and another amount. */
    public WholeAmount min(final WholeAmount other) {
        WholeAmount checked = require(other);

        return compareTo(checked) <= 0 ? this : checked;
    }

    /** Returns whether this amount is zero. */
    public boolean isZero() {
        return value.signum() == 0;
    }

    /** Returns whether this amount is strictly positive. */
    public boolean isPositive() {
        return value.signum() > 0;
    }

    /** Returns the exact arbitrary-precision integer value. */
    public BigInteger toBigInteger() {
        return value;
    }

    /** Returns the exact {@code long} value or throws when it does not fit. */
    public long longValueExact() {
        return value.longValueExact();
    }

    /** Returns the exact {@code int} value or throws when it does not fit. */
    public int intValueExact() {
        return value.intValueExact();
    }

    @Override
    public int compareTo(final WholeAmount other) {
        return value.compareTo(require(other).value);
    }

    @Override
    public boolean equals(final Object other) {
        return this == other || other instanceof WholeAmount amount && value.equals(amount.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    /** Returns the complete unformatted base-ten integer. */
    @Override
    public String toString() {
        return value.toString();
    }

    private static WholeAmount require(final WholeAmount amount) {
        return Objects.requireNonNull(amount, "amount");
    }

    private static BigInteger requireNonNegative(final BigInteger value) {
        BigInteger checked = Objects.requireNonNull(value, "value");

        if (checked.signum() < 0) {
            throw new IllegalArgumentException("Whole amount must not be negative");
        }

        return checked;
    }
}
