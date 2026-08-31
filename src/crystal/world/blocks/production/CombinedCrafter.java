package crystal.world.blocks.production;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.scene.ui.Image;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectIntMap;
import arc.struct.ObjectSet;
import arc.struct.IntSet;
import arc.struct.Queue;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.gen.Building;
import mindustry.gen.Icon;
import mindustry.graphics.Pal;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.type.Liquid;
import mindustry.type.LiquidStack;
import mindustry.ui.Bar;
import mindustry.ui.ReqImage;
import mindustry.world.Block;
import mindustry.world.blocks.production.GenericCrafter;
import mindustry.world.consumers.Consume;
import mindustry.world.consumers.ConsumeItems;
import mindustry.world.consumers.ConsumeLiquid;
import mindustry.world.consumers.ConsumeLiquids;
import mindustry.world.consumers.ConsumePower;
import mindustry.world.modules.ItemModule;
import mindustry.world.modules.LiquidModule;

import static mindustry.Vars.iconMed;

/**
 * 组合工厂 v10
 * 相邻放置的 CombinedCrafter 自动形成组合体，共享 items/liquids/power。
 */
public class CombinedCrafter extends GenericCrafter {

    /** 是否允许与不同类型的 CombinedCrafter 组合 */
    public boolean allowCrossTypeCombo = true;
    /** 物品容量倍率 */
    public float itemCapacityMultiplier = 1f;
    /** 液体容量倍率 */
    public float liquidCapacityMultiplier = 1f;
    /** 安全缓冲系数，产出 > 消耗 * (1 + buffer) 时才输出中间产物 */
    public float safetyBuffer = 0.15f;
    /** 兼容旧版字段：流动平滑（保留兼容） */
    public float flowSmoothing = 0.92f;
    /** 兼容旧版字段：安全缓冲秒数（保留兼容） */
    public float safetyBufferSeconds = 3f;
    /** 用于显示的原始液体容量（单块），避免被 Mindustry 传输系统误用 */
    public float baseLiquidCapacity = 10f;

    /** 缓存：该工厂类型实际涉及的所有物品（输入+输出） */
    public Seq<Item> cachedItems = new Seq<>();
    /** 缓存：该工厂类型实际涉及的所有液体（输入+输出） */
    public Seq<Liquid> cachedLiquids = new Seq<>();

    public CombinedCrafter(String name) {
        super(name);
        conductivePower = true;
        update = true;
        solid = true;
        hasItems = true;
        hasLiquids = true;
        sync = true;
    }

    @Override
    public void init() {
        super.init();
        itemCapacity = Math.max(1, (int) (itemCapacity * itemCapacityMultiplier));
        baseLiquidCapacity = Math.max(1f, liquidCapacity * liquidCapacityMultiplier);
        // 保持大值，让 LiquidSource / 管道等底层传输系统认为空间充足
        liquidCapacity = 9999f;
        hasLiquids = true;
        hasItems = true;
        cachedItems.clear();
        if (consumers != null) {
            for (Consume cons : consumers) {
                if (cons instanceof ConsumeItems ci) {
                    for (ItemStack stack : ci.items) {
                        if (!cachedItems.contains(stack.item))
                            cachedItems.add(stack.item);
                    }
                }
            }
        }
        if (outputItems != null) {
            for (ItemStack stack : outputItems) {
                if (!cachedItems.contains(stack.item))
                    cachedItems.add(stack.item);
            }
        }

        cachedLiquids.clear();
        if (consumers != null) {
            for (Consume cons : consumers) {
                if (cons instanceof ConsumeLiquid cl) {
                    if (!cachedLiquids.contains(cl.liquid))
                        cachedLiquids.add(cl.liquid);
                } else if (cons instanceof ConsumeLiquids cls) {
                    for (LiquidStack stack : cls.liquids) {
                        if (!cachedLiquids.contains(stack.liquid))
                            cachedLiquids.add(stack.liquid);
                    }
                }
            }
        }
        if (outputLiquids != null) {
            for (LiquidStack stack : outputLiquids) {
                if (!cachedLiquids.contains(stack.liquid))
                    cachedLiquids.add(stack.liquid);
            }
        }
    }

    @Override
    public TextureRegion[] icons() {
        return drawer.finalIcons(this);
    }

    // ==================== Building ====================

    public class CombinedCrafterBuild extends GenericCrafterBuild {
        /** 组合体领导者（null 表示自己就是领导） */
        public CombinedCrafterBuild comboLeader;
        /** 组合体成员列表（仅领导者维护有效列表） */
        public Seq<CombinedCrafterBuild> comboGroup = new Seq<>();
        /** 是否需要刷新组合关系 */
        public boolean comboDirty = true;

        /** 缓存：当前组合体总液体容量（由 rebuildCombo 维护） */
        public float comboTotalLiquidCap = 0f;
        /** 缓存：当前组合体总物品容量（由 rebuildCombo 维护） */
        public int comboTotalItemCap = 0;

        /** 获取当前组合体液体总数量（共享模块，直接读 currentAmount() 即可，无需遍历） */
        public float getComboTotalLiquidAmount() {
            return liquids != null ? liquids.currentAmount() : 0f;
        }

        /** 获取领导者（防御性：如果领导者已失效，自动回退到自身） */
        public CombinedCrafterBuild leader() {
            if (comboLeader != null && (!comboLeader.isValid() || comboLeader.tile == null)) {
                comboLeader = null;
            }
            return comboLeader == null ? this : comboLeader;
        }

        /** 是否是领导者 */
        public boolean isLeader() {
            return leader() == this;
        }

