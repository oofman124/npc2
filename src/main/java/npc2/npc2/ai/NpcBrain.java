package npc2.npc2.ai;

import io.github.oofman124.asterisk.ContextTemplate;
import io.github.oofman124.asterisk.Graph;
import net.minecraft.world.phys.Vec3;
import npc2.npc2.FakeNpcEntity;
import npc2.npc2.NpcController;
import npc2.npc2.ai.combat.AttackTargetNode;
import npc2.npc2.ai.combat.BlockMobNode;
import npc2.npc2.ai.combat.ChaseTargetNode;
import npc2.npc2.ai.combat.FocusTargetNode;
import npc2.npc2.ai.combat.PlaceCreeperCoverNode;
import npc2.npc2.ai.combat.TargetRangeNode;
import npc2.npc2.ai.equipment.EquipBestArmorNode;
import npc2.npc2.ai.equipment.EquipBestWeaponNode;
import npc2.npc2.ai.equipment.EquipShieldNode;
import npc2.npc2.ai.equipment.EquipTotemNode;
import npc2.npc2.ai.movement.IdleNode;
import npc2.npc2.ai.movement.SeekLootNode;
import npc2.npc2.ai.movement.SeekChestNode;
import npc2.npc2.ai.movement.ChestLooting;
import npc2.npc2.ai.movement.WanderNode;
import npc2.npc2.ai.sensing.SenseEntitiesNode;
import npc2.npc2.ai.util.DebounceNode;
import npc2.npc2.ai.condition.CanSeekChestNode;
import npc2.npc2.ai.condition.CanSeekGroundLootNode;
import npc2.npc2.ai.condition.CanSleepNode;
import npc2.npc2.ai.condition.HasBedTargetNode;
import npc2.npc2.ai.interaction.ClosedDoorAheadNode;
import npc2.npc2.ai.interaction.OpenDoorNode;
import npc2.npc2.ai.rest.FindBedNode;
import npc2.npc2.ai.rest.MaintainSleepNode;
import npc2.npc2.ai.rest.SleepNode;
import npc2.npc2.ai.rest.PrepareCampNode;
import npc2.npc2.ai.rest.NpcHome;
import npc2.npc2.ai.condition.NeedsHealingNode;
import npc2.npc2.ai.condition.HasFoodNode;
import npc2.npc2.ai.condition.CanCraftNode;
import npc2.npc2.ai.condition.InventoryMaintenanceDueNode;
import npc2.npc2.ai.condition.TerrainAssistanceNeededNode;
import npc2.npc2.ai.condition.CriticalHealthNode;
import npc2.npc2.ai.condition.InWaterNode;
import npc2.npc2.ai.survival.EatFoodNode;
import npc2.npc2.ai.survival.RetreatNode;
import npc2.npc2.ai.movement.FloatInWaterNode;
import npc2.npc2.ai.movement.GatherResourcesNode;
import npc2.npc2.ai.movement.DepositItemsNode;
import npc2.npc2.ai.crafting.CraftBasicSuppliesNode;
import npc2.npc2.ai.crafting.SeekCraftingTableNode;
import npc2.npc2.ai.condition.CanGatherResourcesNode;
import npc2.npc2.ai.condition.CanDepositItemsNode;
import npc2.npc2.ai.condition.CanUseCraftingTableNode;
import npc2.npc2.ai.condition.CanUseFurnaceNode;
import npc2.npc2.ai.inventory.ManageInventoryNode;
import npc2.npc2.ai.interaction.TerrainAssistNode;
import npc2.npc2.ai.processing.UseFurnaceNode;
import npc2.npc2.ai.survival.SurvivalPlanner;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public class NpcBrain {
    public final Graph graph;
    public final FakeNpcEntity npc;
    public final NpcController controller;
    public final NpcMemories memories;
    public final ContextTemplate template;

    public final SenseEntitiesNode senseEntitiesNode;
    public final EquipBestWeaponNode equipBestWeaponNode;
    public final EquipTotemNode equipTotemNode;
    public final EquipShieldNode equipShieldNode;
    public final EquipBestArmorNode equipBestArmorNode;
    public final BlockMobNode blockMobNode;
    public final PlaceCreeperCoverNode placeCreeperCoverNode;
    public final CanSeekGroundLootNode canSeekGroundLootNode;
    public final SeekLootNode seekLootNode;
    public final CanSeekChestNode canSeekChestNode;
    public final SeekChestNode seekChestNode;
    public final MaintainSleepNode maintainSleepNode;
    public final CanSleepNode canSleepNode;
    public final FindBedNode findBedNode;
    public final HasBedTargetNode hasBedTargetNode;
    public final SleepNode sleepNode;
    public final PrepareCampNode prepareCampNode;
    public final ClosedDoorAheadNode closedDoorAheadNode;
    public final OpenDoorNode openDoorNode;
    public final NeedsHealingNode needsHealingNode;
    public final HasFoodNode hasFoodNode;
    public final EatFoodNode eatFoodNode;
    public final CanCraftNode canCraftNode;
    public final CraftBasicSuppliesNode craftBasicSuppliesNode;
    public final InventoryMaintenanceDueNode inventoryMaintenanceDueNode;
    public final ManageInventoryNode manageInventoryNode;
    public final TerrainAssistanceNeededNode terrainAssistanceNeededNode;
    public final TerrainAssistNode terrainAssistNode;
    public final CriticalHealthNode criticalHealthNode;
    public final RetreatNode retreatNode;
    public final InWaterNode inWaterNode;
    public final FloatInWaterNode floatInWaterNode;
    public final CanGatherResourcesNode canGatherResourcesNode;
    public final GatherResourcesNode gatherResourcesNode;
    public final CanDepositItemsNode canDepositItemsNode;
    public final DepositItemsNode depositItemsNode;
    public final CanUseCraftingTableNode canUseCraftingTableNode;
    public final SeekCraftingTableNode seekCraftingTableNode;
    public final CanUseFurnaceNode canUseFurnaceNode;
    public final UseFurnaceNode useFurnaceNode;
    public final FocusTargetNode focusTargetNode;
    public final ChaseTargetNode chaseTargetNode;
    public final TargetRangeNode targetRangeNode;
    public final DebounceNode attackDebounceNode;
    public final AttackTargetNode attackTargetNode;
    public final WanderNode wanderNode;
    public final IdleNode idleNode;

    public NpcBrain(FakeNpcEntity npc, @Nullable Graph graph) {
        this.graph = (graph != null) ? graph : new Graph("NpcBrain");
        this.npc = npc;
        this.controller = npc.getController();
        this.memories = npc.getMemories();
        this.memories.plan = SurvivalPlanner.create(npc, this.controller);
        // The parent template contains stable object references. Each GlobalEventNode
        // creates a fresh child context for values that are valid for only that event.
        this.template = new ContextTemplate(NpcContext.stable(npc, this.controller, this, this.memories));

        this.graph.applyContextTemplate(this.template);
        this.graph.addGlobalEventNode("Tick", null, 1);

        // Keep broad scans inside the entity's useful follow range. A 500-block AABB made
        // every NPC inspect a huge portion of the loaded world for targets it could not pursue.
        this.senseEntitiesNode = new SenseEntitiesNode("SenseEntities", 48.0D);
        this.equipBestWeaponNode = new EquipBestWeaponNode("EquipBestWeapon");
        this.equipTotemNode = new EquipTotemNode("EquipTotem");
        this.equipShieldNode = new EquipShieldNode("EquipShield");
        this.equipBestArmorNode = new EquipBestArmorNode("EquipBestArmor");
        this.blockMobNode = new BlockMobNode("BlockMob");
        this.placeCreeperCoverNode = new PlaceCreeperCoverNode("PlaceCreeperCover");
        this.canSeekGroundLootNode = new CanSeekGroundLootNode("CanSeekGroundLoot", this);
        this.seekLootNode = new SeekLootNode("SeekLoot", 64.0D, 6.0D);
        this.canSeekChestNode = new CanSeekChestNode("CanSeekChest", this, 6.0D);
        this.seekChestNode = new SeekChestNode("SeekChest", 52.0D);
        this.maintainSleepNode = new MaintainSleepNode("MaintainSleep");
        this.canSleepNode = new CanSleepNode("CanSleep", this);
        this.findBedNode = new FindBedNode("FindBed", 32);
        this.hasBedTargetNode = new HasBedTargetNode("HasBedTarget", this);
        this.sleepNode = new SleepNode("Sleep");
        this.prepareCampNode = new PrepareCampNode("PrepareCamp");
        this.closedDoorAheadNode = new ClosedDoorAheadNode("ClosedDoorAhead", this);
        this.openDoorNode = new OpenDoorNode("OpenDoor");
        this.needsHealingNode = new NeedsHealingNode("NeedsHealing", this, 0.7F);
        this.hasFoodNode = new HasFoodNode("HasFood", this);
        this.eatFoodNode = new EatFoodNode("EatFood");
        this.canCraftNode = new CanCraftNode("CanCraft", this);
        this.craftBasicSuppliesNode = new CraftBasicSuppliesNode("CraftBasicSupplies");
        this.inventoryMaintenanceDueNode = new InventoryMaintenanceDueNode("InventoryMaintenanceDue", this, 200);
        this.manageInventoryNode = new ManageInventoryNode("ManageInventory");
        this.terrainAssistanceNeededNode = new TerrainAssistanceNeededNode("TerrainAssistanceNeeded", this);
        this.terrainAssistNode = new TerrainAssistNode("TerrainAssist");
        this.criticalHealthNode = new CriticalHealthNode("CriticalHealth", this, 0.35F);
        this.retreatNode = new RetreatNode("Retreat");
        this.inWaterNode = new InWaterNode("InWater", this);
        this.floatInWaterNode = new FloatInWaterNode("FloatInWater");
        this.canGatherResourcesNode = new CanGatherResourcesNode("CanGatherResources", this);
        this.gatherResourcesNode = new GatherResourcesNode("GatherResources", 88);
        this.canDepositItemsNode = new CanDepositItemsNode("CanDepositItems", this);
        this.depositItemsNode = new DepositItemsNode("DepositItems", 32.0D);
        this.canUseCraftingTableNode = new CanUseCraftingTableNode("CanUseCraftingTable", this);
        this.seekCraftingTableNode = new SeekCraftingTableNode("SeekCraftingTable", 24);
        this.canUseFurnaceNode = new CanUseFurnaceNode("CanUseFurnace", this);
        this.useFurnaceNode = new UseFurnaceNode("UseFurnace", 24);
        this.focusTargetNode = new FocusTargetNode("FocusTarget");
        this.chaseTargetNode = new ChaseTargetNode("ChaseTarget", 0.25D);
        this.targetRangeNode = new TargetRangeNode("TargetRange", 2.25D);
        this.attackDebounceNode = new DebounceNode("AttackDebounce", this, 12);
        this.attackTargetNode = new AttackTargetNode("AttackTarget");
        this.wanderNode = new WanderNode("Wander", new Vec3(30, 30, 30));
        this.idleNode = new IdleNode("Idle", 8.0D);

        this.graph.addNode(this.senseEntitiesNode);
        this.graph.addNode(this.equipBestWeaponNode);
        this.graph.addNode(this.equipTotemNode);
        this.graph.addNode(this.equipShieldNode);
        this.graph.addNode(this.equipBestArmorNode);
        this.graph.addNode(this.blockMobNode);
        this.graph.addNode(this.placeCreeperCoverNode);
        this.graph.addNode(this.canSeekGroundLootNode);
        this.graph.addNode(this.seekLootNode);
        this.graph.addNode(this.canSeekChestNode);
        this.graph.addNode(this.seekChestNode);
        this.graph.addNode(this.maintainSleepNode);
        this.graph.addNode(this.canSleepNode);
        this.graph.addNode(this.findBedNode);
        this.graph.addNode(this.hasBedTargetNode);
        this.graph.addNode(this.sleepNode);
        this.graph.addNode(this.prepareCampNode);
        this.graph.addNode(this.closedDoorAheadNode);
        this.graph.addNode(this.openDoorNode);
        this.graph.addNode(this.needsHealingNode);
        this.graph.addNode(this.hasFoodNode);
        this.graph.addNode(this.eatFoodNode);
        this.graph.addNode(this.canCraftNode);
        this.graph.addNode(this.craftBasicSuppliesNode);
        this.graph.addNode(this.inventoryMaintenanceDueNode);
        this.graph.addNode(this.manageInventoryNode);
        this.graph.addNode(this.terrainAssistanceNeededNode);
        this.graph.addNode(this.terrainAssistNode);
        this.graph.addNode(this.criticalHealthNode);
        this.graph.addNode(this.retreatNode);
        this.graph.addNode(this.inWaterNode);
        this.graph.addNode(this.floatInWaterNode);
        this.graph.addNode(this.canGatherResourcesNode);
        this.graph.addNode(this.gatherResourcesNode);
        this.graph.addNode(this.canDepositItemsNode);
        this.graph.addNode(this.depositItemsNode);
        this.graph.addNode(this.canUseCraftingTableNode);
        this.graph.addNode(this.seekCraftingTableNode);
        this.graph.addNode(this.canUseFurnaceNode);
        this.graph.addNode(this.useFurnaceNode);
        this.graph.addNode(this.focusTargetNode);
        this.graph.addNode(this.chaseTargetNode);
        this.graph.addNode(this.targetRangeNode);
        this.graph.addNode(this.attackDebounceNode);
        this.graph.addNode(this.attackTargetNode);
        this.graph.addNode(this.wanderNode);
        this.graph.addNode(this.idleNode);

        // Core combat pipeline.
        // Water state is updated before movement branches make their decisions.
        this.graph.connectSignals("Tick", "Out", this.inWaterNode.getId(), "In");
        this.graph.connectSignals(this.inWaterNode.getId(), "Out", this.floatInWaterNode.getId(), "In");
        this.graph.connectSignals("Tick", "Out", this.senseEntitiesNode.getId(), "In");
        this.graph.connectSignals(this.senseEntitiesNode.getId(), "Out", this.equipBestWeaponNode.getId(), "In");
        this.graph.connectSignals(this.equipBestWeaponNode.getId(), "Out", this.equipTotemNode.getId(), "In");
        this.graph.connectSignals(this.equipTotemNode.getId(), "Out", this.equipShieldNode.getId(), "In");
        this.graph.connectSignals(this.equipShieldNode.getId(), "Out", this.equipBestArmorNode.getId(), "In");
        this.graph.connectSignals(this.equipBestArmorNode.getId(), "Out", this.blockMobNode.getId(), "In");
        this.graph.connectSignals(this.blockMobNode.getId(), "Out", this.placeCreeperCoverNode.getId(), "In");
        // Update the escape policy before ordinary combat consumes it.
        this.graph.connectSignals(this.blockMobNode.getId(), "Out", this.criticalHealthNode.getId(), "In");
        this.graph.connectSignals(this.criticalHealthNode.getId(), "Out", this.retreatNode.getId(), "In");
        this.graph.connectSignals(this.blockMobNode.getId(), "Out", this.focusTargetNode.getId(), "In");
        this.graph.connectSignals(this.focusTargetNode.getId(), "Out", this.chaseTargetNode.getId(), "In");
        this.graph.connectSignals(this.chaseTargetNode.getId(), "Out", this.targetRangeNode.getId(), "In");
        this.graph.connectSignals(this.targetRangeNode.getId(), "Out", this.attackDebounceNode.getId(), "In");
        this.graph.connectSignals(this.attackDebounceNode.getId(), "Out", this.attackTargetNode.getId(), "In");

        // Optional-task branches. Conditions emit only when their policy is satisfied.
        this.graph.connectSignals(this.blockMobNode.getId(), "Out", this.canSeekGroundLootNode.getId(), "In");
        this.graph.connectSignals(this.canSeekGroundLootNode.getId(), "Out", this.seekLootNode.getId(), "In");
        this.graph.connectSignals(this.blockMobNode.getId(), "Out", this.canSeekChestNode.getId(), "In");
        this.graph.connectSignals(this.canSeekChestNode.getId(), "Out", this.seekChestNode.getId(), "In");

        this.graph.connectSignals(this.blockMobNode.getId(), "Out", this.maintainSleepNode.getId(), "In");
        this.graph.connectSignals(this.maintainSleepNode.getId(), "Out", this.canSleepNode.getId(), "In");
        this.graph.connectSignals(this.canSleepNode.getId(), "Out", this.findBedNode.getId(), "In");
        this.graph.connectSignals(this.findBedNode.getId(), "Out", this.prepareCampNode.getId(), "In");
        this.graph.connectSignals(this.prepareCampNode.getId(), "Out", this.hasBedTargetNode.getId(), "In");
        this.graph.connectSignals(this.hasBedTargetNode.getId(), "Out", this.sleepNode.getId(), "In");

        this.graph.connectSignals(this.blockMobNode.getId(), "Out", this.closedDoorAheadNode.getId(), "In");
        this.graph.connectSignals(this.closedDoorAheadNode.getId(), "Out", this.openDoorNode.getId(), "In");

        // Small survival/maintenance branches.
        this.graph.connectSignals(this.blockMobNode.getId(), "Out", this.needsHealingNode.getId(), "In");
        this.graph.connectSignals(this.needsHealingNode.getId(), "Out", this.hasFoodNode.getId(), "In");
        this.graph.connectSignals(this.hasFoodNode.getId(), "Out", this.eatFoodNode.getId(), "In");

        this.graph.connectSignals(this.blockMobNode.getId(), "Out", this.canCraftNode.getId(), "In");
        this.graph.connectSignals(this.canCraftNode.getId(), "Out", this.craftBasicSuppliesNode.getId(), "In");

        this.graph.connectSignals(this.blockMobNode.getId(), "Out", this.canDepositItemsNode.getId(), "In");
        this.graph.connectSignals(this.canDepositItemsNode.getId(), "Out", this.depositItemsNode.getId(), "In");

        this.graph.connectSignals(this.blockMobNode.getId(), "Out", this.canUseCraftingTableNode.getId(), "In");
        this.graph.connectSignals(this.canUseCraftingTableNode.getId(), "Out", this.seekCraftingTableNode.getId(), "In");

        this.graph.connectSignals(this.blockMobNode.getId(), "Out", this.canUseFurnaceNode.getId(), "In");
        this.graph.connectSignals(this.canUseFurnaceNode.getId(), "Out", this.useFurnaceNode.getId(), "In");

        this.graph.connectSignals(this.blockMobNode.getId(), "Out", this.canGatherResourcesNode.getId(), "In");
        this.graph.connectSignals(this.canGatherResourcesNode.getId(), "Out", this.gatherResourcesNode.getId(), "In");

        this.graph.connectSignals(this.blockMobNode.getId(), "Out", this.inventoryMaintenanceDueNode.getId(), "In");
        this.graph.connectSignals(this.inventoryMaintenanceDueNode.getId(), "Out", this.manageInventoryNode.getId(), "In");

        this.graph.connectSignals(this.blockMobNode.getId(), "Out", this.terrainAssistanceNeededNode.getId(), "In");
        this.graph.connectSignals(this.terrainAssistanceNeededNode.getId(), "Out", this.terrainAssistNode.getId(), "In");

        // Idle behaviors run off sensing every tick
        this.graph.connectSignals(this.senseEntitiesNode.getId(), "Out", this.wanderNode.getId(), "In");
        this.graph.connectSignals(this.senseEntitiesNode.getId(), "Out", this.idleNode.getId(), "In");
    }

    public void Tick() {
        ChestLooting.tickVisual(this.npc);
        NpcHome.updateReturnIntent(this);
        this.memories.plan = SurvivalPlanner.create(this.npc, this.controller);
        NpcContext.tick(this.memories.plan)
                .forEach((key, value) -> this.graph.getGlobalContext().set(key, value));
        this.graph.fireGlobalEventNode("Tick");
    }

    /** True while the planner still expects the NPC to gather or produce something. */
    public boolean hasPlannedWork() {
        return this.memories.returningHome
                || this.memories.plan.shouldGather()
                || this.memories.plan.action() != SurvivalPlanner.Action.NONE;
    }

    /** Crafting/smelting plans should not be displaced by ordinary wandering. */
    public boolean hasProductionPlan() {
        return !this.memories.plan.shouldGather()
                && this.memories.plan.action() != SurvivalPlanner.Action.NONE;
    }
}
