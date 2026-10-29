# BEANUTILS-417: same-package subclass method collision

Recorded 29 October 2026. This is a newly reproduced compatibility finding in the submitted PR, not an upstream acceptance or a production-source update.

## Exact scope

Submitted source: `1d95c95c3045da8939db4c93ba2d13cf27ae241f`.
Original upstream baseline: `22354f22fb150f82ae8cb7d4e444d2bf0d2bb1ff`.

The proposed superclass introduces package-private `final boolean isLegacyStream()`. A pre-existing downstream subclass in the **same runtime package**, loaded on the classpath by the same loader, may already declare its own `public boolean isLegacyStream()`. That valid baseline client fails to load with the submitted PR. Recompiling the client against the PR also fails.

This is a constructed reproducer of a conditional compatibility failure, not a report that a known downstream application has encountered it. The equivalent subclass in another package loads successfully. The earlier computed-UID proof remains valid for its tested subclass shapes; it did not exercise this name collision.

## Executed failing control

The complete production files were recovered from the prior verified packet and their Git blob IDs checked before compilation:

| File | Git blob |
| --- | --- |
| Baseline DynaProperty.java | `af5538f16aac4147d42e374952124d109dd6153b` |
| Submitted DynaProperty.java | `bc19eb96eb1101c9c86f7c74e045e241787a27c9` |
| Submitted DynaPropertySerialization.java | `179913610acfce271a46f2ddea43a497fc7e23ba` |

A minimal client, compiled against the original upstream class, is:

```java
package org.apache.commons.beanutils;

public final class ExistingProperty extends DynaProperty {
    private static final long serialVersionUID = 1L;

    public ExistingProperty() {
        super("existing", String.class);
    }

    public boolean isLegacyStream() {
        return true;
    }

    public static void main(String[] args) {
        ExistingProperty value = new ExistingProperty();
        if (!value.isLegacyStream() || !"existing".equals(value.getName())) {
            throw new AssertionError();
        }
        System.out.println("PASS " + value.getClass().getName());
    }
}
```

Compile the original and submitted production classes into separate directories, then compile the client **once against the original classes**:

```sh
javac --release 8 -d baseline-classes baseline/src/main/java/org/apache/commons/beanutils/DynaProperty.java
javac --release 8 -d submitted-classes submitted/src/main/java/org/apache/commons/beanutils/DynaProperty.java submitted/src/main/java/org/apache/commons/beanutils/DynaPropertySerialization.java
javac --release 8 -cp baseline-classes -d client-classes ExistingProperty.java
java -cp client-classes:baseline-classes org.apache.commons.beanutils.ExistingProperty
java -cp client-classes:submitted-classes org.apache.commons.beanutils.ExistingProperty
```

Here `baseline` and `submitted` are checkouts of the revisions named above. Use the platform classpath separator when reproducing outside Linux.

Observed baseline output, exit 0:

```text
PASS org.apache.commons.beanutils.ExistingProperty
```

Observed submitted-source output, exit 1:

```text
Error: LinkageError occurred while loading main class org.apache.commons.beanutils.ExistingProperty
    java.lang.IncompatibleClassChangeError: class org.apache.commons.beanutils.ExistingProperty overrides final method org.apache.commons.beanutils.DynaPropertySerialization.isLegacyStream()Z
```

The external-package control, declaring the same method, exits 0 with both versions. A second client with Map/String metadata and its own integer state reproduces the same submitted-source linkage failure and source-compilation error.

## Proposed correction, not applied to the PR

Keep the flag private and transient. Use a static accessor taking the new marker type instead of an inherited instance method:

```java
static boolean isLegacyStream(final DynaPropertySerialization property) {
    return property.legacyStream;
}
```

Both `DynaProperty.readObject` conditions call `DynaPropertySerialization.isLegacyStream(this)`. This removes the conflicting zero-argument inherited method and avoids virtual dispatch to a subclass's unrelated method. No superclass name, explicit UID, serialized field, or writer order changes.

Simply removing `final` is **not** a sufficient correction. That counterfactual loads the client but calls its unrelated method while reading a new stream; the executed new-stream roundtrip failed with `java.io.EOFException` in `DynaProperty.readAnyClass`.

## New validation of the local correction

Environment: Linux, OpenJDK/javac 21.0.12.1, `--release 8`. Obsolete source/target-option warnings were retained. This is not Java 8 runtime coverage.

| Check | Result |
| --- | --- |
| Unchanged baseline-compiled same-package client: old stream -> corrected reader -> rewrite/read | Passed |
| Unchanged baseline-compiled external-package control: old stream -> corrected reader -> rewrite/read | Passed |
| New-stream roundtrips for those two clients | Both passed |
| Same-package client recompiled against corrected source, then new-stream roundtrip | Passed |
| Same-package client recompiled against submitted source | Expected compiler error: overridden method is final |
| Submitted vs corrected serialization for seven ordinary property shapes | All seven byte sequences identical; roundtrips passed |
| Previous computed-UID harness against the changed local correction | 15 case checks passed: 3 baseline controls and 12 correction checks; zero failures/errors/skips |

The final collision/serialization harness recorded 20 compilation or runtime commands with the expected outcomes. Three deliberately negative commands returned nonzero: submitted binary linkage, submitted source compilation, and naive nonfinal deserialization. These are not 20 passing JUnit tests.

The ordinary-property shapes are String, primitive int, typed Map, typed List, String array, int array, and null type. The original downstream client class files were unchanged during version-switching runs.

The first harness attempt expected StreamCorruptedException for the deliberately naive nonfinal variant but observed EOFException. Only that exception expectation was corrected; the initial failed harness attempt is retained separately. The production correction did not change between those attempts.

Local corrected source identities:

- DynaProperty.java: Git blob `63f84235ee6df6d626d876594278f9d68408d7b6`, SHA-256 `fa0fa20815bc547f7e474cf7bd2c0fc8d0d1a15ad01fba727ac52b54330b2432`.
- DynaPropertySerialization.java: Git blob `38a611534cff4e0d7d0223b95a6eb6b22ef42c16`, SHA-256 `bfa6611259d872f270a1f94616fd090a9108cdc39abd06de9637beeb73274862`.

These are hashes of unpublished local correction bytes, **not remote commits**. A prepared patch changes the two production files and adds a proposed JUnit regression. Patch SHA-256: `3c0d7f210eecfcf8c3b835a79b39edbd138e07055449088ab223719b58178e97`. It applies cleanly to the retained exact production-file subset; that is not a full repository checkout.

## Remaining validation and review

The new JUnit file has not been compiled or executed with JUnit. The required default Maven build, project quality checks, fresh JBoss-cloner coverage, and independent review have not run on the correction. The October 22 full-build results apply only to the original submitted revision and must not be transferred to the correction.

The PR source branch remains at the original submitted revision. This finding needs resolution before the earlier compatibility evidence is treated as sufficient for all supported subclass cases. Whether same-package downstream extensions are supported remains a maintainer decision; no universal downstream breakage is claimed.

References: [JLS binary compatibility](https://docs.oracle.com/javase/specs/jls/se17/html/jls-13.html#jls-13.4.17), [original PR](https://github.com/apache/commons-beanutils/pull/444), [previous scoped computed-UID proof](https://github.com/agamal026/commons-beanutils/blob/3ae57c660623fbaffc7e75cae6af89dc3be209e6/contribution-review/BEANUTILS-417/computed-uid-20261028/README.md).

AI disclosure: OpenAI ChatGPT (GPT-6 Astra Pro) performed serial review, generated the fixtures and proposed correction, and executed the described local checks. No independent native agent or human review is claimed.
