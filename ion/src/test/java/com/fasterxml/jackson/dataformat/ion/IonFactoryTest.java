package com.fasterxml.jackson.dataformat.ion;

import com.amazon.ion.IonReader;
import com.fasterxml.jackson.core.JsonParser;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.StringReader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class IonFactoryTest {

    // 4-byte Ion 1.0 IVM followed by int 0.
    private static final byte[] BINARY_INT_0 = new byte[] {(byte) 0xE0, 0x01, 0x00, (byte) 0xEA, 0x20};
    private static final String TEXT_INT_0 = "0";

    @Test
    public void byteArrayIsManaged() throws Throwable {
        assertResourceManaged(true, parser(f -> f.createParser(BINARY_INT_0)));
    }

    @Test
    public void charArrayIsManaged() throws Throwable {
        assertResourceManaged(true, parser(f -> f.createParser(TEXT_INT_0.toCharArray())));
    }

    @Test
    public void readerIsManaged() throws Throwable {
        assertResourceManaged(true, parser(f -> f.createParser(new StringReader(TEXT_INT_0))));
    }

    @Test
    public void inputStreamIsManaged() throws Throwable {
        assertResourceManaged(true, parser(f -> f.createParser(new ByteArrayInputStream(BINARY_INT_0))));
    }

    @Test
    public void ionValueIsManaged() throws Throwable {
        assertResourceManaged(true, parser(f -> f.createParser(f.getIonSystem().newInt(0))));
    }

    @Test
    public void ionReaderIsNotManaged() throws Throwable {
        // When the user provides an IonReader, it is not resource-managed, meaning that the user retains the
        // responsibility to close it. In all other cases, the IonReader is created internally, is resource-managed,
        // and is closed automatically in IonParser.close().
        assertResourceManaged(false, parser(f -> f.createParser(f.getIonSystem().newReader(BINARY_INT_0))));
    }

    private void assertResourceManaged(boolean expectResourceManaged, ThrowingSupplier<IonParser> supplier)
        throws Throwable {
        IonParser parser = supplier.get();
        assertEquals(expectResourceManaged, parser._ioContext.isResourceManaged());
        assertTrue(IonReader.class.isAssignableFrom(parser._ioContext.contentReference().getRawContent().getClass()));
        parser.close();
    }

    private interface ThrowingFunction<T, R> {
        R apply(T t) throws Throwable;
    }

    private interface ThrowingSupplier<T> {
        T get() throws Throwable;
    }

    private static ThrowingSupplier<IonParser> parser(ThrowingFunction<IonFactory, JsonParser> f) {
        return () -> (IonParser) f.apply(new IonFactory());
    }
}
