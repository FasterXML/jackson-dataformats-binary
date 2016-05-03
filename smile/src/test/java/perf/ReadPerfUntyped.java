package perf;

import java.util.Map;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
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

        JsonFactory jf = new JsonFactory();
        SmileFactory sf = new SmileFactory();
        ObjectMapper m = new ObjectMapper(sf);

        byte[] smile = convert(json, jf, sf);
        
        // Either Object or Map
        final Class<?> UNTYPED = Map.class;

        Object input = m.readValue(smile, UNTYPED);

        new ReadPerfUntyped()
            .testFromBytes(
                m, "JSON-as-Object", input, UNTYPED
                ,m, "JSON-as-Object2", input, UNTYPED
//               ,m, "JSON-as-Node", input2, JsonNode.class
                );
    }
}
