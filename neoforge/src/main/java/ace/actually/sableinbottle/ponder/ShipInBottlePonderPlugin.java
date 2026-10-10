package ace.actually.sableinbottle.ponder;

import ace.actually.sableinbottle.SableInBottle;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.SharedTextRegistrationHelper;
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

    /** en_us fallbacks; players see the lang file translations (zh_cn included). */
    @Override
    public void registerSharedText(SharedTextRegistrationHelper helper) {
        helper.registerSharedText("oversized_limit",
            "The saved structure exceeds the preview limit, showing a stand-in instead");
        helper.registerSharedText("oversized_config",
            "Raise max_structure_size in config/sableinbottle.json to preview it");
        helper.registerSharedText("structure_saved",
            "This is the physics structure saved in the bottle");
        helper.registerSharedText("full_serialization",
            "Block entities, ticks and structure data are serialized in full");
        helper.registerSharedText("coordinate_free",
            "Saved data is not tied to coordinates - take it anywhere");
        helper.registerSharedText("release_anywhere",
            "On release it appears where you point");
    }
}
