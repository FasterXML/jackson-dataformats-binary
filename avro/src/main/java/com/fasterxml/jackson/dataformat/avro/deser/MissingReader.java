package com.fasterxml.jackson.dataformat.avro.deser;

import java.io.IOException;

import tools.jackson.core.JsonToken;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.core.sym.PropertyNameMatcher;

/**
 * Bogus {@link AvroReadContext} implementation used in two cases:
 *<ol>
 * <li>Parser has not yet been configured by an Avro Schema
 *  </li>
 * <li>Parser has been closed (explicitly or implicitly)
 *  </li>
 *</ol>
 * In former case, attempts to read will result in a {@link StreamReadException};
 * in latter case, results will simply indicate unavailability of content
 * (return `null` in most cases)
 */
public class MissingReader extends AvroReadContext
{
    public final static MissingReader instance = new MissingReader(false);

    public final static MissingReader closedInstance = new MissingReader(true);

    protected final boolean _schemaSet;

    @Deprecated // since 2.9; don't use constructors directly
    public MissingReader() {
        this(true);
    }

    protected MissingReader(boolean schemaSet) {
        super(null, null);
        _type = TYPE_ROOT;
        _schemaSet = schemaSet;
    }

    @Override
    public JsonToken nextToken() throws IOException {
        _checkSchemaSet();
        return null;
    }

    @Override
    public void appendDesc(StringBuilder sb) {
        sb.append("?");
    }

    @Override
    public String nextName() throws IOException {
        _checkSchemaSet();
        return null;
    }

    @Override
    public int nextNameMatch(PropertyNameMatcher matcher) throws IOException {
        _checkSchemaSet();
        return PropertyNameMatcher.MATCH_ODD_TOKEN;
    }

    @Override
    public void skipValue(AvroParserImpl parser) throws IOException {
        _checkSchemaSet();
    }

    protected void _checkSchemaSet() {
        if (!_schemaSet) {
            throw new StreamReadException(null, "No AvroSchema set, can not parse");
        }
    }
}
