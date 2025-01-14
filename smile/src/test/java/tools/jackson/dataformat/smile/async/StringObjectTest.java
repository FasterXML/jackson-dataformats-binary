package tools.jackson.dataformat.smile.async;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonToken;

import tools.jackson.databind.ObjectWriter;
import tools.jackson.dataformat.smile.SmileWriteFeature;

import static org.junit.jupiter.api.Assertions.*;

public class StringObjectTest extends AsyncTestBase
{
    private final static String STR0_9 = "0123456789";
    private final static String ASCII_SHORT_NAME = "a"+STR0_9+"z";
    private final static String UNICODE_SHORT_NAME = "Unicode"+UNICODE_3BYTES+"RlzOk";
    private final static String UNICODE_LONG_NAME = String.format(
            "Unicode-"+UNICODE_3BYTES+"-%s-%s-%s-"+UNICODE_2BYTES+"-%s-%s-%s-"+UNICODE_3BYTES+"-%s-%s-%s",
            STR0_9, STR0_9, STR0_9, STR0_9, STR0_9, STR0_9, STR0_9, STR0_9, STR0_9);

    @Test
    public void testBasicFieldsNamesSharedNames() throws Exception {
        _testBasicFieldsNames(true);
    }

    @Test
    public void testBasicFieldsNamesNonShared() throws Exception {
        _testBasicFieldsNames(false);
    }

    private void _testBasicFieldsNames(boolean sharedNames) throws Exception
    {
        final String json = aposToQuotes(String.format("{'%s':'%s','%s':'%s','%s':'%s'}",
            UNICODE_SHORT_NAME, UNICODE_LONG_NAME,
            UNICODE_LONG_NAME, UNICODE_SHORT_NAME,
            ASCII_SHORT_NAME, ASCII_SHORT_NAME));

        ObjectWriter w = _smileWriter(true);
        if (sharedNames) {
            w = w.withFeatures(SmileWriteFeature.CHECK_SHARED_NAMES,
                    SmileWriteFeature.CHECK_SHARED_STRING_VALUES);
        } else {
            w = w.withoutFeatures(SmileWriteFeature.CHECK_SHARED_NAMES,
                    SmileWriteFeature.CHECK_SHARED_STRING_VALUES);
        }

        byte[] data = _smileDoc(w, json);
        _testBasicFieldsNames(data, 0, 100);
        _testBasicFieldsNames(data, 0, 3);
        _testBasicFieldsNames(data, 0, 1);

        _testBasicFieldsNames(data, 1, 100);
        _testBasicFieldsNames(data, 1, 3);
        _testBasicFieldsNames(data, 1, 1);
    }

    private void _testBasicFieldsNames(byte[] data, int offset, int readSize) throws Exception
    {
        _testBasicFieldsNames2(data, offset, readSize, true);
        _testBasicFieldsNames2(data, offset, readSize, false);
    }

    private void _testBasicFieldsNames2(byte[] data, int offset, int readSize, boolean verifyContents)
        throws Exception
    {
        AsyncReaderWrapper r = asyncForBytes(_smileReader(true), readSize, data, offset);

        // start with "no token"
        assertNull(r.currentToken());
        assertToken(JsonToken.START_OBJECT, r.nextToken());

        assertToken(JsonToken.PROPERTY_NAME, r.nextToken());
        if (verifyContents) {
            assertEquals(UNICODE_SHORT_NAME, r.currentName());
            assertEquals(UNICODE_SHORT_NAME, r.currentText());
        }
        assertToken(JsonToken.VALUE_STRING, r.nextToken());
        // also, should always be accessible this way:
        if (verifyContents) {
            assertTrue(r.parser().hasStringCharacters());
            assertEquals(UNICODE_LONG_NAME, r.currentText());
        }

        assertToken(JsonToken.PROPERTY_NAME, r.nextToken());
        if (verifyContents) {
            assertEquals(UNICODE_LONG_NAME, r.currentName());
            assertEquals(UNICODE_LONG_NAME, r.currentText());
        }
        assertToken(JsonToken.VALUE_STRING, r.nextToken());
        if (verifyContents) {
            assertEquals(UNICODE_SHORT_NAME, r.currentText());
        }

        // and ASCII entry
        assertToken(JsonToken.PROPERTY_NAME, r.nextToken());
        if (verifyContents) {
            assertEquals(ASCII_SHORT_NAME, r.currentName());
            assertEquals(ASCII_SHORT_NAME, r.currentText());
        }
        assertToken(JsonToken.VALUE_STRING, r.nextToken());
        if (verifyContents) {
            assertEquals(ASCII_SHORT_NAME, r.currentText());
        }

        assertToken(JsonToken.END_OBJECT, r.nextToken());
        assertNull(r.nextToken());

        // Second round, try with alternate read method
        if (verifyContents) {
            r = asyncForBytes(_smileReader(true), readSize, data, offset);
            assertToken(JsonToken.START_OBJECT, r.nextToken());
            assertToken(JsonToken.PROPERTY_NAME, r.nextToken());
            assertEquals(UNICODE_SHORT_NAME, r.currentTextViaWriter());
            assertToken(JsonToken.VALUE_STRING, r.nextToken());
            assertEquals(UNICODE_LONG_NAME, r.currentTextViaWriter());

            assertToken(JsonToken.PROPERTY_NAME, r.nextToken());
            assertEquals(UNICODE_LONG_NAME, r.currentTextViaWriter());
            assertToken(JsonToken.VALUE_STRING, r.nextToken());
            assertEquals(UNICODE_SHORT_NAME, r.currentTextViaWriter());

            assertToken(JsonToken.PROPERTY_NAME, r.nextToken());
            assertEquals(ASCII_SHORT_NAME, r.currentTextViaWriter());
            assertToken(JsonToken.VALUE_STRING, r.nextToken());
            assertEquals(ASCII_SHORT_NAME, r.currentTextViaWriter());

            assertToken(JsonToken.END_OBJECT, r.nextToken());
        }
    }
}