        /** 获取组合体成员列表（确保非空） */
        public Seq<CombinedCrafterBuild> group() {
            CombinedCrafterBuild l = leader();
            if (l.comboGroup == null)
                l.comboGroup = new Seq<>();
            return l.comboGroup;
        }

        /** 重建组合关系（BFS）并共享 items/liquids 模块 */
        /** 重建组合关系（BFS）并共享 items/liquids 模块 */
        public void rebuildCombo() {
            Seq<CombinedCrafterBuild> oldGroup = comboGroup != null ? new Seq<>(comboGroup) : new Seq<>();

            comboGroup = new Seq<>();
            comboGroup.add(this);

            IntSet visited = new IntSet();
            Queue<CombinedCrafterBuild> queue = new Queue<>();
            queue.add(this);
            visited.add(pos());

            while (!queue.isEmpty()) {
                CombinedCrafterBuild current = queue.removeFirst();
                for (Building b : current.proximity) {
                    if (b instanceof CombinedCrafterBuild other && other.team == team && other.isValid()) {
                        if (!visited.contains(other.pos())) {
                            CombinedCrafter cb = (CombinedCrafter) current.block;
                            CombinedCrafter ob = (CombinedCrafter) other.block;
                            boolean canCombo = (current.block == other.block)
                                    || cb.allowCrossTypeCombo || ob.allowCrossTypeCombo;
                            if (canCombo) {
                                visited.add(other.pos());
                                queue.addLast(other);
                                comboGroup.add(other);
                            }
                        }
                    }
                }
            }

            CombinedCrafterBuild newLeader = this;
            for (CombinedCrafterBuild b : comboGroup) {
                if (b.isValid() && b.pos() < newLeader.pos())
                    newLeader = b;
            }

            Seq<CombinedCrafterBuild> newGroup = new Seq<>(comboGroup);
            newLeader.comboGroup = newGroup;

            for (CombinedCrafterBuild b : newGroup) {
                if (b.isValid()) {
                    b.comboLeader = newLeader;
                    b.comboGroup = newGroup;
                    b.comboDirty = false;
                }
            }
            newLeader.comboLeader = null;

            float totalLiqCap = 0f;
            int totalItemCap = 0;
            for (CombinedCrafterBuild b : newGroup) {
                if (b.isValid()) {
                    totalLiqCap += ((CombinedCrafter) b.block).baseLiquidCapacity;
                    totalItemCap += b.block.itemCapacity;
                }
            }
            for (CombinedCrafterBuild b : newGroup) {
                if (b.isValid()) {
                    b.comboTotalLiquidCap = totalLiqCap;
                    b.comboTotalItemCap = totalItemCap;
                }
            }

            if (oldGroup.size > newGroup.size) {
                splitAssets(oldGroup, newGroup);
            }

            shareModules(newLeader);

            // FIX: 组合体缩小后，按新总容量截断超出的物品/液体
            if (newLeader.items != null && totalItemCap > 0) {
                int excess = newLeader.items.total() - totalItemCap;
                if (excess > 0) {
                    for (Item item : cachedItems) {
                        int amt = newLeader.items.get(item);
                        if (amt > 0) {
                            int remove = Math.min(amt, excess);
                            newLeader.items.remove(item, remove);
                            excess -= remove;
                            if (excess <= 0)
                                break;
                        }
                    }
                }
            }
            if (newLeader.liquids != null && totalLiqCap > 0.001f) {
                float excess = newLeader.liquids.currentAmount() - totalLiqCap;
                if (excess > 0.001f) {
                    for (Liquid liquid : cachedLiquids) {
                        float amt = newLeader.liquids.get(liquid);
                        if (amt > 0.001f) {
                            float remove = Math.min(amt, excess);
                            newLeader.liquids.remove(liquid, remove);
                            excess -= remove;
                            if (excess <= 0.001f)
                                break;
                        }
                    }
                }
            }

            for (CombinedCrafterBuild oldMember : oldGroup) {
                if (oldMember != this && oldMember.isValid() && !newGroup.contains(oldMember)) {
                    oldMember.comboLeader = null;
                    oldMember.comboGroup = new Seq<>();
                    oldMember.comboDirty = true;
                    oldMember.comboTotalLiquidCap = 0f;
                    oldMember.comboTotalItemCap = 0;
                }
            }

            Log.info("[组合工厂] 重建组合体: 领导者=@(@,@), 成员数=@, 总液容=@, 总物容=@",
                    newLeader.block.localizedName, newLeader.tile.x, newLeader.tile.y, newGroup.size,
                    totalLiqCap, totalItemCap);
        }

        /** 收集旧组合体中所有成员涉及过的物品/液体类型并集（基于缓存，不遍历全表） */
        public void collectInvolvedTypes(Seq<CombinedCrafterBuild> group, Seq<Item> outItems, Seq<Liquid> outLiquids) {
            outItems.clear();
            outLiquids.clear();
            for (CombinedCrafterBuild b : group) {
                if (!b.isValid())
                    continue;
                CombinedCrafter cb = (CombinedCrafter) b.block;
                for (Item item : cb.cachedItems) {
                    if (!outItems.contains(item))
                        outItems.add(item);
                }
                for (Liquid liquid : cb.cachedLiquids) {
                    if (!outLiquids.contains(liquid))
                        outLiquids.add(liquid);
                }
            }
        }

