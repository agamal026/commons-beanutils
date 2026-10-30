# BEANUTILS-417: validated static-accessor correction

30 October 2026. This packet records a three-file correction to the existing
[BeanUtils PR #444](https://github.com/apache/commons-beanutils/pull/444), targeting
`1.X`. It is validation evidence, not upstream acceptance.

## Problem and correction

The submitted serialization marker added a package-private final instance method
named `isLegacyStream()`. An existing same-package subclass can already declare
a public method with that signature. The submitted helper then prevents that
subclass from compiling and, in the earlier unchanged-bytecode control, prevents
it from loading. This is a constructed compatibility scenario, not a reported
downstream production incident.

The correction makes the marker accessor static and invokes it through its
declaring class at both points in `DynaProperty.readObject`. A subclass's own
method is no longer involved in choosing the serialization read order. The
writer, persistent fields, explicit UIDs, format marker and legacy handling are
unchanged by this correction.

The new same-package regression supplies final subclass state through a
constructor parameter. Its assertion reads the actual deserialized field;
it does not rely on the constant-field oracle that was repaired in the earlier
[test review](https://github.com/agamal026/commons-beanutils/blob/4e0d3c8220719a14c8b27c92b8a11ea865a4785d/contribution-review/BEANUTILS-417/test-state-oracle-20261030.md).

## Exact tested source

Published follow-up: [`51a346ab12e0fd683df794ab40c9f18e58f92068`](https://github.com/agamal026/commons-beanutils/commit/51a346ab12e0fd683df794ab40c9f18e58f92068). GitHub independently returned the same tested tree and parent, and PR #444 now points to this commit.

- Submitted parent: `1d95c95c3045da8939db4c93ba2d13cf27ae241f`.
- Original upstream base: `22354f22fb150f82ae8cb7d4e444d2bf0d2bb1ff`.
- Complete tested Git tree: `285a9ca59ab976121d2935722875f310d80756b6`.
- Saved v2 patch SHA-256: `40b5bbac26ff116f8695622fcd4be53d4ba052e8ecd1f0df80d1ab93bef629db`.

[source-identity.json](source-identity.json) records all three complete-file
blob IDs and SHA-256 digests. [source.patch](source.patch) is the Git-formatted
diff from the submitted parent. Its bytes differ from the original saved patch's
formatting, while producing the same reviewed source files.

Tests ran on the submitted parent plus these exact staged changes, before a
follow-up commit was created. The complete tree and file hashes were checked
before and after the build. No claim is made that the test process checked out
a later-created commit.

## Actual results

| Execution | Reported | Passed | Failures | Errors | Skipped | Outcome |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| Submitted production plus new regression | 0 executed | 0 | — | — | — | Expected test-compilation rejection: overridden method is final |
| Corrected focused suite | 105 | 104 | 0 | 0 | 1 | Success |
| Corrected full default lifecycle, final logged run | 1,294 | 1,286 | 0 | 0 | 8 | Success |

The baseline control failed at test compilation, not in an executed JUnit case.
The focused selection includes the new regression and the 45-case serialization
suite; 10 of those serialization cases invoke the actual JBoss serializing
cloner: eight parameter values, one legacy read/rewrite/clone transition and
one subclass-state clone. It also executes the historical fixture reader.

The full run's 105 XML suites and their individual totals were inspected.
[test-results.json](test-results.json) includes every suite's totals, every
nonpassed case and the relevant DynaProperty cases. Counts are not added across
the focused and full runs. All 22 historical `.ser` files remain byte-identical
to the original base; [fixtures.json](fixtures.json) records their hashes.
One active JUnit case reads those 22 files, not 22 separately reported cases.

### Required default gates

The full command supplied no explicit goal or test selector. It executed the
unchanged project default:

```text
clean apache-rat:check verify japicmp:cmp javadoc:javadoc checkstyle:check
```

- RAT: **293 approved**, zero unapproved and zero unknown.
- Checkstyle: **269 files**, zero violations.
- Japicmp: `DynaProperty` reports binary/source compatibility, and still reports
  **`SERIALIZABLE_INCOMPATIBLE_SUPERCLASS_MODIFIED`**. This warning was not suppressed.
- Javadoc: the generated index exists; its byte count and digest are in
  [validation.json](validation.json).

The original compatibility boundary remains: the new reader accepts historical
and new data. Older releases reading newly written data are not supported.
Reflection sees the added superclass. This correction makes no universal
compatibility promise for clients deliberately compiled against the unpublished
package-private helper from the intermediate PR revision.

## Commands, dates and environment

Environment: Linux amd64, full Eclipse Temurin **17.0.21+9**, Maven **3.9.16**,
compiler release **8**. The commands used an external Maven settings file,
a workspace dependency cache and the existing system Java CA trust store.
TLS validation remained enabled. These are environment choices; no project
POM, dependency, test exclusion, warning or quality-gate setting was changed.

The exact command arrays and UTC start/end times are in
[validation.json](validation.json), with local paths replaced consistently by
the variables below. The essential commands are:

```sh
"$MAVEN_HOME/bin/mvn" -B -ntp -U -s "$MAVEN_SETTINGS" -Dmaven.repo.local="$MAVEN_REPO" -Dtest=DynaPropertySubclassCompatibilityTest test
"$MAVEN_HOME/bin/mvn" -B -ntp -s "$MAVEN_SETTINGS" -Dmaven.repo.local="$MAVEN_REPO" '-Dtest=DynaProperty*Test' test
"$MAVEN_HOME/bin/mvn" -B -ntp -l "$EVIDENCE/fixed-default.log" -s "$MAVEN_SETTINGS" -Dmaven.repo.local="$MAVEN_REPO"
```

The first command is the expected-failing control on submitted production plus
the new test; the second and third use corrected production. Focused execution
was **14:52:08–14:52:19 UTC**. The final full build was
**14:55:53–14:56:42 UTC** on 30 October.

### Retained setup and capture limitations

The first setup attempt failed resolving the parent POM because the downloaded
JDK lacked the runtime's trusted CA. It did not compile or run tests. The
subsequent commands used the existing system trust store without disabling TLS.

An earlier full-default attempt exited zero and produced the same 105 suites and
all downstream gate reports, but its redirected console stopped early. Its
partial log is retained. One unchanged-source repeat used Maven's native log
capture and produced the complete final log, including `BUILD SUCCESS` and all
gate output. That repeat closes a concrete evidence gap; it does not create
another set of contribution or acceptance credits.

## Warnings and unexecuted scope

The complete [final log](logs/fixed-default.log) retains the missing POM for the
existing `commons-collections-testframework:3.2.1` dependency, an ERROR-labeled
SPDX metadata message followed by creation of a minimal SPDX package, fallback
to cached SPDX license data, the inherited unrecognized Surefire `forkMode`
setting, and CycloneDX schema-keyword warnings. The lifecycle succeeds with
these messages. This is not a warning-free or complete-SBOM claim.

The eight skips and existing memory-test exclusions remain. No Java 8 runtime,
other operating system, complete application server, coverage percentage or
hosted CI success is claimed. The earlier binary-client, computed-UID and
wire-identity checks retain their original execution dates and were not rerun.

Public logs preserve technical output with only disclosed execution-path and
network-host redactions. Raw/public log hashes are recorded. Raw Surefire XML
system properties, Maven settings and Javadoc option files are excluded.

## Review and AI disclosure

Two independent native AI reviewers assessed the exact source and integration
evidence; see [review.md](review.md). This is automated review, not human review
or upstream acceptance. OpenAI ChatGPT (GPT-6 Astra Pro) generated this correction,
regression and evidence text and performed tool-executed validation. The source
commit carries a `Generated-by` trailer.

## Publication and hosted checks

[publication.json](publication.json) records the source-ref and PR readback. The new Java CI and CodeQL runs both require upstream approval and contain zero jobs at the recorded observation. They are unexecuted, not hosted passes or test failures. The current PR remains open and unmerged.
