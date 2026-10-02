package portableqa;
import com.zuxelus.energycontrol.init.ModItems;
import com.zuxelus.energycontrol.items.cards.ItemCardReader;
import com.zuxelus.energycontrol.tileentities.*;
import com.zuxelus.zlib.blocks.FacingBlock;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

public class WorldQa {
    public static final BlockPos ADV = new BlockPos(-1,-59,-2);
    public static final BlockPos EXT = ADV.east();
    public static final BlockPos BASIC = new BlockPos(2,-59,-2);
    public static final BlockPos HOLO = new BlockPos(-3,-60,-2);
    static ItemStack text(String title) {
        var card=new ItemStack(ModItems.card_text.get());var r=new ItemCardReader(card);
        r.setTitle(title);r.setString("line_0","Minecraft 1.21.1");r.setString("line_1","NeoForge live world");
        return card;
    }
    static void place(ServerPlayer player,BlockPos pos,net.minecraft.world.level.block.Block block) {
        var level=player.serverLevel();level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());
        var stack=new ItemStack(block);var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos.below()).add(0,0.5,0),Direction.UP,pos.below(),false);
        var context=new net.minecraft.world.item.context.BlockPlaceContext(level,player,net.minecraft.world.InteractionHand.MAIN_HAND,stack,hit);
        var result=((net.minecraft.world.item.BlockItem)stack.getItem()).place(context);
        PortableQa.check(result.consumesAction(),"normal BlockItem placement: "+block.getDescriptionId());
    }
    static void setup(ServerPlayer player) {
        var level=player.serverLevel();
        player.setYRot(180);player.setXRot(0);
        place(player,EXT,ModItems.info_panel_advanced_extender.get());
        place(player,ADV,ModItems.info_panel_advanced.get());
        var advanced=(TileEntityAdvancedInfoPanel)level.getBlockEntity(ADV);
        advanced.setPowerMode((byte)TileEntityAdvancedInfoPanel.POWER_ON);advanced.setItem(0,text("Advanced + extender"));advanced.setChanged();
        place(player,BASIC,ModItems.info_panel.get());
        level.setBlockAndUpdate(BASIC.north(),Blocks.REDSTONE_BLOCK.defaultBlockState());
        var basic=(TileEntityInfoPanel)level.getBlockEntity(BASIC);basic.setItem(0,text("Basic panel"));basic.setChanged();
        place(player,HOLO,ModItems.holo_panel.get());
        level.setBlockAndUpdate(HOLO.below(),Blocks.REDSTONE_BLOCK.defaultBlockState());
        var holo=(TileEntityHoloPanel)level.getBlockEntity(HOLO);holo.setItem(0,text("Holographic panel"));holo.setChanged();
        player.teleportTo(0.5,-60,2.5);player.setYRot(180);player.setXRot(0);
        PortableQa.check(level.getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.parse("energycontrol:portable_panel")).isPresent(),"production portable recipe loaded in 1.21.1 RecipeManager");
    }
    static void powerModes(ServerPlayer player) {
        var level=player.serverLevel();var a=(TileEntityAdvancedInfoPanel)level.getBlockEntity(ADV);
        for (int mode:new int[]{TileEntityAdvancedInfoPanel.POWER_ON,TileEntityAdvancedInfoPanel.POWER_OFF,TileEntityAdvancedInfoPanel.POWER_REDSTONE,TileEntityAdvancedInfoPanel.POWER_INVERTED}) {
            a.setPowerMode((byte)mode);
            for (boolean signal:new boolean[]{true,false}) {
                level.setBlockAndUpdate(ADV.north(),signal?Blocks.REDSTONE_BLOCK.defaultBlockState():Blocks.AIR.defaultBlockState());
                boolean expected=mode==TileEntityAdvancedInfoPanel.POWER_ON || mode==TileEntityAdvancedInfoPanel.POWER_REDSTONE&&signal || mode==TileEntityAdvancedInfoPanel.POWER_INVERTED&&!signal;
                PortableQa.check(a.getPowered()==expected&&level.getBlockState(ADV).getValue(com.zuxelus.zlib.blocks.FacingBlockActive.ACTIVE)==expected&&level.getBlockState(EXT).getValue(com.zuxelus.zlib.blocks.FacingBlockActive.ACTIVE)==expected,"power mode "+mode+" respects neighbor signal "+signal+" on core and extender");
            }
        }
        a.setPowerMode((byte)TileEntityAdvancedInfoPanel.POWER_ON);a.setChanged();
    }
    static void verify(ServerPlayer player,boolean reload) {
        var level=player.serverLevel();
        var a=(TileEntityAdvancedInfoPanel)level.getBlockEntity(ADV);
        System.out.println("[PORTABLE-QA] SERVER_EXT_NBT "+level.getBlockEntity(EXT).getUpdateTag(level.registryAccess()));
        PortableQa.check(a!=null&&a.getPowered(),"advanced panel power mode works"+(reload?" after restart":""));
        PortableQa.check(a.getScreen()!=null&&a.getScreen().maxX-a.getScreen().minX==1,"advanced extender forms a two-block screen"+(reload?" after restart":""));
        PortableQa.check(new ItemCardReader(a.getItem(0)).getTitle().equals("Advanced + extender"),"advanced panel text card retained"+(reload?" after restart":""));
        var b=(TileEntityInfoPanel)level.getBlockEntity(BASIC);
        PortableQa.check(b!=null&&b.getPowered()&&b.getPanelStringList(true,false).size()>=3,"basic panel redstone and dynamic text work"+(reload?" after restart":""));
        var h=(TileEntityHoloPanel)level.getBlockEntity(HOLO);
        PortableQa.check(h!=null&&h.getPowered()&&h.getPanelStringList(true,false).size()>=3,"holographic panel receives text"+(reload?" after restart":""));
    }
}