        /**
         * 分裂资产分配 —— 按旧组合体总容量比例分配，带容量限制
         * 修复：被踢出成员累计分配量不超过自身 itemCapacity / baseLiquidCapacity
         * FIX: 不再依赖 comboLeader 找旧 leader（已被 rebuildCombo 污染），改为检测实际共享引用
         */
        public void splitAssets(Seq<CombinedCrafterBuild> oldGroup, Seq<CombinedCrafterBuild> newGroup) {
            // FIX: 通过检测 items/liquids 的实际共享引用来定位旧共享模块持有者
            CombinedCrafterBuild oldLeader = null;
            for (CombinedCrafterBuild b : oldGroup) {
                if (!b.isValid())
                    continue;
                for (CombinedCrafterBuild other : oldGroup) {
                    if (other != b && other.isValid() && (other.items == b.items || other.liquids == b.liquids)) {
                        oldLeader = b;
                        break;
                    }
                }
                if (oldLeader != null)
                    break;
            }
            // 单成员或没有共享关系时，回退到第一个有效成员
            if (oldLeader == null) {
                for (CombinedCrafterBuild b : oldGroup) {
                    if (b.isValid()) {
                        oldLeader = b;
                        break;
                    }
                }
            }
            if (oldLeader == null)
                oldLeader = this;

            ItemModule oldItems = oldLeader.items;
            LiquidModule oldLiquids = oldLeader.liquids;

            Seq<CombinedCrafterBuild> kicked = new Seq<>();
            for (CombinedCrafterBuild b : oldGroup) {
                if (b.isValid() && !newGroup.contains(b))
                    kicked.add(b);
            }

            int oldTotalItemCap = 0;
            float oldTotalLiquidCap = 0f;
            for (CombinedCrafterBuild b : oldGroup) {
                if (b.isValid()) {
                    oldTotalItemCap += b.block.itemCapacity;
                    oldTotalLiquidCap += ((CombinedCrafter) b.block).baseLiquidCapacity;
                }
            }

            int newGroupItemCap = 0;
            float newGroupLiquidCap = 0f;
            for (CombinedCrafterBuild b : newGroup) {
                if (b.isValid()) {
                    newGroupItemCap += b.block.itemCapacity;
                    newGroupLiquidCap += ((CombinedCrafter) b.block).baseLiquidCapacity;
                }
            }

            int[] itemCaps = new int[kicked.size];
            float[] liquidCaps = new float[kicked.size];
            int kickedTotalItemCap = 0;
            float kickedTotalLiquidCap = 0f;
            for (int i = 0; i < kicked.size; i++) {
                CombinedCrafterBuild b = kicked.get(i);
                itemCaps[i] = b.block.itemCapacity;
                liquidCaps[i] = ((CombinedCrafter) b.block).baseLiquidCapacity;
                kickedTotalItemCap += itemCaps[i];
                kickedTotalLiquidCap += liquidCaps[i];
            }

            ItemModule[] newItemMods = new ItemModule[kicked.size];
            LiquidModule[] newLiquidMods = new LiquidModule[kicked.size];
            for (int i = 0; i < kicked.size; i++) {
                newItemMods[i] = new ItemModule();
                newLiquidMods[i] = new LiquidModule();
            }

            Seq<Item> involvedItems = new Seq<>();
            Seq<Liquid> involvedLiquids = new Seq<>();
            collectInvolvedTypes(oldGroup, involvedItems, involvedLiquids);

            // 物品分配：比例 + 容量限制（累计不超过各自 itemCapacity）
            if (oldItems != null && oldTotalItemCap > 0 && kickedTotalItemCap > 0) {
                int[] kickedAllocated = new int[kicked.size];
                for (Item item : involvedItems) {
                    int total = oldItems.get(item);
                    if (total <= 0)
                        continue;

                    int kickedTotalShare = Math.round(total * (float) kickedTotalItemCap / oldTotalItemCap);
                    kickedTotalShare = Math.min(kickedTotalShare, total);

                    int remaining = kickedTotalShare;
                    for (int i = 0; i < kicked.size; i++) {
                        int ideal = (i == kicked.size - 1) ? remaining
                                : Math.round(kickedTotalShare * (float) itemCaps[i] / kickedTotalItemCap);
                        ideal = Math.min(ideal, remaining);

                        int canTake = Math.max(0, itemCaps[i] - kickedAllocated[i]);
                        int share = Math.min(ideal, canTake);

                        if (share > 0) {
                            newItemMods[i].add(item, share);
                            kickedAllocated[i] += share;
                            remaining -= share;
                        }
                    }
                    oldItems.remove(item, kickedTotalShare - remaining);
                }
            }

            // 液体分配：比例 + 容量限制（累计不超过各自 baseLiquidCapacity）
            if (oldLiquids != null && oldTotalLiquidCap > 0.001f && kickedTotalLiquidCap > 0.001f) {
                float[] kickedAllocated = new float[kicked.size];
                for (Liquid liquid : involvedLiquids) {
                    float total = oldLiquids.get(liquid);
                    if (total <= 0.001f)
                        continue;

                    float kickedTotalShare = total * kickedTotalLiquidCap / oldTotalLiquidCap;
                    kickedTotalShare = Math.min(kickedTotalShare, total);

                    float remaining = kickedTotalShare;
                    for (int i = 0; i < kicked.size; i++) {
                        float ideal = (i == kicked.size - 1) ? remaining
                                : kickedTotalShare * liquidCaps[i] / kickedTotalLiquidCap;
                        ideal = Math.min(ideal, remaining);

                        float canTake = Math.max(0f, liquidCaps[i] - kickedAllocated[i]);
                        float share = Math.min(ideal, canTake);

                        if (share > 0.001f) {
                            newLiquidMods[i].add(liquid, share);
                            kickedAllocated[i] += share;
                            remaining -= share;
                        }
                    }
                    oldLiquids.remove(liquid, kickedTotalShare - remaining);
                }
            }

            // FIX: 无论是否有 kicked 成员，都确保 newGroup 继承旧共享模块
            if (oldItems != null) {
                for (CombinedCrafterBuild b : newGroup) {
                    if (b.isValid())
                        b.items = oldItems;
                }
            }
            if (oldLiquids != null) {
                for (CombinedCrafterBuild b : newGroup) {
                    if (b.isValid())
                        b.liquids = oldLiquids;
                }
            }

            for (int i = 0; i < kicked.size; i++) {
                CombinedCrafterBuild b = kicked.get(i);
                b.items = newItemMods[i];
                b.liquids = newLiquidMods[i];
            }
        }

