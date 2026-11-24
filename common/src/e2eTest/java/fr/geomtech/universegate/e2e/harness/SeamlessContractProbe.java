package fr.geomtech.universegate.e2e.harness;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.opentest4j.AssertionFailedError;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

/**
 * Genuine reflection and MethodHandles contract probe for opaque-box E2E tests.
 * Never skips tests via Assumptions; missing or mismatched contracts always fail
 * loudly with {@link AssertionFailedError}.
 */
public final class SeamlessContractProbe {

    private SeamlessContractProbe() {}

    public static Class<?> requireClass(String fqcn) {
        return requireClass(fqcn, true);
    }

    public static Class<?> requireClass(String fqcn, boolean initialize) {
        if (fqcn == null || fqcn.isBlank()) {
            throw new AssertionFailedError("Expected non-blank FQCN");
        }
        try {
            return Class.forName(fqcn, initialize, SeamlessContractProbe.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            throw new AssertionFailedError("Required contract class not available: " + fqcn + " (" + e + ")", e);
        }
    }

    public static <T> Class<? extends T> requireAssignableClass(String fqcn, Class<T> expectedSupertype) {
        Objects.requireNonNull(expectedSupertype, "expectedSupertype");
        Class<?> cls = requireClass(fqcn);
        if (!expectedSupertype.isAssignableFrom(cls)) {
            throw new AssertionFailedError(
                    "Class " + fqcn + " is not assignable to " + expectedSupertype.getName()
            );
        }
        return cls.asSubclass(expectedSupertype);
    }

    public static Class<?> requireRecord(String fqcn, String... expectedComponentNames) {
        Class<?> cls = requireClass(fqcn);
        if (!cls.isRecord()) {
            throw new AssertionFailedError("Expected record type, but found non-record class: " + fqcn);
        }
        if (expectedComponentNames != null && expectedComponentNames.length > 0) {
            RecordComponent[] components = cls.getRecordComponents();
            String[] actualNames = Arrays.stream(components)
                    .map(RecordComponent::getName)
                    .toArray(String[]::new);
            if (!Arrays.equals(expectedComponentNames, actualNames)) {
                throw new AssertionFailedError(
                        "Record " + fqcn + " component mismatch: expected "
                                + Arrays.toString(expectedComponentNames)
                                + " but got " + Arrays.toString(actualNames)
                );
            }
        }
        return cls;
    }

    public static Field requireField(Class<?> owner, String fieldName, Class<?> expectedType) {
        if (owner == null || fieldName == null || expectedType == null) {
            throw new AssertionFailedError("owner, fieldName, and expectedType must be non-null");
        }
        Field field = findField(owner, fieldName);
        if (field == null) {
            throw new AssertionFailedError("Missing field " + owner.getName() + "#" + fieldName);
        }
        if (!isTypeCompatible(expectedType, field.getType())) {
            throw new AssertionFailedError(
                    "Field type mismatch for " + owner.getName() + "#" + fieldName
                            + ": expected " + expectedType.getName()
                            + " but found " + field.getType().getName()
            );
        }
        if (!field.trySetAccessible()) {
            throw new AssertionFailedError(
                    "Unable to make field accessible: " + owner.getName() + "#" + fieldName
            );
        }
        return field;
    }

    public static Field requireStaticField(Class<?> owner, String fieldName, Class<?> expectedType) {
        Field field = requireField(owner, fieldName, expectedType);
        if (!Modifier.isStatic(field.getModifiers())) {
            throw new AssertionFailedError(
                    "Expected static field, but found instance field: " + owner.getName() + "#" + fieldName
            );
        }
        return field;
    }

    public static <T> T readStaticField(Class<?> owner, String fieldName, Class<T> expectedType) {
        Field field = requireStaticField(owner, fieldName, expectedType);
        try {
            return castValue(expectedType, field.get(null));
        } catch (IllegalAccessException e) {
            throw new AssertionFailedError(
                    "Failed to read static field " + owner.getName() + "#" + fieldName, e
            );
        }
    }

    public static <T> T readInstanceField(Object target, String fieldName, Class<T> expectedType) {
        if (target == null) {
            throw new AssertionFailedError("Cannot read instance field " + fieldName + " from null target");
        }
        Field field = requireField(target.getClass(), fieldName, expectedType);
        if (Modifier.isStatic(field.getModifiers())) {
            throw new AssertionFailedError(
                    "Expected instance field, but found static field: "
                            + target.getClass().getName() + "#" + fieldName
            );
        }
        try {
            return castValue(expectedType, field.get(target));
        } catch (IllegalAccessException e) {
            throw new AssertionFailedError(
                    "Failed to read instance field " + target.getClass().getName() + "#" + fieldName, e
            );
        }
    }

    public static <T> T newInstance(Class<T> owner, Class<?>[] paramTypes, Object... args) {
        if (owner == null) {
            throw new AssertionFailedError("owner must be non-null");
        }
        Class<?>[] safeParamTypes = paramTypes == null ? new Class<?>[0] : paramTypes;
        Object[] safeArgs = args == null ? new Object[0] : args;
        try {
            Constructor<T> ctor = owner.getDeclaredConstructor(safeParamTypes);
            if (!ctor.trySetAccessible()) {
                throw new AssertionFailedError(
                        "Unable to make constructor accessible on " + owner.getName()
                );
            }
            return ctor.newInstance(safeArgs);
        } catch (NoSuchMethodException e) {
            throw new AssertionFailedError(
                    "Missing constructor on " + owner.getName()
                            + " with params " + Arrays.toString(safeParamTypes),
                    e
            );
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof AssertionFailedError afe) {
                throw afe;
            }
            throw new AssertionFailedError(
                    "Constructor invocation failed on " + owner.getName() + ": " + cause,
                    cause
            );
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException e) {
            throw new AssertionFailedError(
                    "Failed to instantiate " + owner.getName() + ": " + e.getMessage(),
                    e
            );
        }
    }

