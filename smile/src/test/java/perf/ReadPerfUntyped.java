package perf;

import java.util.Map;

import tools.jackson.databind.*;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;

public class ReadPerfUntyped extends ReaderTestBase
{
    private ReadPerfUntyped() { }

    @Override
    protected int targetSizeMegs() { return 10; }
    
    public static void main(String[] args) throws Exception
    {
        if (args.length != 1) {
            System.err.println("Usage: java [input]");
            System.exit(1);
        }
        byte[] json = readAll(args[0]);

        final ObjectMapper jsonMapper = new ObjectMapper();
        final ObjectMapper smileMapper = new ObjectMapper(new SmileFactory());

        byte[] smile = convert(json, jsonMapper, smileMapper);

        // Either Object or Map
        final Class<?> UNTYPED = Map.class;

        Object input = smileMapper.readValue(smile, UNTYPED);

        new ReadPerfUntyped()
            .testFromBytes(
                    smileMapper, "JSON-as-Object", input, UNTYPED
                    ,smileMapper, "JSON-as-Object2", input, UNTYPED
//               ,m, "JSON-as-Node", input2, JsonNode.class
                );
    }
}
