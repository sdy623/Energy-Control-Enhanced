package portableqa;

import java.nio.file.*;
import java.util.*;
import com.zuxelus.energycontrol.gui.GuiPortablePanel;
import com.zuxelus.energycontrol.containers.ContainerPortablePanel;
import com.zuxelus.energycontrol.items.cards.*;
import com.zuxelus.energycontrol.init.ModItems;
import com.zuxelus.energycontrol.api.CardState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.lwjgl.glfw.GLFW;

public class ClientDriver {
    static int ticks, stage, waitUntil, checks;
    static boolean opened,done,hidden,connecting;
    static long sequence;
    static final List<String> captures=new ArrayList<>();
    static String pendingCapture;
    static void register(){NeoForge.EVENT_BUS.addListener(ClientDriver::tick);NeoForge.EVENT_BUS.addListener(ClientDriver::render);}
    static void check(boolean condition,String text){if(!condition)throw new AssertionError(text);checks++;System.out.println("[PORTABLE-QA] PASS "+text);}
    static int page(GuiPortablePanel screen) throws Exception {var f=GuiPortablePanel.class.getDeclaredField("page");f.setAccessible(true);return f.getInt(screen);}
    static List<Button> buttons(GuiPortablePanel screen){return screen.children().stream().filter(x->x instanceof Button).map(x->(Button)x).toList();}
    static void click(GuiPortablePanel screen,Button b){check(screen.mouseClicked(b.getX()+9,b.getY()+9,0),"real GUI accepted navigation click");screen.mouseReleased(b.getX()+9,b.getY()+9,0);}
    static void capture(String name){pendingCapture=name;captures.add(name);}
    static void render(RenderFrameEvent.Post event){
        if(pendingCapture==null)return;
        var mc=Minecraft.getInstance();String name=pendingCapture;pendingCapture=null;
        Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),c->System.out.println("[PORTABLE-QA] SCREENSHOT "+name));
    }
    static void command(String text){Minecraft.getInstance().getConnection().sendCommand("ecportableqa "+text);}
    static void tick(ClientTickEvent.Post event){
        if(done)return;
        var mc=Minecraft.getInstance();ticks++;
        try {
            if(ticks%100==0)System.out.println("[PORTABLE-QA] STATE ticks="+ticks+" screen="+(mc.screen==null?"none":mc.screen.getClass().getName())+" overlay="+(mc.getOverlay()==null?"none":mc.getOverlay().getClass().getName()));
            if(ticks>12000)throw new AssertionError("client acceptance timeout stage="+stage);
            if(ticks>160&&mc.screen != null && mc.screen.getClass().getSimpleName().contains("LoadingError"))
                throw new AssertionError("Forge loading error screen; acceptance must not bypass loading errors");
            if(mc.player==null){
                if(ticks%100==0&&mc.screen instanceof net.minecraft.client.gui.screens.ConnectScreen){
                    var cf=net.minecraft.client.gui.screens.ConnectScreen.class.getDeclaredField("connection");cf.setAccessible(true);
                    var conn=(net.minecraft.network.Connection)cf.get(mc.screen);
                    System.out.println("[PORTABLE-QA] CONNECTION "+(conn==null?"null":("connected="+conn.isConnected()+" autoRead="+conn.channel().config().isAutoRead()+" received="+conn.getAverageReceivedPackets()+" listener="+conn.getPacketListener().getClass().getName())));
                }
                if(connecting&&ticks>1000)throw new AssertionError("local Forge login did not complete");
                if(connecting&&ticks%200==0){
                    Screenshot.grab(mc.gameDirectory,"diagnostic-"+ticks+".png",mc.getMainRenderTarget(),c->{});
                }
                if(!connecting&&ticks>80&&mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen&&mc.getOverlay()==null){
                    System.out.println("[PORTABLE-QA] CONNECT from "+mc.screen.getClass().getName());
                    net.minecraft.client.gui.screens.ConnectScreen.startConnecting(mc.screen,mc,
                        net.minecraft.client.multiplayer.resolver.ServerAddress.parseString("127.0.0.1:25587"),
                        new net.minecraft.client.multiplayer.ServerData("Portable UI local QA","127.0.0.1:25587",net.minecraft.client.multiplayer.ServerData.Type.OTHER),false,null);
                    connecting=true;
                }
                return;
            }
            if(mc.gameMode==null||ticks<waitUntil)return;
            if(!opened){
                if(!mc.player.getMainHandItem().is(ModItems.portable_panel.get()))return;
                mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);opened=true;waitUntil=ticks+40;return;
            }
            if(stage==11){
                mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);stage=12;waitUntil=ticks+40;return;
            }
            if(stage==60) {
                command("worldcheck");
                var a=(com.zuxelus.energycontrol.tileentities.TileEntityAdvancedInfoPanel)mc.level.getBlockEntity(WorldQa.ADV);
                check(a!=null&&a.getScreen()!=null&&a.getScreen().maxX-a.getScreen().minX==1,"two-block advanced screen geometry synchronizes to client");
                check(new ItemCardReader(a.getItem(0)).getTitle().equals("Advanced + extender"),"advanced text card synchronized to world renderer");
                var e=(com.zuxelus.energycontrol.tileentities.TileEntityAdvancedInfoPanelExtender)mc.level.getBlockEntity(WorldQa.EXT);
                System.out.println("[PORTABLE-QA] VISUAL coreTexture="+a.findTexture()+" extenderTexture="+(e==null?"missing":e.findTexture())+" coreFacing="+a.getFacing()+" extFacing="+(e==null?"missing":e.getFacing())+" coreRotation="+a.getRotation()+" extRotation="+(e==null?"missing":e.getRotation()));
                System.out.println("[PORTABLE-QA] VISUAL_EXT_NBT "+(e==null?"missing":e.getUpdateTag(mc.level.registryAccess())));
                check(e!=null&&e.getScreen()!=null&&e.getScreen().equals(a.getScreen()),"core and extender share synchronized screen bounds for rendering");
                check(a.findTexture()==14&&e.findTexture()==13,"south-facing two-block screen has outer borders only");
                mc.options.hideGui=true;
                mc.player.setYRot(180);mc.player.setXRot(0);capture("11-panels-world.png");stage=61;waitUntil=ticks+40;return;
            }
            if(stage==61) {
                mc.options.hideGui=false;
                mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(WorldQa.ADV).add(0,0,0.5),net.minecraft.core.Direction.SOUTH,WorldQa.ADV,false));
                stage=62;waitUntil=ticks+40;return;
            }
            if(stage==62) {
                check(mc.screen instanceof com.zuxelus.energycontrol.gui.GuiAdvancedInfoPanel,"actual right click opens original advanced panel GUI");
                capture("12-advanced-gui.png");stage=63;waitUntil=ticks+40;return;
            }
            if(stage==63) {mc.player.closeContainer();command("breakext");stage=65;waitUntil=ticks+40;return;}
            if(stage==65) {
                var a=(com.zuxelus.energycontrol.tileentities.TileEntityAdvancedInfoPanel)mc.level.getBlockEntity(WorldQa.ADV);
                check(mc.screen==null,"panel GUI closes normally before block interaction");
                check(mc.level.getBlockState(WorldQa.EXT).isAir()&&a.getScreen()!=null&&a.getScreen().maxX==a.getScreen().minX,"ordinary extender removal and one-block geometry synchronize to client");
                command("restoreext");stage=66;waitUntil=ticks+40;return;
            }
            if(stage==66) {
                var a=(com.zuxelus.energycontrol.tileentities.TileEntityAdvancedInfoPanel)mc.level.getBlockEntity(WorldQa.ADV);
                check(a.getScreen()!=null&&a.getScreen().maxX-a.getScreen().minX==1,"normal extender replacement restores two-block screen on client");
                command("worldcheck");stage=64;waitUntil=ticks+40;return;
            }
            if(stage==64) {mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);stage=50;waitUntil=ticks+40;return;}
            if(!(mc.screen instanceof GuiPortablePanel screen))return;
            if (Boolean.getBoolean("portableqa.reload") && stage==0) stage=40;
            var menu=(ContainerPortablePanel)mc.player.containerMenu;var card=menu.te.getItem(0);var bs=buttons(screen);
            switch(stage){
                case 0 -> {
                    check(menu.slots.size()==11,"original portable menu has two device slots and nine hotbar slots");
                    check(bs.size()==2&&bs.get(0).visible&&bs.get(1).visible,"multi-page controls rendered");
                    check(page(screen)==0&&!bs.get(0).active&&bs.get(1).active,"first page boundaries");
                    check(new ItemCardReader(card).getInt("rows")==29,"server fixture rows arrived through original menu synchronization");
                    sequence=new ItemCardReader(card).getLong("sequence");capture("01-page-one.png");
                }
                case 1 -> {
                    check(new ItemCardReader(card).getLong("sequence")>sequence,"live server readings update while GUI remains open");
                    click(screen,bs.get(1));
                }
                case 2 -> {check(page(screen)==1&&bs.get(0).active&&bs.get(1).active,"second page reached by actual GUI click");capture("02-page-two.png");}
                case 3 -> {click(screen,bs.get(1));}
                case 4 -> {check(page(screen)==2&&bs.get(0).active&&!bs.get(1).active,"third page ends at row twenty-nine");capture("03-page-three.png");}
                case 5 -> {command("rows 2");}
                case 6 -> {check(new ItemCardReader(card).getInt("rows")==2&&page(screen)==0&&!bs.get(0).visible&&!bs.get(1).visible,"live row reduction clamps and hides navigation");capture("04-two-rows.png");command("rows 29");}
                case 7 -> {check(bs.get(1).visible&&page(screen)==0,"live row growth restores controls");click(screen,bs.get(1));command("error");}
                case 8 -> {check(new ItemCardReader(card).getState()==CardState.OUT_OF_RANGE&&page(screen)==0&&!bs.get(1).visible,"original error state clears invalid page");capture("05-original-error.png");command("restore");}
                case 9 -> {check(bs.get(1).visible,"original data returns after error");mc.gameMode.handleInventoryMouseClick(menu.containerId,0,0,ClickType.PICKUP,mc.player);}
                case 10 -> {check(menu.te.getItem(0).isEmpty()&&!menu.getCarried().isEmpty()&&page(screen)==0&&!bs.get(1).visible,"real slot packet removes card and resets UI");mc.gameMode.handleInventoryMouseClick(menu.containerId,0,0,ClickType.PICKUP,mc.player);stage=19;}
                case 12 -> {
                    check(card.is(ModItems.card_text.get())&&new ItemCardReader(card).getString("line_9").equals("Saved original line 10"),"original text card survives close and reopen through parent item NBT");
                    check(page(screen)==0&&!bs.get(1).visible,"original single-page card layout preserved after reopen");capture("07-original-text-reopened.png");
                }
                case 13 -> {command("energy");stage=29;}
                case 30 -> {
                    check(card.is(ModItems.card_energy.get()),"production energy card installed through original menu");
                    check(new ItemCardReader(card).getDouble(com.zuxelus.energycontrol.utils.DataHelper.ENERGY)==3200,"production card displays real NeoForge energy capability amount");
                    capture("08-real-energy.png");command("charge");
                }
                case 31 -> {
                    check(new ItemCardReader(card).getDouble(com.zuxelus.energycontrol.utils.DataHelper.ENERGY)==4800,"real charging is synchronized into open portable GUI");
                    capture("09-real-energy-changed.png");
                }
                case 32 -> {mc.player.closeContainer();command("world");stage=59;}
                case 40 -> {
                    check(card.is(ModItems.card_energy.get()),"saved production energy card restored on client after server restart");
                    check(new ItemCardReader(card).getDouble(com.zuxelus.energycontrol.utils.DataHelper.ENERGY)==4800,"saved production energy amount synchronizes after server restart");
                    capture("10-reloaded-energy.png");mc.player.closeContainer();command("view");stage=59;
                }
                case 50 -> {
                    boolean all=captures.stream().allMatch(n->Files.isRegularFile(mc.gameDirectory.toPath().resolve("screenshots").resolve(n)));
                    if(!all){waitUntil=ticks+20;return;}
                    mc.player.closeContainer();System.out.println("[PORTABLE-QA] ALL_DONE checks="+checks+" screenshots="+captures.size());done=true;mc.stop();return;
                }
                case 20 -> {check(!card.isEmpty()&&menu.getCarried().isEmpty()&&bs.get(1).visible,"real slot packet reinserts card without loss");command("text");}
                case 21 -> {
                    check(card.is(ModItems.card_text.get())&&!bs.get(1).visible,"production text card still renders without pagination");capture("06-original-text.png");
                }
                case 22 -> {mc.player.closeContainer();stage=10;}
                default -> throw new AssertionError("unexpected stage="+stage);
            }
            stage++;waitUntil=ticks+40;
        } catch(Throwable error){System.out.println("[PORTABLE-QA] FAIL stage="+stage+" "+error);error.printStackTrace();done=true;mc.stop();}
    }
}
