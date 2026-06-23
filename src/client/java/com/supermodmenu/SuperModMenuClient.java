package com.supermodmenu;

import com.supermodmenu.data.ModDataManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public class SuperModMenuClient implements ClientModInitializer {

    public static final String MOD_ID = "supermodmenu";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /**
     * Modrinth requires a descriptive, uniquely-identifying User-Agent on every API
     * request. A generic one gets rate-limited by Cloudflare (error 1015). Built from
     * the single identity source so contact info stays consistent.
     */
    public static final String USER_AGENT =
            "devxkamlesh/supermodmenu/1.0.0 (+" + Attribution.URL + ")";

    @Override
    public void onInitializeClient() {
        LOGGER.info("Super Mod Menu initializing...");
        ModDataManager.init();
        LOGGER.info("Super Mod Menu ready.");
    }
}
