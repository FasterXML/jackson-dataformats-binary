/*
 * Copyright 2012-2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at:
 *
 *     http://aws.amazon.com/apache2.0/
 *
 * or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.fasterxml.jackson.dataformat.ion;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.amazon.ion.*;
import com.amazon.ion.system.IonSystemBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonTypeResolver;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;
import com.fasterxml.jackson.databind.jsontype.impl.ClassNameIdResolver;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.dataformat.ion.ionvalue.IonValueMapper;
import com.fasterxml.jackson.dataformat.ion.polymorphism.*;

import static org.junit.jupiter.api.Assertions.*;

public class PolymorphicRoundtripTest {

    /**
     * Because of the method of configuring stuff Jackson, the alternative to making these (ugly) member variables is
     * to make them arguments to the IonAnnotationModule's construction, where they would then get passed through as
     * member variables to each of (as they are constructed during serialization): IonAnnotationModule,
     * ClassNameIonAnnotationIntrospector, and MultipleClassNameIdResolver (where they actually get used). I think
     * this is (a little) easier to understand.
     */
    boolean resolveAllTypes = false; // apply resolver under test to all types, not just annotated ones
    String preferredTypeId = null; // if asked to resolve from multiple ids, choose this one.
    IonSystem ionSystem = IonSystemBuilder.standard().build();

    @BeforeEach
    public void reset() {
        resolveAllTypes = false;
        preferredTypeId = null;
    }

    @Test
    public void testSimple() throws IOException {
        Bean original = new Bean("parent_field", new ChildBean("child_field"));
        IonObjectMapper mapper = new IonObjectMapper();
        mapper.registerModule(new IonAnnotationModule());
        String serialized = mapper.writeValueAsString(original);
        Bean deserialized = mapper.readValue(serialized, Bean.class);

        assertEquals(original.field, deserialized.field);
        assertEquals(original.child.someField, deserialized.child.someField);
    }

    @Test
    public void testSubclass() throws IOException {
        Bean original = new Bean("parent_field", new ChildBeanSub("child_field", "extended_field"));
        IonObjectMapper mapper = new IonObjectMapper();
        mapper.registerModule(new IonAnnotationModule());
        String serialized = mapper.writeValueAsString(original);
        Bean deserialized = mapper.readValue(serialized, Bean.class);

        assertEquals(original.field, deserialized.field);
        assertEquals(original.child.someField, deserialized.child.someField);
        assertInstanceOf(ChildBeanSub.class, deserialized.child);
        assertEquals(((ChildBeanSub) original.child).extraField, ((ChildBeanSub) deserialized.child).extraField);
    }

    @Test
    public void testTopLevelPolymorphism() throws IOException {
        resolveAllTypes = true;

        Bean original = new Bean("parent_field", new ChildBean("child_field"));
        IonObjectMapper mapper = new IonObjectMapper();
        mapper.registerModule(new IonAnnotationModule());
        String serialized = mapper.writeValueAsString(original);
        Object obj = mapper.readValue(serialized, Object.class);
        assertInstanceOf(Bean.class, obj);
        Bean deserialized = (Bean) obj;
        assertEquals(original.field, deserialized.field);
        assertEquals(original.child.someField, deserialized.child.someField);
    }

    @Test
    public void testSelectivePolymorphism() throws IOException {
        // preferredTypeId is a crude testing mechanism of choosing among several serialized type ids.
        preferredTypeId = "no match"; // setting non-null so that multiple type ids get serialized

        Bean original = new Bean("parent_field", new ChildBeanSub("child_field", "extended_field"));
        IonObjectMapper mapper = new IonObjectMapper();
        mapper.registerModule(new IonAnnotationModule());
        String serialized = mapper.writeValueAsString(original);

        // first, try deserializing with no preferred type id (no matching one, anyway). We expect the first type id
        // to be chosen (and we expect that first id to be the most narrow type, ChildBeanSub).
        Bean deserialized = mapper.readValue(serialized, Bean.class);

        assertEquals(ChildBeanSub.class, deserialized.child.getClass());
        assertEquals(((ChildBeanSub) original.child).extraField, ((ChildBeanSub) deserialized.child).extraField);

        // second, try deserializing with the wider type (ChildBean). We're losing data (extraField)
        preferredTypeId = getClass().getCanonicalName() + "$ChildBean";
        deserialized = mapper.readValue(serialized, Bean.class);

        assertEquals(ChildBean.class, deserialized.child.getClass());
        assertEquals(original.child.someField, deserialized.child.someField);

        // third, try deserializing into an Object. The child node should deserialize, but immediately fail mapping.
        preferredTypeId = "java.lang.Object";
        try {
            deserialized = mapper.readValue(serialized, Bean.class);
            fail("Expected jackson to complain about casting a (concrete) Object into a ChildBean.");
        } catch (JsonMappingException e) {
        }
    }

    @Test
    public void testWithIonDate() throws IOException {
        resolveAllTypes = true;
        ObjectMapper mapper = new IonObjectMapper()
            .registerModule(new IonAnnotationModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        long etime = 1449191916000L;
        java.util.Date uDate = new java.util.Date(etime);
        java.sql.Date sDate = new java.sql.Date(etime);

        // java.util.Date can serialize properly
        String serialized = mapper.writeValueAsString(uDate);
        IonValue ionVal = ionSystem.singleValue(serialized);
        assertEquals(IonType.TIMESTAMP, ionVal.getType(), "Expected date to be serialized into an IonTimestamp");

        // java.sql.Date can serialize properly
        serialized = mapper.writeValueAsString(sDate);
        ionVal = ionSystem.singleValue(serialized);
        assertEquals(IonType.TIMESTAMP, ionVal.getType(), "Expected date to be serialized into an IonTimestamp");
    }

    @Test
    public void testWithDateAsTimestamp() throws IOException {
        resolveAllTypes = true;
        ObjectMapper ionDateMapper = new IonObjectMapper()
            .registerModule(new IonAnnotationModule())
            .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        long etime = 1449191916000L;
        java.util.Date uDate = new java.util.Date(etime);
        java.sql.Date sDate = new java.sql.Date(etime);

        // java.util.Date can serialize properly
        String serialized = ionDateMapper.writeValueAsString(uDate);
        IonValue ionVal = ionSystem.singleValue(serialized);
        assertEquals(IonType.INT, ionVal.getType(), "Expected date to be serialized into an int");

        // java.sql.Date can serialize properly
        serialized = ionDateMapper.writeValueAsString(sDate);
        ionVal = ionSystem.singleValue(serialized);
        assertEquals(IonType.INT, ionVal.getType(), "Expected date to be serialized into an int");
    }

    @Test
    public void testPolymorphicTypeWithDate() throws IOException{
        resolveAllTypes = true;
        long etime = 1449191416000L;
        java.util.Date uDate = new java.util.Date(etime);
        java.sql.Date sDate = new java.sql.Date(etime);
        Bean original = new Bean("parent_field",
                new ChildBeanSub("child_field", "extra_field", uDate, sDate, null));

        IonObjectMapper mapper = new IonObjectMapper();
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.registerModule(new IonAnnotationModule());

        // roundtrip
        String serialized = mapper.writeValueAsString(original);
        Bean deserialized = mapper.readValue(serialized, Bean.class);
        ChildBeanSub deserializedSub = (ChildBeanSub)deserialized.child;
        assertEquals(uDate, deserializedSub.uDate, "Date result not the same as serialized value.");
        assertEquals(sDate, deserializedSub.sDate, "Date result not the same as serialized value.");
    }

    @Test
    public void testPolymorphicTypeWithIonValue() throws IOException{
        resolveAllTypes = true;
        IonValue dynamicData = ionSystem.newString("dynamic");
        Bean original = new Bean("parent_field",
                new ChildBeanSub("child_field", "extra_field", null, null, dynamicData));

        IonObjectMapper mapper = new IonValueMapper(ionSystem);
        mapper.registerModule(new IonAnnotationModule());

        // roundtrip
        String serialized = mapper.writeValueAsString(original);
        Bean deserialized = mapper.readValue(serialized, Bean.class);
        ChildBeanSub deserializedSub = (ChildBeanSub)deserialized.child;
        assertEquals(dynamicData, deserializedSub.dynamicData, "Dynamic data not the same as serialized IonValue.");
    }

    static class Bean {
        public String field;
        public ChildBean child;

        public Bean() {
        }

        public Bean(String field, ChildBean child) {
            this.field = field;
            this.child = child;
        }
    }

    @JsonTypeResolver(IonAnnotationTypeResolverBuilder.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ChildBean {
        public String someField;

        public ChildBean() {
        }

        public ChildBean(String someField) {
            this.someField = someField;
        }
    }

    static class ChildBeanSub extends ChildBean {
        public String extraField;
        public java.util.Date uDate;
        public java.sql.Date sDate;
        public IonValue dynamicData;

        public ChildBeanSub() {
        }

        public ChildBeanSub(String someField, String extraField) {
            super(someField);
            this.extraField = extraField;
        }

        public ChildBeanSub(String someField, String extraField, java.util.Date uDate, java.sql.Date sDate,
                IonValue dynamicData) {
            super(someField);
            this.extraField = extraField;
            this.uDate = uDate;
            this.sDate = sDate;
            this.dynamicData = dynamicData;
        }
    }

    class IonAnnotationModule extends SimpleModule {
		private static final long serialVersionUID = 1L;

		IonAnnotationModule() {
            super("IonAnnotationMod", Version.unknownVersion());
        }

        @Override
        public void setupModule(SetupContext context) {
            IonAnnotationIntrospector introspector = new ClassNameIonAnnotationIntrospector();
            context.appendAnnotationIntrospector(introspector);
        }
    }

    // For testing, use Jackson's classname TypeIdResolver
    class ClassNameIonAnnotationIntrospector extends IonAnnotationIntrospector {
		private static final long serialVersionUID = 1L;

		ClassNameIonAnnotationIntrospector() {
            super(resolveAllTypes);
        }

        @Override
        protected TypeIdResolver defaultIdResolver(MapperConfig<?> config, JavaType baseType) {
            if (null != preferredTypeId) {
                return new MultipleClassNameIdResolver(baseType, config.getTypeFactory(),
                        config.getPolymorphicTypeValidator());
            } else {
                return new ClassNameIdResolver(baseType, config.getTypeFactory(),
                        null, config.getPolymorphicTypeValidator());
            }
        }
    }

    // Extends Jackson's ClassNameIdResolver to add superclass names, recursively
    class MultipleClassNameIdResolver extends ClassNameIdResolver implements MultipleTypeIdResolver {
        private static final long serialVersionUID = 1L;

        MultipleClassNameIdResolver(JavaType baseType, TypeFactory typeFactory,
                PolymorphicTypeValidator ptv) {
            super(baseType, typeFactory, null, ptv);
        }

        @Override
        public String[] idsFromValue(Object value) {
            List<String> ids = new ArrayList<>();
            Class<?> cls = value.getClass();
            while (null != cls) {
                ids.add(super.idFromValueAndType(value, cls));
                cls = cls.getSuperclass();
            }
            return ids.toArray(new String[0]);
        }

        @Override
        public String selectId(String[] ids) {
            if (ids.length == 0) {
                return null;
            }
            for (String id : ids) {
                if (preferredTypeId.equals(id)) {
                    return preferredTypeId;
                }
            }
            return ids[0];
        }
    }

}
