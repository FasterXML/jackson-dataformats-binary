package com.fasterxml.jackson.dataformat.avro.interop.annotations;

import java.util.Arrays;
import java.util.List;

import org.apache.avro.reflect.Nullable;
import org.apache.avro.reflect.Union;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.dataformat.avro.interop.InteropTestBase;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import static org.assertj.core.api.Assertions.assertThat;

import static com.fasterxml.jackson.dataformat.avro.interop.ApacheAvroInteropUtil.jacksonDeserializer;

/**
 * Tests for @Union
 */
public class UnionTest extends InteropTestBase {

    @Union({ Cat.class, Dog.class })
    public interface Animal {

    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Cat implements Animal {

        @Nullable
        private String color;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Dog implements Animal {

        private int size;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Bird implements Animal {

        private boolean flying;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Cage {

        private Animal animal;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PetShop {

        public PetShop(Animal... pets) {
            this(Arrays.asList(pets));
        }

        private List<Animal> pets;
    }

    /*
     * Jackson deserializer doesn't understand native TypeIDs yet, so it can't handle deserialization of unions.
     */
    @Before
    public void ignoreJacksonDeserializer() {
        Assume.assumeTrue(deserializeFunctor != jacksonDeserializer);
    }

    @Test
    public void testInterfaceUnionWithCat() {
        Cage cage = new Cage(new Cat("test"));
        //
        Cage result = roundTrip(cage);
        //
        assertThat(result).isEqualTo(cage);
    }

    @Test
    public void testInterfaceUnionWithDog() {
        Cage cage = new Cage(new Dog(4));
        //
        Cage result = roundTrip(cage);
        //
        assertThat(result).isEqualTo(cage);
    }

    @Test(expected = Exception.class)
    public void testInterfaceUnionWithBird() {
        Cage cage = new Cage(new Bird(true));
        //
        roundTrip(cage);
    }

    @Test
    public void testListWithInterfaceUnion() {
        PetShop shop = new PetShop(new Cat("tabby"), new Dog(4), new Dog(5), new Cat("calico"));
        //
        PetShop result = roundTrip(shop);
        //
        assertThat(result).isEqualTo(shop);
    }

}
