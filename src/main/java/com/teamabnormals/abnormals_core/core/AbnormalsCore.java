package com.teamabnormals.abnormals_core.core;

import com.teamabnormals.abnormals_core.core.api.banner.BannerManager;
import com.teamabnormals.abnormals_core.core.registry.ACEntities;
import com.teamabnormals.abnormals_core.core.registry.ACTileEntities;
import com.teamabnormals.abnormals_core.core.util.registry.RegistryHelper;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.teamabnormals.abnormals_core.client.renderer.AbnormalsBoatRenderer;
import com.teamabnormals.abnormals_core.client.tile.*;
import com.teamabnormals.abnormals_core.common.blocks.AbnormalsBeehiveBlock;
import com.teamabnormals.abnormals_core.common.capability.chunkloading.*;
import com.teamabnormals.abnormals_core.common.network.*;
import com.teamabnormals.abnormals_core.common.network.entity.*;
import com.teamabnormals.abnormals_core.common.network.particle.*;
import com.teamabnormals.abnormals_core.core.config.ACConfig;
import com.teamabnormals.abnormals_core.core.api.conditions.*;
import com.teamabnormals.abnormals_core.core.endimator.EndimationDataManager;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.IReloadableResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.village.PointOfInterestType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DeferredWorkQueue;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.simple.SimpleChannel;
import net.minecraftforge.registries.ForgeRegistries;

//TODO: update package to com.minecraftabnormals.abnormals_core
@SuppressWarnings("deprecation")
@Mod(AbnormalsCore.MODID)
@Mod.EventBusSubscriber(modid = AbnormalsCore.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class AbnormalsCore {
	public static final Logger LOGGER = LogManager.getLogger();
	public static final String MODID = "abnormals_core";
	public static final String NETWORK_PROTOCOL = "AC1";
	public static final EndimationDataManager ENDIMATION_DATA_MANAGER = new EndimationDataManager();
	public static final RegistryHelper REGISTRY_HELPER = new RegistryHelper.Builder(MODID).build();

	public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder.named(new ResourceLocation(MODID, "net"))
			.networkProtocolVersion(() -> NETWORK_PROTOCOL)
			.clientAcceptedVersions(NETWORK_PROTOCOL::equals)
			.serverAcceptedVersions(NETWORK_PROTOCOL::equals)
			.simpleChannel();

	public AbnormalsCore() {
		IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
		MinecraftForge.EVENT_BUS.register(this);
		MinecraftForge.EVENT_BUS.register(new ChunkLoaderEvents());

		this.setupMessages();

		CraftingHelper.register(new QuarkFlagRecipeCondition.Serializer());
		CraftingHelper.register(new ACAndRecipeCondition.Serializer());
		BannerManager.RECIPE_SERIALIZERS.register(modEventBus);

		REGISTRY_HELPER.getEntitySubHelper().register(modEventBus);
		REGISTRY_HELPER.getTileEntitySubHelper().register(modEventBus);

		modEventBus.addListener((ModConfig.ModConfigEvent event) -> {
			final ModConfig config = event.getConfig();
			if (config.getSpec() == ACConfig.COMMON_SPEC) {
				ACConfig.ValuesHolder.updateCommonValuesFromConfig(config);
			}
		});

		DistExecutor.runWhenOn(Dist.CLIENT, () -> () -> {
			((IReloadableResourceManager) Minecraft.getInstance().getResourceManager()).addReloadListener(ENDIMATION_DATA_MANAGER);
			modEventBus.addListener(this::clientSetup);
		});

		modEventBus.addListener(EventPriority.LOWEST, this::commonSetup);
		ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ACConfig.COMMON_SPEC);
	}

	private void commonSetup(final FMLCommonSetupEvent event) {
		DeferredWorkQueue.runLater(() -> {
			this.replaceBeehivePOI();
		});
		ChunkLoaderCapability.register();
	}

	@OnlyIn(Dist.CLIENT)
	private void clientSetup(final FMLClientSetupEvent event) {
		RenderingRegistry.registerEntityRenderingHandler(ACEntities.BOAT.get(), AbnormalsBoatRenderer::new);

		ClientRegistry.bindTileEntityRenderer(ACTileEntities.CHEST.get(), AbnormalsChestTileEntityRenderer::new);
		ClientRegistry.bindTileEntityRenderer(ACTileEntities.TRAPPED_CHEST.get(), AbnormalsChestTileEntityRenderer::new);
		ClientRegistry.bindTileEntityRenderer(ACTileEntities.SIGN.get(), AbnormalsSignTileEntityRenderer::new);
	}

	private void setupMessages() {
		int id = -1;

		CHANNEL.registerMessage(id++, MessageS2CEndimation.class, MessageS2CEndimation::serialize, MessageS2CEndimation::deserialize, MessageS2CEndimation::handle);
		CHANNEL.registerMessage(id++, MessageSOpenSignEditor.class, MessageSOpenSignEditor::serialize, MessageSOpenSignEditor::deserialize, MessageSOpenSignEditor::handle);
		CHANNEL.registerMessage(id++, MessageC2SEditSign.class, MessageC2SEditSign::serialize, MessageC2SEditSign::deserialize, MessageC2SEditSign::handle);
		CHANNEL.registerMessage(id++, MessageS2CUpdateSign.class, MessageS2CUpdateSign::serialize, MessageS2CUpdateSign::deserialize, MessageS2CUpdateSign::handle);
		CHANNEL.registerMessage(id++, MessageS2CTeleportEntity.class, MessageS2CTeleportEntity::serialize, MessageS2CTeleportEntity::deserialize, MessageS2CTeleportEntity::handle);
		CHANNEL.registerMessage(id++, MessageS2CSpawnParticle.class, MessageS2CSpawnParticle::serialize, MessageS2CSpawnParticle::deserialize, MessageS2CSpawnParticle::handle);
		CHANNEL.registerMessage(id++, MessageC2S2CSpawnParticle.class, MessageC2S2CSpawnParticle::serialize, MessageC2S2CSpawnParticle::deserialize, MessageC2S2CSpawnParticle::handle);
		CHANNEL.registerMessage(id++, MessageS2CServerRedirect.class, MessageS2CServerRedirect::serialize, MessageS2CServerRedirect::deserialize, MessageS2CServerRedirect::handle);
		CHANNEL.registerMessage(id++, MessageS2CUpdateEntityData.class, MessageS2CUpdateEntityData::serialize, MessageS2CUpdateEntityData::deserialize, MessageS2CUpdateEntityData::handle);
	}

	private void replaceBeehivePOI() {
		ImmutableList<Block> BEEHIVES = ForgeRegistries.BLOCKS.getValues().stream().filter(block -> block instanceof AbnormalsBeehiveBlock).collect(ImmutableList.toImmutableList());
		PointOfInterestType.BEEHIVE.blockStates = Sets.newHashSet(PointOfInterestType.BEEHIVE.blockStates);
		BEEHIVES.stream().forEach((block) -> block.getStateContainer().getValidStates().forEach(state -> {
			PointOfInterestType.POIT_BY_BLOCKSTATE.put(state, PointOfInterestType.BEEHIVE);
			PointOfInterestType.BEEHIVE.blockStates.add(state);
		}));
	}
}