        /**
         * 共享 items 和 liquids 模块 —— 修复：按总量截断，防止超上限
         * FIX: 如果 leader 本身缺少 items/liquids 模块，先向 group 中其他成员借用
         * FIX: 使用 ObjectSet 防止重复合并同一个模块（旧成员共享同一模块时只合并一次）
         */
        public void shareModules(CombinedCrafterBuild leader) {
            int totalItemCap = leader.comboTotalItemCap;
            float totalLiquidCap = leader.comboTotalLiquidCap;

            // FIX: leader 没有 items/liquids 模块时，向 group 中第一个有模块的成员借用
            if (leader.items == null) {
                for (CombinedCrafterBuild member : group()) {
                    if (member.items != null) {
                        leader.items = member.items;
                        break;
                    }
                }
            }
            if (leader.liquids == null) {
                for (CombinedCrafterBuild member : group()) {
                    if (member.liquids != null) {
                        leader.liquids = member.liquids;
                        break;
                    }
                }
            }

            // FIX: 使用 Set 记录已处理的模块，防止旧成员共享同一模块时被重复合并
            ObjectSet<ItemModule> processedItems = new ObjectSet<>();
            ObjectSet<LiquidModule> processedLiquids = new ObjectSet<>();

            if (leader.items != null) {
                processedItems.add(leader.items);
                for (CombinedCrafterBuild member : group()) {
                    if (member != leader && member.isValid() && member.items != null
                            && !processedItems.contains(member.items)) {
                        processedItems.add(member.items);
                        for (Item item : ((CombinedCrafter) member.block).cachedItems) {
                            int amt = member.items.get(item);
                            if (amt > 0) {
                                int currentTotal = leader.items.total();
                                int canAccept = Math.max(0, totalItemCap - currentTotal);
                                int transfer = Math.min(amt, canAccept);
                                if (transfer > 0)
                                    leader.items.add(item, transfer);
                            }
                        }
                    }
                }
                for (CombinedCrafterBuild member : group()) {
                    if (member.isValid())
                        member.items = leader.items;
                }
            }

            if (leader.liquids != null) {
                processedLiquids.add(leader.liquids);
                for (CombinedCrafterBuild member : group()) {
                    if (member != leader && member.isValid() && member.liquids != null
                            && !processedLiquids.contains(member.liquids)) {
                        processedLiquids.add(member.liquids);
                        for (Liquid liquid : ((CombinedCrafter) member.block).cachedLiquids) {
                            float amt = member.liquids.get(liquid);
                            if (amt > 0.001f) {
                                float currentTotal = leader.liquids.currentAmount();
                                float canAccept = Math.max(0f, totalLiquidCap - currentTotal);
                                float transfer = Math.min(amt, canAccept);
                                if (transfer > 0.001f)
                                    leader.liquids.add(liquid, transfer);
                            }
                        }
                    }
                }
                for (CombinedCrafterBuild member : group()) {
                    if (member.isValid())
                        member.liquids = leader.liquids;
                }
            }

            Log.info("[组合工厂] 模块共享完成: 领导者物品总数=@, 液体总数=@",
                    leader.items != null ? leader.items.total() : 0,
                    leader.liquids != null ? leader.liquids.currentAmount() : 0f);
        }

        @Override
        public void created() {
            super.created();
            comboDirty = true;
        }

        @Override
        public void onProximityUpdate() {
            super.onProximityUpdate();
            comboDirty = true;
            for (CombinedCrafterBuild member : group()) {
                if (member.isValid())
                    member.comboDirty = true;
            }
        }

