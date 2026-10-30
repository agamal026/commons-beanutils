## Problem

[BEANUTILS-417](https://issues.apache.org/jira/browse/BEANUTILS-417): `DynaProperty.writeObject` writes custom class metadata before default fields. The actual JBoss Marshalling serializing `ObjectCloner` rejects this ordering with `IllegalStateException: defaultWriteObject may not be called in this context`.

This targets the maintained **`1.X`** branch and follows the [earlier maintainer requirement](https://github.com/apache/commons-beanutils/pull/430#issuecomment-5105326637): a new writer format is permitted if the new reader accepts both old and new data. Callers continue to use their ordinary `ObjectInputStream`.

## Change

- Write default fields before the existing custom type/content-type encoding.
- Add an empty, package-private serializable superclass. When it is absent from a historical stream, `readObjectNoData()` marks the legacy ordering before `DynaProperty` reads its data. New streams include the marker and use the new ordering.
- Always write the new format, including objects read from historical streams. Preserve `DynaProperty`'s UID `1L`, public/protected members, transient class fields and primitive-class codec.
- Add test-scoped JBoss Marshalling 2.0.12.Final coverage, legacy-read/rewrite/clone transitions and subclass-state checks. Keep all **22 historical `.ser` fixtures** and their existing reader test unchanged; update the three assertions that deliberately checked the old writer order.
- Document the compatibility boundary and permanent marker commitment; add a scoped changelog entry.
- Keep the marker accessor static and call it through its declaring class, so a same-package subclass's existing `isLegacyStream()` method cannot interfere with read-order selection. The new regression verifies constructor-supplied subclass state after deserialization.

### Compatibility qualification

Japicmp reports `DynaProperty` as binary- and source-compatible, with UID `1L` retained, **and explicitly flags `SERIALIZABLE_INCOMPATIBLE_SUPERCLASS_MODIFIED`**. This finding has not been suppressed. The added superclass, including its name, UID, empty persistent form and position, becomes part of the serialized protocol; reflection observes the hierarchy change.

The intended guarantee is **new readers accept historical and new data**. Earlier library releases reading newly written data are not supported by this change. The legacy fixtures, new round trips and an unchanged baseline-compiled subclass exercise the intended direction.

## Executed proof

Current source: [`51a346ab12e0fd683df794ab40c9f18e58f92068`](https://github.com/agamal026/commons-beanutils/commit/51a346ab12e0fd683df794ab40c9f18e58f92068). This follows submitted revision `1d95c95c3045da8939db4c93ba2d13cf27ae241f` and retains the original upstream base [`22354f22fb150f82ae8cb7d4e444d2bf0d2bb1ff`](https://github.com/apache/commons-beanutils/commit/22354f22fb150f82ae8cb7d4e444d2bf0d2bb1ff).

**[Current exact-source proof packet](https://github.com/agamal026/commons-beanutils/blob/8686e6f615c3eee822ef5325ddf56e3aaf8b27eb/contribution-review/BEANUTILS-417/static-accessor-validation-20261030/README.md)** records the static-accessor correction, complete source and fixture identities, actual commands/timestamps, baseline compilation rejection, all suite totals, retained warnings, and full gate output. The complete tested tree is `285a9ca59ab976121d2935722875f310d80756b6`. Proof remains on a separate fork branch.

The preceding submitted marker added a final instance helper that conflicts with a same-package subclass's existing public method. The new regression fails to compile against that submitted production code. Making the accessor static resolves the conflict without changing the writer, persistent fields, explicit UIDs, format marker, or legacy handling. This is a constructed compatibility scenario, not a reported downstream incident.

| Current correction validation | Reported | Passed | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: | ---: |
| Submitted production plus new regression | 0 executed | 0 | — | — | — |
| Corrected focused suite | 105 | 104 | 0 | 0 | 1 |
| Corrected full default lifecycle | 1,294 | 1,286 | 0 | 0 | 8 |

The first row is the expected **test-compilation rejection**, not an executed JUnit failure. Focused and full totals are separate executions and are not added together. The final full run contains 105 XML suites. All 22 historical `.ser` fixtures remain byte-identical to the upstream base; one active test reads those files, rather than 22 separately counted JUnit cases.

The focused suite includes the new subclass regression and the 45-case serialization suite. Ten serialization cases invoke the actual JBoss serializing cloner: eight parameter values, one legacy-read/rewrite/clone transition, and one subclass-state clone. The subclass-state assertion reads the actual deserialized constructor-supplied field.

Full validation used Maven **without an explicit goal or test selector**, executing the unchanged default `clean apache-rat:check verify japicmp:cmp javadoc:javadoc checkstyle:check`. RAT: **293 approved**, zero unknown/unapproved. Checkstyle: **269 files**, zero violations. Japicmp completed while retaining the serialization qualification above. Javadoc was generated.

Environment for this correction: Maven **3.9.16**, Eclipse Temurin **17.0.21+9**, Linux amd64, compiler release **8**. The build used an external Maven settings file, workspace dependency cache, and existing system Java trust store with TLS validation enabled. An initial setup attempt could not resolve the parent POM and ran no tests. An earlier complete default execution had a truncated console capture; one unchanged-source repeat using Maven's native log capture produced the complete final evidence. The final full run was **14:55:53–14:56:42 UTC on 30 October 2026**.

The final log retains existing dependency-metadata/SPDX, Surefire and CycloneDX warnings. The eight skips and existing memory-test exclusions remain. No Java 8 runtime, other platform, full application-server, coverage-percentage, warning-free build, or hosted-CI success is claimed. Tests ran before the follow-up commit was created; the exact tested files and complete tree were subsequently verified against the published commit and PR head.

### Earlier writer-order and compatibility evidence

The [original immutable packet](https://github.com/agamal026/commons-beanutils/blob/cf4a3bcc5b8646fe71638b1d8f7112774b44aed4/contribution-review/BEANUTILS-417/README.md) remains evidence for the preceding submitted revision. Its unchanged-production control has three writer-order assertion failures and nine strict-cloner errors; all twelve pass after the serialization fix. Primitive-int cloning already passes on that baseline.

The unchanged baseline-compiled client/subclass, computed-UID, and wire-identity checks retain their original execution dates and were not rerun for the static-accessor follow-up. The client exercises constructor/protected-field linkage, UID, historical subclass input, and subclass-specific state. The current correction adds no universal compatibility promise for clients deliberately compiled against the unpublished package-private helper from the intermediate PR revision.

## Contribution checklist and AI disclosure

- [x] Read the contribution guidelines and ASF Generative Tooling Guidance.
- [x] Ran the required default Maven build and meaningful failing-baseline/passing-fixed checks.
- [x] Kept the patch scoped and provided a meaningful issue-linked commit.
- [x] **AI used:** OpenAI ChatGPT (GPT-6 Astra Pro) generated the implementation, tests, documentation, PR text and proof, with separate AI design/integration/evidence reviews and actual tool-executed validation. This does not claim human review. The commit includes a `Generated-by` trailer.
