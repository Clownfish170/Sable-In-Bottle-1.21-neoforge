package ace.actually.nautical;

import ace.actually.nautical.items.ShipInABottleItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class NauticalItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Nautical.MOD_ID);

    public static final Supplier<ShipInABottleItem> SHIP_IN_A_BOTTLE = ITEMS.register(
            "ship_in_a_bottle", () -> new ShipInABottleItem(new Item.Properties()));
}
