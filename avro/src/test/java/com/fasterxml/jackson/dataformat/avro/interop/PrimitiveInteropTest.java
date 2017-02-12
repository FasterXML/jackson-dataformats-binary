package com.fasterxml.jackson.dataformat.avro.interop;

import lombok.Data;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@RunWith(Parameterized.class)
public class PrimitiveInteropTest extends InteropTestBase<PrimitiveInteropTest.PrimitivesObject, PrimitiveInteropTest.PrimitivesObject> {
    @Data
    public static class PrimitivesObject {
        public static PrimitivesObject dummy() {
            PrimitivesObject object = new PrimitivesObject();
            object.booleanValue = true;
            object.booleanWrapper = false;
            object.byteValue = -97;
            object.byteWrapper = 56;
            object.shortValue = -8000;
            object.shortWrapper = 4567;
            object.intValue = Integer.MAX_VALUE;
            object.intWrapper = 0;
            object.longValue = Long.MIN_VALUE;
            object.longWrapper = Double.doubleToLongBits(123456789);
            object.floatValue = 0.12377523f;
            object.floatWrapper = 12435.1236787353f;
            object.doubleValue = 123967.1298751;
            object.doubleWrapper = 9892384.1985;
            return object;
        }

        private boolean booleanValue;
        private byte    byteValue;
        private short   shortValue;
        private int     intValue;
        private long    longValue;
        private float   floatValue;
        private double  doubleValue;
        private Boolean booleanWrapper;
        private Byte    byteWrapper;
        private Short   shortWrapper;
        private Integer intWrapper;
        private Long    longWrapper;
        private Float   floatWrapper;
        private Double  doubleWrapper;
    }

    @Parameterized.Parameters(name = "{4}")
    public static Object[][] getParameters() {
        return getParameters(PrimitivesObject.class, PrimitivesObject.dummy(), PrimitivesObject.class);
    }

    @Test
    public void testBooleanValue() {
        assertThat(actual.booleanValue, is(equalTo(expected.booleanValue)));
    }

    @Test
    public void testBooleanWrapper() {
        assertThat(actual.booleanWrapper, is(equalTo(expected.booleanWrapper)));
    }

    @Test
    public void testByteValue() {
        assertThat(actual.byteValue, is(equalTo(expected.byteValue)));
    }

    @Test
    public void testByteWrapper() {
        assertThat(actual.byteWrapper, is(equalTo(expected.byteWrapper)));
    }

    @Test
    public void testDoubleValue() {
        assertThat(actual.doubleValue, is(equalTo(expected.doubleValue)));
    }

    @Test
    public void testDoubleWrapper() {
        assertThat(actual.doubleWrapper, is(equalTo(expected.doubleWrapper)));
    }

    @Test
    public void testFloatValue() {
        assertThat(actual.floatValue, is(equalTo(expected.floatValue)));
    }

    @Test
    public void testFloatWrapper() {
        assertThat(actual.floatWrapper, is(equalTo(expected.floatWrapper)));
    }

    @Test
    public void testIntValue() {
        assertThat(actual.intValue, is(equalTo(expected.intValue)));
    }

    @Test
    public void testIntWrapper() {
        assertThat(actual.intWrapper, is(equalTo(expected.intWrapper)));
    }

    @Test
    public void testLongValue() {
        assertThat(actual.longValue, is(equalTo(expected.longValue)));
    }

    @Test
    public void testLongWrapper() {
        assertThat(actual.longWrapper, is(equalTo(expected.longWrapper)));
    }

    @Test
    public void testShortValue() {
        assertThat(actual.shortValue, is(equalTo(expected.shortValue)));
    }

    @Test
    public void testShortWrapper() {
        assertThat(actual.shortWrapper, is(equalTo(expected.shortWrapper)));
    }
}
