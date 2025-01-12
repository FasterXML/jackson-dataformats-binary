package tools.jackson.dataformat.ion;

import java.io.ByteArrayInputStream;
import java.io.StringReader;

import com.amazon.ion.IonReader;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonParser;
import tools.jackson.core.ObjectReadContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
