package ace.actually.sableinbottle;

import ace.actually.sableinbottle.loot.ModLootModifiers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(SableInBottle.MOD_ID)
public class SableInBottleNeoForge {
    public SableInBottleNeoForge(IEventBus modEventBus) {
        SableInBottle.init();

        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModCreativeModeTabs.CREATIVE_TABS.register(modEventBus);
        ModLootModifiers.GLOBAL_LOOT_MODIFIER_SERIALIZERS.register(modEventBus);
    }
}
