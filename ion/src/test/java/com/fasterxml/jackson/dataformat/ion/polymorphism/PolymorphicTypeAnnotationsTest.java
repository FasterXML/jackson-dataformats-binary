package com.fasterxml.jackson.dataformat.ion.polymorphism;

import java.io.IOException;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.dataformat.ion.IonObjectMapper;
import com.fasterxml.jackson.dataformat.ion.IonParser.Feature;
import org.junit.Assert;
import org.junit.Test;

import com.amazon.ion.IonValue;
import com.amazon.ion.system.IonSystemBuilder;

public class PolymorphicTypeAnnotationsTest {
    private static final String SUBCLASS_TYPE_NAME = "subtype";

    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "base",
        visible = true
    )
    @JsonSubTypes({
        @Type(value = Subclass.class, name = SUBCLASS_TYPE_NAME),
    })
    static public class BaseClass {
    }


    public static class Subclass extends BaseClass {
        public String base;
    }


    public static class Container {
        public BaseClass objectWithType;
    }


    private static final IonValue CONTAINER_WITH_TYPED_OBJECT = asIonValue(
        "{" +
        "  objectWithType:type::" +
        "  {" +
        "    base:\"" + SUBCLASS_TYPE_NAME + "\"," +
        "  }" +
        "}");

    @Test
    public void testNativeTypeIdsDisabledReadsTypeAnnotationsSuccessfully() throws IOException {
        IonObjectMapper mapper = IonObjectMapper.builder()
                .disable(Feature.USE_NATIVE_TYPE_ID)
                .build();

        Container containerWithBaseClass = mapper.readValue(CONTAINER_WITH_TYPED_OBJECT, Container.class);

        Assert.assertTrue(containerWithBaseClass.objectWithType instanceof Subclass);
        Assert.assertEquals(SUBCLASS_TYPE_NAME, ((Subclass) containerWithBaseClass.objectWithType).base);
    }

    private static IonValue asIonValue(final String ionStr) {
        return IonSystemBuilder.standard()
            .build()
            .singleValue(ionStr);
    }
}
