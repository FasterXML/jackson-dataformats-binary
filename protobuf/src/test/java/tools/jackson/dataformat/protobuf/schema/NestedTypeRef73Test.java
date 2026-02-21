package tools.jackson.dataformat.protobuf.schema;

import java.io.StringReader;

import org.junit.jupiter.api.Test;

import tools.jackson.dataformat.protobuf.ProtobufMapper;
import tools.jackson.dataformat.protobuf.ProtobufTestBase;

import static org.junit.jupiter.api.Assertions.assertNotNull;

// [dataformats-binary#73]
public class NestedTypeRef73Test extends ProtobufTestBase
{
    final ProtobufMapper MAPPER = new ProtobufMapper();

    // [dataformats-binary#73]: dot-notation reference to a nested message type
    @Test
    public void testNestedTypeRefViaRootType() throws Exception
    {
        final String SCHEMA_STR =
"        package mypackage;\n"
+"        message t1 {\n"
+"                message i1 {\n"
+"                        optional uint32 x = 1;\n"
+"                        optional uint32 y = 2;\n"
+"                }\n"
+"        }\n"
+"        message t2 {\n"
+"                optional t1.i1 z = 1;\n"
+"        }\n"
                ;

        ProtobufSchema schema = MAPPER.schemaLoader()
                .load(new StringReader(SCHEMA_STR), "t2");
        assertNotNull(schema);
    }
}
