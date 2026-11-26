# Isolated runtime probe

This probe runs the candidate on real Paper 26.2 with Slimefun, in a fresh flat world without players. It checks plugin enablement, command and break-listener registration, registered item identity after ItemStack serialization, connected-block registration, typed block-storage values and removal, and the display-library beam lifecycle (spawn, material, transformation and removal).

Current status: all 167 assertions pass on Paper 26.2-133 with JDK 25, including 139 item round trips, complete queued storage deletion and the beam lifecycle across ticks. Both the server and runner exit with code zero, with no error or exception lines through shutdown. This result applies to the retained candidate with SHA-256 `db4cdd8215b26301f126e4cf8e61cba8dd8ecdc3f7b373e8cca8aa682ec4169a`; input and probe hashes are recorded in `runtime-evidence/loaded-chunk-final.json`.

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

The storage fixture registers `QP_TRANSMITTER_1` before writing auxiliary fields and verifies that Slimefun resolves that identifier. Removing individual fields is immediate; removing the complete block queues deletion in Slimefun's ticker. The probe checks complete removal on subsequent ticks, with a limit of 101 ticks, while leaving the server thread free to process the deletion. It verifies that the identity and every written field are absent. `getLocationInfo` returns an empty data object when storage is absent, so the assertion checks its values rather than expecting `null`. It never calls Slimefun's unsafe deletion methods.

The original beam lookup failure was a missing fixture chunk precondition. A plain Bukkit `BlockDisplay` control reproduces an invalid entity and unresolved UUID lookup while its chunk is unloaded; explicitly obtaining the chunk makes it valid and discoverable. The probe logs those states without entity identifiers, retains a plugin chunk ticket and waits for entity loading before running storage and beam checks. It releases the ticket after both suites finish. The beam suite checks material changes, a finite and changed transformation, visibility on the next tick, mutation through a reconstructed beam ID and complete entity removal. Storage and beam failures are reported independently, so one suite cannot suppress the other.

Success requires server exit code zero, `QUAPTICS_DONE failures=0`, and no error or exception lines, including shutdown. `result.json` records the candidate, Slimefun, Paper and Java probe SHA-256 hashes, assertion markers, warnings and errors; `console.log` retains the complete fixture log. On timeout the runner requests an orderly stop, then terminates only its own fixture process if necessary.

This probe does not exercise a player's placement or breaking actions, protection integrations, complete optical networks, production data migration or production performance. Passing does not authorize deployment. Remove these test files to undo the harness; it does not change plugin runtime code or configuration.
