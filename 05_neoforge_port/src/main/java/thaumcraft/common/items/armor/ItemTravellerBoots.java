package thaumcraft.common.items.armor;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import thaumcraft.Thaumcraft;
import thaumcraft.common.registry.TCArmorMaterials;

/**
 * The Boots of the Traveller are real foot armor again instead of a catalog
 * placeholder. TC6 grants faster travel and a full-block step while charged;
 * the attribute form keeps both effects authoritative on the server.
 */
public final class ItemTravellerBoots extends ArmorItem {
    private static final ResourceLocation SPEED_ID =
            ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "traveller_boots_speed");
    private static final ResourceLocation STEP_ID =
            ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "traveller_boots_step");

    public ItemTravellerBoots() {
        super(
                TCArmorMaterials.TRAVELLER,
                Type.BOOTS,
                TCArmorMaterials.armorProperties(Type.BOOTS, 25).rarity(Rarity.RARE)
        );
    }

    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers() {
        return super.getDefaultAttributeModifiers()
                .withModifierAdded(
                        Attributes.MOVEMENT_SPEED,
                        new AttributeModifier(SPEED_ID, 0.10D, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
                        EquipmentSlotGroup.FEET
                )
                .withModifierAdded(
                        Attributes.STEP_HEIGHT,
                        new AttributeModifier(STEP_ID, 0.40D, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.FEET
                );
    }
}
