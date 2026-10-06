package ace.actually.sableinbottle;

import ace.actually.sableinbottle.item.ShipInABottleItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SableInBottle.MOD_ID);

    public static final Supplier<ShipInABottleItem> SHIP_IN_A_BOTTLE = ITEMS.register(
            "ship_in_a_bottle", () -> new ShipInABottleItem(new Item.Properties()));
}