        @Override
        public void onRemoved() {
            Seq<CombinedCrafterBuild> members = new Seq<>(group());
            boolean wasLeader = isLeader();

            // FIX: 先保存原始共享模块，再做防御性复制
            // leader 拆除时要分配的就是这个原始模块，绝不能提前清空
            ItemModule oldItems = this.items;
            LiquidModule oldLiquids = this.liquids;

            // 只有非 leader 才需要提前复制空模块
            // （防止 super.onRemoved 期间误清 leader 的共享模块）
            if (!wasLeader) {
                if (items != null) {
                    boolean shared = false;
                    for (CombinedCrafterBuild member : members) {
                        if (member != this && member.isValid() && member.items == this.items) {
                            shared = true;
                            break;
                        }
                    }
                    if (shared)
                        items = new ItemModule();
                }
                if (liquids != null) {
                    boolean shared = false;
                    for (CombinedCrafterBuild member : members) {
                        if (member != this && member.isValid() && member.liquids == this.liquids) {
                            shared = true;
                            break;
                        }
                    }
                    if (shared)
                        liquids = new LiquidModule();
                }
            }

            if (wasLeader) {
                Seq<CombinedCrafterBuild> survivors = new Seq<>();
                for (CombinedCrafterBuild b : members) {
                    if (b != this && b.isValid())
                        survivors.add(b);
                }

                int[] itemCaps = new int[survivors.size];
                float[] liquidCaps = new float[survivors.size];
                int totalItemCap = 0;
                float totalLiquidCap = 0f;
                for (int i = 0; i < survivors.size; i++) {
                    CombinedCrafterBuild b = survivors.get(i);
                    itemCaps[i] = b.block.itemCapacity;
                    liquidCaps[i] = ((CombinedCrafter) b.block).baseLiquidCapacity;
                    totalItemCap += itemCaps[i];
                    totalLiquidCap += liquidCaps[i];
                }

                ItemModule[] itemMods = new ItemModule[survivors.size];
                LiquidModule[] liquidMods = new LiquidModule[survivors.size];
                for (int i = 0; i < survivors.size; i++) {
                    itemMods[i] = new ItemModule();
                    liquidMods[i] = new LiquidModule();
                }

                Seq<Item> involvedItems = new Seq<>();
                Seq<Liquid> involvedLiquids = new Seq<>();
                collectInvolvedTypes(members, involvedItems, involvedLiquids);

                // 物品分配：加容量限制
                if (oldItems != null && totalItemCap > 0) {
                    int[] allocated = new int[survivors.size];
                    for (Item item : involvedItems) {
                        int total = oldItems.get(item);
                        if (total <= 0)
                            continue;
                        int remaining = total;
                        for (int i = 0; i < survivors.size; i++) {
                            int ideal = (i == survivors.size - 1) ? remaining
                                    : Math.round(total * (float) itemCaps[i] / totalItemCap);
                            ideal = Math.min(ideal, remaining);
                            int canTake = Math.max(0, itemCaps[i] - allocated[i]);
                            int share = Math.min(ideal, canTake);
                            if (share > 0) {
                                itemMods[i].add(item, share);
                                allocated[i] += share;
                                remaining -= share;
                            }
                        }
                    }
                }

                // 液体分配：加容量限制
                if (oldLiquids != null && totalLiquidCap > 0.001f) {
                    float[] allocated = new float[survivors.size];
                    for (Liquid liquid : involvedLiquids) {
                        float total = oldLiquids.get(liquid);
                        if (total <= 0.001f)
                            continue;
                        float remaining = total;
                        for (int i = 0; i < survivors.size; i++) {
                            float ideal = (i == survivors.size - 1) ? remaining
                                    : total * liquidCaps[i] / totalLiquidCap;
                            ideal = Math.min(ideal, remaining);
                            float canTake = Math.max(0f, liquidCaps[i] - allocated[i]);
                            float share = Math.min(ideal, canTake);
                            if (share > 0.001f) {
                                liquidMods[i].add(liquid, share);
                                allocated[i] += share;
                                remaining -= share;
                            }
                        }
                    }
                }

                for (int i = 0; i < survivors.size; i++) {
                    CombinedCrafterBuild b = survivors.get(i);
                    b.items = itemMods[i];
                    b.liquids = liquidMods[i];
                    b.comboLeader = null;
                    b.comboGroup = new Seq<>();
                    b.comboDirty = true;
                    b.comboTotalLiquidCap = 0f;
                    b.comboTotalItemCap = 0;
                }
            } else {
                CombinedCrafterBuild leader = leader();
                if (leader != null && leader.isValid() && leader != this) {
                    leader.comboDirty = true;
                }
            }

            comboLeader = null;
            comboGroup = new Seq<>();
            comboDirty = false;
            comboTotalLiquidCap = 0f;
            comboTotalItemCap = 0;
            super.onRemoved();
        }

        @Override
        public void updateTile() {
            if (isLeader() && comboDirty) {
                rebuildCombo();
            }

            if (efficiency > 0) {
                progress += getProgressIncrease(craftTime);
                warmup = Mathf.approachDelta(warmup, warmupTarget(), warmupSpeed);

                if (outputLiquids != null) {
                    float inc = getProgressIncrease(1f);
                    for (var output : outputLiquids) {
                        handleLiquid(this, output.liquid,
                                Math.min(output.amount * inc, comboTotalLiquidCap - liquids.get(output.liquid)));
                    }
                }

                if (wasVisible && Mathf.chanceDelta(updateEffectChance)) {
                    updateEffect.at(x + Mathf.range(size * updateEffectSpread),
                            y + Mathf.range(size * updateEffectSpread));
                }
            } else {
                warmup = Mathf.approachDelta(warmup, 0f, warmupSpeed);
            }

            totalProgress += warmup * Time.delta;
            if (progress >= 1f)
                craft();
            dumpOutputs();
        }

        @Override
        public boolean shouldConsume() {
            if (outputItems != null) {
                for (var output : outputItems) {
                    if (items.get(output.item) + output.amount > getMaximumAccepted(output.item)) {
                        return false;
                    }
                }
            }
            if (outputLiquids != null && !ignoreLiquidFullness) {
                boolean allFull = true;
                for (var output : outputLiquids) {
                    if (liquids.get(output.liquid) >= comboTotalLiquidCap - 0.001f) {
                        if (!dumpExtraLiquid)
                            return false;
                    } else {
                        allFull = false;
                    }
                }
                if (allFull)
                    return false;
            }
            return enabled;
        }

        @Override
        public float getProgressIncrease(float baseTime) {
            if (ignoreLiquidFullness)
                return super.getProgressIncrease(baseTime);
            float scaling = 1f, max = 1f;
            if (outputLiquids != null) {
                max = 0f;
                for (var s : outputLiquids) {
                    float value = (comboTotalLiquidCap - liquids.get(s.liquid)) / (s.amount * edelta());
                    scaling = Math.min(scaling, value);
                    max = Math.max(max, value);
                }
            }
            return super.getProgressIncrease(baseTime) * (dumpExtraLiquid ? Math.min(max, 1f) : scaling);
        }

        @Override
        public void craft() {
            consume();
            if (outputItems != null) {
                for (var output : outputItems) {
                    for (int i = 0; i < output.amount; i++)
                        items.add(output.item, 1);
                }
            }
            if (wasVisible)
                craftEffect.at(x, y);
            progress %= 1f;
        }

