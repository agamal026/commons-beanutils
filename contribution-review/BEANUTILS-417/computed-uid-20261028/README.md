# BEANUTILS-417: supplemental computed-UID compatibility check

This is supplemental evidence for [apache/commons-beanutils PR #444](https://github.com/apache/commons-beanutils/pull/444). It changes no production code, does not replace the prior full default build, and does not establish upstream acceptance.

The executed client is [CompatibilityClient.java](CompatibilityClient.java). Full command timestamps, exit codes, stdout/stderr, and bytecode hashes are retained in [execution.json](execution.json).

## Risk checked

The existing precompiled-client proof declares an explicit subclass serialVersionUID. Adding a serializable superclass warrants checking downstream subclasses that instead use Java's computed serialVersionUID. This check covers three such shapes: direct inheritance, two levels of inheritance, and custom writeObject/readObject callbacks. Subclass state, protected metadata, custom trailing data, and shared-reference identity are asserted explicitly rather than inferred from DynaProperty.equals().

## Exact source and execution

- Baseline: `22354f22fb150f82ae8cb7d4e444d2bf0d2bb1ff`.
- Submitted candidate: `1d95c95c3045da8939db4c93ba2d13cf27ae241f`.
- Execution: **2026-10-28T09:58:43.872770Z to 2026-10-28T09:58:47.268229Z**.
- Environment: Linux, OpenJDK 21.0.12.1 (Debian), javac `--release 8`; class-file major version 52.

The candidate DynaProperty and marker source were recovered through the connected repository reader. Baseline DynaProperty was reconstructed by reversing the actual PR diff. All three complete files matched their Git blob IDs before compilation; no production source was altered.

| Complete source file | Git blob ID |
|---|---|
| Baseline DynaProperty.java | `af5538f16aac4147d42e374952124d109dd6153b` |
| Candidate DynaProperty.java | `bc19eb96eb1101c9c86f7c74e045e241787a27c9` |
| Candidate DynaPropertySerialization.java | `179913610acfce271a46f2ddea43a497fc7e23ba` |

## Results

| Scenario | Passed case checks | Failed |
|---|---:|---:|
| Old writer -> old reader (baseline control) | 3 | 0 |
| Old writer -> candidate reader, using unchanged baseline-compiled client | 3 | 0 |
| Candidate rewrite of old-read objects -> candidate reader | 3 | 0 |
| Candidate writer -> candidate reader, using unchanged baseline-compiled client | 3 | 0 |
| Client recompiled against candidate -> old stream | 3 | 0 |

**15 case checks: 3 baseline controls and 12 candidate checks; no failures, errors or skips.** These are repeated compatibility scenarios over three subclass shapes, not 15 distinct JUnit test methods. Compilation and every command below returned zero.

The original client's four class files remained byte-for-byte unchanged during candidate execution. A separate compilation of the same client source against the candidate produced identical class bytes. All three computed subclass UIDs matched:

```text
CompatibilityClient$CustomProperty=-9024995153549563532
CompatibilityClient$DirectProperty=-9129654479144846737
CompatibilityClient$LeafProperty=-1597413464192825955
```

The old stream lacks the new superclass descriptor; newly written and rewritten streams include it. The check found no incompatibility in these scenarios. It is not proof for every possible subclass.

## Reproduction

Use an existing clone containing both pinned commits. From the repository root, prepare an empty scratch directory and copy the accompanying CompatibilityClient.java into it. Set REPO to the absolute clone directory, then run in the scratch directory:

```sh
BASE=22354f22fb150f82ae8cb7d4e444d2bf0d2bb1ff
HEAD=1d95c95c3045da8939db4c93ba2d13cf27ae241f
PKG=org/apache/commons/beanutils
mkdir -p baseline/src/$PKG candidate/src/$PKG
mkdir -p work/baseline-classes work/candidate-classes work/client-classes work/recompiled-client
git -C "$REPO" show "$BASE:src/main/java/$PKG/DynaProperty.java" > baseline/src/$PKG/DynaProperty.java
git -C "$REPO" show "$HEAD:src/main/java/$PKG/DynaProperty.java" > candidate/src/$PKG/DynaProperty.java
git -C "$REPO" show "$HEAD:src/main/java/$PKG/DynaPropertySerialization.java" > candidate/src/$PKG/DynaPropertySerialization.java
# Check git hash-object of each file against the table above before compiling.
```

The following are the actual executed compile/runtime command arrays rendered as shell commands:

```sh
java -version
javac -version
javac --release 8 -d work/baseline-classes baseline/src/org/apache/commons/beanutils/DynaProperty.java
javac --release 8 -d work/candidate-classes candidate/src/org/apache/commons/beanutils/DynaProperty.java candidate/src/org/apache/commons/beanutils/DynaPropertySerialization.java
javac --release 8 -sourcepath '' -cp work/baseline-classes -d work/client-classes CompatibilityClient.java
java -cp work/baseline-classes:work/client-classes CompatibilityClient write work/legacy.ser work/baseline-uids.properties
java -cp work/baseline-classes:work/client-classes CompatibilityClient read work/legacy.ser work/baseline-uids.properties
java -cp work/candidate-classes:work/client-classes CompatibilityClient rewrite work/legacy.ser work/baseline-uids.properties work/rewritten.ser
java -cp work/candidate-classes:work/client-classes CompatibilityClient write work/new.ser work/candidate-uids.properties
java -cp work/candidate-classes:work/client-classes CompatibilityClient read work/new.ser work/baseline-uids.properties
javac --release 8 -sourcepath '' -cp work/candidate-classes -d work/recompiled-client CompatibilityClient.java
java -cp work/candidate-classes:work/recompiled-client CompatibilityClient read work/legacy.ser work/baseline-uids.properties
```

The complete candidate legacy-read/rewrite stdout was:

```text
UID CompatibilityClient$CustomProperty=-9024995153549563532
UID CompatibilityClient$DirectProperty=-9129654479144846737
UID CompatibilityClient$LeafProperty=-1597413464192825955
DYNA_SUPER org.apache.commons.beanutils.DynaPropertySerialization
CASE PASS input direct-default-uid
CASE PASS input multilevel-default-uid
CASE PASS input custom-callback-default-uid
CASE PASS rewritten direct-default-uid
CASE PASS rewritten multilevel-default-uid
CASE PASS rewritten custom-callback-default-uid
```

All four compilation commands emitted the same three obsolete Java 8 source/target-option warnings. These warnings were retained without suppression in execution.json. Runtime probes completed without errors. The client uses ordinary ObjectInputStream/ObjectOutputStream and no third-party dependencies or substitutes.

## Boundaries

This is an isolated javac/Java compatibility check of the exact production classes, not a new Maven/JUnit/default-project build, JBoss Marshalling run, application-server deployment, Java 8 runtime test, or cross-platform test. The previous full default-build results retain their October 22 execution date and original qualifications. No historical project fixtures were changed.

The PR's directional guarantee remains unchanged: new readers accept historical and new data; old readers of newly written streams are not promised. Japicmp's SERIALIZABLE_INCOMPATIBLE_SUPERCLASS_MODIFIED warning remains explicit and unsuppressed. No source correction follows from a passing supplemental check.

AI disclosure: OpenAI ChatGPT (GPT-6 Astra Pro) generated this supplemental client, execution procedure and text. Validation was actually tool-executed; review was serial AI review, not independent-agent or human review. Existing Apache source is linked and attributed through the pinned revisions and repository license/notice.
