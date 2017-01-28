package com.fasterxml.jackson.dataformat.avro;

import java.util.concurrent.atomic.AtomicReference;

import org.apache.avro.Schema;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.Decoder;

import com.fasterxml.jackson.core.FormatSchema;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.avro.deser.AvroReaderFactory;
import com.fasterxml.jackson.dataformat.avro.deser.AvroStructureReader;

/**
 * Wrapper for Schema information needed to encode and decode Avro-format
 * data.
 */
public class AvroSchema implements FormatSchema
{
    public final static String TYPE_ID = "avro";

    /**
     * Schema that is used for reading data ("reader" schema) by this instance;
     * and for lowest-level instance also schema used for decoding underlying
     * data ("writer" schema).
     */
    protected final Schema _avroSchema;

    /**
     * Lazily instantiated value reader for this schema.
     */
    protected final AtomicReference<AvroStructureReader> _reader = new AtomicReference<AvroStructureReader>();

    public AvroSchema(Schema asch)
    {
        _avroSchema = asch;
    }

    /**
     * Method that will consider this schema instance (used as so-called "Writer Schema"),
     * and specified "Reader Schema" instance, and will either construct a new schema
     * with appropriate translations, to use for reading (if reader and writer schemas are
     * not same); or, if schemas are the same, return `this`.
     *<p>
     * Note that neither `this` instance nor `readerSchema` is ever modified: if an altered
     * version is needed, a new schema object will be constructed.
     *
     * @param readerSchema "Reader Schema" to use (in Avro terms): schema that specified how
     *    reader wants to see the data; specifies part of translation needed along with this
     *    schema (which would be "Writer Schema" in Avro terms).
     *
     * @throws JsonProcessingException If given reader schema is incompatible with (writer-)
     *     schema this instance was constructed with, 
     * 
     * @since 2.9
     */
    public AvroSchema withReaderSchema(AvroSchema readerSchema)
        throws JsonProcessingException
    {
        Schema w = _avroSchema;
        Schema r = readerSchema.getAvroSchema();

        if (r.equals(w)) {
            return this;
        }
        return new Resolving(this, r);
    }

    @Override
    public String getSchemaType() {
        return TYPE_ID;
    }

    public Schema getAvroSchema() { return _avroSchema; }

    /*
    /**********************************************************************
    /* Factory methods
    /**********************************************************************
     */

    public AvroStructureReader getReader()
    {
        AvroStructureReader r = _reader.get();
        if (r == null) {
            r = new AvroReaderFactory().createReader(_avroSchema);
            _reader.set(r);
        }
        return r;
    }

    public Decoder decoder(BinaryDecoder physical) throws JsonProcessingException {
        return physical;
    }

    /*
    /**********************************************************************
    /* Standard method overrides
    /**********************************************************************
     */

    @Override
    public String toString() {
        return String.format("{AvroSchema: name=%s}", _avroSchema.getFullName());
    }

    @Override
    public int hashCode() {
        return _avroSchema.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ((o == null) || o.getClass() != getClass()) return false;
        AvroSchema other = (AvroSchema) o;
        return _avroSchema.equals(other._avroSchema);
    }

    /*
    /**********************************************************************
    /* Specialized sub-class(es), helper classes
    /**********************************************************************
     */

    /**
     * Sub-class that does writer-to-reader conversion by using "resolving decoder"
     * (by avro codec) on top of binary codec, exposing content using (reader) schema
     * this instance was configured with.
     */
    private final static class Resolving extends AvroSchema
    {
        private final AvroSchema _parent;

        public Resolving(AvroSchema parent, Schema reader)
        {
            super(reader);
            if (parent == null) {
                throw new IllegalArgumentException("null `parent`");
            }
            _parent = parent;
        }

        /*
        /**********************************************************************
        /* Factory method overrides
        /**********************************************************************
         */

        public Decoder decoder(BinaryDecoder physical) throws JsonProcessingException {
            Decoder src = _parent.decoder(physical);
            return CodecRecycler.convertingDecoder(src,
                    _parent.getAvroSchema(), getAvroSchema());
        }

        /*
        /**********************************************************************
        /* Standard method overrides
        /**********************************************************************
         */

        @Override
        public String toString() {
            return String.format("{AvroSchema.Resolving: name=%s; from %s}",
                    _avroSchema.getFullName(),
                    _parent);
        }

        @Override
        public int hashCode() {
            return _avroSchema.hashCode() ^ _parent.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if ((o == null) || o.getClass() != getClass()) return false;
            Resolving other = (Resolving) o;
            return _avroSchema.equals(other._avroSchema)
                    && _parent.equals(other._parent);
        }
    }
}
