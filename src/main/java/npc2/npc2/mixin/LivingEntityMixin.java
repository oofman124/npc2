package npc2.npc2.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.BedBlock;
import npc2.npc2.FakeNpcEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps an intentional non-bed sleeping pose alive on both the server and client. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "checkBedExists", at = @At("HEAD"), cancellable = true)
    private void npc2$acceptFloorSleep(CallbackInfoReturnable<Boolean> callback) {
        if ((Object) this instanceof FakeNpcEntity npc
                && (npc.level().isClientSide() || npc.getMemories().floorSleeping)
                && npc.getSleepingPos()
                .map(pos -> !(npc.level().getBlockState(pos).getBlock() instanceof BedBlock))
                .orElse(false)) {
            callback.setReturnValue(true);
        }
    }
}
