import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.util.List;
import org.apache.commons.beanutils.DynaProperty;

/** Runtime compatibility check; compiled once against the unchanged 1.X baseline. */
public final class PrecompiledDynaPropertyClient {
    private static final class ExtendedProperty extends DynaProperty {
        private static final long serialVersionUID = 21L;
        private final int extraValue;

        private ExtendedProperty() {
            super("precompiled", List.class, String.class);
            extraValue = 42;
        }

        private void check() {
            require("precompiled".equals(name), "protected name");
            require(type == List.class, "protected type");
            require(contentType == String.class, "protected contentType");
            require(extraValue == 42, "subclass state");
        }
    }

    private static void require(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void main(final String[] args) throws Exception {
        require(ObjectStreamClass.lookup(DynaProperty.class).getSerialVersionUID() == 1L, "DynaProperty UID");
        require(new DynaProperty("untyped").getType() == Object.class, "one-argument constructor");
        require(new DynaProperty("primitive", Integer.TYPE).getType() == Integer.TYPE, "two-argument constructor");
        final ExtendedProperty original;
        if ("write".equals(args[0])) {
            original = new ExtendedProperty();
            original.check();
            try (ObjectOutputStream output = new ObjectOutputStream(new FileOutputStream(args[1]))) {
                output.writeObject(original);
            }
        } else {
            try (ObjectInputStream input = new ObjectInputStream(new FileInputStream(args[1]))) {
                original = (ExtendedProperty) input.readObject();
            }
            original.check();
            final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
                output.writeObject(original);
            }
            try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                final ExtendedProperty copy = (ExtendedProperty) input.readObject();
                copy.check();
                require(copy != original && copy.equals(original), "rewritten subclass");
            }
        }
        System.out.println("PASS " + args[0] + ": UID, public constructors, protected metadata and subclass state");
    }
}
