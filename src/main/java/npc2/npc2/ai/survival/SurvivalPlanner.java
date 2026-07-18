package npc2.npc2.ai.survival;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Items;
import npc2.npc2.FakeNpcEntity;
import npc2.npc2.NpcController;
import npc2.npc2.ai.crafting.BasicCrafting;
import npc2.npc2.ai.crafting.ToolProgression;
import npc2.npc2.ai.crafting.WorkstationCrafting;
import npc2.npc2.ai.processing.FurnaceProcessing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Produces one weighted survival plan for all gathering and production decisions.
 * Scores combine ordinary stockpiles with prerequisites projected from the next
 * useful craft or smelt operation.
 */
public final class SurvivalPlanner {
    private SurvivalPlanner() {
    }

    public static Plan create(FakeNpcEntity npc, NpcController controller) {
        SimpleContainer bag = npc.getInventory();
        EnumMap<Resource, MutableNeed> needs = new EnumMap<>(Resource.class);

        int food = controller.countFood(npc);
        int logs = controller.countInventoryTag(bag, ItemTags.LOGS);
        int cobblestone = bag.countItem(Items.COBBLESTONE);
        int soil = bag.countItem(Items.DIRT) + bag.countItem(Items.NETHERRACK);
        int fuel = SurvivalNeeds.countFuel(bag);
        int rawIron = bag.countItem(Items.RAW_IRON);
        int iron = bag.countItem(Items.IRON_INGOT);
        int diamonds = bag.countItem(Items.DIAMOND);
        int wool = controller.countInventoryTag(bag, ItemTags.WOOL);
        int torches = bag.countItem(Items.TORCH);

        add(needs, Resource.FOOD, food, SurvivalNeeds.FOOD_TARGET,
                55.0D + (1.0D - npc.getHealth() / npc.getMaxHealth()) * 90.0D, "food stock and healing");
        add(needs, Resource.LOGS, logs, SurvivalNeeds.LOG_TARGET, 45.0D, "wood stockpile");
        add(needs, Resource.COBBLESTONE, cobblestone, SurvivalNeeds.COBBLESTONE_TARGET, 40.0D, "stone stockpile");
        add(needs, Resource.SOIL, soil, SurvivalNeeds.SOIL_TARGET, 22.0D, "terrain assistance blocks");
        add(needs, Resource.FUEL, fuel, SurvivalNeeds.FUEL_TARGET, 32.0D, "fuel and torches");
        add(needs, Resource.IRON_ORE, iron + rawIron, SurvivalNeeds.IRON_TARGET, 34.0D, "iron stockpile");
        add(needs, Resource.DIAMOND, diamonds, SurvivalNeeds.DIAMOND_TARGET, 18.0D, "late-game tool stockpile");
        add(needs, Resource.TORCHES, torches, SurvivalNeeds.TORCH_TARGET,
                npc.level().isBrightOutside() ? 18.0D : 42.0D, "portable light");
        if (torches < SurvivalNeeds.TORCH_TARGET) {
            require(needs, Resource.FUEL, fuel, 1, 54.0D, "coal for torches");
            require(needs, Resource.LOGS, logs, 1, 38.0D, "sticks for torches");
        }

        if (SurvivalNeeds.isNight(npc) && !SurvivalNeeds.hasBedAvailable(npc)) {
            add(needs, Resource.WOOL, wool, 3, 72.0D, "bed before night");
            require(needs, Resource.LOGS, logs, 2, 58.0D,
                    "planks for a bed");
        }

        int pickaxeTier = ToolProgression.pickaxeTier(npc);
        if (pickaxeTier == ToolProgression.NONE) {
            require(needs, Resource.LOGS, logs, 3, 95.0D,
                    "wooden pickaxe and crafting table");
        } else if (pickaxeTier == ToolProgression.WOOD) {
            require(needs, Resource.COBBLESTONE, cobblestone, 3, 90.0D,
                    "stone pickaxe upgrade");
        } else if (pickaxeTier == ToolProgression.STONE) {
            require(needs, Resource.IRON_ORE, iron + rawIron, 3, 88.0D,
                    "iron pickaxe upgrade");
        } else if (pickaxeTier == ToolProgression.IRON) {
            require(needs, Resource.DIAMOND, diamonds, 3, 82.0D,
                    "diamond pickaxe upgrade");
        }

        // Project the support chain needed to turn raw ore into an iron tool.
        if (rawIron > 0 || (pickaxeTier >= ToolProgression.STONE && iron < 3)) {
            if (!FurnaceProcessing.hasFurnaceAvailable(npc)) {
                require(needs, Resource.COBBLESTONE, cobblestone, 8, 84.0D,
                        "furnace for raw iron");
            }
            require(needs, Resource.FUEL, fuel, 1, 86.0D,
                    "smelting raw iron");
        }

        Action action = Action.NONE;
        double actionScore = 0.0D;
        double handCraftScore = BasicCrafting.craftingPriority(npc, controller);
        double workstationScore = WorkstationCrafting.craftingPriority(npc, controller);
        double furnaceScore = FurnaceProcessing.canStart(npc) ? 92.0D : 0.0D;
        if (handCraftScore >= workstationScore && handCraftScore >= furnaceScore && handCraftScore > 0.0D) {
            action = Action.HAND_CRAFT;
            actionScore = handCraftScore;
        } else if (workstationScore >= furnaceScore && workstationScore > 0.0D) {
            action = Action.CRAFTING_TABLE;
            actionScore = workstationScore;
        } else if (furnaceScore > 0.0D) {
            action = Action.FURNACE;
            actionScore = furnaceScore;
        }

        EnumMap<Resource, Need> snapshot = new EnumMap<>(Resource.class);
        needs.forEach((resource, need) -> snapshot.put(resource, need.snapshot(resource)));
        return new Plan(Map.copyOf(snapshot), action, actionScore);
    }