        @Override
        public void dumpOutputs() {
            if (timer(timerDump, dumpTime / timeScale)) {
                for (CombinedCrafterBuild member : group()) {
                    if (!member.isValid())
                        continue;
                    CombinedCrafter mb = (CombinedCrafter) member.block;
                    if (mb.outputItems != null) {
                        for (ItemStack output : mb.outputItems) {
                            boolean isIntermediate = isConsumedInCombo(output.item);
                            boolean shouldDump = !isIntermediate || shouldDumpIntermediate(output.item);
                            boolean forceDump = isIntermediate
                                    && items.get(output.item) >= getMaximumAccepted(output.item) * 0.99f;
                            if (shouldDump || forceDump)
                                dump(output.item);
                        }
                    }
                }
            }
            for (CombinedCrafterBuild member : group()) {
                if (!member.isValid())
                    continue;
                CombinedCrafter mb = (CombinedCrafter) member.block;
                if (mb.outputLiquids != null) {
                    for (int i = 0; i < mb.outputLiquids.length; i++) {
                        var output = mb.outputLiquids[i];
                        int dir = liquidOutputDirections.length > i ? liquidOutputDirections[i] : -1;
                        boolean isIntermediate = isLiquidConsumedInCombo(output.liquid);
                        boolean shouldDump = !isIntermediate || shouldDumpIntermediateLiquid(output.liquid);
                        boolean forceDump = isIntermediate
                                && liquids.get(output.liquid) >= comboTotalLiquidCap * 0.99f;
                        if (shouldDump || forceDump)
                            dumpLiquid(output.liquid, 2f, dir);
                    }
                }
            }
        }

        public boolean isConsumedInCombo(Item item) {
            for (CombinedCrafterBuild member : group()) {
                if (member.isValid() && member.block.consumesItem(item))
                    return true;
            }
            return false;
        }

        public boolean isLiquidConsumedInCombo(Liquid liquid) {
            for (CombinedCrafterBuild member : group()) {
                if (member.isValid() && member.block.consumesLiquid(liquid))
                    return true;
            }
            return false;
        }

        public boolean shouldDumpIntermediate(Item item) {
            float produceRate = 0f, consumeRate = 0f;
            int needPerCraftTotal = 0;
            for (CombinedCrafterBuild member : group()) {
                if (!member.isValid())
                    continue;
                CombinedCrafter mb = (CombinedCrafter) member.block;
                if (mb.outputItems != null) {
                    for (ItemStack out : mb.outputItems) {
                        if (out.item == item)
                            produceRate += out.amount / mb.craftTime * 60f;
                    }
                }
                if (member.block.consumers != null) {
                    for (Consume cons : member.block.consumers) {
                        if (cons instanceof ConsumeItems ci) {
                            for (ItemStack in : ci.items) {
                                if (in.item == item) {
                                    consumeRate += in.amount / mb.craftTime * 60f;
                                    needPerCraftTotal += in.amount;
                                }
                            }
                        }
                    }
                }
            }
            if (needPerCraftTotal > 0 && items.get(item) < needPerCraftTotal * 2)
                return false;
            return produceRate > consumeRate * (1f + ((CombinedCrafter) block).safetyBuffer);
        }

        public boolean shouldDumpIntermediateLiquid(Liquid liquid) {
            float produceRate = 0f, consumeRate = 0f;
            float needPerCraftTotal = 0f;
            for (CombinedCrafterBuild member : group()) {
                if (!member.isValid())
                    continue;
                CombinedCrafter mb = (CombinedCrafter) member.block;
                if (mb.outputLiquids != null) {
                    for (LiquidStack out : mb.outputLiquids) {
                        if (out.liquid == liquid)
                            produceRate += out.amount * 60f;
                    }
                }
                if (member.block.consumers != null) {
                    for (Consume cons : member.block.consumers) {
                        if (cons instanceof ConsumeLiquids cl) {
                            for (LiquidStack in : cl.liquids) {
                                if (in.liquid == liquid) {
                                    consumeRate += in.amount * 60f;
                                    needPerCraftTotal += in.amount;
                                }
                            }
                        } else if (cons instanceof ConsumeLiquid cl) {
                            if (cl.liquid == liquid) {
                                consumeRate += cl.amount * 60f;
                                needPerCraftTotal += cl.amount;
                            }
                        }
                    }
                }
            }
            if (needPerCraftTotal > 0.001f && liquids.get(liquid) < needPerCraftTotal * 2f)
                return false;
            return produceRate > consumeRate * (1f + ((CombinedCrafter) block).safetyBuffer);
        }

        @Override
        public boolean acceptItem(Building source, Item item) {
            if (!block.hasItems)
                return false;
            boolean needed = false;
            for (CombinedCrafterBuild member : group()) {
                if (member.isValid() && member.block.consumesItem(item)) {
                    needed = true;
                    break;
                }
            }
            return needed && items.get(item) < getMaximumAccepted(item);
        }

        @Override
        public int getMaximumAccepted(Item item) {
            return Math.max(comboTotalItemCap, 1);
        }

        @Override
        public void handleItem(Building source, Item item) {
            items.add(item, 1);
        }

        @Override
        public boolean acceptLiquid(Building source, Liquid liquid) {
            if (!block.hasLiquids)
                return false;
            boolean needed = false;
            for (CombinedCrafterBuild member : group()) {
                if (member.isValid() && member.block.consumesLiquid(liquid)) {
                    needed = true;
                    break;
                }
            }
            return needed && liquids.get(liquid) < comboTotalLiquidCap - 0.001f;
        }

