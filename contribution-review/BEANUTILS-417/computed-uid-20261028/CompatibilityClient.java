/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy at https://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 */
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.beanutils.DynaProperty;

/** Supplemental compatibility probe. Missing subclass UIDs are deliberate. */
public final class CompatibilityClient {
    private static class DirectProperty extends DynaProperty {
        private final int value;
        private final String label;
        DirectProperty(String name, Class<?> type, Class<?> content, int value, String label) {
            super(name, type, content);
            this.value = value;
            this.label = label;
        }
        void verify(String expectedName, Class<?> expectedType, Class<?> expectedContent,
                    int expectedValue, String expectedLabel) {
            require(expectedName.equals(name), "protected name");
            require(type == expectedType, "protected type");
            require(contentType == expectedContent, "protected contentType");
            require(value == expectedValue, "subclass integer");
            require(expectedLabel.equals(label), "subclass string");
        }
    }
    private static final class LeafProperty extends DirectProperty {
        private final long leafValue;
        LeafProperty() {
            super("leaf", Map.class, Integer.TYPE, 22, "leaf-label");
            leafValue = 0x123456789abcdefL;
        }
        void verifyLeaf() {
            verify("leaf", Map.class, Integer.TYPE, 22, "leaf-label");
            require(leafValue == 0x123456789abcdefL, "leaf state");
        }
    }
    private static final class CustomProperty extends DynaProperty {
        private final int value;
        private transient boolean callbackRead;
        CustomProperty() {
            super("custom", int[].class);
            value = 33;
        }
        private void writeObject(ObjectOutputStream out) throws IOException {
            out.defaultWriteObject();
            out.writeInt(0x13579bdf);
        }
        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            require(in.readInt() == 0x13579bdf, "custom trailing data");
            callbackRead = true;
        }
        void verifyCustom() {
            require("custom".equals(name), "custom protected name");
            require(type == int[].class && contentType == Integer.TYPE, "custom array metadata");
            require(value == 33 && callbackRead, "custom fields and read callback");
        }
    }
    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
    private static Class<?>[] classes() {
        return new Class<?>[]{DirectProperty.class, LeafProperty.class, CustomProperty.class};
    }
    private static Properties uids() throws Exception {
        Properties result = new Properties();
        for (Class<?> type : classes()) {
            try {
                type.getDeclaredField("serialVersionUID");
                throw new AssertionError("Subclass unexpectedly declares a UID: " + type.getName());
            } catch (NoSuchFieldException expected) {
                // This is the point of this supplemental check.
            }
            result.setProperty(type.getName(), Long.toString(ObjectStreamClass.lookup(type).getSerialVersionUID()));
        }
        require(ObjectStreamClass.lookup(DynaProperty.class).getSerialVersionUID() == 1L, "DynaProperty UID");
        return result;
    }
    private static Object[] graph() {
        DirectProperty direct = new DirectProperty("direct", List.class, String.class, 11, "direct-label");
        return new Object[]{direct, new LeafProperty(), new CustomProperty(), direct};
    }
    private static void write(Path path, Object[] graph) throws IOException {
        try (ObjectOutputStream output = new ObjectOutputStream(Files.newOutputStream(path))) {
            output.writeObject(graph);
        }
    }
    private static Object[] read(Path path) throws Exception {
        try (ObjectInputStream input = new ObjectInputStream(Files.newInputStream(path))) {
            Object[] result = (Object[]) input.readObject();
            require(input.read() == -1, "no trailing stream bytes");
            return result;
        }
    }
    private static void verify(Object[] value, String phase) {
        require(value.length == 4 && value[0] == value[3], "shared reference identity");
        require(value[0].getClass() == DirectProperty.class, "direct runtime class");
        ((DirectProperty)value[0]).verify("direct", List.class, String.class, 11, "direct-label");
        System.out.println("CASE PASS " + phase + " direct-default-uid");
        require(value[1].getClass() == LeafProperty.class, "leaf runtime class");
        ((LeafProperty)value[1]).verifyLeaf();
        System.out.println("CASE PASS " + phase + " multilevel-default-uid");
        require(value[2].getClass() == CustomProperty.class, "custom runtime class");
        ((CustomProperty)value[2]).verifyCustom();
        System.out.println("CASE PASS " + phase + " custom-callback-default-uid");
    }
    public static void main(String[] args) throws Exception {
        require(args.length >= 3, "usage: write|read|rewrite stream uid-properties [rewritten-stream]");
        Path stream = Paths.get(args[1]);
        Path uidFile = Paths.get(args[2]);
        Properties current = uids();
        for (String name : new TreeSet<String>(current.stringPropertyNames())) {
            System.out.println("UID " + name + "=" + current.getProperty(name));
        }
        System.out.println("DYNA_SUPER " + DynaProperty.class.getSuperclass().getName());
        if ("write".equals(args[0])) {
            try (OutputStream out = Files.newOutputStream(uidFile)) { current.store(out, "Computed subclass UIDs"); }
            write(stream, graph());
            System.out.println("WRITE OK " + stream.getFileName());
            return;
        }
        Properties expected = new Properties();
        try (InputStream input = Files.newInputStream(uidFile)) { expected.load(input); }
        require(current.equals(expected), "computed UIDs unchanged from baseline");
        Object[] restored = read(stream);
        verify(restored, "input");
        if ("rewrite".equals(args[0])) {
            require(args.length == 4, "rewritten path");
            Path rewritten = Paths.get(args[3]);
            write(rewritten, restored);
            Object[] reread = read(rewritten);
            require(restored[0] != reread[0], "fresh rewritten object");
            verify(reread, "rewritten");
        } else { require("read".equals(args[0]), "recognized mode"); }
    }
}
