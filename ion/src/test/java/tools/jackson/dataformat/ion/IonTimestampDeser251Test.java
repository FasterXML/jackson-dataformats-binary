package tools.jackson.dataformat.ion;

import com.amazon.ion.Timestamp;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

import tools.jackson.dataformat.ion.ionvalue.IonValueModule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

// For [dataformats-binary#251]
public class IonTimestampDeser251Test
{
    static class MessageWithoutTimestamp {
        final String message;
        final Integer count;

        @JsonCreator
        public MessageWithoutTimestamp(@JsonProperty("message") String message,
                       @JsonProperty("count") Integer count) {
            this.message = message;
            this.count = count;
        }

        public String getMessage() {
            return message;
        }
    }

    static class MessageWithTimestamp {
        final String message;
        final Integer count;

        public Timestamp timestamp;

        @JsonCreator
        public MessageWithTimestamp(@JsonProperty("message") String message,
                       @JsonProperty("count") Integer count) {
            this.message = message;
            this.count = count;
        }

        public String getMessage() {
            return message;
        }
    }
    
    private final ObjectMapper ION_MAPPER = IonObjectMapper.builder()
            .addModule(new IonValueModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

    // [dataformats-binary#251]
    @Test
    public void testDeserWithIgnoredBufferedTimestamp() throws Exception {
        String ion = "{message: \"Hello, world\", timestamp:2021-03-10T01:49:30.242-00:00}";
        MessageWithoutTimestamp message = ION_MAPPER.readValue(ion,
                MessageWithoutTimestamp.class);
        assertNotNull(message);
        assertEquals("Hello, world", message.message);
    }

    @Test
    public void testDeserWithBufferedTimestamp() throws Exception {
        String ion = "{message: \"Hello, world\", timestamp:2021-03-10T01:49:30.242-00:00}";
        MessageWithTimestamp message = ION_MAPPER.readValue(ion,
                MessageWithTimestamp.class);
        assertNotNull(message);
        assertEquals("Hello, world", message.message);
        assertNotNull(message.timestamp);
    }
}
