package probe;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.metamechanists.quaptics.Quaptics;
import org.metamechanists.quaptics.beams.beam.DirectBeam;
import org.metamechanists.quaptics.implementation.base.QuapticBreakListener;
import org.metamechanists.quaptics.items.Groups;
import org.metamechanists.quaptics.utils.BlockStorageAPI;

import java.util.UUID;

public final class QuapticsProbe extends JavaPlugin {
    private int assertions;
    private int failures;
    private int pendingSuites;
    private Chunk fixtureChunk;

    @Override
    public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, this::run, 40L);
    }

    private void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        assertions++;
        getLogger().info("QUAPTICS_PASS " + name);
    }

    private void run() {
        try {
            check(Bukkit.getPluginManager().isPluginEnabled("Quaptics"), "enabled");
            check(Quaptics.getInstance() != null, "instance");
            check(Bukkit.getCommandMap().getCommand("quaptics") != null, "command_registered");
            check(Bukkit.getCommandMap().getCommand("qp") != null, "command_alias_registered");
            var listeners = HandlerList.getRegisteredListeners(Quaptics.getInstance());
            check(listeners.stream().filter(l -> l.getListener() instanceof QuapticBreakListener).count() == 2,
                    "break_handlers_registered");
            var items = Slimefun.getRegistry().getAllSlimefunItems().stream()
                    .filter(item -> item.getAddon() == Quaptics.getInstance()).toList();
            check(items.size() >= 60, "items_registered_" + items.size());
            for (var item : items) {
                ItemStack restored = ItemStack.deserializeBytes(item.getItem().serializeAsBytes());
                check(SlimefunItem.getByItem(restored) == item, "item_roundtrip_" + item.getId());
            }
            check(!Groups.getBlocks().isEmpty(), "connected_blocks_registered_" + Groups.getBlocks().size());
            Location source = new Location(Bukkit.getWorlds().getFirst(), 2, 65, 0);
            BlockDisplay control = source.getWorld().spawn(source, BlockDisplay.class);
            getLogger().info("QUAPTICS_CONTEXT before_get_chunk loaded="
                    + source.getWorld().isChunkLoaded(0, 0) + " valid=" + control.isValid()
                    + " lookup=" + (Bukkit.getEntity(control.getUniqueId()) == control));
            fixtureChunk = source.getChunk();
            logContext("before_ticket", control);
            control.remove();
            check(fixtureChunk.addPluginChunkTicket(this), "fixture_chunk_ticket");
            awaitFixtureChunk(20);
        } catch (Throwable error) {
            recordFailure(error);
            finish();
        }
    }

    private void logContext(String phase, BlockDisplay control) {
        getLogger().info("QUAPTICS_CONTEXT " + phase + " loaded=" + fixtureChunk.isLoaded()
                + " entities_loaded=" + fixtureChunk.isEntitiesLoaded()
                + " level=" + fixtureChunk.getLoadLevel() + " valid=" + control.isValid()
                + " lookup=" + (Bukkit.getEntity(control.getUniqueId()) == control));
    }

    private void awaitFixtureChunk(int remainingTicks) {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                if (!fixtureChunk.isEntitiesLoaded() && remainingTicks > 0) {
                    awaitFixtureChunk(remainingTicks - 1);
                    return;
                }
                check(fixtureChunk.isEntitiesLoaded(), "fixture_entities_loaded");
                Location location = new Location(fixtureChunk.getWorld(), 2, 65, 0);
                BlockDisplay control = location.getWorld().spawn(location, BlockDisplay.class);
                try {
                    logContext("after_ticket", control);
                    check(Bukkit.getEntity(control.getUniqueId()) == control, "fixture_control_lookup");
                } finally {
                    control.remove();
                }
                runSuites();
            } catch (Throwable error) {
                recordFailure(error);
                finish();
            }
        }, 1L);
    }

    private void runSuites() {
        pendingSuites = 2;
        try {
            awaitStorageClear(storageRoundTrip(), 100);
        } catch (Throwable error) {
            completeSuite("storage", error);
        }
        try {
            displayLifecycle();
        } catch (Throwable error) {
            completeSuite("beam", error);
        }
    }

    private void recordFailure(Throwable error) {
        failures++;
        getLogger().log(java.util.logging.Level.SEVERE, "QUAPTICS_FAIL", error);
    }

    private void completeSuite(String name, Throwable error) {
        if (error != null) recordFailure(error);
        getLogger().info("QUAPTICS_SUITE " + name + " success=" + (error == null));
        if (--pendingSuites == 0) finish();
    }

    private void finish() {
        if (fixtureChunk != null) fixtureChunk.removePluginChunkTicket(this);
        getLogger().info("QUAPTICS_DONE failures=" + failures + " assertions=" + assertions);
        Bukkit.getScheduler().runTaskLater(this, Bukkit::shutdown, 1L);
    }

    private void awaitStorageClear(Location location, int remainingTicks) {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                if (BlockStorageAPI.hasData(location) && remainingTicks > 0) {
                    awaitStorageClear(location, remainingTicks - 1);
                    return;
                }
                check(!BlockStorageAPI.hasData(location), "storage_cleared");
                var info = BlockStorage.getLocationInfo(location);
                check(java.util.stream.Stream.of("id", "probe_int", "probe_double", "probe_boolean",
                                "probe_face", "probe_vectorx", "probe_vectory", "probe_vectorz", "probe_uuid")
                        .allMatch(key -> info.getString(key) == null), "storage_all_fields_cleared");
                completeSuite("storage", null);
            } catch (Throwable error) {
                completeSuite("storage", error);
            }
        }, 1L);
    }

    private Location storageRoundTrip() {
        Location location = new Location(Bukkit.getWorlds().getFirst(), 0, 65, 0);
        try {
            var item = SlimefunItem.getById("QP_TRANSMITTER_1");
            check(item != null && item.getAddon() == Quaptics.getInstance(), "storage_fixture_item");
            BlockStorage.addBlockInfo(location, "id", item.getId());
            check(BlockStorageAPI.hasData(location) && BlockStorageAPI.check(location) == item,
                    "storage_fixture_registered");
            BlockStorageAPI.set(location, "probe_int", 42);
            BlockStorageAPI.set(location, "probe_double", 3.5);
            BlockStorageAPI.set(location, "probe_boolean", true);
            BlockStorageAPI.set(location, "probe_face", BlockFace.NORTH);
            Vector vector = new Vector(1.25, 2.5, -3.75);
            UUID uuid = UUID.randomUUID();
            BlockStorageAPI.set(location, "probe_vector", vector);
            BlockStorageAPI.set(location, "probe_uuid", uuid);
            check(BlockStorageAPI.getInt(location, "probe_int") == 42, "storage_int");
            check(BlockStorageAPI.getDouble(location, "probe_double") == 3.5, "storage_double");
            check(BlockStorageAPI.getBoolean(location, "probe_boolean"), "storage_boolean");
            check(BlockStorageAPI.getBlockFace(location, "probe_face").orElseThrow() == BlockFace.NORTH,
                    "storage_face");
            check(BlockStorageAPI.getVector(location, "probe_vector").orElseThrow().equals(vector), "storage_vector");
            check(BlockStorageAPI.getUuid(location, "probe_uuid").orElseThrow().equals(uuid), "storage_uuid");
            BlockStorageAPI.set(location, "probe_vector", (Vector) null);
            BlockStorageAPI.set(location, "probe_uuid", (UUID) null);
            check(BlockStorageAPI.getVector(location, "probe_vector").isEmpty(), "storage_vector_removed");
            check(BlockStorageAPI.getUuid(location, "probe_uuid").isEmpty(), "storage_uuid_removed");
        } finally {
            BlockStorageAPI.removeData(location);
        }
        return location;
    }

    private void displayLifecycle() {
        Location source = new Location(Bukkit.getWorlds().getFirst(), 2, 65, 0);
        Location target = source.clone().add(0, 0, 4);
        DirectBeam beam = new DirectBeam(Material.GLASS, source, target, 0.1F, 0);
        UUID id = beam.getId().getUUID();
        try {
            check(Bukkit.getEntity(id) instanceof BlockDisplay, "beam_spawned");
            var display = (BlockDisplay) Bukkit.getEntity(id);
            beam.setMaterial(Material.RED_STAINED_GLASS);
            check(display.getBlock().getMaterial() == Material.RED_STAINED_GLASS,
                    "beam_material");
            var originalScale = display.getTransformation().getScale();
            beam.setThicknessAndRoll(source, target.clone().add(1, 0, 0), 0.2F, 0.5);
            var scale = display.getTransformation().getScale();
            check(scale.isFinite() && !scale.equals(originalScale), "beam_transform");
            Bukkit.getScheduler().runTaskLater(this, () -> {
                try {
                    check(Bukkit.getEntity(id) == display && display.isValid(), "beam_visible_next_tick");
                    new DirectBeam(beam.getId()).setMaterial(Material.BLUE_STAINED_GLASS);
                    check(display.getBlock().getMaterial() == Material.BLUE_STAINED_GLASS,
                            "beam_reconstructed_material");
                    new DirectBeam(beam.getId()).remove();
                    check(!display.isValid() && Bukkit.getEntity(id) == null, "beam_removed");
                    completeSuite("beam", null);
                } catch (Throwable error) {
                    completeSuite("beam", error);
                } finally {
                    beam.remove();
                }
            }, 1L);
        } catch (Throwable error) {
            beam.remove();
            throw error;
        }
    }
}
