package tools.jackson.dataformat.cbor.mapper;

import java.io.*;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import tools.jackson.dataformat.cbor.CBORMapper;
import tools.jackson.dataformat.cbor.CBORTestBase;

import static org.junit.jupiter.api.Assertions.*;

public class JDKSerializabilityTest extends CBORTestBase
{
    @Test
    public void testApacheMapperWithModule() throws Exception {
        // very simple validation: should still work wrt serialization
        CBORMapper unfrozenMapper = serializeAndDeserialize(new CBORMapper());

        // and then simple verification that write+read still works

        Object input = _simpleData();
        byte[] encoded = unfrozenMapper.writeValueAsBytes(input);
        final Object result = unfrozenMapper.readerFor(Object.class)
                .readValue(encoded);
        assertEquals(result, input);

        // and also verify `rebuild()` works:
        CBORMapper copy = unfrozenMapper.rebuild().build();
        assertNotSame(unfrozenMapper, copy);
        // with 3.x, factories are immutable so they need not be unshared:
        assertSame(unfrozenMapper.tokenStreamFactory(), copy.tokenStreamFactory());

        final Object result2 = copy.readerFor(Object.class)
                .readValue(encoded);
        assertEquals(input, result2);
    }

    private CBORMapper serializeAndDeserialize(CBORMapper mapper) throws Exception {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ObjectOutputStream outputStream = new ObjectOutputStream(byteArrayOutputStream);

        outputStream.writeObject(mapper);
        byte[] serializedBytes = byteArrayOutputStream.toByteArray();

        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(serializedBytes);
        ObjectInputStream inputStream = new ObjectInputStream(byteArrayInputStream);
        Object deserializedObject = inputStream.readObject();
        assertTrue(deserializedObject instanceof CBORMapper,
                "Deserialized object should be an instance of ObjectMapper");
        return (CBORMapper) deserializedObject;
    }

    private Object _simpleData() {
        return Arrays.asList("foobar", 378, true);
    }
}