    public static MethodHandle requireStaticMethod(
            Class<?> owner,
            String name,
            Class<?> returnType,
            Class<?>... paramTypes
    ) {
        Method method = resolveMethod(owner, name, returnType, paramTypes);
        if (!Modifier.isStatic(method.getModifiers())) {
            throw new AssertionFailedError(
                    "Expected static method, but found instance method: " + owner.getName() + "#" + name
            );
        }
        return unreflect(method);
    }

    public static MethodHandle requireInstanceMethod(
            Class<?> owner,
            String name,
            Class<?> returnType,
            Class<?>... paramTypes
    ) {
        Method method = resolveMethod(owner, name, returnType, paramTypes);
        if (Modifier.isStatic(method.getModifiers())) {
            throw new AssertionFailedError(
                    "Expected instance method, but found static method: " + owner.getName() + "#" + name
            );
        }
        return unreflect(method);
    }

    public static <T> T invokeStatic(
            Class<?> owner,
            String name,
            Class<T> returnType,
            Class<?>[] paramTypes,
            Object... args
    ) {
        MethodHandle handle = requireStaticMethod(owner, name, returnType, paramTypes);
        Object[] safeArgs = args == null ? new Object[0] : args;
        try {
            Object result = handle.invokeWithArguments(safeArgs);
            return castValue(returnType, result);
        } catch (AssertionFailedError afe) {
            throw afe;
        } catch (Throwable t) {
            throw new AssertionFailedError(
                    "Static method invocation failed for " + owner.getName() + "#" + name + ": " + t,
                    t
            );
        }
    }

    public static <T> T invokeInstance(
            Object target,
            String name,
            Class<T> returnType,
            Class<?>[] paramTypes,
            Object... args
    ) {
        if (target == null) {
            throw new AssertionFailedError("Cannot invoke instance method " + name + " on null target");
        }
        MethodHandle handle = requireInstanceMethod(target.getClass(), name, returnType, paramTypes);
        Object[] safeArgs = args == null ? new Object[0] : args;
        try {
            Object result = handle.bindTo(target).invokeWithArguments(safeArgs);
            return castValue(returnType, result);
        } catch (AssertionFailedError afe) {
            throw afe;
        } catch (Throwable t) {
            throw new AssertionFailedError(
                    "Instance method invocation failed for "
                            + target.getClass().getName() + "#" + name + ": " + t,
                    t
            );
        }
    }

    @SuppressWarnings("unchecked")
    public static <B extends ByteBuf, V> StreamCodec<B, V> requireStreamCodec(Class<?> owner, String fieldName) {
        StreamCodec<?, ?> codec = readStaticField(owner, fieldName, StreamCodec.class);
        if (codec == null) {
            throw new AssertionFailedError("StreamCodec field " + owner.getName() + "#" + fieldName + " was null");
        }
        return (StreamCodec<B, V>) codec;
    }

