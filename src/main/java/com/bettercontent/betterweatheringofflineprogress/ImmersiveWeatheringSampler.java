package com.bettercontent.betterweatheringofflineprogress;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(ImmersiveWeatheringSampler.MOD_ID)
public final class ImmersiveWeatheringSampler {
    public static final String MOD_ID = "better_weathering_offline_progress";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ImmersiveWeatheringSampler() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SamplerConfig.SPEC);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(SamplerGameTests::register);
        MinecraftForge.EVENT_BUS.register(SamplerEvents.class);
    }
}
