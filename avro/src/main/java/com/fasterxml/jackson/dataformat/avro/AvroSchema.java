package com.fasterxml.jackson.dataformat.avro;

import java.util.concurrent.atomic.AtomicReference;

import org.apache.avro.Schema;
import org.apache.avro.SchemaCompatibility;
import org.apache.avro.SchemaCompatibility.SchemaCompatibilityType;
import org.apache.avro.SchemaCompatibility.SchemaPairCompatibility;

import com.fasterxml.jackson.core.FormatSchema;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
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
     * Schema that was used for writing the data to decode; for simple instance
     * used for reading as well (reader schema).
     */
    protected final Schema _writerSchema;

    /**
     * Lazily instantiated value reader for this schema.
     */
    protected final AtomicReference<AvroStructureReader> _reader = new AtomicReference<AvroStructureReader>();

    public AvroSchema(Schema asch)
    {
        _writerSchema = asch;
    }

    /**
     * Method that will consider this schema instance (used as so-called "Writer Schema"),
     * and specified "Reader Schema" instance, and will either construct a new schema
     * with appropriate translations, to use for reading (if reader and writer schemas are
     * not same); or, if schemas are the same, return `this`.
     *<p>
     * Note that neither `this` instance nor `readerSchema` is ever modified: if an altered
     * version is needed, a new schema object will be constructed.
     *<p>
     * NOTE: this is a relatively expensive operation due to validation (although significant
     *  part of cost is deferred until the first call to {@link #getReader}) so it is recommended
     *  that these instances are reused whenever possible.
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
        Schema w = _writerSchema;
        Schema r = readerSchema.getAvroSchema();

        if (r.equals(w)) {
            return this;
        }
        // First: apply simple renamings:
        w = Schema.applyAliases(w, r);

        // and then use Avro std lib to validate compatibility
        SchemaPairCompatibility comp;
        try {
            comp = SchemaCompatibility.checkReaderWriterCompatibility(r, w);
        } catch (Exception e) {
            throw new JsonMappingException(null, String.format(
                    "Failed to resolve given reader/writer schemas, problem: (%s) %s",
                    e.getClass().getName(), e.getMessage()));
        }
        if (comp.getType() != SchemaCompatibilityType.COMPATIBLE) {
            throw new JsonMappingException(null, String.format("Incompatible reader/writer schema: %s",
                    comp.getDescription()));
        }
        return Resolving.create(w, r);
    }

    @Override
    public String getSchemaType() {
        return TYPE_ID;
    }

    /**
     * Accessor for "writer schema" contained in this instance.
     */
    public Schema getAvroSchema() { return _writerSchema; }

    /*
    /**********************************************************************
    /* Factory methods
    /**********************************************************************
     */

    public AvroStructureReader getReader()
    {
        AvroStructureReader r = _reader.get();
        if (r == null) {
            r = _constructReader();
            _reader.set(r);
        }
        return r;
    }

    protected AvroStructureReader _constructReader() {
        return AvroReaderFactory.createFor(_writerSchema);
    }

    /*
    /**********************************************************************
    /* Standard method overrides
    /**********************************************************************
     */

    @Override
    public String toString() {
        return String.format("{AvroSchema: name=%s}", _writerSchema.getFullName());
    }

    @Override
    public int hashCode() {
        return _writerSchema.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ((o == null) || o.getClass() != getClass()) return false;
        AvroSchema other = (AvroSchema) o;
        return _writerSchema.equals(other._writerSchema);
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
        private final Schema _readerSchema;

        public Resolving(Schema writer, Schema reader)
        {
            super(writer);
            _readerSchema = reader;
        }

        public static Resolving create(Schema writer, Schema reader) {
            return new Resolving(writer, reader);
        }

        @Override
        protected AvroStructureReader _constructReader() {
            return AvroReaderFactory.createFor(_writerSchema, _readerSchema);
        }

        /*
        /**********************************************************************
        /* Standard method overrides
        /**********************************************************************
         */

        @Override
        public String toString() {
            return String.format("{AvroSchema.Resolving: name=%s}", _writerSchema.getFullName());
        }

        @Override
        public int hashCode() {
            return super.hashCode() ^ _readerSchema.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if ((o == null) || o.getClass() != getClass()) return false;
            Resolving other = (Resolving) o;
            return _writerSchema.equals(other._writerSchema)
                    && _readerSchema.equals(other._readerSchema);
        }
    }
}
