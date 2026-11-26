# Isolated runtime probe

This probe runs the candidate on real Paper 26.2 with Slimefun, in a fresh flat world without players. It checks plugin enablement, command and break-listener registration, registered item identity after ItemStack serialization, connected-block registration, typed block-storage values and removal, and the display-library beam lifecycle (spawn, material, transformation and removal).

Current status: startup, registration and typed storage checks pass, including 139 item round trips. The fixture now registers a Slimefun block identifier. The first resumed run exposed an immediate-removal assertion against Slimefun's queued deletion; the probe now waits on subsequent ticks. The second run passes 156 assertions and stops at `beam_spawned`: `DirectBeam` returns, but immediate Bukkit lookup by its UUID does not resolve a `BlockDisplay`. Complete storage deletion and beam mutation/removal remain unverified. Two failed runs triggered the stop rule; no further runtime attempt was made. Do not treat this harness as a green compatibility gate.

Run with JDK 25 through the development host's build lock and resource limits:

```sh
python3 scripts/runtime_probe.py \
  --paper "$PAPER_JAR" \
  --paper-api "$PAPER_API_JAR" \
  --cache-dir "$PAPER_CACHE" \
  --libraries-dir "$PAPER_LIBRARIES" \
  --slimefun "$SLIMEFUN_JAR" \
  --candidate "$CANDIDATE_JAR" \
  --work-dir "$SCRATCH_DIRECTORY"
```

The runner copies server dependencies and the candidate into a unique scratch directory. It binds an ephemeral loopback port, disables addon auto-updates and metrics, and stops the server after assertions. It does not copy existing worlds, databases or production configurations. Running it accepts the Minecraft EULA for the disposable instance.

The storage fixture registers `QP_TRANSMITTER_1` before writing auxiliary fields and verifies that Slimefun resolves that identifier. Removing individual fields is immediate; removing the complete block queues deletion in Slimefun's ticker. After the beam suite passes, the probe checks complete removal on subsequent ticks, with a limit of 101 ticks, while leaving the server thread free to process the deletion. It never calls Slimefun's unsafe deletion methods.

The next investigation must distinguish a fixture chunk/entity visibility precondition from a plugin defect. The packaged display builder calls `World.spawn` synchronously, and the ID wrappers preserve the resulting UUID. Neither fact explains the unresolved lookup by itself. Retain a loaded chunk through the display test and inspect entity visibility across ticks before changing runtime plugin code; these are proposed diagnostics, not completed verification.

Success requires server exit code zero, `QUAPTICS_DONE failures=0`, and no error or exception lines, including shutdown. `result.json` records the candidate, Slimefun, Paper and Java probe SHA-256 hashes, assertion markers, warnings and errors; `console.log` retains the complete fixture log. On timeout the runner requests an orderly stop, then terminates only its own fixture process if necessary.

This probe does not exercise a player's placement or breaking actions, protection integrations, complete optical networks, production data migration or production performance. Passing does not authorize deployment. Remove these test files to undo the harness; it does not change plugin runtime code or configuration.
