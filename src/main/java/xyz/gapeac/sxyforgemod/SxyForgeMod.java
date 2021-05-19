package xyz.gapeac.sxyforgemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.play.NetworkPlayerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLDedicatedServerSetupEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.network.FMLNetworkConstants;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.fml.network.simple.SimpleChannel;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.UUID;

@Mod("sxyforgemod")
public class SxyForgeMod
{
    // Directly reference a log4j logger.
    private static final Logger LOGGER = LogManager.getLogger();

    public HashMap<UUID, Integer> playerLivesCount;

    final String DATA_DIR = "./sxyforge/";
    final String LIVES_BIN_PATH = DATA_DIR + "lives.bin";

    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("sxyforgemod", "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    int packetIndex;

    public SxyForgeMod()
    {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::serverSetup);

        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::commonSetup);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);

        ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.DISPLAYTEST, () -> Pair.of(() -> FMLNetworkConstants.IGNORESERVERONLY, (a, b) -> true));
    }

    private void commonSetup(FMLCommonSetupEvent event)
    {
        LOGGER.info("SxyForgeMod presents, slow ass loading by the ForgeTM team");

        packetIndex = 0;
        INSTANCE.registerMessage(packetIndex++, UUIDLifePair.class, (data, buffer) ->
        {
            LOGGER.debug("Encode uuid life pair");

            buffer.writeUUID(data.getKey());
            buffer.writeInt(data.getValue());
        }, packetBuffer ->
                {
                    UUID id = packetBuffer.readUUID();
                    Integer lifeCount = packetBuffer.readInt();

                    return new UUIDLifePair(id, lifeCount);
                },
                (msg, supplier) ->
                {
                    if (supplier.get().getDirection().getReceptionSide().isClient())
                    {
                        LOGGER.info("Received new life map");

                        if (msg != null)
                        {
                            playerLivesCount.put(msg.getKey(), msg.getValue());
                        }
                        else
                        {
                            LOGGER.error("Got invalid player lives count");
                        }
                    }
                });
    }

    private void serverSetup(FMLDedicatedServerSetupEvent event)
    {
        LOGGER.info("SxyForgeMod Starting, data dir is " + DATA_DIR);
        new File(DATA_DIR).mkdir();

        try
        {
            FileInputStream lifeFile = new FileInputStream(LIVES_BIN_PATH);
            ObjectInputStream in     = new ObjectInputStream(lifeFile);
            ObjectInput input        = new ObjectInputStream(in);

            playerLivesCount = (HashMap<UUID, Integer>) input.readObject();

            LOGGER.info("Read existing life file");
        }
        catch (IOException | ClassNotFoundException e)
        {
            LOGGER.info("Reset life file");
            playerLivesCount = new HashMap<>();
        }
    }

    private void clientSetup(final FMLClientSetupEvent event)
    {
        LOGGER.info("The SxyForge mod says hello");
        playerLivesCount = new HashMap<>();
    }

    @SubscribeEvent
    public void onDisplayName(PlayerEvent.NameFormat nameFormatEvent)
    {
        PlayerEntity player = nameFormatEvent.getPlayer();

        if (playerLivesCount != null && playerLivesCount.containsKey(player.getUUID()))
        {
            switch (playerLivesCount.get(player.getUUID()))
            {
                case 1:
                {
                    nameFormatEvent.setDisplayname(new StringTextComponent(TextFormatting.RED + nameFormatEvent.getDisplayname().getString()));
                } break;

                case 2:
                {
                    nameFormatEvent.setDisplayname(new StringTextComponent(TextFormatting.YELLOW + nameFormatEvent.getDisplayname().getString()));
                } break;

                case 3:
                {
                    nameFormatEvent.setDisplayname(new StringTextComponent(TextFormatting.GREEN + nameFormatEvent.getDisplayname().getString()));
                } break;
            }
        }
    }

    @SubscribeEvent
    public void onServerStopping(FMLServerStoppingEvent event)
    {
        try
        {
            FileOutputStream lifeFile = new FileOutputStream(LIVES_BIN_PATH);
            ObjectOutputStream out = new ObjectOutputStream(lifeFile);

            out.writeObject(playerLivesCount);
            out.close();
            lifeFile.close();

            LOGGER.info("Saved life file to " + LIVES_BIN_PATH);
        } catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event)
    {
       if (FMLEnvironment.dist != Dist.DEDICATED_SERVER)
       {
           return;
       }

       Entity e = event.getEntity();

        if (e instanceof PlayerEntity)
        {
            PlayerEntity player = (PlayerEntity) e;

            if (!playerLivesCount.containsKey(player.getUUID()))
            {
                LOGGER.error("Player " + player.getDisplayName().getString() + " doesn't have a life count!");
                return;
            }

            Integer lifeCount = playerLivesCount.get(player.getUUID());

            lifeCount -= 1;
            playerLivesCount.put(player.getUUID(), lifeCount);

            if (lifeCount > 0)
            {
                player.sendMessage(new StringTextComponent(String.format("You have %s lives remaining", lifeCount)), player.getUUID());
                LOGGER.info("Minus one life for " + player.getDisplayName().getString());
            }
            else
            {
                player.sendMessage(new StringTextComponent("You're out of lives! Setting your gamemode to spectator"), player.getUUID());
                player.setGameMode(GameType.SPECTATOR);

                LOGGER.info(player.getDisplayName().getString() + " has run out of lives!");
            }

            LOGGER.info("Sending new life pair to clients");
            INSTANCE.send(PacketDistributor.ALL.noArg(), new UUIDLifePair(player.getUUID(), lifeCount));
        }
    }

    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public void renderPlayerPre(RenderPlayerEvent.Pre event)
    {
        event.getPlayer().refreshDisplayName();
    }

    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public void renderGameOverlayPre(RenderGameOverlayEvent.Pre event)
    {
        if (event.getType() == RenderGameOverlayEvent.ElementType.PLAYER_LIST)
        {

        }
    }

    @SubscribeEvent
    public void playerJoin(PlayerEvent.PlayerLoggedInEvent event)
    {
        if (FMLEnvironment.dist != Dist.DEDICATED_SERVER)
        {
            return;
        }

        PlayerEntity player = event.getPlayer();

        if (!playerLivesCount.containsKey(player.getUUID()))
        {
            playerLivesCount.put(player.getUUID(), 3);

            player.sendMessage(new StringTextComponent("Welcome! You have three lives left starting now!"), player.getUUID());
        }
        else
        {
            Integer count = playerLivesCount.get(player.getUUID());

            if (count == 0)
            {
                player.sendMessage(new StringTextComponent("You're out of lives!"), player.getUUID());
            } else
            {
                player.sendMessage(new StringTextComponent(String.format("Welcome back, you have %s lives", count.toString())), player.getUUID());
            }
        }

        LOGGER.info("Sending new life pair to clients");
        INSTANCE.send(PacketDistributor.ALL.noArg(), new UUIDLifePair(player.getUUID(), playerLivesCount.get(player.getUUID())));
    }
}
