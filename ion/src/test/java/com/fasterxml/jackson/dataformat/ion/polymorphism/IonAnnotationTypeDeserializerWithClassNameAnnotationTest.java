package com.fasterxml.jackson.dataformat.ion.polymorphism;

import java.io.IOException;

import com.amazon.ion.IonValue;
import org.junit.jupiter.api.*;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;
import com.fasterxml.jackson.databind.jsontype.impl.ClassNameIdResolver;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.ion.IonObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    @BeforeAll
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

    @BeforeEach
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
        IonObjectMapper mapper = new IonObjectMapper();
        mapper.registerModule(new IonAnnotationModule());

        return mapper;
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
        protected TypeIdResolver defaultIdResolver(MapperConfig<?> config, JavaType baseType) {
            return new ClassNameIdResolver(baseType, config.getTypeFactory(), config.getPolymorphicTypeValidator());
        }
    }

    static class ClassA {
        public int value;
    }

    static class ClassB<T> {
        public T content;
    }
}
