package ace.actually.nautical;

import ace.actually.nautical.loot.NauticalLootModifiers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(Nautical.MOD_ID)
public class NauticalNeoForge {
    public NauticalNeoForge(IEventBus modEventBus) {
        Nautical.init();

        NauticalItems.ITEMS.register(modEventBus);
        NauticalCreativeTab.CREATIVE_TABS.register(modEventBus);
        NauticalLootModifiers.GLOBAL_LOOT_MODIFIER_SERIALIZERS.register(modEventBus);
    }
}
