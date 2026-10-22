# BEANUTILS-417: serialization compatibility proof

This packet records executed checks for [source commit 1d95c95c3045da8939db4c93ba2d13cf27ae241f](https://github.com/agamal026/commons-beanutils/commit/1d95c95c3045da8939db4c93ba2d13cf27ae241f), based on the maintained 1.X branch at [22354f22fb150f82ae8cb7d4e444d2bf0d2bb1ff](https://github.com/apache/commons-beanutils/commit/22354f22fb150f82ae8cb7d4e444d2bf0d2bb1ff).

## Problem and compatibility boundary

[BEANUTILS-417](https://issues.apache.org/jira/browse/BEANUTILS-417) concerns default fields being written after custom class metadata. The actual JBoss Marshalling 2.0.12.Final serializing ObjectCloner reproduces the defaultWriteObject context error. The dependency is test-scoped; this does not execute a complete WildFly or WebSphere deployment.

The fix writes default fields first and introduces an empty, package-private serializable superclass. For an old stream missing that superclass, readObjectNoData marks the legacy ordering before DynaProperty reads its data. New streams contain the marker; their reader uses the new ordering. Every write uses the new format, including objects loaded from historical streams. The marker's binary name, UID, empty persistent form and hierarchy position are a permanent protocol commitment. Existing DynaProperty public/protected members, UID 1L and primitive-class codec remain in place.

This follows the [maintainer requirement to read both historical and new data while allowing a new writer format](https://github.com/apache/commons-beanutils/pull/430#issuecomment-5105326637), without requiring callers to replace their ObjectInputStream.

**Japicmp explicitly reports SERIALIZABLE_INCOMPATIBLE_SUPERCLASS_MODIFIED.** It also reports DynaProperty binaryCompatible=true and sourceCompatible=true, with explicit UID unchanged at 1. The serialization finding has not been suppressed. The guarantee is directional: the new reader accepts historical and new data. Earlier library releases reading newly written streams are not supported or claimed by this proof. Reflection also observes the new superclass.

## Executed results

| Run | UTC interval, 22 October 2026 | Reported | Passed | Failures | Errors | Skipped | Exit |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Unchanged production + candidate tests | 18:21:29–18:21:50 | 104 | 91 | 3 | 9 | 1 | 1 |
| Fixed focused suite | 18:25:38–18:25:58 | 104 | 103 | 0 | 0 | 1 | 0 |
| Full configured default build | 18:26:23–18:27:51 | 1,293 | 1,285 | 0 | 0 | 8 | 0 |

Counts were derived from actual Surefire XML testcase nodes and independently checked against suite attributes and logs. The full build contains 104 suites. The three baseline failures are the changed writer-order assertions. The nine errors are seven parameterized ObjectCloner cases, the old-fixture transition and the subclass clone. The primitive Integer.TYPE parameter already passes before the fix; all twelve former failure/error cases pass afterward.

All **22 committed historical .ser fixtures are byte-for-byte unchanged**, as is their existing reader test. They are covered by one active test method, not 22 separately counted JUnit cases. The single focused skip is the existing manual fixture-generator test. The full build's eight skips are that generator, three BeanMap operations excluded by assumptions, two indexed-list tests unsupported by this JDK, and two existing Jira422 indexed-list assumptions. Exact names, messages and hashes are in [validation.json](validation.json). The project's existing MemoryTest/MemoryTestCase exclusions remain unchanged.

Additional focused tests cover old fixture → new reader → rewrite/read, strict cloning of an old-read object, serialization/read of its clone, and subclass-specific final state. No historical fixtures were regenerated.

## Precompiled client and subclass

[PrecompiledDynaPropertyClient.java](precompiled-client/PrecompiledDynaPropertyClient.java) was compiled once against unchanged baseline classes using release 8, producing class-file major version 52. Its original bytecode then ran against the fixed classes without recompilation. Both write and read modes passed, covering public constructors, the precompiled subclass's three-argument super-constructor, protected field linkage, UID 1L, historical subclass deserialization, and rewrite/read preservation of subclass state.

The original bytecode and historical subclass stream remained unchanged. Base64 copies of the three class files and both benign metadata streams, together with exact commands, results and SHA-256 identities, are included under [precompiled-client](precompiled-client). Decode a .base64 file to the same name without that suffix to recover its exact bytes. This supplemental baseline-generated stream is separate from the project's 22 unchanged historical fixtures.

## Commands and environment

Actual environment: Maven 3.9.11, OpenJDK 17.0.20, Linux amd64; compiler release 8. The focused selection was:

    mvn --errors --show-version --batch-mode --no-transfer-progress -DtrimStackTrace=false '-Dtest=DynaProperty*' clean test

The full build invoked Maven without an explicit goal or test selector:

    mvn --errors --show-version --batch-mode --no-transfer-progress -DtrimStackTrace=false

The POM's unmodified default is:

    clean apache-rat:check verify japicmp:cmp javadoc:javadoc checkstyle:check

The captured invocations additionally select the environment's Maven settings/local cache. The full run sets the Javadoc launcher and prefixes the environment-only launcher directory on PATH because native JDK tool launchers are missing. The exact redacted command arrays are in validation.json; actual launcher implementations are in [launchers](launchers). No project test or validation gate was skipped or weakened to accommodate the environment.

To reproduce the failing control, check out the upstream base and apply [baseline-tests.patch](baseline-tests.patch), which changes only the test-scoped dependency and the final regression-test file, then run the focused command. To reproduce the fixed runs, check out the source commit and run the focused and default commands. Ordinary installations with complete JDK launchers do not require the environment-specific wrappers.

## Gate results and limitations

RAT reports 292 approved licenses, zero unapproved and zero unknown. Checkstyle reports zero violations. Japicmp against 1.11.0 completes with the explicitly disclosed serializable-superclass change; the other reported converter differences predate this contribution. Javadoc generated its index. The complete selected reports are under [gates](gates).

The successful default log retains warnings: the inherited Surefire forkMode setting is unknown; the existing commons-collections-testframework 3.2.1 POM is missing, so SPDX generates a minimal package for that dependency; online SPDX license retrieval falls back to cached data; CycloneDX emits schema warnings. This proof does not claim warning-free execution or a complete SBOM. JaCoCo, PMD and SpotBugs did not run in this project's default lifecycle, and no coverage percentage or result from those tools is claimed.

These are local Linux/Java 17 executions with Java 8 bytecode targeting. They do not establish execution on a Java 8 runtime, other operating systems/JDKs, application servers, hosted CI, maintainer approval or upstream acceptance.

## Provenance and retained evidence

The tests ran before the source commit was created, with baseline HEAD plus the captured worktree changes. Every fixed-focused/full-build source snapshot was then verified byte-for-byte against the published commit. Its complete tree is 19a97c4cd64f359e18cf8535e6475a5c7ae8f1ec, matching the independently constructed local tree; the local checkout is clean at that commit. The unchanged source files come from the exact upstream parent. The baseline control deliberately retains old production source. The new superclass is captured in the fixed source manifests even though it was untracked during the executions.

Complete logs: [baseline](logs/baseline.log), [fixed focused](logs/fixed-focused.log), [full default](logs/default.log). Public logs differ from raw logs only by consistent environment-path substitutions. Raw and public SHA-256 values, source blob/hash identities, all legacy fixture hashes, suite counts, failed/skipped cases, and gate checks are recorded in validation.json. Raw XML system properties, credentials and private Maven settings are not published.

**AI disclosure:** OpenAI ChatGPT (GPT-6 Astra Pro) generated the implementation, tests, documentation and this proof, with separate AI design/integration/evidence reviews and actual tool-executed validation. This is not a claim of human review.
