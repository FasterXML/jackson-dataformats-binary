package tools.jackson.dataformat.cbor.mapper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.databind.*;
import tools.jackson.dataformat.cbor.CBORTestBase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

public class RecursiveIgnorePropertiesTest extends CBORTestBase
{
    static class Person {
        public String name;

        @JsonProperty("person_z") // renaming this to person_p works
        @JsonIgnoreProperties({"person_z"}) // renaming this to person_p works
//        public Set<Person> personZ;
        public Person personZ;
    }

    private final ObjectMapper MAPPER = cborMapper();

    @Test
    public void testRecursiveForDeser() throws Exception
    {
        byte[] doc = cborDoc(aposToQuotes("{ 'name': 'admin',\n"
//                + "    'person_z': [ { 'name': 'admin' } ]"
              + "    'person_z': { 'name': 'admin' }"
                + "}"));
        Person result = MAPPER.readValue(doc, Person.class);
        assertEquals("admin", result.name);
    }

    @Test
    public void testRecursiveForSer() throws Exception
    {
        Person input = new Person();
        input.name = "Bob";
        Person p2 = new Person();
        p2.name = "Bill";
        input.personZ = p2;
        p2.personZ = input;

        byte[] doc = MAPPER.writeValueAsBytes(input);
        assertNotNull(doc);
    }
}
