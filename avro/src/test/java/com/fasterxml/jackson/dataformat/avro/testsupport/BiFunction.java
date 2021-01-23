package com.fasterxml.jackson.dataformat.avro.testsupport;

public interface BiFunction<T, U, V> {
    V apply(T first, U second);
}
