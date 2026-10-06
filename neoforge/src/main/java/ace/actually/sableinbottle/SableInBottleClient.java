package ace.actually.sableinbottle;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = SableInBottle.MOD_ID, value = Dist.CLIENT)
public class SableInBottleClient {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            net.minecraft.client.renderer.item.ItemProperties.register(ModItems.SHIP_IN_A_BOTTLE.get(), SableInBottle.id("has_ship"),
                (stack, level, entity, seed) -> {
                    var data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
                    return data != null && data.copyTag().contains("ship") ? 1.0F : 0.0F;
                });
        });
    }
}
