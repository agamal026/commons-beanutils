## Problem

[BEANUTILS-417](https://issues.apache.org/jira/browse/BEANUTILS-417): `DynaProperty.writeObject` writes custom class metadata before default fields. The actual JBoss Marshalling serializing `ObjectCloner` rejects this ordering with `IllegalStateException: defaultWriteObject may not be called in this context`.

This targets the maintained **`1.X`** branch and follows the [earlier maintainer requirement](https://github.com/apache/commons-beanutils/pull/430#issuecomment-5105326637): a new writer format is permitted if the new reader accepts both old and new data. Callers continue to use their ordinary `ObjectInputStream`.

## Change

- Write default fields before the existing custom type/content-type encoding.
- Add an empty, package-private serializable superclass. When it is absent from a historical stream, `readObjectNoData()` marks the legacy ordering before `DynaProperty` reads its data. New streams include the marker and use the new ordering.
- Always write the new format, including objects read from historical streams. Preserve `DynaProperty`'s UID `1L`, public/protected members, transient class fields and primitive-class codec.
- Add test-scoped JBoss Marshalling 2.0.12.Final coverage, legacy-read/rewrite/clone transitions and subclass-state checks. Keep all **22 historical `.ser` fixtures** and their existing reader test unchanged; update the three assertions that deliberately checked the old writer order.
- Document the compatibility boundary and permanent marker commitment; add a scoped changelog entry.

### Compatibility qualification

Japicmp reports `DynaProperty` as binary- and source-compatible, with UID `1L` retained, **and explicitly flags `SERIALIZABLE_INCOMPATIBLE_SUPERCLASS_MODIFIED`**. This finding has not been suppressed. The added superclass, including its name, UID, empty persistent form and position, becomes part of the serialized protocol; reflection observes the hierarchy change.

The intended guarantee is **new readers accept historical and new data**. Earlier library releases reading newly written data are not supported by this change. The legacy fixtures, new round trips and an unchanged baseline-compiled subclass exercise the intended direction.

## Executed proof

Source: [`1d95c95c3045da8939db4c93ba2d13cf27ae241f`](https://github.com/agamal026/commons-beanutils/commit/1d95c95c3045da8939db4c93ba2d13cf27ae241f), based on [`22354f22fb150f82ae8cb7d4e444d2bf0d2bb1ff`](https://github.com/apache/commons-beanutils/commit/22354f22fb150f82ae8cb7d4e444d2bf0d2bb1ff).

**[Immutable proof packet](https://github.com/agamal026/commons-beanutils/blob/cf4a3bcc5b8646fe71638b1d8f7112774b44aed4/contribution-review/BEANUTILS-417/README.md)** — full sanitized logs, exact commands/timestamps, source and fixture hashes, failed/skipped cases, gate reports, a tests-only baseline patch, and the precompiled-client evidence. The proof lives on a separate fork branch; this PR changes five source/test/changelog/build files.

| Run | Reported | Passed | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: | ---: |
| Unchanged production + candidate tests | 104 | 91 | 3 | 9 | 1 |
| Fixed focused suite | 104 | 103 | 0 | 0 | 1 |
| Full configured default build | 1,293 | 1,285 | 0 | 0 | 8 |

The baseline has three writer-order assertion failures and nine strict-cloner errors. Primitive-int cloning already passes on the baseline; all twelve former failure/error cases pass with the fix. One active compatibility test reads the 22 unchanged fixtures; these are not counted as 22 separate JUnit cases.

The supplemental client/subclass was compiled once against baseline classes, then its unchanged bytecode passed write and read/rewrite modes against the fixed classes. It checks constructor/protected-field linkage, UID, historical subclass input and subclass-specific final state.

Focused command: `mvn -Dtest=DynaProperty* clean test`. Full validation used Maven **without an explicit goal or test selector**, executing the unchanged default `clean apache-rat:check verify japicmp:cmp javadoc:javadoc checkstyle:check`. RAT: 292 approved, zero unknown/unapproved; Checkstyle: zero violations; Japicmp completed with the qualification above; Javadoc generated. Full logs retain the nonfatal dependency-metadata/SPDX and schema warnings, including a minimal SPDX package for the existing test-framework dependency.

Environment: Maven 3.9.11, OpenJDK 17.0.20, Linux amd64, compiler release 8. Environment-only JDK-module launchers replace missing native launchers. The full run reports eight skips in unchanged tests; the existing memory-test exclusions remain unchanged. No Java 8 runtime, other platform, full application-server, coverage-percentage or hosted-CI result is claimed. Tests ran before commit creation; all captured fixed-run source bytes were subsequently verified against the linked commit and complete tree.

## Contribution checklist and AI disclosure

- [x] Read the contribution guidelines and ASF Generative Tooling Guidance.
- [x] Ran the required default Maven build and meaningful failing-baseline/passing-fixed checks.
- [x] Kept the patch scoped and provided a meaningful issue-linked commit.
- [x] **AI used:** OpenAI ChatGPT (GPT-6 Astra Pro) generated the implementation, tests, documentation, PR text and proof, with separate AI design/integration/evidence reviews and actual tool-executed validation. This does not claim human review. The commit includes a `Generated-by` trailer.
