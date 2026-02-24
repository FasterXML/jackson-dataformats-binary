package tools.jackson.dataformat.ion;

import java.io.ByteArrayInputStream;
import java.io.StringReader;

import com.amazon.ion.IonReader;
import com.amazon.ion.IonSystem;
import com.amazon.ion.system.IonSystemBuilder;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IonFactoryTest {
    private final static ObjectReadContext EMPTY_READ_CTXT = ObjectReadContext.empty();

    // 4-byte Ion 1.0 IVM followed by int 0.
    private static final byte[] BINARY_INT_0 = new byte[] {(byte) 0xE0, 0x01, 0x00, (byte) 0xEA, 0x20};
    private static final String TEXT_INT_0 = "0";

    @Test
    public void byteArrayIsManaged() throws Throwable {
        assertResourceManaged(true, parser(f -> f.createParser(EMPTY_READ_CTXT,
                BINARY_INT_0)));
    }

    @Test
    public void charArrayIsManaged() throws Throwable {
        assertResourceManaged(true, parser(f -> f.createParser(EMPTY_READ_CTXT,
                TEXT_INT_0.toCharArray())));
    }

    @Test
    public void readerIsManaged() throws Throwable {
        assertResourceManaged(true, parser(f -> f.createParser(EMPTY_READ_CTXT,
                new StringReader(TEXT_INT_0))));
    }

    @Test
    public void inputStreamIsManaged() throws Throwable {
        assertResourceManaged(true, parser(f -> f.createParser(EMPTY_READ_CTXT,
                new ByteArrayInputStream(BINARY_INT_0))));
    }

    @Test
    public void ionValueIsManaged() throws Throwable {
        assertResourceManaged(true, parser(f -> f.createParser(EMPTY_READ_CTXT,
                f.getIonSystem().newInt(0))));
    }

    @Test
    public void ionReaderIsNotManaged() throws Throwable {
        // When the user provides an IonReader, it is not resource-managed, meaning that the user retains the
        // responsibility to close it. In all other cases, the IonReader is created internally, is resource-managed,
        // and is closed automatically in IonParser.close().
        assertResourceManaged(false, parser(f -> f.createParser(EMPTY_READ_CTXT,
                f.getIonSystem().newReader(BINARY_INT_0))));
    }

    // [dataformats-binary#436]: createParser(IonReader) should initialize state
    // if the reader is already positioned at a value
    @Test
    public void createParserFromPositionedIonReader() throws Exception {
        IonSystem ion = IonSystemBuilder.standard().build();
        IonFactory f = new IonFactory();

        // Case 1: unpositioned reader -> currentToken() should be null
        IonReader unpositioned = ion.newReader(BINARY_INT_0);
        try (IonParser p = f.createParser(EMPTY_READ_CTXT, unpositioned)) {
            assertNull(p.currentToken(),
                "Unpositioned reader: currentToken() should be null before nextToken()");
        }

        // Case 2: reader already positioned at an int value
        IonReader positionedAtInt = ion.newReader(BINARY_INT_0);
        positionedAtInt.next(); // advance to int 0
        try (IonParser p = f.createParser(EMPTY_READ_CTXT, positionedAtInt)) {
            assertEquals(JsonToken.VALUE_NUMBER_INT, p.currentToken(),
                "Pre-positioned reader (INT): currentToken() should reflect reader state");
            assertEquals(0, p.getIntValue());
        }

        // Case 3: reader already positioned at start of a struct
        IonReader positionedAtStruct = ion.newReader("{a:1,b:true}");
        positionedAtStruct.next(); // advance to struct
        try (IonParser p = f.createParser(EMPTY_READ_CTXT, positionedAtStruct)) {
            assertEquals(JsonToken.START_OBJECT, p.currentToken(),
                "Pre-positioned reader (STRUCT): currentToken() should be START_OBJECT");
            // Verify we can still read the full struct content via nextToken()
            assertEquals(JsonToken.PROPERTY_NAME, p.nextToken());
            assertEquals("a", p.currentName());
            assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertEquals(1, p.getIntValue());
            assertEquals(JsonToken.PROPERTY_NAME, p.nextToken());
            assertEquals("b", p.currentName());
            assertEquals(JsonToken.VALUE_TRUE, p.nextToken());
            assertEquals(JsonToken.END_OBJECT, p.nextToken());
            assertNull(p.nextToken());
        }
    }

    private void assertResourceManaged(boolean expectResourceManaged, ThrowingSupplier<IonParser> supplier)
        throws Throwable {
        IonParser parser = supplier.get();
        assertEquals(
                expectResourceManaged, parser.ioContext().isResourceManaged(),
                "Expected managed to be: "+expectResourceManaged);
        Class<?> refType = parser.ioContext().contentReference().getRawContent().getClass();
        assertTrue(
                IonReader.class.isAssignableFrom(refType),
                "ContentReference should be a IonReader, was: "+refType);
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
