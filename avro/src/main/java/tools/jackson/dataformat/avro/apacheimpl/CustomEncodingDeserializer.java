package tools.jackson.dataformat.avro.apacheimpl;

import java.io.IOException;

import org.apache.avro.reflect.CustomEncoding;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.exc.JacksonIOException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.dataformat.avro.CustomEncodingWrapper;
import tools.jackson.dataformat.avro.deser.AvroParserImpl;

/**
 * Deserializes an object using a avro {@link CustomEncoding}
 *
 * @see tools.jackson.dataformat.avro.AvroAnnotationIntrospector
 */
public class CustomEncodingDeserializer<T> extends ValueDeserializer<T> {

    private final CustomEncodingWrapper<T> encoding;

    public CustomEncodingDeserializer(CustomEncoding<T> encoding) {
        this.encoding = new CustomEncodingWrapper<>(encoding);
    }

    @Override
    public T deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
        return _read(p, null);
    }

    @Override
    public T deserialize(JsonParser p, DeserializationContext ctxt, T intoValue) throws JacksonException {
        return _read(p, intoValue);
    }

    private T _read(JsonParser p, T reuse) throws JacksonException
    {
        final AvroParserImpl avroParser = (AvroParserImpl) p;
        // `CustomEncoding` reads scalar leaves straight off the `Decoder`, and
        // `DecoderOverAvroParser` swallows the structural tokens it steps over on the
        // way. For a structured value that leaves the stream parked *inside* the value
        // once the last leaf is read, with its closing token(s) still pending; the
        // enclosing deserializer would then read our END_OBJECT as its own and stop
        // early, silently dropping every property after this one. So remember how deep
        // we started, and unwind back to the matching close token afterwards.
        final JsonToken start = p.currentToken();
        final boolean structured = (start == JsonToken.START_OBJECT) || (start == JsonToken.START_ARRAY);
        final int startDepth = structured ? avroParser.getAvroContextDepth() : 0;

        final T value;
        try {
            value = encoding.read(reuse, new DecoderOverAvroParser(avroParser));
        } catch (IOException e) {
            throw JacksonIOException.construct(e);
        }
        if (structured) {
            while (avroParser.getAvroContextDepth() >= startDepth) {
                if (p.nextToken() == null) { // truncated content; let caller report it
                    break;
                }
            }
        }
        return value;
    }
}
