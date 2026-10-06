package ace.actually.nautical.loot;

import ace.actually.nautical.Nautical;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public class NauticalLootModifiers {
    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> GLOBAL_LOOT_MODIFIER_SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, Nautical.MOD_ID);

    public static final Supplier<MapCodec<ShipInBottleLootModifier>> SHIP_IN_BOTTLE =
            GLOBAL_LOOT_MODIFIER_SERIALIZERS.register("ship_in_bottle", () -> ShipInBottleLootModifier.CODEC);
}
