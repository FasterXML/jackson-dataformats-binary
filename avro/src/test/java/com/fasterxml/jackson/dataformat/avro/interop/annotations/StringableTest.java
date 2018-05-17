package com.fasterxml.jackson.dataformat.avro.interop.annotations;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import lombok.Data;
import org.apache.avro.reflect.AvroSchema;
import org.apache.avro.reflect.Stringable;
import org.junit.Test;

import com.fasterxml.jackson.dataformat.avro.interop.ApacheAvroInteropUtil;
import com.fasterxml.jackson.dataformat.avro.interop.InteropTestBase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeTrue;

/**
 * Tests support for using classes marked {@link Stringable @Stringable} as map keys. These classes must have a constructor which accepts a
 * single string as an argument, and their {@link #toString()} must return a serialized version of the object that can be passed back into
 * the constructor to recreate it. In addition, Avro considers the following classes
 * stringable (see {code SpecificData.stringableClasses()}) by default:
 * <ul>
 * <li>{@link File}</li>
 * <li>{@link BigInteger}</li>
 * <li>{@link BigDecimal}</li>
 * <li>{@link URI}</li>
 * <li>{@link URL}</li>
 * </ul>
 */
public class StringableTest extends InteropTestBase {
    @Stringable
    @Data
    public static class CustomStringableKey {
        private final String test;

        public CustomStringableKey(String test) {
            this.test = test;
        }

        @Override
        public String toString() {
            return test;
        }
    }

    @Data
    static class BigNumberWrapper {
        @AvroSchema("\"double\"")
        private BigDecimal bigDecimal;
        @AvroSchema("\"long\"")
        private BigInteger bigInteger;

        protected BigNumberWrapper() { }
        public BigNumberWrapper(BigDecimal d1, BigInteger i2) {
            bigDecimal = d1;
            bigInteger = i2;
        }
    }

    @Test
    public void testBigDecimalWithDoubleSchema() throws IOException {
        // Apache impl can't do coercion
        assumeTrue(serializeFunctor != ApacheAvroInteropUtil.apacheSerializer);
        assumeTrue(deserializeFunctor != ApacheAvroInteropUtil.apacheDeserializer);

        double value = 0.32198154657;
        BigNumberWrapper wrapper = new BigNumberWrapper(new BigDecimal(value), BigInteger.ONE);
        //
        BigNumberWrapper result = roundTrip(wrapper);
        //
        assertThat(result.bigDecimal.doubleValue()).isEqualTo(value);
    }

    @Test
    public void testBigIntegerWithDoubleSchema() throws IOException {
        // Apache impl can't do coercion
        assumeTrue(serializeFunctor != ApacheAvroInteropUtil.apacheSerializer);
        assumeTrue(deserializeFunctor != ApacheAvroInteropUtil.apacheDeserializer);

        long value = 948241716844286248L;
        BigNumberWrapper wrapper = new BigNumberWrapper(BigDecimal.ZERO, BigInteger.valueOf(value));
        //
        BigNumberWrapper result = roundTrip(wrapper);
        //
        assertThat(result.bigInteger.longValue()).isEqualTo(value);
    }

    @Test
    public void testBigDecimal() throws IOException {
        BigDecimal original = new BigDecimal("0.7193789624775822761924891294139324921");
        //
        BigDecimal result = roundTrip(original);
        //
        assertThat(result).isEqualTo(original);
    }

    @Test
    public void testBigDecimalArray() throws IOException {
        ArrayList<BigDecimal> array = new ArrayList<>();
        array.add(new BigDecimal("32165498701061140.034501381101601018405251061"));
        array.add(new BigDecimal("0.7193789624775822761924891294139324921"));
        //
        ArrayList<BigDecimal> result = roundTrip(type(ArrayList.class, BigDecimal.class), array);
        //
        assertThat(result).isEqualTo(array);
    }

    @Test
    public void testBigDecimalKeys() throws IOException {
        Map<BigDecimal, String> map = new HashMap<>();
        map.put(new BigDecimal("32165498701061140.034501381101601018405251061"), "one");
        map.put(new BigDecimal("0.7193789624775822761924891294139324921"), "two");
        //
        Map<BigDecimal, String> result = roundTrip(type(Map.class, BigDecimal.class, String.class), map);
        //
        assertThat(result).isEqualTo(map);
    }

    @Test
    public void testBigInteger() throws IOException {
        BigInteger original = new BigInteger("1236549816934246813682843621431493681279364198");
        //
        BigInteger result = roundTrip(original);
        //
        assertThat(result).isEqualTo(original);
    }