    public static <V> V roundTripByteBufCodec(StreamCodec<? super RegistryFriendlyByteBuf, V> codec, V value) {
        if (codec == null) {
            throw new AssertionFailedError("codec must be non-null");
        }
        RegistryFriendlyByteBuf buf = E2eBootstrap.newRegistryFriendlyByteBuf();
        try {
            codec.encode(buf, value);
            V decoded = codec.decode(buf);
            if (buf.readableBytes() != 0) {
                throw new AssertionFailedError(
                        "StreamCodec left " + buf.readableBytes() + " unread bytes after decode"
                );
            }
            return decoded;
        } finally {
            buf.release();
        }
    }

    private static Field findField(Class<?> owner, String fieldName) {
        Class<?> current = owner;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static Method resolveMethod(
            Class<?> owner,
            String name,
            Class<?> returnType,
            Class<?>... paramTypes
    ) {
        if (owner == null || name == null || returnType == null) {
            throw new AssertionFailedError("owner, name, and returnType must be non-null");
        }
        Class<?>[] safeParamTypes = paramTypes == null ? new Class<?>[0] : paramTypes;
        Method method = findMethod(owner, name, safeParamTypes);
        if (method == null) {
            throw new AssertionFailedError(
                    "Missing method " + owner.getName() + "#" + name
                            + Arrays.toString(safeParamTypes)
            );
        }
        if (!isTypeCompatible(returnType, method.getReturnType())) {
            throw new AssertionFailedError(
                    "Return type mismatch for " + owner.getName() + "#" + name
                            + ": expected " + returnType.getName()
                            + " but found " + method.getReturnType().getName()
            );
        }
        if (!method.trySetAccessible()) {
            throw new AssertionFailedError(
                    "Unable to make method accessible: " + owner.getName() + "#" + name
            );
        }
        return method;
    }

    private static Method findMethod(Class<?> owner, String name, Class<?>[] paramTypes) {
        Class<?> current = owner;
        while (current != null) {
            try {
                return current.getDeclaredMethod(name, paramTypes);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        Queue<Class<?>> interfaces = new ArrayDeque<>();
        Set<Class<?>> visited = new HashSet<>();
        enqueueInterfaces(owner, interfaces, visited);
        while (!interfaces.isEmpty()) {
            Class<?> iface = interfaces.poll();
            try {
                return iface.getDeclaredMethod(name, paramTypes);
            } catch (NoSuchMethodException ignored) {
                enqueueInterfaces(iface, interfaces, visited);
            }
        }
        return null;
    }

    private static void enqueueInterfaces(Class<?> type, Queue<Class<?>> queue, Set<Class<?>> visited) {
        Class<?> current = type;
        while (current != null) {
            for (Class<?> iface : current.getInterfaces()) {
                if (visited.add(iface)) {
                    queue.add(iface);
                }
            }
            current = current.getSuperclass();
        }
    }

    private static MethodHandle unreflect(Method method) {
        Class<?> declaringClass = method.getDeclaringClass();
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(declaringClass, MethodHandles.lookup());
            return lookup.unreflect(method);
        } catch (IllegalAccessException e) {
            try {
                return MethodHandles.lookup().unreflect(method);
            } catch (IllegalAccessException fallbackEx) {
                throw new AssertionFailedError(
                        "Failed to unreflect method " + declaringClass.getName() + "#" + method.getName()
                                + ": " + fallbackEx.getMessage(),
                        fallbackEx
                );
            }
        }
    }

    private static boolean isTypeCompatible(Class<?> expected, Class<?> actual) {
        if (expected == actual) {
            return true;
        }
        if (expected.isPrimitive() || actual.isPrimitive()) {
            return box(expected) == box(actual);
        }
        return expected.isAssignableFrom(actual);
    }

    @SuppressWarnings("unchecked")
    private static <T> T castValue(Class<T> expectedType, Object value) {
        if (expectedType == void.class || expectedType == Void.class) {
            return null;
        }
        if (value == null) {
            if (expectedType.isPrimitive()) {
                throw new AssertionFailedError(
                        "Expected primitive " + expectedType.getName() + " value but got null"
                );
            }
            return null;
        }
        Class<?> target = box(expectedType);
        try {
            return (T) target.cast(value);
        } catch (ClassCastException e) {
            throw new AssertionFailedError(
                    "Value of type " + value.getClass().getName()
                            + " is not compatible with expected type " + expectedType.getName(),
                    e
            );
        }
    }

    private static Class<?> box(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == char.class) return Character.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == void.class) return Void.class;
        return type;
    }
}
