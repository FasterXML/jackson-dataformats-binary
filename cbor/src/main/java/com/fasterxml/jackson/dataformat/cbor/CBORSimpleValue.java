package com.fasterxml.jackson.dataformat.cbor;

/**
 * Simple value object to be used for exposing undefined "simple values"
 * when encountered during parsing.
 * Note that as of Jackson 2.12, this class is <b>not yet</b> used for
 * exposing simple values: instead they are report as
 * {@link tools.jackson.core.JsonToken#VALUE_NUMBER_INT}s.
 *<p>
 * Simple values left undefined in
 * <a href="https://tools.ietf.org/html/rfc7049">CBOR 1.0</a>
 * specification contain values {@code [0 - 19], [32, 255]}: other
 * values are not used to represent general simple values.
 * Specifically, values below {@code 0}, above {@code 255} or
 * in range {@code [20, 31] (inclusive)} are never exposed.
 *<p>
 * Values are not guaranteed to be canonicalized, but being immutable
 * may be reused (and in future possible canonicalized if that makes sense).
 *<p>
 * Note that it is possible that some of above-mentioned values may be
 * defined to have specific meaning and get reported using some other
 * mechanism.
 *
 * @since 2.12
 */
public class CBORSimpleValue {
    /**
     * Actual numeric value represented. Usually should be in range
     * of {@code [0-19][32-255]}.
     */
    protected final int _value;

    public CBORSimpleValue(int value) {
        _value = value;
    }

    /**
     * Accessor for the simple integer value represented
     *
     * @return Simple integer value this instance represents
     */
    public int getValue() { return _value; }

    @Override
    public int hashCode() { return _value; }

    @Override
    public String toString() {
        return Integer.valueOf(_value).toString();
    }

    @Override
    public boolean equals(Object o)
    {
        if (o == this) return true;
        if (o instanceof CBORSimpleValue) {
            CBORSimpleValue other = (CBORSimpleValue) o;
            return _value == other._value;
        }
        return false;
    }
}