        @Override
        public void handleLiquid(Building source, Liquid liquid, float amount) {
            if (amount <= 0.001f)
                return;
            float current = liquids.get(liquid);
            float canAccept = Math.max(0f, comboTotalLiquidCap - current);
            float actual = Math.min(amount, canAccept);
            if (actual > 0.001f)
                liquids.add(liquid, actual);
        }

        public float getTotalLiquidCapacity() {
            return Math.max(comboTotalLiquidCap, 1f);
        }

        @Override
        public void display(Table table) {
            // 把全部内容包在一个固定宽度的容器里，防止信息面板被撑得太宽
            table.table(cont -> {
                cont.top().left();
                cont.defaults().growX().left();

                cont.table(t -> {
                    t.left();
                    TextureRegion icon = block.getDisplayIcon(tile);
                    if (icon == null)
                        icon = Core.atlas.find("clear");
                    t.add(new Image(icon)).size(8 * 4);
                    int count = group().size;
                    String title = count > 1
                            ? "[accent]组合工厂[] x" + count + "\n" + block.getDisplayName(tile)
                            : block.getDisplayName(tile);
                    t.labelWrap(title).left().width(160f).padLeft(4);
                }).growX().left();
                cont.row();

                if (team != mindustry.Vars.player.team())
                    return;

                Table barsTable = new Table();
                barsTable.left();
                barsTable.update(() -> {
                    barsTable.clearChildren();
                    barsTable.defaults().growX().height(18f).pad(4);
                    buildComboBars(barsTable);
                });
                cont.add(barsTable).growX().left();
                cont.row();

                Table comboIO = new Table();
                comboIO.left();
                comboIO.update(() -> {
                    comboIO.clearChildren();
                    buildComboIO(comboIO);
                });
                cont.add(comboIO).growX().left();
                cont.row();

                Table localIO = new Table();
                localIO.left();
                localIO.update(() -> {
                    localIO.clearChildren();
                    buildLocalIO(localIO);
                });
                cont.add(localIO).growX().left();
            }).width(260f).left(); // 限制整体面板宽度为 260f
        }

        public void buildComboBars(Table table) {
            float totalPower = 0f;
            for (CombinedCrafterBuild member : group()) {
                if (member.isValid() && member.block.consPower != null)
                    totalPower += member.block.consPower.usage;
            }
            // FIX: 防御 power == null（当前建筑可能 hasPower=false）
            if (totalPower > 0 && power != null) {
                final float tp = totalPower;
                table.add(new Bar(
                        () -> "电力 " + Strings.fixed(tp * power.status * 60f, 1) + " ⚡/s",
                        () -> Pal.power,
                        () -> power.status));
                table.row();
            }

            Seq<Item> involvedItems = new Seq<>();
            for (CombinedCrafterBuild member : group()) {
                if (member.isValid()) {
                    for (Item item : ((CombinedCrafter) member.block).cachedItems) {
                        if (!involvedItems.contains(item))
                            involvedItems.add(item);
                    }
                }
            }

            if (items != null) {
                for (Item item : involvedItems) {
                    int total = items.get(item);
                    if (total > 0) {
                        final int t = total, c = Math.max(comboTotalItemCap, 1);
                        table.add(new Bar(
                                () -> item.localizedName + ": " + t + "/" + c,
                                () -> item.color,
                                () -> (float) t / c));
                        table.row();
                    }
                }
            }

            Seq<Liquid> involvedLiquids = new Seq<>();
            for (CombinedCrafterBuild member : group()) {
                if (member.isValid()) {
                    for (Liquid liquid : ((CombinedCrafter) member.block).cachedLiquids) {
                        if (!involvedLiquids.contains(liquid))
                            involvedLiquids.add(liquid);
                    }
                }
            }

            // FIX: 即使当前建筑 hasLiquids=false（this.liquids==null），
            // 回退查找组合体中任意共享的液体模块（leader 或 group 成员）
            LiquidModule sharedLiq = this.liquids;
            if (sharedLiq == null) {
                CombinedCrafterBuild l = leader();
                if (l != null)
                    sharedLiq = l.liquids;
            }
            if (sharedLiq == null) {
                for (CombinedCrafterBuild member : group()) {
                    if (member.liquids != null) {
                        sharedLiq = member.liquids;
                        break;
                    }
                }
            }
            if (sharedLiq != null) {
                for (Liquid liquid : involvedLiquids) {
                    float total = sharedLiq.get(liquid);
                    if (total > 0.001f) {
                        final float t = total, c = Math.max(comboTotalLiquidCap, 1f);
                        table.add(new Bar(
                                () -> liquid.localizedName + ": " + Strings.fixed(t, 1) + "/" + Strings.fixed(c, 1),
                                () -> liquid.barColor != null ? liquid.barColor : liquid.color,
                                () -> t / c));
                        table.row();
                    }
                }
            }
        }

        public void buildComboIO(Table table) {
            table.left();
            table.add("[lightgray]组合体构成:").left();
            table.row();

            ObjectIntMap<Block> blockCounts = new ObjectIntMap<>();
            for (CombinedCrafterBuild member : group()) {
                if (member.isValid()) {
                    int old = blockCounts.get(member.block, 0);
                    blockCounts.put(member.block, old + 1);
                }
            }

            Seq<Block> sortedBlocks = new Seq<>();
            for (Block b : blockCounts.keys())
                sortedBlocks.add(b);
            sortedBlocks.sort(b -> b.id);

            boolean hasContent = false;
            for (Block b : sortedBlocks) {
                int count = blockCounts.get(b, 0);
                if (count > 0) {
                    hasContent = true;
                    table.add(b.localizedName + "*" + count).color(Color.white).left();
                    table.row();
                }
            }
            if (!hasContent) {
                table.add("[darkGray]无").left();
                table.row();
            }
        }

