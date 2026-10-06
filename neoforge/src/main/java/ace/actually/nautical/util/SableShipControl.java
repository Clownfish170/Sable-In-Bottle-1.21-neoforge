package ace.actually.nautical.util;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public class SableShipControl {

    /**
     * Get the ServerSubLevel that contains the given block position, if any.
     */
    public static ServerSubLevel getSubLevel(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) return null;
        SubLevel subLevel = Sable.HELPER.getContaining(serverLevel, pos);
        if (subLevel instanceof ServerSubLevel serverSubLevel && !serverSubLevel.isRemoved()) {
            return serverSubLevel;
        }
        return null;
    }
}
