package xyz.gapeac.sxyforgemod;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;
import com.fasterxml.jackson.databind.type.TypeFactory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.play.ClientPlayNetHandler;
import net.minecraft.client.network.play.NetworkPlayerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.GameType;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ExtensionPoint;
import net.minecraftforge.fml.LogicalSide;
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
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import xyz.gapeac.sxyforgemod.network.UUIDLifePair;

import java.io.*;
import java.util.*;

@Mod("sxyforgemod")
public class SxyForgeMod
{
    public static final Logger LOGGER = LogManager.getLogger();

    public Map<UUID, Integer> playerLivesCount;

    final String DATA_DIR = "./sxyforge/";
    final String LIVES_BIN_PATH = DATA_DIR + "lives.json";

    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("sxyforgemod", "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    int packetIndex;

    ObjectMapper objectMapper;

    public SxyForgeMod()
    {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::serverSetup);

        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(this);

        ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.DISPLAYTEST, () -> Pair.of(() -> FMLNetworkConstants.IGNORESERVERONLY, (a, b) -> true));
    }

    private void commonSetup(FMLCommonSetupEvent event)
    {
        objectMapper = new ObjectMapper();

        LOGGER.info("SxyForgeMod presents, slow ass loading by the ForgeTM team");

        // Register life pair packet
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
                        LOGGER.info("Received new life map for " + msg.getKey() + " with " + msg.getValue() + " lives");
                        playerLivesCount.put(msg.getKey(), msg.getValue());
                    }
                });
    }

    private void saveLiveLife()
    {
        Mono.create(callback -> {
            try
            {
                objectMapper.writeValue(new File(LIVES_BIN_PATH), playerLivesCount);

                LOGGER.info("Saved life file to " + LIVES_BIN_PATH);
            } catch (IOException e)
            {
                e.printStackTrace();
                LOGGER.error("Could not write lives file");
            }
        }).subscribeOn(Schedulers.boundedElastic()).subscribe();
    }

    private void serverSetup(FMLDedicatedServerSetupEvent event)
    {
        LOGGER.info("SxyForgeMod Starting, data dir is " + DATA_DIR);
        if (new File(DATA_DIR).mkdir())
        {
            LOGGER.debug("Created data dir");
        }
        else
        {
            LOGGER.debug("Data dir exists");
        }

        TypeFactory typeFactory = objectMapper.getTypeFactory();
        MapType mapType = typeFactory.constructMapType(HashMap.class, UUID.class, Integer.class);

        try
        {
            playerLivesCount = objectMapper.readValue(new File(LIVES_BIN_PATH), mapType);
            LOGGER.info("Read existing lives file");
        } catch (IOException e)
        {
            e.printStackTrace();

            LOGGER.error(e.getMessage());
            LOGGER.error("Could not read lives file, creating new map...");

            playerLivesCount = new HashMap<>();
        }
    }

    public Enum<TextFormatting> getPlayerColor(UUID playerID)
    {
        Enum<TextFormatting> result = TextFormatting.WHITE;

        if (playerLivesCount != null)
        {
            Integer lives = playerLivesCount.get(playerID);

            if (lives != null)
            {
                switch (lives)
                {
                    default:
                    case 0:
                    {
                        result = TextFormatting.GRAY;
                    } break;

                    case 1:
                    {
                        result =  TextFormatting.RED;
                    } break;

                    case 2:
                    {
                        result = TextFormatting.YELLOW;
                    } break;

                    case 3:
                    {
                        result = TextFormatting.GREEN;
                    } break;
                }
            }
        }

        return result;
    }

    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public void renderOverlayPre(RenderGameOverlayEvent.Pre event)
    {
        if (event.getType() == RenderGameOverlayEvent.ElementType.CHAT || event.getType() == RenderGameOverlayEvent.ElementType.PLAYER_LIST)
        {
            try
            {
                ClientPlayNetHandler clientPlayNetHandler = Minecraft.getInstance().getConnection();

                if (clientPlayNetHandler != null)
                {
                    clientPlayNetHandler.getOnlinePlayers().forEach(player ->
                    {
                        try
                        {
                            player.setTabListDisplayName(new StringTextComponent(getPlayerColor(player.getProfile().getId()) + Objects.requireNonNull(player.getTabListDisplayName()).getString()));
                        }
                        catch (NullPointerException ignored) {}
                    });

                }
            }
            catch (NullPointerException e)
            {
                e.printStackTrace();
            }
        }
    }

    public void clientSetup(final FMLClientSetupEvent event)
    {
        LOGGER.info("\"I'm here to kick gum and chew ass. And I'm all out of ass.\" - Dick Kickem");
        playerLivesCount = new HashMap<>();
    }

    @SubscribeEvent
    public void onServerStopping(FMLServerStoppingEvent event)
    {
        saveLiveLife();
    }

    @SubscribeEvent
    public void onDisplayName(PlayerEvent.NameFormat nameFormatEvent)
    {
        PlayerEntity player = nameFormatEvent.getPlayer();

        if (playerLivesCount != null && playerLivesCount.containsKey(player.getUUID()))
        {
            nameFormatEvent.setDisplayname(new StringTextComponent(getPlayerColor(player.getUUID()) + nameFormatEvent.getDisplayname().getString()));
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

            if (lifeCount > 0)
            {
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

                LOGGER.info("Sending new life data clients");
                playerLivesCount.forEach(((uuid, integer) -> INSTANCE.send(PacketDistributor.ALL.noArg(), new UUIDLifePair(uuid, integer))));
                saveLiveLife();
            }
        }
    }

    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public void renderPlayerPre(RenderPlayerEvent.Pre event)
    {
        event.getPlayer().refreshDisplayName();
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
        playerLivesCount.forEach(((uuid, integer) -> INSTANCE.send(PacketDistributor.ALL.noArg(), new UUIDLifePair(uuid, integer))));
    }
}
