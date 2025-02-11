package com.fasterxml.jackson.dataformat.smile.mapper;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.dataformat.smile.BaseTestForSmile;
import com.fasterxml.jackson.dataformat.smile.databind.SmileMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class NumberReadTest extends BaseTestForSmile {

    static class DeserializationIssue4917 {
        public DecimalHolder4917 decimalHolder;
        public double number;
    }

    static class DecimalHolder4917 {
        public BigDecimal value;

        private DecimalHolder4917(BigDecimal value) {
            this.value = value;
        }

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        static DecimalHolder4917 of(BigDecimal value) {
            return new DecimalHolder4917(value);
        }
    }

    // [databind#4917]
    @Test
    public void testIssue4917() throws Exception {
        final String bd = "100.00";
        final double d = 50.0;
        final DeserializationIssue4917 value = new DeserializationIssue4917();
        value.decimalHolder = DecimalHolder4917.of(new BigDecimal(bd));
        value.number = d;
        final SmileMapper mapper = smileMapper();
        final byte[] data = mapper.writeValueAsBytes(value);
        final DeserializationIssue4917 result = mapper.readValue(
                data, DeserializationIssue4917.class);
        assertEquals(value.decimalHolder.value, result.decimalHolder.value);
        assertEquals(value.number, result.number);
    }

}
