package thaumcraft.common.items;

import net.minecraft.world.item.ItemStack;
import thaumcraft.common.registry.TCDataComponents;

/**
 * Small NeoForge data-component replacement for TC6's IRechargable +
 * RechargeHelper path. The original mod stored a charge pool on several magic
 * items and then buffered that charge into a short lived "energy" value while
 * the item was actively used. Keeping both numbers as synced components makes
 * Traveller Boots and the next armor/bauble ports deterministic on server and
 * visible on client HUD/tooltip code later.
 */
public final class TCRechargeHelper {
    private TCRechargeHelper() {
    }

    public static int getCharge(ItemStack stack) {
        return Math.max(0, stack.getOrDefault(TCDataComponents.CHARGE.get(), 0));
    }

    public static int getActiveEnergy(ItemStack stack) {
        return Math.max(0, stack.getOrDefault(TCDataComponents.ACTIVE_ENERGY.get(), 0));
    }

    public static boolean hasUsableEnergy(ItemStack stack) {
        return getCharge(stack) > 0 || getActiveEnergy(stack) > 0;
    }

    public static void ensureInitialCharge(ItemStack stack, int maxCharge) {
        if (!stack.has(TCDataComponents.CHARGE.get())) {
            stack.set(TCDataComponents.CHARGE.get(), Math.max(0, maxCharge));
        }
        if (!stack.has(TCDataComponents.ACTIVE_ENERGY.get())) {
            stack.set(TCDataComponents.ACTIVE_ENERGY.get(), 0);
        }
    }

    public static void clampCharge(ItemStack stack, int maxCharge) {
        int max = Math.max(0, maxCharge);
        int charge = Math.min(getCharge(stack), max);
        int energy = Math.min(getActiveEnergy(stack), max);
        stack.set(TCDataComponents.CHARGE.get(), charge);
        stack.set(TCDataComponents.ACTIVE_ENERGY.get(), energy);
    }

    /**
     * Mirrors TC6 Traveller Boots: every second active energy ticks down; when
     * it empties, one stored charge is consumed and converted into a 60 second
     * active energy buffer.
     */
    public static boolean tickOneSecondEnergyBuffer(ItemStack stack, int maxCharge, int refillEnergy) {
        ensureInitialCharge(stack, maxCharge);
        clampCharge(stack, maxCharge);

        int energy = getActiveEnergy(stack);
        if (energy > 0) {
            stack.set(TCDataComponents.ACTIVE_ENERGY.get(), energy - 1);
            return true;
        }

        int charge = getCharge(stack);
        if (charge <= 0) {
            return false;
        }

        stack.set(TCDataComponents.CHARGE.get(), charge - 1);
        stack.set(TCDataComponents.ACTIVE_ENERGY.get(), Math.max(0, refillEnergy));
        return true;
    }
}
