package npc2.npc2.ai.inventory;

import io.github.oofman124.asterisk.Context;
import io.github.oofman124.asterisk.nodes.ExecutableNode;
import io.github.oofman124.asterisk.ports.SignalPort;
import io.github.oofman124.asterisk.ports.SignalPortMode;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import npc2.npc2.FakeNpcEntity;
import npc2.npc2.NpcController;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Compacts stacks and sorts survival-critical items toward the front of the bag. */
@NullMarked
public class ManageInventoryNode extends ExecutableNode {
    public final SignalPort outPort;

    public ManageInventoryNode(String id) {
        super(id);
        this.outPort = new SignalPort("Out", SignalPortMode.SEND, context -> null);
        this.getSignalPorts().put("Out", this.outPort);
    }

    @Override
    protected void onExecute(Context context) {
        if (context != null
                && context.get("Npc") instanceof FakeNpcEntity npc
                && context.get("Controller") instanceof NpcController controller) {
            SimpleContainer bag = npc.getInventory();
            List<ItemStack> stacks = new ArrayList<>(bag.removeAllItems());
            stacks.sort(Comparator.comparingDouble(stack -> -priority(controller, npc, stack)));
            for (ItemStack stack : stacks) {
                if (!stack.isEmpty()) {
                    bag.addItem(stack);
                }
            }
            bag.setChanged();
        }
        this.outPort.fire(context);
    }

    private static double priority(NpcController controller, FakeNpcEntity npc, ItemStack stack) {
        if (controller.isTotem(stack)) return 1000.0D;
        if (controller.isFood(stack)) return 900.0D;
        if (controller.isShield(stack)) return 800.0D;
        if (controller.isArmor(stack)) return 700.0D + controller.getArmorScore(stack);
        if (controller.isWeapon(stack)) return 600.0D + controller.getItemAttackDamage(stack);
        if (stack.getItem() instanceof BlockItem) return 300.0D;
        return 100.0D;
    }
}
