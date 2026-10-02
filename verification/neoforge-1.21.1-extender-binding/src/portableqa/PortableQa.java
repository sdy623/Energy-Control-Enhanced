package portableqa;

import java.util.*;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.zuxelus.energycontrol.api.*;
import com.zuxelus.energycontrol.items.*;
import com.zuxelus.energycontrol.items.cards.*;
import com.zuxelus.energycontrol.containers.ContainerPortablePanel;
import com.zuxelus.energycontrol.init.ModItems;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.registries.*;

@Mod("ec_portable_qa")
public class PortableQa {
    static final DeferredRegister<Item> ITEMS = DeferredRegister.create(net.minecraft.core.registries.Registries.ITEM, "ec_portable_qa");
    static final DeferredHolder<Item, Item> CARD = ITEMS.register("rows_card", RowsCard::new);
    public PortableQa(IEventBus bus) {
        if (!Boolean.getBoolean("portableqa.enabled")) throw new IllegalStateException("QA must be explicitly enabled");
        ITEMS.register(bus);
        NeoForge.EVENT_BUS.addListener(PortableQa::login);
        NeoForge.EVENT_BUS.addListener(PortableQa::commands);
        if (FMLEnvironment.dist == Dist.CLIENT) ClientDriver.register();
    }
    static void check(boolean value,String name) { if (!value) throw new AssertionError(name); System.out.println("[PORTABLE-QA] SERVER_PASS "+name); }
    static final BlockPos ENERGY_POS = new BlockPos(0,-60,2);
    static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        player.teleportTo(0.5,-60,0.5);
        if (Boolean.getBoolean("portableqa.reload")) {
            var be=player.level().getBlockEntity(ENERGY_POS);
            check(be instanceof com.zuxelus.energycontrol.tileentities.TileEntityKitAssembler, "production assembler restored from saved world");
            var capability=player.level().getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, ENERGY_POS, null);
            check(capability != null && capability.getEnergyStored()==4800, "real FE amount survives complete server restart");
            var stored=player.getMainHandItem();
            check(stored.is(ModItems.portable_panel.get()), "portable item survives saved player reload");
            var card=new InventoryPortablePanel(stored).getItem(0);
            check(card.is(ModItems.card_energy.get()) && new ItemCardReader(card).getTarget().equals(ENERGY_POS), "energy card target survives player reload");
            System.out.println("[PORTABLE-QA] SERVER_READY reload");
            return;
        }
        ItemStack portable = new ItemStack(ModItems.portable_panel.get());
        ItemStack card = new ItemStack(CARD.get());
        new ItemCardReader(card).setInt("rows",29);
        new ItemCardReader(card).setState(CardState.OK);
        new InventoryPortablePanel(portable).setItem(0, card);
        player.getInventory().selected = 0;
        player.getInventory().setItem(0, portable);
        player.inventoryMenu.broadcastChanges();
        System.out.println("[PORTABLE-QA] SERVER_READY fixtureRows=29");
    }
    static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ecportableqa")
            .then(Commands.literal("rows").then(Commands.argument("count", IntegerArgumentType.integer(0, 100)).executes(c -> {
                var player=c.getSource().getPlayerOrException();
                var menu=(ContainerPortablePanel)player.containerMenu;
                new ItemCardReader(menu.te.getItem(0)).setInt("rows",IntegerArgumentType.getInteger(c,"count"));
                menu.te.setChanged();menu.broadcastChanges();return 1;
            })))
            .then(Commands.literal("error").executes(c -> {
                var menu=(ContainerPortablePanel)c.getSource().getPlayerOrException().containerMenu;
                new ItemCardReader(menu.te.getItem(0)).setBoolean("error",true);menu.te.setChanged();return 1;
            }))
            .then(Commands.literal("restore").executes(c -> {
                var menu=(ContainerPortablePanel)c.getSource().getPlayerOrException().containerMenu;
                new ItemCardReader(menu.te.getItem(0)).setBoolean("error",false);menu.te.setChanged();return 1;
            }))
            .then(Commands.literal("view").executes(c -> {
                var p=c.getSource().getPlayerOrException();p.teleportTo(0.5,-60,2.5);p.setYRot(180);p.setXRot(0);return 1;
            }))
            .then(Commands.literal("world").executes(c -> { WorldQa.setup(c.getSource().getPlayerOrException()); return 1; }))
            .then(Commands.literal("breakext").executes(c -> {
                var player=c.getSource().getPlayerOrException();
                check(player.gameMode.destroyBlock(WorldQa.EXT),"normal player game mode destroys extender without test protection");
                check(player.level().getBlockState(WorldQa.EXT).isAir(),"destroyed extender is air on server");
                var a=(com.zuxelus.energycontrol.tileentities.TileEntityAdvancedInfoPanel)player.level().getBlockEntity(WorldQa.ADV);
                check(a.getScreen()!=null&&a.getScreen().maxX==a.getScreen().minX,"breaking extender rebuilds the remaining single-block screen");
                return 1;
            }))
            .then(Commands.literal("restoreext").executes(c -> {
                var player=c.getSource().getPlayerOrException();player.setYRot(180);player.setXRot(0);
                WorldQa.place(player,WorldQa.EXT,ModItems.info_panel_advanced_extender.get());return 1;
            }))
            .then(Commands.literal("worldcheck").executes(c -> { WorldQa.verify(c.getSource().getPlayerOrException(),Boolean.getBoolean("portableqa.reload")); WorldQa.powerModes(c.getSource().getPlayerOrException()); return 1; }))
            .then(Commands.literal("energy").executes(c -> {
                var player=c.getSource().getPlayerOrException();var level=player.serverLevel();
                level.setBlockAndUpdate(ENERGY_POS,ModItems.kit_assembler.get().defaultBlockState());
                var capability=level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,ENERGY_POS,null);
                check(capability != null,"production assembler exposes real NeoForge energy capability");
                for(int i=0;i<100&&capability.getEnergyStored()<3200;i++) capability.receiveEnergy(3200-capability.getEnergyStored(),false);
                check(capability.getEnergyStored()==3200,"real capability accepts energy");
                var be=level.getBlockEntity(ENERGY_POS);be.setChanged();
                var menu=(ContainerPortablePanel)player.containerMenu;
                var card=new ItemStack(ModItems.card_energy.get());ItemStackHelper.setCoordinates(card,ENERGY_POS);
                menu.te.setItem(0,card);menu.broadcastChanges();return 1;
            }))
            .then(Commands.literal("charge").executes(c -> {
                var level=c.getSource().getPlayerOrException().serverLevel();
                var capability=level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,ENERGY_POS,null);
                for(int i=0;i<100&&capability.getEnergyStored()<4800;i++) capability.receiveEnergy(4800-capability.getEnergyStored(),false);
                check(capability.getEnergyStored()==4800,"real capability amount changes after charging");
                level.getBlockEntity(ENERGY_POS).setChanged();return 1;
            }))
            .then(Commands.literal("text").executes(c -> {
                var menu=(ContainerPortablePanel)c.getSource().getPlayerOrException().containerMenu;
                var card=new ItemStack(ModItems.card_text.get());var reader=new ItemCardReader(card);
                reader.setTitle("Original text card");
                for(int i=0;i<10;i++)reader.setString("line_"+i,"Saved original line "+(i+1));
                reader.setState(CardState.OK);menu.te.setItem(0,card);menu.broadcastChanges();return 1;
            })));
    }
    static class RowsCard extends ItemCardMain {
        RowsCard() { super(new Item.Properties()); }
        @Override public CardState update(Level world, ICardReader reader, int range, BlockPos pos) {
            reader.setLong("sequence",world.getGameTime());
            return reader.getBoolean("error") ? CardState.OUT_OF_RANGE : CardState.OK;
        }
        @Override public List<PanelString> getStringData(Level world,int settings,ICardReader reader,boolean server,boolean labels) {
            List<PanelString> result=new ArrayList<>();
            for(int i=0;i<reader.getInt("rows");i++)result.add(new PanelString("Reading "+(i+1)+": "+(reader.getLong("sequence")+i)));
            return result;
        }
        @Override public List<PanelSetting> getSettingsList(){return null;}
    }
}
