package tools.jackson.dataformat.avro;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;

import tools.jackson.core.StreamWriteFeature;
import tools.jackson.databind.DeserializationFeature;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class Issue19Test extends AvroTestBase
{
    static class EventID {
        public String description;
        public Integer first;
        public Integer second;

        public EventID(String description, Integer first, Integer second) {
            this.description = description;
            this.first = first;
            this.second = second;
        }
    }

    static class Problem {
        public Integer x;
        public Integer y;

        public Problem(Integer x, Integer y) {
            this.x = x;
            this.y = y;
        }
    }

    static class Event {
        public Integer playerCount;
        public EventID eventID;

        public Event(Integer playerCount, EventID eventID) {
            this.playerCount = playerCount;
            this.eventID = eventID;
        }
    }

    static class EventLog {
        public final static Integer MAX_EVENTS = 255;

        public Integer version;
        public Byte eventCount;
        public List<Event> events;
        public List<Problem> problems;

        public EventLog(Integer version, Byte eventCount, List<Event> events, List<Problem> problems) {
            this.version = version;
            this.eventCount = eventCount;
            this.events = events;
            this.problems = problems;
        }
    }

    @Test
    public void testIssue19() throws Exception
    {
        List<Event> sampleEvents = new ArrayList<>();
        sampleEvents.add(new Event(10, new EventID("sample1", 1, 2)));
        sampleEvents.add(new Event(20, new EventID("sample2", 10, 20)));
        sampleEvents.add(new Event(30, new EventID("sample3", 100, 200)));

        List<Problem> sampleProblems = new ArrayList<>();
        sampleProblems.add(new Problem(800, 801));
        sampleProblems.add(new Problem(900, 901));

        EventLog input = new EventLog(9999, (byte) sampleEvents.size(), sampleEvents, sampleProblems);

        AvroMapper mapper = AvroMapper.builder()
                .enable(StreamWriteFeature.IGNORE_UNKNOWN)
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .changeDefaultVisibility(vc ->
                    vc.withVisibility(PropertyAccessor.FIELD, Visibility.PUBLIC_ONLY)
                    .withVisibility(PropertyAccessor.GETTER, Visibility.NONE))
                .build();

        // First, see if we can generate schema, use that:
        AvroSchema schema = mapper.schemaFor(EventLog.class);
        byte[] encoded = mapper.writer(schema).writeValueAsBytes(input);
        assertNotNull(encoded);

        // And if that works, use a subset
        InputStream in = getClass().getResourceAsStream("issue19.avsc");
        AvroSchema partialSchema = mapper.schemaFrom(in);
        in.close(); // just prevent compiler from warning

        byte[] encoded2 = mapper.writer(partialSchema).writeValueAsBytes(input);
        assertNotNull(encoded2);

    }
}
