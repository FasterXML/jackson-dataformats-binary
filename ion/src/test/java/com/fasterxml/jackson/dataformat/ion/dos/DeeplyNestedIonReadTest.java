package com.fasterxml.jackson.dataformat.ion.dos;

import java.util.Collections;

import org.junit.Test;

import com.amazon.ion.IonReader;
import com.amazon.ion.IonSystem;
import com.amazon.ion.IonValue;
import com.amazon.ion.system.IonSystemBuilder;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;

import com.fasterxml.jackson.dataformat.ion.IonFactory;
import com.fasterxml.jackson.dataformat.ion.IonObjectMapper;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Verifies that {@link StreamReadConstraints#getMaxNestingDepth()} is enforced
 * by the Ion parser -- for textual and binary input alike, and for the Ion-specific
 * {@link IonReader} / {@link IonValue} entry points -- matching CBOR / Smile codecs.
 * See [dataformats-binary#803].
 */
public class DeeplyNestedIonReadTest
{
    private final static int MAX_DEPTH = 10;

    private final static int OVER_LIMIT = MAX_DEPTH + 3;

    private final IonSystem ION_SYSTEM = IonSystemBuilder.standard().build();

    // Vanilla mappers, for producing test documents
    private final IonObjectMapper TEXT_MAPPER = IonObjectMapper.builderForTextualWriters().build();
    private final IonObjectMapper BINARY_MAPPER = IonObjectMapper.builderForBinaryWriters().build();

    // Factory under test: writer mode is irrelevant for reading, since parser
    // detects textual vs binary input itself
    private final IonFactory CONSTRAINED_FACTORY;
    private final IonObjectMapper CONSTRAINED_MAPPER;
    {
        CONSTRAINED_FACTORY = new IonFactory();
        CONSTRAINED_FACTORY.setStreamReadConstraints(StreamReadConstraints.builder()
                .maxNestingDepth(MAX_DEPTH).build());
        CONSTRAINED_MAPPER = IonObjectMapper.builder(CONSTRAINED_FACTORY).build();
    }

    @Test
    public void testDeepNestingListReadTextual() throws Exception {
        _verifyTripsLimit(TEXT_MAPPER.writeValueAsString(_nestedLists(OVER_LIMIT)));
    }

    @Test
    public void testDeepNestingStructReadTextual() throws Exception {
        _verifyTripsLimit(TEXT_MAPPER.writeValueAsString(_nestedStructs(OVER_LIMIT)));
    }

    @Test
    public void testDeepNestingListReadBinary() throws Exception {
        _verifyTripsLimit(BINARY_MAPPER.writeValueAsBytes(_nestedLists(OVER_LIMIT)));
    }

    @Test
    public void testDeepNestingStructReadBinary() throws Exception {
        _verifyTripsLimit(BINARY_MAPPER.writeValueAsBytes(_nestedStructs(OVER_LIMIT)));
    }

    // Tree binding is the likelier real-world entry point: verify exception is
    // not swallowed on the way through databind
    @Test
    public void testDeepNestingReadTree() throws Exception {
        final String doc = TEXT_MAPPER.writeValueAsString(_nestedLists(OVER_LIMIT));
        try {
            CONSTRAINED_MAPPER.readTree(doc);
            fail("Should not pass: nesting depth should exceed limit");
        } catch (StreamConstraintsException e) {
            _verifyException(e);
        }
    }

    // Ion-specific entry points build their own `IOContext`: verify constraints
    // reach the parser on those routes too
    @Test
    public void testDeepNestingReadFromIonReader() throws Exception {
        final String doc = TEXT_MAPPER.writeValueAsString(_nestedLists(OVER_LIMIT));
        try (IonReader r = ION_SYSTEM.newReader(doc)) {
            _verifyTripsLimit(CONSTRAINED_FACTORY.createParser(r));
        }
    }

    @Test
    public void testDeepNestingReadFromIonValue() throws Exception {
        final String doc = TEXT_MAPPER.writeValueAsString(_nestedLists(OVER_LIMIT));
        IonValue value = ION_SYSTEM.singleValue(doc);
        _verifyTripsLimit(CONSTRAINED_FACTORY.createParser(value));
    }

    @Test
    public void testNestingAtLimitPasses() throws Exception {
        // Documents exactly at the limit must still be readable, textual or binary
        _readAll(TEXT_MAPPER.writeValueAsString(_nestedLists(MAX_DEPTH)));
        _readAll(TEXT_MAPPER.writeValueAsString(_nestedStructs(MAX_DEPTH)));
        _readAll(BINARY_MAPPER.writeValueAsBytes(_nestedLists(MAX_DEPTH)));
        _readAll(BINARY_MAPPER.writeValueAsBytes(_nestedStructs(MAX_DEPTH)));

        assertNotNull(CONSTRAINED_MAPPER.readTree(
                TEXT_MAPPER.writeValueAsString(_nestedLists(MAX_DEPTH))));
    }

    private void _verifyTripsLimit(String doc) throws Exception {
        _verifyTripsLimit(CONSTRAINED_MAPPER.createParser(doc));
    }

    private void _verifyTripsLimit(byte[] doc) throws Exception {
        _verifyTripsLimit(CONSTRAINED_MAPPER.createParser(doc));
    }

    private void _verifyTripsLimit(JsonParser p) throws Exception {
        try {
            _drain(p);
            fail("Should not pass: nesting depth should exceed limit");
        } catch (StreamConstraintsException e) {
            _verifyException(e);
        } finally {
            p.close();
        }
    }

    private void _verifyException(StreamConstraintsException e) {
        final String msg = e.getMessage();
        assertTrue("Unexpected message: "+msg,
                msg.startsWith("Document nesting depth ("+(MAX_DEPTH+1)+")"));
        assertTrue("Unexpected message: "+msg,
                msg.contains("exceeds the maximum allowed ("+MAX_DEPTH
                        +", from `StreamReadConstraints.getMaxNestingDepth()`)"));
    }

    private void _readAll(String doc) throws Exception {
        try (JsonParser p = CONSTRAINED_MAPPER.createParser(doc)) {
            _drain(p);
        }
    }

    private void _readAll(byte[] doc) throws Exception {
        try (JsonParser p = CONSTRAINED_MAPPER.createParser(doc)) {
            _drain(p);
        }
    }

    private void _drain(JsonParser p) throws Exception {
        while (p.nextToken() != null) { }
    }

    private Object _nestedLists(int depth) {
        Object curr = Integer.valueOf(42);
        for (int i = 0; i < depth; ++i) {
            curr = Collections.singletonList(curr);
        }
        return curr;
    }

    private Object _nestedStructs(int depth) {
        Object curr = Integer.valueOf(42);
        for (int i = 0; i < depth; ++i) {
            curr = Collections.singletonMap("a", curr);
        }
        return curr;
    }
}
