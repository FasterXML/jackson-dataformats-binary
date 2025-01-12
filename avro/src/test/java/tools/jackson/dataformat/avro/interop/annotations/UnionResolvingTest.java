package tools.jackson.dataformat.avro.interop.annotations;

import java.io.IOException;
import java.util.*;

import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.reflect.Union;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static tools.jackson.dataformat.avro.interop.ApacheAvroInteropUtil.jacksonDeserialize;
import static tools.jackson.dataformat.avro.interop.ApacheAvroInteropUtil.jacksonSerialize;

public class UnionResolvingTest {
    private Schema SCHEMA = SchemaBuilder
        .builder(UnionResolvingTest.class.getName() + "$")
        .record(Box.class.getSimpleName())
            .fields()
            .name("animal")
            .type()
            .unionOf()
                .record(Cat.class.getSimpleName())
                    .fields()
                    .name("name").type().stringType().noDefault()
                .endRecord()
            .and()
                .record(Dog.class.getSimpleName())
                .fields()
                    .name("tricks").type().array().items().stringType().noDefault()
                .endRecord()
            .endUnion()
            .noDefault()
        .endRecord();


    @Union({ Cat.class, Dog.class })
    public interface Animal { }

    static final class Cat implements Animal {
        public Cat() {}

        @Override
        public boolean equals(Object o) {
            return o instanceof Cat;
        }
    }

    static final class Dog implements Animal {
        // Unsupported schema generation
        public Set<?> tricks = new HashSet<>();

        public Dog() {
        }

        public Dog(final Set<?> tricks) {
            this.tricks = tricks;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Dog && tricks.equals(((Dog) o).tricks);
        }
    }

    public static class Box {
        public Animal animal;

        public Box() {
        }

        public Box(Animal a) { animal = a; }

        @Override
        public boolean equals(Object o) {
            return o instanceof Box && Objects.equals(animal, ((Box) o).animal);
        }
    }

    @Test
    public void testResolveUnionUsingSchemaName() throws IOException {
        final Box box = new Box(new Dog(Collections.singleton("catch stick")));

        final Box result = jacksonDeserialize(SCHEMA, Box.class, jacksonSerialize(SCHEMA, box));

        assertThat(result).isEqualTo(box);
    }
}
