# Proactive work log

## 2026-11-26 — Ticket 464: standalone candidate validation

The candidate from commit `40f0d8cf9721c4c2a81cd6422cf3fa100e13d119` compiled and passed the ABI gate but failed to enable on isolated Paper 26.2-133 with Slimefun. Its SHA-256 is `bd4baaadc75051b07d38ed7a9f9d6d8f88cb5dc96c1cccad7d7f34563c6fb0b5`. The reproducible failure was `NoClassDefFoundError: io/papermc/lib/PaperLib`, from `Quaptics.onEnable`. Neither the candidate nor the inspected live JAR contains PaperLib; successful operation in a larger plugin installation does not establish standalone dependency completeness.

Declare PaperLib 1.0.8 explicitly and relocate it inside the shaded plugin. This keeps the existing Paper check and packages the class it calls, without relying on another plugin's class loader. A reusable disposable-server probe now covers registration, serialized item identities, block-storage values and display-library calls. The baseline fixture exited normally after reporting the enable failure; its worlds were newly generated and contained no players.

The pre-change POM has a local backup. Revert the POM dependency and relocation to undo the packaging change; the probe and its documentation can be removed independently. `main` contains documentation only; executable source is maintained on `port-26x`.

The first patched run enabled Quaptics successfully, then exposed an incorrect harness assertion: ACF registers commands in the general command map, so `getPluginCommand` is not the appropriate lookup. The probe now checks both `quaptics` and `qp` in that map. This harness failure is preserved separately from the original plugin enable failure.

Before the packaging fix, comparison of the local rebuild and the CI candidate also found three differing SefiLib class entries: `DisplayGroupManager`, `DisplayGroupBlock` and `DisplayGroupBlock$2`. SefiLib is a mutable SNAPSHOT dependency. Runtime validation applies only to the recorded artifact hash; it does not establish byte-for-byte equivalence with a future CI rebuild.

Production was inspected through read-only file and log operations. Its JAR hash still matches intake (`292f665ffd377b28c700c073894bb33d8e7d2f5b46f34ce39e35594d07bffa9b`), no Quaptics update is pending, and the current Quaptics log scan found no errors. No production deployment or restart is authorized by this isolated failure alone.

The patched artifact is 3,101,409 bytes, SHA-256 `db4cdd8215b26301f126e4cf8e61cba8dd8ecdc3f7b373e8cca8aa682ec4169a`. `mvn -B clean package` passed through the host build lock; the ABI gate passed 1,649 classes with maximum bytecode major 69. The final fixture enabled Quaptics, registered both command aliases and both break handlers, round-tripped all 139 registered items, and found 71 connected blocks. It passed 146 assertions before stopping at `storage_int`, with an orderly server shutdown and exit code zero. The probe itself correctly returned failure.

Read-only source inspection explains that remaining failure: `BlockStorageAPI.getInt` first calls `hasData`; Slimefun's `BlockStorage.hasBlockInfo` requires an `id` field. The fixture supplies auxiliary fields but never registers a Slimefun identifier at that location. This is a missing fixture precondition, not evidence of production data loss. No further fix or runtime attempt was made after the two-failure stop. Storage validation and the subsequent beam suite remain unverified. The ticket remains BLOCKED pending fixture correction and complete QA; production staging also remains subject to the current error/WARN/security policy.

The three fixture outcomes are preserved under `docs/runtime-evidence/`: baseline enable failure, the initial patched run with the command-lookup harness defect, and the final run stopped at the storage precondition. The local built artifact and full fixture logs are retained separately for the next pass. No claim of complete runtime verification is made.