    private static void add(EnumMap<Resource, MutableNeed> needs, Resource resource, int current,
                            int target, double weight, String reason) {
        if (target <= current) return;
        double score = deficit(current, target) * weight;
        merge(needs, resource, current, target, score, reason);
    }

    private static void require(EnumMap<Resource, MutableNeed> needs, Resource resource, int current,
                                int target, double score, String reason) {
        if (target <= current) return;
        merge(needs, resource, current, target, score, reason);
    }

    private static void merge(EnumMap<Resource, MutableNeed> needs, Resource resource, int current,
                              int target, double score, String reason) {
        MutableNeed existing = needs.get(resource);
        if (existing == null) {
            needs.put(resource, new MutableNeed(current, target, score, reason));
        } else {
            existing.target = Math.max(existing.target, target);
            if (score > existing.score) {
                existing.score = score;
                existing.reason = reason;
            }
        }
    }

    private static double deficit(int current, int target) {
        return Math.max(0.0D, target - current) / Math.max(1, target);
    }

    public enum Resource {
        FOOD(false),
        LOGS(true),
        COBBLESTONE(true),
        SOIL(true),
        FUEL(true),
        IRON_ORE(true),
        DIAMOND(true),
        TORCHES(false),
        WOOL(false);

        private final boolean blockGatherable;

        Resource(boolean blockGatherable) {
            this.blockGatherable = blockGatherable;
        }

        public boolean blockGatherable() {
            return this.blockGatherable;
        }
    }

    public enum Action {
        NONE,
        HAND_CRAFT,
        CRAFTING_TABLE,
        FURNACE
    }

    public record Need(Resource resource, int current, int target, double score, String reason) {
    }

    public record Plan(Map<Resource, Need> needs, Action action, double actionScore) {
        public double score(Resource resource) {
            Need need = this.needs.get(resource);
            return need == null ? 0.0D : need.score;
        }

        public double highestGatheringScore() {
            return this.needs.values().stream()
                    .filter(need -> need.resource.blockGatherable())
                    .mapToDouble(Need::score)
                    .max()
                    .orElse(0.0D);
        }

        public List<Need> rankedNeeds() {
            List<Need> ranked = new ArrayList<>(this.needs.values());
            ranked.sort(Comparator.comparingDouble(Need::score).reversed());
            return List.copyOf(ranked);
        }

        public boolean shouldGather() {
            return highestGatheringScore() > this.actionScore;
        }
    }

    private static final class MutableNeed {
        private final int current;
        private int target;
        private double score;
        private String reason;

        private MutableNeed(int current, int target, double score, String reason) {
            this.current = current;
            this.target = target;
            this.score = score;
            this.reason = reason;
        }

        private Need snapshot(Resource resource) {
            return new Need(resource, this.current, this.target, this.score, this.reason);
        }
    }
}
