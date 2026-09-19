package com.fasterxml.jackson.dataformat.ion.dos;

import org.junit.Test;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;

import com.fasterxml.jackson.dataformat.ion.IonFactory;
import com.fasterxml.jackson.dataformat.ion.IonObjectMapper;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Verifies that {@link StreamReadConstraints#getMaxNestingDepth()} is enforced
 * by the Ion parser, matching CBOR / Smile codecs. See [dataformats-binary#358].
 */
public class DeeplyNestedIonReadTest
{
    private final static int MAX_DEPTH = 10;

    private final IonObjectMapper CONSTRAINED_MAPPER;
    {
        IonFactory f = IonFactory.builderForTextualWriters().build();
        f.setStreamReadConstraints(StreamReadConstraints.builder()
                .maxNestingDepth(MAX_DEPTH).build());
        CONSTRAINED_MAPPER = IonObjectMapper.builder(f).build();
    }

    @Test
    public void testDeepNestingListRead() throws Exception {
        _verifyTripsLimit(_nestedLists(MAX_DEPTH + 3));
    }

    @Test
    public void testDeepNestingStructRead() throws Exception {
        _verifyTripsLimit(_nestedStructs(MAX_DEPTH + 3));
    }

    @Test
    public void testNestingAtLimitPasses() throws Exception {
        // Document exactly at the limit must still be readable
        _readAll(_nestedLists(MAX_DEPTH));
        _readAll(_nestedStructs(MAX_DEPTH));
    }

    private void _verifyTripsLimit(String doc) throws Exception {
        try {
            _readAll(doc);
            fail("Should not pass: nesting depth should exceed limit");
        } catch (StreamConstraintsException e) {
            String msg = e.getMessage();
            assertTrue("Unexpected message: "+msg,
                    msg.startsWith("Document nesting depth ("+(MAX_DEPTH+1)+")"));
            assertTrue("Unexpected message: "+msg,
                    msg.contains("exceeds the maximum allowed ("+MAX_DEPTH
                            +", from `StreamReadConstraints.getMaxNestingDepth()`)"));
        }
    }

    private void _readAll(String doc) throws Exception {
        try (JsonParser p = CONSTRAINED_MAPPER.createParser(doc)) {
            while (p.nextToken() != null) { }
        }
    }

    private String _nestedLists(int depth) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < depth; ++i) {
            sb.append('[');
        }
        sb.append(42);
        for (int i = 0; i < depth; ++i) {
            sb.append(']');
        }
        return sb.toString();
    }

    private String _nestedStructs(int depth) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < depth; ++i) {
            sb.append("{a:");
        }
        sb.append(42);
        for (int i = 0; i < depth; ++i) {
            sb.append('}');
        }
        return sb.toString();
    }
}