        public void buildLocalIO(Table table) {
            table.left();
            CombinedCrafter mb = (CombinedCrafter) this.block;
            table.add("[lightgray]本机: " + block.localizedName + "[]").left();
            table.row();

            if (block.consumers != null && block.consumers.length > 0) {
                boolean hasLocalInput = false;
                for (Consume cons : block.consumers) {
                    if (cons instanceof ConsumeItems ci) {
                        for (ItemStack stack : ci.items) {
                            if (!hasLocalInput) {
                                table.add("[gray]输入:").left();
                                table.row();
                                hasLocalInput = true;
                            }
                            boolean has = items != null && items.get(stack.item) >= stack.amount;
                            table.table(row -> {
                                row.left();
                                if (stack.item.uiIcon != null) {
                                    row.add(new ReqImage(stack.item.uiIcon, () -> has)).size(iconMed).padRight(4f);
                                }
                                row.add(stack.item.localizedName + " x" + stack.amount)
                                        .color(has ? Color.white : Color.scarlet).left();
                            }).left();
                            table.row();
                        }
                    } else if (cons instanceof ConsumeLiquid cl) {
                        if (!hasLocalInput) {
                            table.add("[gray]输入:").left();
                            table.row();
                            hasLocalInput = true;
                        }
                        boolean has = liquids != null && liquids.get(cl.liquid) >= cl.amount * 10f;
                        table.table(row -> {
                            row.left();
                            if (cl.liquid.uiIcon != null) {
                                row.add(new ReqImage(cl.liquid.uiIcon, () -> has)).size(iconMed).padRight(4f);
                            }
                            row.add(cl.liquid.localizedName + " " + Strings.fixed(cl.amount * 60f, 1) + "/s")
                                    .color(has ? Color.white : Color.scarlet).left();
                        }).left();
                        table.row();
                    } else if (cons instanceof ConsumeLiquids cls) {
                        for (LiquidStack stack : cls.liquids) {
                            if (!hasLocalInput) {
                                table.add("[gray]输入:").left();
                                table.row();
                                hasLocalInput = true;
                            }
                            boolean has = liquids != null && liquids.get(stack.liquid) >= stack.amount * 10f;
                            table.table(row -> {
                                row.left();
                                if (stack.liquid.uiIcon != null) {
                                    row.add(new ReqImage(stack.liquid.uiIcon, () -> has)).size(iconMed).padRight(4f);
                                }
                                row.add(stack.liquid.localizedName + " " + Strings.fixed(stack.amount * 60f, 1) + "/s")
                                        .color(has ? Color.white : Color.scarlet).left();
                            }).left();
                            table.row();
                        }
                    } else if (cons instanceof ConsumePower cp) {
                        if (!hasLocalInput) {
                            table.add("[gray]输入:").left();
                            table.row();
                            hasLocalInput = true;
                        }
                        table.table(row -> {
                            row.left();
                            row.image(Icon.powerSmall).size(iconMed).padRight(4f);
                            row.add("电力 " + Strings.fixed(cp.usage * 60f, 1) + " ⚡/s").color(Color.white).left();
                        }).left();
                        table.row();
                    }
                }
            }

            boolean hasLocalOutput = false;
            if (mb.outputItems != null) {
                for (ItemStack stack : mb.outputItems) {
                    if (!hasLocalOutput) {
                        table.add("[gray]产出:").left();
                        table.row();
                        hasLocalOutput = true;
                    }
                    table.table(row -> {
                        row.left();
                        if (stack.item.uiIcon != null)
                            row.image(stack.item.uiIcon).size(24f).padRight(4f);
                        row.add(stack.item.localizedName + " x" + stack.amount + " / "
                                + Strings.fixed(mb.craftTime / 60f, 1) + "s [gray](单台)[]").color(Pal.accent).left();
                    }).left();
                    table.row();
                }
            }
            if (mb.outputLiquids != null) {
                for (LiquidStack stack : mb.outputLiquids) {
                    if (!hasLocalOutput) {
                        table.add("[gray]产出:").left();
                        table.row();
                        hasLocalOutput = true;
                    }
                    table.table(row -> {
                        row.left();
                        if (stack.liquid.uiIcon != null)
                            row.image(stack.liquid.uiIcon).size(24f).padRight(4f);
                        row.add(stack.liquid.localizedName + " " + Strings.fixed(stack.amount * 60f, 1)
                                + "/s [gray](单台)[]").color(Pal.accent).left();
                    }).left();
                    table.row();
                }
            }
            if (!hasLocalOutput && (block.consumers == null || block.consumers.length == 0)) {
                table.add("[darkGray]无").left();
                table.row();
            }
        }

        @Override
        public void write(Writes write) {
            ItemModule savedItems = items;
            LiquidModule savedLiquids = liquids;

            CombinedCrafterBuild trueLeader = this;
            if (comboGroup != null && comboGroup.size > 0) {
                for (CombinedCrafterBuild b : comboGroup) {
                    if (b != null && b.isValid() && b.pos() < trueLeader.pos())
                        trueLeader = b;
                }
            }

            if (this != trueLeader) {
                if (items != null)
                    items = new ItemModule();
                if (liquids != null)
                    liquids = new LiquidModule();
            }

            super.write(write);
            items = savedItems;
            liquids = savedLiquids;

            write.bool(comboLeader != null);
            if (comboLeader != null)
                write.i(comboLeader.pos());
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            boolean hasLeader = read.bool();
            int leaderPos = -1;
            if (hasLeader)
                leaderPos = read.i();
            comboDirty = true;
            if (hasLeader && leaderPos != pos()) {
                if (items != null)
                    items = new ItemModule();
                if (liquids != null)
                    liquids = new LiquidModule();
            }
        }
    }
}
