package thaumcraft.common.items.curios;

import java.util.List;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.common.items.TCRechargeHelper;
import thaumcraft.common.registry.TCItems;

/**
 * Runtime bridge for TC6 Baubles behaviour while the NeoForge 1.21.1 port still
 * uses split item ids and data driven Curios slots.
 *
 * The Vis Amulet logic follows the TC6 crafted amulet path: every 5 ticks it
 * looks for the first rechargeable item, preferring the hotbar before the rest
 * of the inventory and worn equipment, and restores one charge.
 */
@EventBusSubscriber(modid = Thaumcraft.MODID)
public final class TCBaubleEvents {
    private static final int CRAFTED_VIS_AMULET_INTERVAL_TICKS = 5;
    private static final int HOTBAR_SIZE = 9;

    private TCBaubleEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || player.tickCount % CRAFTED_VIS_AMULET_INTERVAL_TICKS != 0) {
            return;
        }

        ItemStack visAmulet = firstMatching(player, TCItems.VIS_AMULET.get());
        if (visAmulet.isEmpty()) {
            return;
        }

        rechargeFirstEligibleStack(player, visAmulet);
    }

    private static boolean rechargeFirstEligibleStack(Player player, ItemStack source) {
        Inventory inventory = player.getInventory();
        int hotbarEnd = Math.min(HOTBAR_SIZE, inventory.items.size());

        if (rechargeFrom(inventory.items, source, 0, hotbarEnd)) {
            return true;
        }
        if (rechargeFrom(inventory.items, source, hotbarEnd, inventory.items.size())) {
            return true;
        }
        if (rechargeFrom(inventory.armor, source, 0, inventory.armor.size())) {
            return true;
        }
        return rechargeFrom(inventory.offhand, source, 0, inventory.offhand.size());
    }

    private static boolean rechargeFrom(List<ItemStack> stacks, ItemStack source, int startInclusive, int endExclusive) {
        int start = Math.max(0, startInclusive);
        int end = Math.min(stacks.size(), endExclusive);
        for (int i = start; i < end; i++) {
            ItemStack target = stacks.get(i);
            if (target.isEmpty() || target == source) {
                continue;
            }
            if (TCRechargeHelper.recharge(target, 1) > 0) {
                return true;
            }
        }
        return false;
    }

    private static ItemStack firstMatching(Player player, Item item) {
        Inventory inventory = player.getInventory();
        ItemStack stack = firstMatching(inventory.items, item);
        if (!stack.isEmpty()) {
            return stack;
        }
        stack = firstMatching(inventory.armor, item);
        if (!stack.isEmpty()) {
            return stack;
        }
        return firstMatching(inventory.offhand, item);
    }

    private static ItemStack firstMatching(List<ItemStack> stacks, Item item) {
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty() && stack.getItem() == item) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}
