package xyz.gapeac.sxyforgemod;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.play.NetworkPlayerInfo;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerList;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.thread.SidedThreadGroups;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLDedicatedServerSetupEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.network.FMLNetworkConstants;
import net.minecraftforge.fml.server.ServerLifecycleHooks;
import net.minecraftforge.server.permission.DefaultPermissionLevel;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jline.utils.Log;

import java.io.*;
import java.util.HashMap;
import java.util.UUID;

@Mod("sxyforgemod")
public class SxyForgeMod
{
    // Directly reference a log4j logger.
    private static final Logger LOGGER = LogManager.getLogger();
    protected HashMap<UUID, Integer> playerLivesCount;

    final String DATA_DIR = "./sxyforge/";
    final String LIVES_BIN_PATH = DATA_DIR + "lives.bin";

    public SxyForgeMod()
    {
        // Register the doClientStuff method for modloading
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::serverSetup);

        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::commonSetup);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);


        ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.DISPLAYTEST, () -> Pair.of(() -> FMLNetworkConstants.IGNORESERVERONLY, (a, b) -> true));
    }

    private void commonSetup(FMLCommonSetupEvent event)
    {

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
        LOGGER.info("SxyForgeMod says hello");
    }

    @SubscribeEvent
    public void onDisplayName(PlayerEvent.NameFormat nameFormatEvent)
    {
        nameFormatEvent.setDisplayname(new StringTextComponent(TextFormatting.GREEN + nameFormatEvent.getDisplayname().getString()));

    }

//    @SubscribeEvent
//    public void onCommandsRegister(RegisterCommandsEvent event)
//    {
//        LOGGER.info("Registered commands");
//
//        event.getDispatcher().register(Commands.literal("reset_all_lives").requires((context) ->
//        {
//            return context.hasPermission(0);
//        }).executes((context) ->
//        {
//            PlayerList list = ServerLifecycleHooks.getCurrentServer().getPlayerList();
//
//            playerLivesCount.forEach(((uuid, integer) -> {
//                ServerPlayerEntity player = list.getPlayer(uuid);
//            }));
//
//            return 0;
//        }));
//    }

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
       if (FMLEnvironment.dist == Dist.DEDICATED_SERVER)
       {
           Entity e = event.getEntity();

           if (e instanceof PlayerEntity)
           {
               PlayerEntity player = (PlayerEntity) e;

               Integer lifeCount = playerLivesCount.get(player.getUUID());
               lifeCount -= 1;
               playerLivesCount.replace(player.getUUID(), lifeCount);

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
           }
       }
    }

    @SubscribeEvent
    public void playerJoin(PlayerEvent.PlayerLoggedInEvent event)
    {
        if (FMLEnvironment.dist == Dist.DEDICATED_SERVER)
        {
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
                }
                else
                {
                    player.sendMessage(new StringTextComponent(String.format("Welcome back, you have %s lives", count.toString())), player.getUUID());
                }
            }
        }
    }
}
