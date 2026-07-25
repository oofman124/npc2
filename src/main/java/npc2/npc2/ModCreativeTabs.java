package npc2.npc2;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.world.item.CreativeModeTabs;

/** Adds npc2's spawn items to the matching vanilla creative inventory tab. */
public final class ModCreativeTabs {
    private ModCreativeTabs() {
    }

    public static void register() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS)
                .register(output -> output.accept(ModItems.FAKE_NPC_SPAWN_EGG));
    }
}
