package com.fasterxml.jackson.dataformat.ion.dos;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.amazon.ion.IonReader;
import com.amazon.ion.IonSystem;
import com.amazon.ion.IonValue;
import com.amazon.ion.IonWriter;
import com.amazon.ion.system.IonBinaryWriterBuilder;
import com.amazon.ion.system.IonSystemBuilder;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;
import com.fasterxml.jackson.databind.JsonMappingException;

import com.fasterxml.jackson.dataformat.ion.IonFactory;
import com.fasterxml.jackson.dataformat.ion.IonObjectMapper;
import com.fasterxml.jackson.dataformat.ion.ionvalue.IonValueMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

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

    // For `IonValue` binding: needs `IonValueModule`
    private final IonObjectMapper CONSTRAINED_VALUE_MAPPER = IonValueMapper.builder(
            IonFactory.builderForTextualWriters()
                .ionSystem(ION_SYSTEM)
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxNestingDepth(MAX_DEPTH).build())
                .build())
            .build();

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

    // [dataformats-binary#911]: `IonValue` binding copies the value with
    // `getEmbeddedObject()`, not via `nextToken()`: must also be checked
    @Test
    public void testDeepNestingReadAsIonValue() throws Exception {
        _verifyTripsLimit(TEXT_MAPPER.writeValueAsString(_nestedLists(OVER_LIMIT)), IonValue.class);
        _verifyTripsLimit(TEXT_MAPPER.writeValueAsString(_nestedStructs(OVER_LIMIT)), IonValue.class);
        _verifyTripsLimit(BINARY_MAPPER.writeValueAsBytes(_nestedLists(OVER_LIMIT)), IonValue.class);
    }

    @Test
    public void testDeepNestingReadAsIonValueProperty() throws Exception {
        // property value starts one level down, so it trips the limit one level sooner
        _verifyTripsLimit(TEXT_MAPPER.writeValueAsString(
                Collections.singletonMap("value", _nestedLists(MAX_DEPTH))), IonValueWrapper.class);
        assertNotNull(CONSTRAINED_VALUE_MAPPER.readValue(TEXT_MAPPER.writeValueAsString(
                Collections.singletonMap("value", _nestedLists(MAX_DEPTH - 1))),
                IonValueWrapper.class).value);
    }

    // [dataformats-binary#911]: other container kinds, encodings
    @Test
    public void testDeepNestingReadAsIonValueOtherContainers() throws Exception {
        _verifyTripsLimit(BINARY_MAPPER.writeValueAsBytes(_nestedStructs(OVER_LIMIT)), IonValue.class);
        final String sexps = _nestedIon("(", ")", OVER_LIMIT);
        _verifyTripsLimit(sexps, IonValue.class);
        _verifyTripsLimit(_toBinary(sexps), IonValue.class);
        final String annotated = _nestedIon("ann::{f:x::[y::(", ")]}", (OVER_LIMIT + 2) / 3);
        _verifyTripsLimit(annotated, IonValue.class);
        _verifyTripsLimit(_toBinary(annotated), IonValue.class);
    }

    // [dataformats-binary#911]: copy must retain field names, annotations,
    // container types (including typed nulls), from textual and binary input
    @Test
    public void testIonValueCopyRetainsContent() throws Exception {
        final String[] docs = new String[] {
                // 9 levels, mixed containers, annotated at each
                _nestedIon("ann::{f:x::[y::z::(", ")]}", 3),
                _nestedIon("(", ")", MAX_DEPTH),
                "a::{ l:b::[1, c::\"x\", null.list, d::null.struct, (e::f g::(h)), {}],"
                    +" s:i::{ 'quoted name':j::[[], k::()], n:null.sexp, t:2020-01-01T }, last:m::3.5 }",
                "[a::{b:c::[d::{e:f::(g::{h:i::[]})}]}, 2]"
        };
        for (String doc : docs) {
            final IonValue exp = ION_SYSTEM.singleValue(doc);
            assertEquals(exp, CONSTRAINED_VALUE_MAPPER.readValue(doc, IonValue.class));
            assertEquals(exp, CONSTRAINED_VALUE_MAPPER.readValue(_toBinary(doc), IonValue.class));
        }
    }

    // [dataformats-binary#911]: depth is counted from where parser starts, also
    // when `IonReader` given is already positioned inside a container
    @Test
    public void testDeepNestingReadAsIonValueFromSteppedInReader() throws Exception {
        final IonFactory f = (IonFactory) CONSTRAINED_VALUE_MAPPER.getFactory();
        final String atLimit = _nestedIon("[", "]", MAX_DEPTH);

        try (IonReader r = ION_SYSTEM.newReader("{a:{b:" + atLimit + ", c:123}}")) {
            r.next();
            r.stepIn();
            r.next();
            r.stepIn();
            try (JsonParser p = f.createParser(r)) {
                assertEquals(JsonToken.START_ARRAY, p.nextToken());
                assertEquals(ION_SYSTEM.singleValue(atLimit), p.getEmbeddedObject());
                assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
                assertEquals(123, p.getIntValue());
            }
        }
        try (IonReader r = ION_SYSTEM.newReader("{a:{b:[" + atLimit + "], c:123}}")) {
            r.next();
            r.stepIn();
            r.next();
            r.stepIn();
            try (JsonParser p = f.createParser(r)) {
                assertEquals(JsonToken.START_ARRAY, p.nextToken());
                try {
                    p.getEmbeddedObject();
                    fail("Should not pass: nesting depth should exceed limit");
                } catch (StreamConstraintsException e) {
                    _verifyException(e);
                }
            }
        }
    }

    // After failed copy underlying reader may be part-way into the value:
    // further reads must fail, instead of returning content from wrong position
    @Test
    public void testParserNotUsableAfterFailedIonValueCopy() throws Exception {
        // nesting too deep
        try (JsonParser p = CONSTRAINED_VALUE_MAPPER.createParser(
                "[" + _nestedIon("[", "]", OVER_LIMIT) + ", 7] 8")) {
            assertEquals(JsonToken.START_ARRAY, p.nextToken());
            try {
                p.getEmbeddedObject();
                fail("Should not pass: nesting depth should exceed limit");
            } catch (StreamConstraintsException e) {
                _verifyException(e);
            }
            _verifyNotUsable(p);
        }
        // invalid content
        try (JsonParser p = CONSTRAINED_VALUE_MAPPER.createParser("[[1, 2}] 3")) {
            assertEquals(JsonToken.START_ARRAY, p.nextToken());
            try {
                p.getEmbeddedObject();
                fail("Should not pass");
            } catch (JsonParseException e) {
                assertTrue(e.getMessage().contains("Corrupt content to decode"),
                        "Unexpected message: "+e.getMessage());
            }
            _verifyNotUsable(p);
        }
    }

    // But if copy fails before reader moves (here: container that is itself at
    // limit), parser state is unchanged: same as if copy had not been attempted
    @Test
    public void testParserStateUnchangedIfIonValueCopyFailsUpFront() throws Exception {
        try (JsonParser p = CONSTRAINED_VALUE_MAPPER.createParser(_nestedIon("[", "]", MAX_DEPTH + 1))) {
            // outer START_ARRAYs up to and including the innermost one, which is
            // at depth MAX_DEPTH + 1 once stepped into
            for (int i = 0; i <= MAX_DEPTH; ++i) {
                assertEquals(JsonToken.START_ARRAY, p.nextToken());
            }
            for (int i = 0; i < 2; ++i) {
                try {
                    p.getEmbeddedObject();
                    fail("Should not pass: nesting depth should exceed limit");
                } catch (StreamConstraintsException e) {
                    _verifyException(e);
                }
                assertEquals(JsonToken.START_ARRAY, p.currentToken());
            }
            // and `nextToken()` fails as it would have without the copy attempt
            try {
                p.nextToken();
                fail("Should not pass: nesting depth should exceed limit");
            } catch (StreamConstraintsException e) {
                _verifyException(e);
            }
        }
    }

    private void _verifyNotUsable(JsonParser p) throws Exception {
        // more than once, for both
        for (int i = 0; i < 3; ++i) {
            try {
                p.getEmbeddedObject();
                fail("Should not pass");
            } catch (JsonParseException e) {
                assertTrue(e.getMessage().contains("Parser not usable"),
                        "Unexpected message: "+e.getMessage());
            }
            try {
                p.nextToken();
                fail("Should not pass");
            } catch (JsonParseException e) {
                assertTrue(e.getMessage().contains("Parser not usable"),
                        "Unexpected message: "+e.getMessage());
            }
        }
    }

    // Without current value there is nothing to copy: should get regular
    // parse exception, not `IndexOutOfBoundsException`
    @Test
    public void testReadAsIonValueWithoutCurrentValue() throws Exception {
        try (JsonParser p = CONSTRAINED_VALUE_MAPPER.createParser("[1] 2")) {
            _verifyNoCurrentValue(p); // before first token
            assertEquals(JsonToken.START_ARRAY, p.nextToken());
            assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertEquals(JsonToken.END_ARRAY, p.nextToken());
            _verifyNoCurrentValue(p);
            assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertEquals(ION_SYSTEM.newInt(2), p.getEmbeddedObject());
            assertNull(p.nextToken());
            _verifyNoCurrentValue(p); // at end of input
        }
        // also after value has been copied: reader has moved past it
        try (JsonParser p = CONSTRAINED_VALUE_MAPPER.createParser("[1, 2] 3")) {
            assertEquals(JsonToken.START_ARRAY, p.nextToken());
            assertEquals(ION_SYSTEM.singleValue("[1, 2]"), p.getEmbeddedObject());
            _verifyNoCurrentValue(p);
            assertNull(p.getBinaryValue());
            assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertEquals(3, p.getIntValue());
        }
    }

    private void _verifyNoCurrentValue(JsonParser p) throws Exception {
        try {
            p.getEmbeddedObject();
            fail("Should not pass");
        } catch (JsonParseException e) {
            assertTrue(e.getMessage().contains("No current value to read as `IonValue`"),
                    "Unexpected message: "+e.getMessage());
        }
    }

    // Problems from underlying `IonReader` during copy must be reported the
    // same way as for `nextToken()`, not as unchecked `IonException`
    @Test
    public void testTruncatedContentReadAsIonValue() throws Exception {
        for (String doc : new String[] { "[[1, 2", "{a:{b:[1,", "[1, [2, }" }) {
            try {
                CONSTRAINED_VALUE_MAPPER.readValue(doc, IonValue.class);
                fail("Should not pass for: "+doc);
            } catch (JsonParseException e) {
                assertTrue(e.getMessage().contains("Corrupt content to decode"),
                        "Unexpected message: "+e.getMessage());
            }
        }
        // binary too
        byte[] bin = BINARY_MAPPER.writeValueAsBytes(_nestedLists(MAX_DEPTH));
        try {
            CONSTRAINED_VALUE_MAPPER.readValue(Arrays.copyOf(bin, bin.length - 3), IonValue.class);
            fail("Should not pass");
        } catch (JsonParseException e) {
            assertTrue(e.getMessage().contains("Corrupt content to decode"),
                    "Unexpected message: "+e.getMessage());
        }
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

        final String doc = TEXT_MAPPER.writeValueAsString(_nestedStructs(MAX_DEPTH));
        assertEquals(ION_SYSTEM.singleValue(doc), CONSTRAINED_VALUE_MAPPER.readValue(doc, IonValue.class));
    }

    private void _verifyTripsLimit(Object doc, Class<?> type) throws Exception {
        try {
            if (doc instanceof byte[]) {
                CONSTRAINED_VALUE_MAPPER.readValue((byte[]) doc, type);
            } else {
                CONSTRAINED_VALUE_MAPPER.readValue((String) doc, type);
            }
            fail("Should not pass: nesting depth should exceed limit");
        } catch (StreamConstraintsException e) {
            _verifyException(e);
        } catch (JsonMappingException e) {
            // wrapped by databind when thrown for a POJO property
            assertTrue(e.getCause() instanceof StreamConstraintsException, "Unexpected exception: "+e);
            _verifyException((StreamConstraintsException) e.getCause());
        }
    }

    static class IonValueWrapper {
        public IonValue value;
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
        assertTrue(msg.startsWith("Document nesting depth ("+(MAX_DEPTH+1)+")"),
                "Unexpected message: "+msg);
        assertTrue(msg.contains("exceeds the maximum allowed ("+MAX_DEPTH
                        +", from `StreamReadConstraints.getMaxNestingDepth()`)"),
                "Unexpected message: "+msg);
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

    // Ion text with given open/close markers repeated, around a scalar
    private String _nestedIon(String open, String close, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; ++i) {
            sb.append(open);
        }
        sb.append("42");
        for (int i = 0; i < count; ++i) {
            sb.append(close);
        }
        return sb.toString();
    }

    private byte[] _toBinary(String ionText) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (IonWriter w = IonBinaryWriterBuilder.standard().build(bytes)) {
            ION_SYSTEM.singleValue(ionText).writeTo(w);
        }
        return bytes.toByteArray();
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
