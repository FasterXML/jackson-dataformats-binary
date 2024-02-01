package tools.jackson.dataformat.ion.failing;

import org.junit.Test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

import tools.jackson.dataformat.ion.IonObjectMapper;
import tools.jackson.dataformat.ion.ionvalue.IonValueModule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

// For [dataformats-binary#251]
public class IonTimestampDeser251Test
{
    static class Message {
        final String message;
        final Integer count;

        @JsonCreator
        public Message(@JsonProperty("message") String message,
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
    public void testTimestampDeserWithBuffering() throws Exception {
        String ion = "{message: \"Hello, world\", timestamp:2021-03-10T01:49:30.242-00:00}";
        Message message = ION_MAPPER.readValue(ion, Message.class);
        assertNotNull(message);
        assertEquals("Hello, world", message.message);
    }
}
