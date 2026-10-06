package ace.actually.nautical;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = Nautical.MOD_ID, value = Dist.CLIENT)
public class NauticalNeoForgeClient {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            net.minecraft.client.renderer.item.ItemProperties.register(NauticalItems.SHIP_IN_A_BOTTLE.get(), Nautical.id("has_ship"),
                (stack, level, entity, seed) -> {
                    var data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
                    return data != null && data.copyTag().contains("ship") ? 1.0F : 0.0F;
                });
        });
    }
}
