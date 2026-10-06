package ace.actually.sableinbottle;

import ace.actually.sableinbottle.ponder.ShipInBottlePonder;
import ace.actually.sableinbottle.ponder.ShipInBottlePonderPlugin;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderTooltipHandler;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = SableInBottle.MOD_ID, value = Dist.CLIENT)
public class SableInBottleClient {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemProperties.register(ModItems.SHIP_IN_A_BOTTLE.get(), SableInBottle.id("has_ship"),
                (stack, level, entity, seed) -> {
                    var data = stack.get(DataComponents.CUSTOM_DATA);
                    return data != null && data.copyTag().contains("ship") ? 1.0F : 0.0F;
                });

            // Ponder: hold the ponder key (W) while hovering the bottle to inspect
            // the saved physics structure. The plugin must be added before FMLLoadComplete.
            PonderIndex.addPlugin(new ShipInBottlePonderPlugin());
            PonderTooltipHandler.registerHoveredPonderStackCallback(SableInBottleClient::trackHoveredBottle);
        });
    }

    private static void trackHoveredBottle(ItemStack stack) {
        if (stack.is(ModItems.SHIP_IN_A_BOTTLE.get())) {
            ShipInBottlePonder.lastHoveredBottle = stack.copy();
        }
    }
}
