# Isolated runtime probe

This probe runs the candidate on real Paper 26.2 with Slimefun, in a fresh flat world without players. It checks plugin enablement, command and break-listener registration, registered item identity after ItemStack serialization, connected-block registration, typed block-storage values and removal, and the display-library beam lifecycle (spawn, material, transformation and removal).

Current status: startup and registration checks pass after the PaperLib packaging fix, including 139 item round trips. The complete probe still fails at `storage_int`: the fixture writes auxiliary fields at a location without a Slimefun `id`, while the wrapper's `hasData` requires that identifier. The storage fixture must first seed a registered Slimefun block identifier. This prerequisite has been diagnosed but has not been changed or rerun after the two-failure stop. The display suite has not executed. Do not treat this harness as a green compatibility gate.

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

Success requires server exit code zero, `QUAPTICS_DONE failures=0`, and no error or exception lines, including shutdown. `result.json` records the candidate, Slimefun, Paper and Java probe SHA-256 hashes, assertion markers, warnings and errors; `console.log` retains the complete fixture log. On timeout the runner requests an orderly stop, then terminates only its own fixture process if necessary.

This probe does not exercise a player's placement or breaking actions, protection integrations, complete optical networks, production data migration or production performance. Passing does not authorize deployment. Remove these test files to undo the harness; it does not change plugin runtime code or configuration.
