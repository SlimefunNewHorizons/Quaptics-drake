package probe;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import org.bukkit.Bukkit;
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
        int failures = 0;
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
            storageRoundTrip();
            displayLifecycle();
        } catch (Throwable error) {
            failures++;
            getLogger().log(java.util.logging.Level.SEVERE, "QUAPTICS_FAIL", error);
        } finally {
            getLogger().info("QUAPTICS_DONE failures=" + failures + " assertions=" + assertions);
            Bukkit.getScheduler().runTaskLater(this, Bukkit::shutdown, 1L);
        }
    }

    private void storageRoundTrip() {
        Location location = new Location(Bukkit.getWorlds().getFirst(), 0, 65, 0);
        try {
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
        check(!BlockStorageAPI.hasData(location), "storage_cleared");
    }

    private void displayLifecycle() {
        Location source = new Location(Bukkit.getWorlds().getFirst(), 2, 65, 0);
        Location target = source.clone().add(0, 0, 4);
        DirectBeam beam = new DirectBeam(Material.GLASS, source, target, 0.1F, 0);
        UUID id = beam.getId().getUUID();
        try {
            check(Bukkit.getEntity(id) instanceof BlockDisplay, "beam_spawned");
            beam.setMaterial(Material.RED_STAINED_GLASS);
            check(((BlockDisplay) Bukkit.getEntity(id)).getBlock().getMaterial() == Material.RED_STAINED_GLASS,
                    "beam_material");
            beam.setThicknessAndRoll(source, target.clone().add(1, 0, 0), 0.2F, 0.5);
            check(((BlockDisplay) Bukkit.getEntity(id)).getTransformation().getScale().isFinite(), "beam_transform");
            var display = Bukkit.getEntity(id);
            new DirectBeam(beam.getId()).remove();
            check(!display.isValid(), "beam_removed");
        } finally {
            beam.remove();
        }
    }
}
