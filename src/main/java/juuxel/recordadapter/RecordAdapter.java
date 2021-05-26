/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package juuxel.recordadapter;

import blue.endless.jankson.Comment;
import blue.endless.jankson.Jankson;
import blue.endless.jankson.JsonElement;
import blue.endless.jankson.JsonObject;
import blue.endless.jankson.annotation.SerializedName;
import blue.endless.jankson.api.DeserializationException;
import blue.endless.jankson.api.DeserializerFunction;
import blue.endless.jankson.api.Marshaller;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * A record (de)serialiser for {@link Jankson}.
 *
 * <p>Records are serialised as {@linkplain JsonObject JSON objects} with properties matching the components.
 * Each property is converted to/from JSON using the same {@link Marshaller}. The property names are
 * pulled from {@link SerializedName} annotations if present, and otherwise the name matches the component's name.
 *
 * <p>The order of the components in the serialised JSON object
 * is the same as in the record definition. This does not affect deserialisation,
 * which works in any order.
 *
 * <p>Automatic comments can be added by applying the {@link Comment} annotation
 * on the record components.
 *
 * <p>The accessors and constructor of the record must be public – {@code RecordAdapter} will not
 * use any access hacks.
 *
 * @param <R> the record type; cannot be {@link Record} itself
 */
public final class RecordAdapter<R extends Record> implements BiFunction<R, Marshaller, JsonElement>, DeserializerFunction<JsonObject, R> {
    private static final Map<Class<? extends Record>, RecordAdapter<?>> CACHE = new HashMap<>();

    private final Class<R> type;
    private final List<ComponentData> components;
    private final MethodHandle constructor;

    private RecordAdapter(Class<R> type) {
        this.type = type;

        var lookup = MethodHandles.lookup();
        RecordComponent[] rawComponents = type.getRecordComponents();
        Class<?>[] componentTypes = new Class[rawComponents.length];
        Map<String, ComponentData> namesToData = new HashMap<>(rawComponents.length); // for checking for name conflicts
        List<ComponentData> components = new ArrayList<>();

        for (int i = 0; i < rawComponents.length; i++) {
            var component = rawComponents[i];
            componentTypes[i] = rawComponents[i].getType();

            try {
                @Nullable Comment comment = getComponentAnnotation(component, Comment.class);
                @Nullable SerializedName serializedName = getComponentAnnotation(component, SerializedName.class);
                String name = serializedName != null ? serializedName.value() : component.getName();

                ComponentData data = new ComponentData(component, name, lookup.unreflect(component.getAccessor()), comment);
                ComponentData existing;

                components.add(data);
                if ((existing = namesToData.put(name, data)) != null) {
                    throw new IllegalArgumentException(type + " has multiple components with serialised name '" + name + "': " + existing.component() + ", " + component);
                }
            } catch (IllegalAccessException e) {
                throw new IllegalArgumentException("Could not access the accessor method of " + component, e);
            }
        }

        this.components = Collections.unmodifiableList(components);

        try {
            Constructor<R> ctor = type.getConstructor(componentTypes);
            constructor = lookup.unreflectConstructor(ctor);
        } catch (NoSuchMethodException e) {
            throw new NoSuchElementException("Could not find constructor of " + type, e);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("Could not access constructor of " + type, e);
        }
    }

    private static <A extends Annotation> @Nullable A getComponentAnnotation(RecordComponent component, Class<A> annotationType) {
        @Nullable A annotation = component.getAnnotation(annotationType);

        // fall back to fields if missing
        if (annotation == null) {
            try {
                Field backing = component.getDeclaringRecord().getDeclaredField(component.getName());
                annotation = backing.getAnnotation(annotationType);
            } catch (NoSuchFieldException e) {
                throw new NoSuchElementException("Could not find the backing field of " + component, e);
            }
        }

        return annotation;
    }

    /**
     * Gets a record adapter for the provided record type.
     *
     * @param type the record class
     * @param <R>  the record type
     * @return the record adapter for {@code R}
     * @throws NullPointerException     if the class is null
     * @throws IllegalArgumentException if the class is not a record
     * @throws IllegalArgumentException if the accessor methods or the constructor cannot be accessed
     * @throws IllegalArgumentException if there are multiple components with the same serialised name
     */
    @SuppressWarnings("unchecked")
    public static <R extends Record> RecordAdapter<R> of(Class<R> type) {
        Objects.requireNonNull(type, "record type");
        if (!type.isRecord()) throw new IllegalArgumentException(type + " is not a record!");

        if (CACHE.containsKey(type)) {
            return (RecordAdapter<R>) CACHE.get(type);
        } else {
            var adapter = new RecordAdapter<>(type);
            CACHE.put(type, adapter);
            return adapter;
        }
    }

    /**
     * Registers a record adapter as a (de)serialiser to a {@link Jankson.Builder}.
     *
     * @param builder the target builder
     * @param type    the record class
     * @param <R>     the record type
     * @return the target builder
     * @throws NullPointerException     if the builder or the class is null
     * @throws IllegalArgumentException if the class is not a record
     * @throws IllegalArgumentException if the accessor methods or the constructor cannot be accessed
     * @throws IllegalArgumentException if there are multiple components with the same serialised name
     */
    public static <R extends Record> Jankson.Builder registerFor(Jankson.Builder builder, Class<R> type) {
        Objects.requireNonNull(builder, "jankson builder");
        RecordAdapter<R> adapter = of(type);
        return builder.registerSerializer(type, adapter).registerDeserializer(JsonObject.class, type, adapter);
    }

    /**
     * Deserialises a record from a JSON object.
     *
     * @param json the JSON object
     * @param m    the marshaller used for deserialising the components
     * @return the deserialised instance of {@code R}
     * @throws DeserializationException if a component is missing or the record could not be constructed
     */
    @SuppressWarnings("unchecked")
    @Override
    public R apply(JsonObject json, Marshaller m) throws DeserializationException {
        var values = new ArrayList<>(components.size());

        for (var data : components) {
            if (!json.containsKey(data.name())) {
                throw new DeserializationException("Missing key for " + data.component());
            }

            values.add(m.marshall(data.component().getType(), json.get(data.name())));
        }

        try {
            return (R) constructor.invokeWithArguments(values);
        } catch (Throwable t) {
            throw new DeserializationException("Could not construct " + type, t);
        }
    }

    /**
     * Serialises the provided record instance.
     *
     * @param r          the record instance; cannot be null
     * @param marshaller the marshaller used for serialising the record components
     * @return the JSON representation of the record
     */
    @Override
    public JsonElement apply(R r, Marshaller marshaller) {
        JsonObject json = new JsonObject();

        for (var data : components) {
            try {
                json.put(data.name(), marshaller.serialize(data.accessor().invoke(r)));

                @Nullable Comment comment = data.comment();
                if (comment != null) {
                    json.setComment(data.name(), comment.value());
                }
            } catch (Throwable t) {
                throw new RuntimeException("Could not serialise " + r, t);
            }
        }

        return json;
    }

    private record ComponentData(RecordComponent component, String name, MethodHandle accessor, @Nullable Comment comment) {
    }
}
