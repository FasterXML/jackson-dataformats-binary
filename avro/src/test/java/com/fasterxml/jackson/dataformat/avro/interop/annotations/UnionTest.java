package com.fasterxml.jackson.dataformat.avro.interop.annotations;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.avro.UnresolvedUnionException;
import org.apache.avro.reflect.Nullable;
import org.apache.avro.reflect.Union;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.dataformat.avro.interop.InteropTestBase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

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

    @Test
    public void testInterfaceUnionWithCat() throws IOException {
        Cage cage = new Cage(new Cat("test"));
        //
        Cage result = roundTrip(cage);
        //
        assertThat(result).isEqualTo(cage);
    }

    @Test
    public void testInterfaceUnionWithDog() throws IOException {
        Cage cage = new Cage(new Dog(4));
        //
        Cage result = roundTrip(cage);
        //
        assertThat(result).isEqualTo(cage);
    }

    @Test
    public void testInterfaceUnionWithBird() throws IOException {
        Cage cage = new Cage(new Bird(true));
        //
        try {
            roundTrip(cage);
            fail("Should throw exception about Bird not being in union");
        } catch (UnresolvedUnionException | JsonMappingException e) {
            // success
        }
    }

    @Test
    public void testListWithInterfaceUnion() throws IOException {
        PetShop shop = new PetShop(new Cat("tabby"), new Dog(4), new Dog(5), new Cat("calico"));
        //
        PetShop result = roundTrip(shop);
        //
        assertThat(result).isEqualTo(shop);
    }

}
