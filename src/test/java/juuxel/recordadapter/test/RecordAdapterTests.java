/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package juuxel.recordadapter.test;

import blue.endless.jankson.Jankson;
import blue.endless.jankson.JsonElement;
import blue.endless.jankson.JsonNull;
import blue.endless.jankson.JsonObject;
import blue.endless.jankson.JsonPrimitive;
import blue.endless.jankson.api.DeserializationException;
import blue.endless.jankson.api.SyntaxError;
import juuxel.recordadapter.RecordAdapter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class RecordAdapterTests {
    private static <A> A make(A a, Consumer<? super A> configurator) {
        configurator.accept(a);
        return a;
    }

    private final Jankson jankson = make(Jankson.builder(), builder -> {
        RecordAdapter.registerFor(builder, TestRecords.Basket.class);
        RecordAdapter.registerFor(builder, TestRecords.Vegetable.class);
    }).build();

    @Test
    void serialise() {
        JsonObject expected = make(new JsonObject(), basket -> {
            basket.put("vegetable", make(new JsonObject(), vegetable -> {
                vegetable.put("name", new JsonPrimitive("onion"));
                vegetable.put("colour", new JsonPrimitive("purple"));
                vegetable.put("long name", new JsonPrimitive("red onion"));
            }));
            basket.put("count", new JsonPrimitive(10));
            basket.put("mass", JsonNull.INSTANCE, "the total mass of this basket");
        });

        TestRecords.Basket basket = new TestRecords.Basket(
            new TestRecords.Vegetable("onion", "purple", "red onion"),
            10,
            null
        );

        JsonElement actual = jankson.toJson(basket);

        assertThat(actual)
            .isInstanceOf(JsonObject.class)
            .isEqualTo(expected);

        // check key order
        assertThat(new ArrayList<>(((JsonObject) actual).entrySet()))
            .describedAs("the entry set of the JSON object as a list")
            .map(Map.Entry::getKey)
            .isEqualTo(List.of("vegetable", "count", "mass"));
    }

    @Test
    void deserialise() throws SyntaxError, DeserializationException {
        TestRecords.Basket expected = new TestRecords.Basket(
            new TestRecords.Vegetable("potato", "brown", "lil tater"),
            5,
            1.23
        );

        String json = """
            {
              "vegetable": { "name": "potato", "colour": "brown", "long name": "lil tater" },
              "count": 5,
              // the total mass of this basket
              "mass": 1.23
            }
            """;

        assertThat(jankson.fromJsonCarefully(json, TestRecords.Basket.class)).isEqualTo(expected);
    }

    @Test
    void nullTypeThrows() {
        assertThatThrownBy(() -> RecordAdapter.of(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullBuilderThrows() {
        assertThatThrownBy(() -> RecordAdapter.registerFor(null, TestRecords.Basket.class))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void usingRecordSuperclassThrows() {
        assertThatThrownBy(() -> RecordAdapter.of(Record.class))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void usingNonRecordClassThrows() {
        assertThatThrownBy(() -> RecordAdapter.of((Class) String.class))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void inaccessibleThrows() {
        assertThatThrownBy(() -> RecordAdapter.of(TestRecords.Inaccessible.class))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void duplicateNameThrows() {
        assertThatThrownBy(() -> RecordAdapter.of(TestRecords.DuplicateSerialisedNames.class))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
