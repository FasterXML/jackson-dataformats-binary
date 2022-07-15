package com.fasterxml.jackson.dataformat.ion.polymorphism;

import com.amazon.ion.IonValue;
import tools.jackson.core.Version;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.cfg.MapperConfig;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;
import tools.jackson.databind.jsontype.TypeIdResolver;
import tools.jackson.databind.jsontype.impl.ClassNameIdResolver;
import tools.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.ion.IonObjectMapper;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 * This test checks that {@link IonAnnotationTypeDeserializer} with {@link IonAnnotationIntrospector} expecting class
 * name can still deserialize regular Json/Ion (specifically ION without class name) payloads.
 *
 * @author Binh Tran
 */
public class IonAnnotationTypeDeserializerWithClassNameAnnotationTest {

    private static IonValue ionValueWithoutAnnotation;
    private static IonValue ionValueWithAnnotation;

    IonObjectMapper mapperUnderTest;

    @BeforeClass
    public static void setupClass() throws IOException {
        ClassA inner = new ClassA();
        inner.value = 42;

        ClassB<ClassA> outer = new ClassB<>();
        outer.content = inner;

        IonObjectMapper mapper = new IonObjectMapper();
        ionValueWithoutAnnotation = mapper.writeValueAsIonValue(outer);

        mapper = constructIomWithClassNameIdResolver();
        ionValueWithAnnotation = mapper.writeValueAsIonValue(outer);
    }

    @Before
    public void setup() {
        // Important: since Jackson caches type resolving information, we need to create a separate mapper for testing.
        mapperUnderTest = constructIomWithClassNameIdResolver();
    }

    @Test
    public void testDeserializeAnnotatedPayload() throws IOException {
        IonObjectMapper mapper = constructIomWithClassNameIdResolver();

        ClassB<ClassA> newObj = mapper.readValue(ionValueWithAnnotation, new TypeReference<ClassB<ClassA>>() {});

        ClassA content = newObj.content;
        assertEquals(42, content.value);
    }

    @Test
    public void testDeserializeNonAnnotatedPayload() throws IOException {
        IonObjectMapper mapper = constructIomWithClassNameIdResolver();

        ClassB<ClassA> newObj = mapper.readValue(ionValueWithoutAnnotation, new TypeReference<ClassB<ClassA>>() {});

        ClassA content = newObj.content;
        assertEquals(42, content.value);
    }

    private static IonObjectMapper constructIomWithClassNameIdResolver() {
        return IonObjectMapper.builder()
                // 18-Dec-2019, tatu: not sure if this is needed separately but...
                .addModule(new IonAnnotationModule())
                .build();
    }

    // Helper classes to reproduce the issue.

    static class IonAnnotationModule extends SimpleModule {
        private static final long serialVersionUID = 3018097049612590165L;

        IonAnnotationModule() {
            super("IonAnnotationMod", Version.unknownVersion());
        }

        @Override
        public void setupModule(SetupContext context) {
            IonAnnotationIntrospector introspector = new ClassNameIonAnnotationIntrospector();
            context.appendAnnotationIntrospector(introspector);
        }
    }

    static class ClassNameIonAnnotationIntrospector extends IonAnnotationIntrospector {
        private static final long serialVersionUID = -5519199636013243472L;

        ClassNameIonAnnotationIntrospector() {
            super(true);
        }

        @Override
        protected TypeIdResolver defaultIdResolver(MapperConfig<?> config, JavaType baseType,
                PolymorphicTypeValidator ptv) {
            return new ClassNameIdResolver(baseType, ptv);
        }
    }

    static class ClassA {
        public int value;
    }

    static class ClassB<T> {
        public T content;
    }
}
