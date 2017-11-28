package com.fasterxml.jackson.dataformat.cbor.mapper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.dataformat.cbor.CBORTestBase;

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
    
    public void testRecursiveForDeser() throws Exception
    {
        byte[] doc = cborDoc(aposToQuotes("{ 'name': 'admin',\n"
//                + "    'person_z': [ { 'name': 'admin' } ]"
              + "    'person_z': { 'name': 'admin' }"
                + "}"));
        Person result = MAPPER.readValue(doc, Person.class);
        assertEquals("admin", result.name);
    }

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
