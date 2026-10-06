package ace.actually.nautical;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class NauticalCreativeTab {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(BuiltInRegistries.CREATIVE_MODE_TAB, Nautical.MOD_ID);

    public static final Supplier<CreativeModeTab> TAB = CREATIVE_TABS.register(
            "nautical_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("nautical.tab"))
                    .icon(() -> {
                        ItemStack stack = new ItemStack(NauticalItems.SHIP_IN_A_BOTTLE.get());
                        CompoundTag tag = new CompoundTag();
                        tag.put("ship", new CompoundTag());
                        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                        return stack;
                    })
                    .displayItems((params, output) ->
                            output.accept(NauticalItems.SHIP_IN_A_BOTTLE.get()))
                    .build()
    );
}
