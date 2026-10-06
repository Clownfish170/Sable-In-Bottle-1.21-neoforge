package ace.actually.sableinbottle.loot;

import ace.actually.sableinbottle.SableInBottle;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public class ModLootModifiers {
    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> GLOBAL_LOOT_MODIFIER_SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, SableInBottle.MOD_ID);

    public static final Supplier<MapCodec<ShipInABottleLootModifier>> SHIP_IN_BOTTLE =
            GLOBAL_LOOT_MODIFIER_SERIALIZERS.register("ship_in_bottle", () -> ShipInABottleLootModifier.CODEC);
}
