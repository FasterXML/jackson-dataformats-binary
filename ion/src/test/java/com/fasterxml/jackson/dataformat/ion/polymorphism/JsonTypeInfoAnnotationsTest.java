package com.fasterxml.jackson.dataformat.ion.polymorphism;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import software.amazon.ion.IonSystem;
import software.amazon.ion.IonValue;
import software.amazon.ion.system.IonSystemBuilder;
import com.fasterxml.jackson.dataformat.ion.IonFactory;
import com.fasterxml.jackson.dataformat.ion.IonObjectMapper;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonTypeResolver;

public class JsonTypeInfoAnnotationsTest {

    @Test
    public void testSimple() throws IOException {

        IonSystem ios = IonSystemBuilder.standard().build();
        ObjectMapper mapper = new IonObjectMapper(new IonFactory(null, ios));
        Map<String, Integer> mapField = new HashMap<>();
        mapField.put("key1", 1);
        mapField.put("key2", 2);

        Bean original = new Bean(
                "parent",
                new ChildBean("foo"),
                new ChildBeanSub("foo", "bar"),
                new ChildBeanValueTypeSub("fiz"),
                new DefaultChild("baz"),
                new AnotherChild("anotherBaz"),
                new MapValueTypeChild(mapField),
                new ListValueTypeChild(Arrays.asList(1, 2, 3)));

        IonValue expected = ios.singleValue(
                        "{" +
                        "  field:\"parent\"," +
                        "  childBean:ChildA::{" +
                        "    someField:\"foo\"" +
                        "  }," +
                        "  childSub:ChildB::{" +
                        "    someField:\"foo\"," +
                        "    extraField:\"bar\"" +
                        "  }," +
                        "  childValueTypeSub:ChildC::\"fiz\"," +
                        "  defaultChild:'.JsonTypeInfoAnnotationsTest$DefaultChild'::{" +
                        "    thisIsAField:\"baz\"" +
                        "  }," +
                        "  anotherChild:'.JsonTypeInfoAnnotationsTest$AnotherChild'::{" +
                        "    thisIsAnotherField:\"anotherBaz\"" +
                        "  }," +
                        "  mapValueTypeChild: '.JsonTypeInfoAnnotationsTest$MapValueTypeChild'::{ " +
                        "    key1: 1," +
                        "    key2: 2," +
                        "  }," +
                        "  listValueTypeChild: '.JsonTypeInfoAnnotationsTest$ListValueTypeChild'::[1, 2, 3]" +
                        "}");

        String serialized = mapper.writeValueAsString(original);

        Assert.assertEquals(expected, ios.singleValue(serialized));

        // Make sure it can be re-serialized
        mapper.readValue(serialized, Bean.class);
    }

    @Test
    public void testDefaultType() throws IOException {

        IonSystem ios = IonSystemBuilder.standard().build();
        ObjectMapper mapper = new IonObjectMapper(new IonFactory(null, ios));

        final String unTypedIon =
                "{" +
                "  defaultChild:{" +
                "    thisIsAField:\"test\"" +
                "  }" +
                "}";

        DefaultBean bean = mapper.readValue(unTypedIon, DefaultBean.class);

        Assert.assertSame(DefaultChild.class, bean.defaultChild.getClass());
    }

    static class Bean {
        public String field;
        public ChildBean childBean;
        public ChildBean childSub;
        public ChildBean childValueTypeSub;
        public ChildInterface defaultChild;
        public ChildInterface anotherChild;
        public ChildInterface mapValueTypeChild;
        public ChildInterface listValueTypeChild;

        public Bean() {
        }

        public Bean(
                String field,
                ChildBean childBean,
                ChildBean childSub,
                ChildBean childValueTypeSub,
                ChildInterface defaultChild,
                ChildInterface anotherChild,
                ChildInterface mapValueTypeChild,
                ChildInterface listValueTypeChild) {
            this.field = field;
            this.childBean = childBean;
            this.childSub = childSub;
            this.childValueTypeSub = childValueTypeSub;
            this.defaultChild = defaultChild;
            this.anotherChild = anotherChild;
            this.mapValueTypeChild = mapValueTypeChild;
            this.listValueTypeChild = listValueTypeChild;
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
    @JsonTypeResolver(IonAnnotationStdTypeResolverBuilder.class)
    @JsonTypeName("ChildA")
    @JsonSubTypes({
        @Type(ChildBeanSub.class),
        @Type(ChildBeanValueTypeSub.class)
    })
    static class ChildBean {
        public String someField;

        public ChildBean() {
        }

        public ChildBean(String someField) {
            this.someField = someField;
        }
    }

    @JsonTypeName("ChildB")
    static class ChildBeanSub extends ChildBean {
        public String extraField;

        public ChildBeanSub() {
        }

        public ChildBeanSub(String someField, String extraField) {
            super(someField);
            this.extraField = extraField;
        }
    }
    
    @JsonTypeName("ChildC")
    static class ChildBeanValueTypeSub extends ChildBean {

        @JsonCreator
        public ChildBeanValueTypeSub(String someField) {
            super(someField);
        }
        
        @JsonValue
        public String getSomeField() {
            return someField;
        }
    }

    @JsonTypeInfo(
            use = JsonTypeInfo.Id.MINIMAL_CLASS,
            defaultImpl = DefaultChild.class)
    @JsonTypeResolver(IonAnnotationStdTypeResolverBuilder.class)
    static interface ChildInterface {
    }

    static class DefaultChild implements ChildInterface {
        public String thisIsAField;

        public DefaultChild() {
        }

        public DefaultChild(String someField) {
            this.thisIsAField = someField;
        }
    }

    static class AnotherChild implements ChildInterface {
        public String thisIsAnotherField;

        public AnotherChild() {
        }

        public AnotherChild(String someField) {
            this.thisIsAnotherField = someField;
        }
    }
    
    static class MapValueTypeChild implements ChildInterface {
        public Map<String, Integer> mapField;
        
        @JsonCreator
        public MapValueTypeChild(Map<String, Integer> mapField) {
            this.mapField = mapField;
        }
        
        @JsonValue
        public Map<String, Integer> getMapField() {
            return mapField;
        }
    }
    
    static class ListValueTypeChild implements ChildInterface {
        public List<Integer> listField;
        
        @JsonCreator
        public ListValueTypeChild(List<Integer> listField) {
            this.listField = listField;
        }
        
        @JsonValue
        public List<Integer> getMapField() {
            return listField;
        }
    }

    static final class DefaultBean {
        public ChildInterface defaultChild;
    }

}
