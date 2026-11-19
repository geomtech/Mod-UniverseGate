package fr.geomtech.universegate.mixin;

import fr.geomtech.universegate.ModItems;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {

    @Inject(method = "createResult", at = @At("TAIL"))
    private void universegate$restrictDnaEnchantmentsToUnbreaking(CallbackInfo ci) {
        AnvilMenu anvilMenu = (AnvilMenu) (Object) this;

        ItemStack baseStack = anvilMenu.getSlot(AnvilMenu.INPUT_SLOT).getItem();
        if (!baseStack.is(ModItems.DNA)) {
            return;
        }

        ItemStack resultStack = anvilMenu.getSlot(AnvilMenu.RESULT_SLOT).getItem();
        if (resultStack.isEmpty()) {
            return;
        }

        ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(resultStack);
        if (enchantments.isEmpty()) {
            return;
        }

        for (var entry : enchantments.entrySet()) {
            if (!entry.getKey().is(Enchantments.UNBREAKING)) {
                anvilMenu.getSlot(AnvilMenu.RESULT_SLOT).set(ItemStack.EMPTY);
                return;
            }
        }
    }
}
