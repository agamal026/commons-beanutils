# BEANUTILS-417: subclass-state assertion correction

30 October 2026. This supplements the [October 29 method-collision review](final-method-collision-20261029.md). No production PR update or upstream acceptance is claimed.

## Test weakness found

The proposed regression fixture declared `private final int extraState = 29`. The direct expression in `assertEquals(29, copy.extraState)` can therefore evaluate to the compile-time constant rather than observing the deserialized field. [JLS 13.1](https://docs.oracle.com/javase/specs/jls/se21/html/jls-13.html#jls-13.1) requires constant-variable field references to be resolved at compile time.

An isolated JDK audit mechanically extracted the nested class from the proposed JUnit file. It ran the equivalent scalar predicate with the unchanged local static-accessor production correction. It did not load or simulate the JUnit API. For a negative control, only a copy of the fixture's field was made transient, deliberately omitting it from serialization. Reflection measured the actual restored field independently.

| Fixture | Direct field expression | Actual stored value | Oracle outcome |
| --- | ---: | ---: | --- |
| Original, normal serialization | 29 | 29 | Accepted |
| Original, deliberately omitted field | 29 | 0 | False pass |
| Revised, normal serialization | 29 | 29 | Accepted |
| Revised, deliberately omitted field | 0 | 0 | Rejected as intended |

Complete decisive output from those four JVM runs, in table order:

```text
direct=29;stored=29;fieldInStream=true
ORACLE_ACCEPTED

direct=29;stored=0;fieldInStream=false
ORACLE_ACCEPTED

direct=29;stored=29;fieldInStream=true
ORACLE_ACCEPTED

direct=0;stored=0;fieldInStream=false
Exception in thread "main" java.lang.AssertionError: subclass state: expected 29 but was 0
```

The first three executions returned 0; the last returned the expected 1. This is four oracle observations, not four passing JUnit tests. Bytecode inspection shows constant 29 in the original expression and an actual field read in the revision. The deliberately omitted-field mutation is not part of the proposed patch.

## Minimal test correction

```diff
-        private final int extraState = 29;
+        private final int extraState;
 
-        private ExistingProperty() {
+        private ExistingProperty(final int extraState) {
             super("existing", Map.class, String.class);
+            this.extraState = extraState;
         }
@@
-        final ExistingProperty original = new ExistingProperty();
+        final ExistingProperty original = new ExistingProperty(29);
```

The field remains final and the JUnit assertion is unchanged. The two production files remain byte-for-byte identical to the October 29 static-accessor correction. Its published hashes and serialization-format qualification still apply.

The October 29 standalone method-collision client also had a constant-initialized scalar field. Its linkage and stream-path observations remain accurate, but that particular assertion was insufficient evidence of the stored scalar value. The new checks below replace that limited part of the evidence. This does not invalidate the separate October 28 computed-UID harness, whose state was constructor-assigned.

## Updated state-preservation checks

Two revised clients were compiled against the original upstream code: one in the same package and one in an external package. Their final state is now constructor-assigned from a runtime argument. Their class files were unchanged during version switching.

For each client, old streams containing state 29 were written and read with the original library, then read, rewritten and read again with the local correction. New streams carrying state -73 were roundtripped with the local correction. Both clients retained the actual values. The submitted PR still fails to load the same-package client with its existing final-method linkage error; the external-package submitted control succeeds.

Execution accounting: 4 successful compiler commands and 10 JVM scenarios, comprising 9 successful executions plus 1 expected nonzero linkage control. All declared outcomes were observed. These are not JUnit-suite totals. The unchanged seven-shape wire-identity and 15-case computed-UID checks were not rerun.

## Exact source and execution

- Original baseline: `22354f22fb150f82ae8cb7d4e444d2bf0d2bb1ff`.
- Submitted PR: `1d95c95c3045da8939db4c93ba2d13cf27ae241f`.
- Local corrected production blobs remain `63f84235ee6df6d626d876594278f9d68408d7b6` and `38a611534cff4e0d7d0223b95a6eb6b22ef42c16`; these identify local source, not remote commits.
- Updated three-file patch SHA-256: `40b5bbac26ff116f8695622fcd4be53d4ba052e8ecd1f0df80d1ab93bef629db`. It applies to the exact retained production-file subset, not a complete repository checkout.
- Oracle audit: 2026-10-30T06:24:31Z to 06:24:40Z.
- Updated state recheck: 2026-10-30T06:26:57Z to 06:27:02Z.
- Linux, OpenJDK/javac 21.0.12.1, `--release 8`; obsolete-target warnings retained. Java 8 runtime coverage is not established.

The actual JUnit test has not been compiled or run with JUnit. The complete default Maven build, project quality gates, fresh JBoss-cloner validation and independent review remain incomplete. No earlier full-build result is transferred to this correction. The proposed production fix must remain unsubmitted until its required gates are completed.

AI disclosure: OpenAI ChatGPT (GPT-6 Astra Pro) performed serial source review, generated the revised fixture and isolated audits, and executed the recorded compiler/JVM checks. No independent native-agent or human review is claimed.
