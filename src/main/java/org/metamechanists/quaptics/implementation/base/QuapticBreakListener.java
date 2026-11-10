package org.metamechanists.quaptics.implementation.base;

import org.metamechanists.displaymodellib.sefilib.entity.display.DisplayGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.metamechanists.quaptics.utils.BlockStorageAPI;
import org.metamechanists.quaptics.utils.ProtectionCompat;

public class QuapticBreakListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractionDamage(final EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof final Player player)) {
            return;
        }

        if (!(event.getEntity() instanceof final Interaction interaction)) {
            return;
        }

        final Location location = interaction.getLocation().getBlock().getLocation();
        final SlimefunItem sfItem = BlockStorageAPI.check(location);
        if (!(sfItem instanceof final QuapticBlock quapticBlock)) {
            return;
        }

        event.setCancelled(true);

        // Chequeo de proteccion (CoreProtect / GriefPrevention / WorldGuard)
        if (!ProtectionCompat.hasPermission(player, location, "BREAK_BLOCK")) {
            return;
        }

        // Ejecutar rotura limpia
        quapticBlock.onBreak(location);
        QuapticBlock.getDisplayGroup(location.clone()).ifPresent(displayGroup -> {
            displayGroup.getDisplays().values().forEach(Entity::remove);
            displayGroup.remove();
        });
        interaction.remove();

        location.getBlock().setType(Material.AIR);
        BlockStorageAPI.removeData(location);

        // Sonido de rotura
        location.getWorld().playSound(location.toCenterLocation(), Sound.BLOCK_STONE_BREAK, 1.0F, 1.0F);

        // En supervivencia, soltar el item
        if (player.getGameMode() != GameMode.CREATIVE) {
            final ItemStack dropItem = sfItem.getItem().clone();
            location.getWorld().dropItemNaturally(location.toCenterLocation(), dropItem);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFallbackBlockBreak(final BlockBreakEvent event) {
        final Location location = event.getBlock().getLocation();
        final SlimefunItem sfItem = BlockStorageAPI.check(location);
        if (sfItem instanceof final QuapticBlock quapticBlock) {
            quapticBlock.onBreak(location);
            QuapticBlock.getDisplayGroup(location.clone()).ifPresent(displayGroup -> {
                displayGroup.getDisplays().values().forEach(Entity::remove);
                displayGroup.remove();
            });
            BlockStorageAPI.removeData(location);
        }
    }
}
