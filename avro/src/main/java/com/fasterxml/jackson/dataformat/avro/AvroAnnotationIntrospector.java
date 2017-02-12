package com.fasterxml.jackson.dataformat.avro;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.dataformat.avro.deser.AvroParserImpl;
import org.apache.avro.Schema;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.Encoder;
import org.apache.avro.reflect.AvroEncode;
import org.apache.avro.reflect.CustomEncoding;
import org.apache.avro.util.Utf8;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

public class AvroAnnotationIntrospector extends JacksonAnnotationIntrospector {
    public static class AvroDeserializer<T> extends JsonDeserializer<T> {
        public static class AvroParserDecoder extends Decoder {
            private final AvroParserImpl parser;

            public AvroParserDecoder(AvroParserImpl parser) {
                this.parser = parser;
            }

            @Override
            public void readNull() throws IOException {
                // don't need to do anything for null.
            }

            @Override
            public boolean readBoolean() throws IOException {
                return parser.decodeBoolean() == JsonToken.VALUE_TRUE;
            }

            @Override
            public int readInt() throws IOException {
                parser.decodeInt();
                return parser.getIntValue();
            }

            @Override
            public long readLong() throws IOException {
                parser.decodeLong();
                return parser.getLongValue();
            }

            @Override
            public float readFloat() throws IOException {
                parser.decodeFloat();
                return parser.getFloatValue();
            }

            @Override
            public double readDouble() throws IOException {
                parser.decodeDouble();
                return parser.getDoubleValue();
            }

            @Override
            public Utf8 readString(Utf8 old) throws IOException {
                return old.set(readString());
            }

            @Override
            public String readString() throws IOException {
                parser.decodeString();
                return parser._textValue; //
            }

            @Override
            public void skipString() throws IOException {
                parser.skipString();
            }

            @Override
            public ByteBuffer readBytes(ByteBuffer old) throws IOException {
                parser.decodeBytes();
                byte[] value = parser.getBinaryValue();
                if (old.capacity() < value.length) {
                    return ByteBuffer.wrap(value);
                }
                old.reset();
                old.put(value);
                old.flip();
                return old;
            }

            @Override
            public void skipBytes() throws IOException {
                parser.skipBytes();
            }

            @Override
            public void readFixed(byte[] bytes, int start, int length) throws IOException {
                parser.decodeFixed(length - start);
                byte[] value = parser.getBinaryValue();
                System.arraycopy(value, 0, bytes, start, length);
            }

            @Override
            public void skipFixed(int length) throws IOException {
                parser.skipFixed(length);
            }

            @Override
            public int readEnum() throws IOException {
                return parser.decodeEnum();
            }

            @Override
            public long readArrayStart() throws IOException {
                return parser.decodeArrayStart();
            }

            @Override
            public long arrayNext() throws IOException {
                return parser.decodeArrayNext();
            }

            @Override
            public long skipArray() throws IOException {
                return parser.skipArray();
            }

            @Override
            public long readMapStart() throws IOException {
                return parser.decodeMapStart();
            }

            @Override
            public long mapNext() throws IOException {
                return parser.decodeMapNext();
            }

            @Override
            public long skipMap() throws IOException {
                return parser.skipMap();
            }

            @Override
            public int readIndex() throws IOException {
                return parser.decodeIndex();
            }
        }

        private final CustomEncoderWrapper<T> encoding;

        public AvroDeserializer(CustomEncoding<T> encoding) {
            this.encoding = new CustomEncoderWrapper<>(encoding);
        }

        @Override
        public T deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
            AvroParserImpl    avroParser = (AvroParserImpl) p;
            AvroParserDecoder decoder    = new AvroParserDecoder(avroParser);
            return encoding.read(null, decoder);
        }

        @Override
        public T deserialize(JsonParser p, DeserializationContext ctxt, T intoValue) throws IOException, JsonProcessingException {
            AvroParserImpl    avroParser = (AvroParserImpl) p;
            AvroParserDecoder decoder    = new AvroParserDecoder(avroParser);
            return encoding.read(intoValue, decoder);
        }
    }

    public static class CustomEncoderWrapper<T> {
        private static final Method GET_SCHEMA;
        private static final Method READ;
        private static final Method WRITE;

        static {
            try {
                GET_SCHEMA = CustomEncoding.class.getDeclaredMethod("getSchema");
                READ = CustomEncoding.class.getDeclaredMethod("read", Object.class, Decoder.class);
                WRITE = CustomEncoding.class.getDeclaredMethod("write", Object.class, Encoder.class);
                GET_SCHEMA.setAccessible(true);
                READ.setAccessible(true);
                WRITE.setAccessible(true);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("Failed to initialize CustomEncoderWrapper, Avro version mismatch?", e);
            }
        }

        private final CustomEncoding<T> encoding;

        public CustomEncoderWrapper(CustomEncoding<T> encoding) {
            this.encoding = encoding;
        }

        public void write(Object datum, Encoder out) throws IOException {
            try {
                WRITE.invoke(encoding, datum, out);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException("Failed to encode object", e);
            }
        }

        public Schema getSchema() {
            try {
                return (Schema) GET_SCHEMA.invoke(encoding);
            } catch (IllegalAccessException| InvocationTargetException e) {
                throw new RuntimeException("Failed to access schema", e);
            }
        }

        public T read(Object reuse, Decoder in) throws IOException {
            try {
                return (T) READ.invoke(encoding, reuse, in);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException("Failed to decode object", e);
            }
        }

    }

    @Override
    public Object findDeserializer(Annotated a) {
        AvroEncode avroEncoder = _findAnnotation(a, AvroEncode.class);
        if (avroEncoder != null) {
            Class<? extends CustomEncoding<?>> encoderClass = avroEncoder.using();
            try {
                CustomEncoding<?> encoder = encoderClass.newInstance();
                return new AvroDeserializer<>(encoder);
            } catch (InstantiationException| IllegalAccessException e) {
                throw new RuntimeException("Couldn't instantiate avro encoding '" + encoderClass.getName() + "'", e);
            }
        }
        return super.findDeserializer(a);
    }
}