    @Test
    public void testBigIntegerArray() throws IOException {
        ArrayList<BigInteger> array = new ArrayList<>();
        array.add(new BigInteger("32165498701061140034501381101601018405251061"));
        array.add(new BigInteger("7193789624775822761924891294139324921"));
        //
        ArrayList<BigInteger> result = roundTrip(type(ArrayList.class, BigInteger.class), array);
        //
        assertThat(result).isEqualTo(array);
    }

    @Test
    public void testBigIntegerKeys() throws IOException {
        Map<BigInteger, String> map = new HashMap<>();
        map.put(new BigInteger("32165498701061140034501381101601018405251061"), "one");
        map.put(new BigInteger("7193789624775822761924891294139324921"), "two");
        //
        Map<BigInteger, String> result = roundTrip(type(Map.class, BigInteger.class, String.class), map);
        //
        assertThat(result).isEqualTo(map);
    }

    @Test
    public void testCustomStringable() throws IOException {
        CustomStringableKey original = new CustomStringableKey("one");
        //
        CustomStringableKey result = roundTrip(original);
        //
        assertThat(result).isEqualTo(original);
    }

    @Test
    public void testCustomStringableArray() throws IOException {
        ArrayList<CustomStringableKey> array = new ArrayList<>();
        array.add(new CustomStringableKey("one"));
        array.add(new CustomStringableKey("two"));
        //
        ArrayList<CustomStringableKey> result = roundTrip(type(ArrayList.class, CustomStringableKey.class), array);
        //
        assertThat(result).isEqualTo(array);
    }

    @Test
    public void testCustomStringableKeyWithScalarValue() throws IOException {
        Map<CustomStringableKey, String> object = new HashMap<>();
        object.put(new CustomStringableKey("one"), "two");
        object.put(new CustomStringableKey("three"), "four");
        //
        Map<CustomStringableKey, String> result = roundTrip(type(Map.class, CustomStringableKey.class, String.class), object);
        //
        assertThat(result).isEqualTo(object);
    }

    @Test
    public void testFile() throws IOException {
        File original = new File("/a/cool/file");
        //
        File result = roundTrip(original);
        //
        assertThat(result).isEqualTo(original);
    }

    @Test
    public void testFileArray() throws IOException {
        ArrayList<File> array = new ArrayList<>();
        array.add(new File("/some/path"));
        array.add(new File("/some/other/path"));
        //
        ArrayList<File> result = roundTrip(type(ArrayList.class, File.class), array);
        //
        assertThat(result).isEqualTo(array);
    }

    @Test
    public void testFileKeys() throws IOException {
        Map<File, String> object = new HashMap<>();
        object.put(new File("/some/path"), "one");
        object.put(new File("/some/other/path"), "two");
        //
        Map<File, String> result = roundTrip(type(Map.class, File.class, String.class), object);
        //
        assertThat(result).isEqualTo(object);
    }

    @Test
    public void testURI() throws URISyntaxException, IOException {
        URI original = new URI("https://github.com");
        //
        URI result = roundTrip(original);
        //
        assertThat(result).isEqualTo(original);
    }

    @Test
    public void testURIArray() throws URISyntaxException, IOException {
        ArrayList<URI> array = new ArrayList<>();
        array.add(new URI("http://fasterxml.com"));
        array.add(new URI("https://github.com"));
        //
        ArrayList<URI> result = roundTrip(type(ArrayList.class, URI.class), array);
        //
        assertThat(result).isEqualTo(array);
    }

    @Test
    public void testURIKeys() throws URISyntaxException, IOException {
        Map<URI, String> object = new HashMap<>();
        object.put(new URI("http://fasterxml.com"), "one");
        object.put(new URI("https://github.com"), "two");
        //
        Map<URI, String> result = roundTrip(type(Map.class, URI.class, String.class), object);
        //
        assertThat(result).isEqualTo(object);
    }

    @Test
    public void testURL() throws IOException {
        URL original = new URL("https://github.com");
        //
        URL result = roundTrip(original);
        //
        assertThat(result).isEqualTo(original);
    }

    @Test
    public void testURLArray() throws IOException {
        ArrayList<URL> array = new ArrayList<>();
        array.add(new URL("http://fasterxml.com"));
        array.add(new URL("https://github.com"));
        //
        ArrayList<URL> result = roundTrip(type(ArrayList.class, URL.class), array);
        //
        assertThat(result).isEqualTo(array);
    }

    @Test
    public void testURLKeys() throws IOException {
        Map<URL, String> map = new HashMap<>();
        map.put(new URL("http://fasterxml.com"), "one");
        map.put(new URL("https://github.com"), "two");
        //
        Map<URL, String> result = roundTrip(type(Map.class, URL.class, String.class), map);
        //
        assertThat(result).isEqualTo(map);
    }
}
