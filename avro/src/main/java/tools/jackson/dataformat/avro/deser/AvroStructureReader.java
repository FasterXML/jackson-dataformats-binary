package tools.jackson.dataformat.avro.deser;

import java.io.IOException;

import tools.jackson.core.JsonToken;

/**
 * Base class for handlers for Avro structured types (or in some cases,
 * scalar types that need to be exposed in unified way similar to
 * structured types).
 */
public abstract class AvroStructureReader
    extends AvroReadContext
{
    protected AvroStructureReader(AvroReadContext parent, int type, String typeId) {
        super(parent, typeId);
        _type = type;
    }

    /*
    /**********************************************************************
    /* Metadata access
    /**********************************************************************
     */

    /**
     * Method that may be called to check if the values "read" by this reader
     * are zero-length, that is, consume no content: most common example being
     * Record with no fields.
     *<p>
     * Note: Arrays can not return {@code true} as they need to encode length
     * even for "empty" arrays.
     */
    public boolean consumesNoContent() {
        return false;
    }
    
    /*
    /**********************************************************************
    /* Reader API
    /**********************************************************************
     */

    /**
     * Method for creating actual instance to use for reading (initial
     * instance constructed is so-called blue print).
     */
    public abstract AvroStructureReader newReader(AvroReadContext parent, AvroParserImpl parser);

    @Override
    public abstract JsonToken nextToken() throws IOException;

    /**
     * Alternative to {@link #nextToken} which will simply skip the full
     * value.
     */
    @Override
    public abstract void skipValue(AvroParserImpl parser) throws IOException;

    protected void throwIllegalState(int state) {
        throw new IllegalStateException("Illegal state for reader of type "
                +getClass().getName()+": "+state);
    }

    protected <T> T _throwUnsupported() {
        throw new IllegalStateException("Can not call on "+getClass().getName());
    }
}
