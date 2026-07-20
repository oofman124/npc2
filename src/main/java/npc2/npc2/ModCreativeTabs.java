package npc2.npc2;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/** Creative inventory home for NPC2's tools and spawn items. */
public final class ModCreativeTabs {
    public static final ResourceKey<CreativeModeTab> NPC2_TAB_KEY =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, Npc2.id("npc2"));

    private ModCreativeTabs() {
    }

    public static void register() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, NPC2_TAB_KEY,
                FabricCreativeModeTab.builder()
                        .title(Component.translatable("itemGroup.npc2"))
                        .icon(() -> new ItemStack(ModItems.FAKE_NPC_SPAWN_EGG))
                        .displayItems((parameters, output) -> output.accept(ModItems.FAKE_NPC_SPAWN_EGG))
                        .build());
    }
}
