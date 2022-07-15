package tools.jackson.dataformat.avro.deser;

import java.io.IOException;

import tools.jackson.core.JsonToken;
import tools.jackson.core.util.InternCache;

/**
 * Entity that encapsulates details of accessing value of a single field
 * of a "Record" (Avro term, corresponding roughly to JSON Object).
 */
public abstract class AvroFieldReader
{
    // 14-Nov-2017, tatu: MUST intern names to match to be able to use intern-based
    //   field matcher
    private final static InternCache INTERNER = InternCache.instance;

    protected final String _name;
    protected final boolean _isSkipper;
    protected final String _typeId;

    protected AvroFieldReader(String name, boolean isSkipper, String typeId) {
        name = INTERNER.intern(name);
        _name = name;
        _isSkipper = isSkipper;
        _typeId = typeId;
    }

    public static AvroFieldReader construct(String name, AvroStructureReader structureReader) {
        return new Structured(name, false, structureReader);
    }

    public static AvroFieldReader constructSkipper(String name, AvroStructureReader structureReader) {
        return new Structured(name, true, structureReader);
    }

    public final String getName() { return _name; }
    public final boolean isSkipper() { return _isSkipper; }

    public abstract JsonToken readValue(AvroReadContext parent, AvroParserImpl parser) throws IOException;

    public abstract void skipValue(AvroParserImpl parser) throws IOException;

    public String getTypeId() {
        return _typeId;
    }

    /**
     * Implementation used for non-scalar-valued (structured) fields
     */
    private final static class Structured extends AvroFieldReader {
        protected final AvroStructureReader _reader;

        public Structured(String name, boolean skipper, AvroStructureReader r) {
            super(name, skipper, null);
            _reader = r;
        }

        @Override
        public JsonToken readValue(AvroReadContext parent, AvroParserImpl parser) throws IOException
        {
            return _reader.newReader(parent, parser).nextToken();
        }

        @Override
        public void skipValue(AvroParserImpl parser) throws IOException {
            _reader.skipValue(parser);
        }

        @Override
        public String getTypeId() {
            return _reader.getTypeId();
        }
    }
}
