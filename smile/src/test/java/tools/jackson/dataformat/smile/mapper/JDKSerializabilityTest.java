package tools.jackson.dataformat.smile.mapper;

import java.io.*;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import tools.jackson.dataformat.smile.BaseTestForSmile;
import tools.jackson.dataformat.smile.SmileMapper;

import static org.junit.jupiter.api.Assertions.*;

public class JDKSerializabilityTest extends BaseTestForSmile
{
    @Test
    public void testApacheMapperWithModule() throws Exception {
        // very simple validation: should still work wrt serialization
        SmileMapper unfrozenMapper = serializeAndDeserialize(new SmileMapper());

        // and then simple verification that write+read still works

        Object input = _simpleData();
        byte[] encoded = unfrozenMapper.writeValueAsBytes(input);
        final Object result = unfrozenMapper.readerFor(Object.class)
                .readValue(encoded);
        assertEquals(result, input);

        // and also verify `rebuild()` works:
        SmileMapper copy = unfrozenMapper.rebuild().build();
        assertNotSame(unfrozenMapper, copy);
        // with 3.x, factories are immutable so they need not be unshared:
        assertSame(unfrozenMapper.tokenStreamFactory(), copy.tokenStreamFactory());

        final Object result2 = copy.readerFor(Object.class)
                .readValue(encoded);
        assertEquals(input, result2);
    }

    private SmileMapper serializeAndDeserialize(SmileMapper mapper) throws Exception {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ObjectOutputStream outputStream = new ObjectOutputStream(byteArrayOutputStream);

        outputStream.writeObject(mapper);
        byte[] serializedBytes = byteArrayOutputStream.toByteArray();

        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(serializedBytes);
        ObjectInputStream inputStream = new ObjectInputStream(byteArrayInputStream);
        Object deserializedObject = inputStream.readObject();
        assertTrue(deserializedObject instanceof SmileMapper,
                "Deserialized object should be an instance of ObjectMapper");
        return (SmileMapper) deserializedObject;
    }

    private Object _simpleData() {
        return Arrays.asList("foobar", 378, true);
    }
}
