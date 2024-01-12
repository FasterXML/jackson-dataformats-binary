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

package tools.jackson.dataformat.ion.failing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import tools.jackson.core.Version;

import tools.jackson.databind.DatabindContext;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.annotation.JsonTypeResolver;
import tools.jackson.databind.cfg.MapperConfig;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;
import tools.jackson.databind.jsontype.TypeIdResolver;
import tools.jackson.databind.jsontype.impl.ClassNameIdResolver;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.dataformat.ion.IonObjectMapper;
import tools.jackson.dataformat.ion.polymorphism.IonAnnotationIntrospector;
import tools.jackson.dataformat.ion.polymorphism.IonAnnotationTypeResolverBuilder;
import tools.jackson.dataformat.ion.polymorphism.MultipleTypeIdResolver;

import com.amazon.ion.IonSystem;
import com.amazon.ion.IonType;
import com.amazon.ion.IonValue;
import com.amazon.ion.system.IonSystemBuilder;

public class PolymorphicRoundtripTest
{
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
        protected TypeIdResolver defaultIdResolver(MapperConfig<?> config, JavaType baseType,
                PolymorphicTypeValidator ptv) {
            if (null != preferredTypeId) {
                return new MultipleClassNameIdResolver(baseType,ptv);
            } else {
                return new ClassNameIdResolver(baseType, ptv);
            }
        }
    }

    // Extends Jackson's ClassNameIdResolver to add superclass names, recursively
    class MultipleClassNameIdResolver extends ClassNameIdResolver implements MultipleTypeIdResolver {
        private static final long serialVersionUID = 1L;

        MultipleClassNameIdResolver(JavaType baseType, PolymorphicTypeValidator ptv) {
            super(baseType, ptv);
        }

        @Override
        public String[] idsFromValue(DatabindContext ctxt, Object value) {
            List<String> ids = new ArrayList<String>();
            Class<?> cls = value.getClass();
            while (null != cls) {
                ids.add(super.idFromValueAndType(ctxt, value, cls));
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

    @Before
    public void reset() {
        resolveAllTypes = false;
        preferredTypeId = null;
    }

    /*
    /**********************************************************************
    /* Test methods
    /**********************************************************************
     */

    @Test
    public void testSimple() throws IOException {
        Bean original = new Bean("parent_field", new ChildBean("child_field"));
        IonObjectMapper mapper = IonObjectMapper.builder()
                .addModule(new IonAnnotationModule())
                .build();
        String serialized = mapper.writeValueAsString(original);
        Bean deserialized = mapper.readValue(serialized, Bean.class);

        assertEquals(original.field, deserialized.field);
        assertEquals(original.child.someField, deserialized.child.someField);
    }

    @Test
    public void testSubclass() throws IOException {
        IonObjectMapper mapper = IonObjectMapper.builder()
                .addModule(new IonAnnotationModule())
                .build();
        Bean original = new Bean("parent_field", new ChildBeanSub("child_field", "extended_field"));
        String serialized = mapper.writeValueAsString(original);
        Bean deserialized = mapper.readValue(serialized, Bean.class);

        assertEquals(original.field, deserialized.field);
        assertEquals(original.child.someField, deserialized.child.someField);
        assertTrue(deserialized.child instanceof ChildBeanSub);
        assertEquals(((ChildBeanSub) original.child).extraField, ((ChildBeanSub) deserialized.child).extraField);
    }

    @Test
    public void testTopLevelPolymorphism() throws IOException {
        resolveAllTypes = true;

        Bean original = new Bean("parent_field", new ChildBean("child_field"));
        IonObjectMapper mapper = IonObjectMapper.builder()
                .addModule(new IonAnnotationModule())
                .build();
        String serialized = mapper.writeValueAsString(original);
        Object obj = mapper.readValue(serialized, Object.class);
        assertTrue(obj instanceof Bean);
        Bean deserialized = (Bean) obj;
        assertEquals(original.field, deserialized.field);
        assertEquals(original.child.someField, deserialized.child.someField);
    }

    @Test
    public void testSelectivePolymorphism() throws IOException {
        // preferredTypeId is a crude testing mechanism of choosing among several serialized type ids.
        preferredTypeId = "no match"; // setting non-null so that multiple type ids get serialized

        Bean original = new Bean("parent_field", new ChildBeanSub("child_field", "extended_field"));
        IonObjectMapper mapper = IonObjectMapper.builder()
                .addModule(new IonAnnotationModule())
                .build();
        String serialized = mapper.writeValueAsString(original);

        // first, try deserializing with no preferred type id (no matching one, anyway). We expect the first type id
        // to be chosen (and we expect that first id to be the most narrow type, ChildBeanSub).
        Bean deserialized = mapper.readValue(serialized, Bean.class);

        assertEquals(deserialized.child.getClass(), ChildBeanSub.class);
        assertEquals(((ChildBeanSub) original.child).extraField, ((ChildBeanSub) deserialized.child).extraField);

        // second, try deserializing with the wider type (ChildBean). We're losing data (extraField)
        preferredTypeId = getClass().getCanonicalName() + "$ChildBean";
        deserialized = mapper.readValue(serialized, Bean.class);

        assertEquals(deserialized.child.getClass(), ChildBean.class);
        assertEquals(original.child.someField, deserialized.child.someField);

        // third, try deserializing into an Object. The child node should deserialize, but immediately fail mapping.
        preferredTypeId = "java.lang.Object";
        try {
            deserialized = mapper.readValue(serialized, Bean.class);
            Assert.fail("Expected jackson to complain about casting a (concrete) Object into a ChildBean.");
        } catch (DatabindException e) {
        }
    }

    @Test
    public void testWithIonDate() throws IOException {
        resolveAllTypes = true;
        ObjectMapper mapper = IonObjectMapper.builder()
                .addModule(new IonAnnotationModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        long etime = 1449191916000L;
        java.util.Date uDate = new java.util.Date(etime);
        java.sql.Date sDate = new java.sql.Date(etime);

        // java.util.Date can serialize properly
        String serialized = mapper.writeValueAsString(uDate);
        IonValue ionVal = ionSystem.singleValue(serialized);
        Assert.assertEquals("Expected date to be serialized into an IonTimestamp", IonType.TIMESTAMP, ionVal.getType());

        // java.sql.Date can serialize properly
        serialized = mapper.writeValueAsString(sDate);
        ionVal = ionSystem.singleValue(serialized);
        Assert.assertEquals("Expected date to be serialized into an IonTimestamp", IonType.TIMESTAMP, ionVal.getType());
    }

    @Test
    public void testWithDateAsTimestamp() throws IOException {
        resolveAllTypes = true;
        ObjectMapper ionDateMapper = IonObjectMapper.builder()
                .addModule(new IonAnnotationModule())
                .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        long etime = 1449191916000L;
        java.util.Date uDate = new java.util.Date(etime);
        java.sql.Date sDate = new java.sql.Date(etime);

        // java.util.Date can serialize properly
        String serialized = ionDateMapper.writeValueAsString(uDate);
        IonValue ionVal = ionSystem.singleValue(serialized);
        Assert.assertEquals("Expected date to be serialized into an int", IonType.INT, ionVal.getType());

        // java.sql.Date can serialize properly
        serialized = ionDateMapper.writeValueAsString(sDate);
        ionVal = ionSystem.singleValue(serialized);
        Assert.assertEquals("Expected date to be serialized into an int", IonType.INT, ionVal.getType());
    }

    @Test
    public void testPolymorphicTypeWithDate() throws IOException{
        resolveAllTypes = true;
        long etime = 1449191416000L;
        java.util.Date uDate = new java.util.Date(etime);
        java.sql.Date sDate = new java.sql.Date(etime);
        Bean original = new Bean("parent_field",
                new ChildBeanSub("child_field", "extra_field", uDate, sDate, null));

        IonObjectMapper mapper = IonObjectMapper.builder()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .addModule(new IonAnnotationModule())
                .build();
        // roundtrip
        String serialized = mapper.writeValueAsString(original);
        Bean deserialized = mapper.readValue(serialized, Bean.class);
        ChildBeanSub deserializedSub = (ChildBeanSub)deserialized.child;
        Assert.assertEquals("Date result not the same as serialized value.", uDate, deserializedSub.uDate);
        Assert.assertEquals("Date result not the same as serialized value.", sDate, deserializedSub.sDate);
    }
/*

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
        Assert.assertEquals("Dynamic data not the same as serialized IonValue.", dynamicData, deserializedSub.dynamicData);
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
                        config.getPolymorphicTypeValidator());
            }
        }
    }

    // Extends Jackson's ClassNameIdResolver to add superclass names, recursively
    class MultipleClassNameIdResolver extends ClassNameIdResolver implements MultipleTypeIdResolver {

        MultipleClassNameIdResolver(JavaType baseType, TypeFactory typeFactory,
                PolymorphicTypeValidator ptv) {
            super(baseType, typeFactory, ptv);
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
*/
}
