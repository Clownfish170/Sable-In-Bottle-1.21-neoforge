package ace.actually.nautical.loot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

public class ShipInBottleLootModifier extends LootModifier {
    public static final MapCodec<ShipInBottleLootModifier> CODEC = RecordCodecBuilder.mapCodec(inst ->
            codecStart(inst).and(
                    inst.group(
                            ItemStack.CODEC.fieldOf("item").forGetter(m -> m.item),
                            com.mojang.serialization.Codec.FLOAT.fieldOf("chance").forGetter(m -> m.chance)
                    )
            ).apply(inst, ShipInBottleLootModifier::new)
    );

    private final ItemStack item;
    private final float chance;

    public ShipInBottleLootModifier(LootItemCondition[] conditionsIn, ItemStack item, float chance) {
        super(conditionsIn);
        this.item = item;
        this.chance = chance;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        if (context.getRandom().nextFloat() < chance) {
            generatedLoot.add(item.copy());
        }
        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
