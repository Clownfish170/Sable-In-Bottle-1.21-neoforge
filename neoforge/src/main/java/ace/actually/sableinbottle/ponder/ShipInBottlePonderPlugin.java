package ace.actually.sableinbottle.ponder;

import ace.actually.sableinbottle.SableInBottle;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public class ShipInBottlePonderPlugin implements PonderPlugin {

    @Override
    public String getModId() {
        return SableInBottle.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.addStoryBoard(
            SableInBottle.id("ship_in_a_bottle"),
            "bottle/bottled_ship",
            ShipInBottlePonder::bottledShip
        );
    }
}
