package dev.ocarina;

import java.nio.channels.*;import java.nio.file.*;import java.util.*;
import net.minecraft.client.Minecraft;import net.minecraft.client.KeyMapping;import net.minecraft.network.chat.Component;import net.minecraft.resources.Identifier;import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import com.mojang.blaze3d.platform.InputConstants;import org.lwjgl.glfw.GLFW;import org.lwjgl.opengl.GL11;import org.lwjgl.opengl.GL30;import org.slf4j.Logger;import org.slf4j.LoggerFactory;

/** Opt-in full engine preview. The original engine owns its story, saves, and collision. */
public final class StoryClient {
 private static final Logger LOG=LoggerFactory.getLogger("ocarina-story");
 private final Runnable releaseLink;
 private StoryChannel channel;private StoryRenderer renderer;private StoryChannel.Frame frame;private Process guest;
 private FileChannel lockFile;private FileLock lock;
 private net.minecraft.client.player.LocalPlayer owner;private Vec3 home,lastWorldPose;private boolean gravity,physics,hideGui;
 private net.minecraft.server.level.ServerPlayer serverOwner;private boolean serverGravity,serverPhysics;
 private net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
 private boolean worldMode;private Vec3 origin;private int originScene=-1;private net.minecraft.client.CameraType previousCamera;
 private long startNanos,lastFrameNanos,session;private int received;
 private KeyMapping sword,shield,target,start,cUp,cDown,cLeft,cRight;
 public StoryClient(Runnable releaseLink,KeyMapping sword,KeyMapping shield,KeyMapping target){this.releaseLink=releaseLink;this.sword=sword;this.shield=shield;this.target=target;}
 public boolean active(){return channel!=null;}
 public void initialize(){
  net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((dispatcher,access)->dispatcher.register(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("ootworld").executes(ctx->{begin(true);return active()?1:0;})));
  var category=KeyMapping.Category.register(Identifier.fromNamespaceAndPath("ocarina","story_controls"));
  // Minecraft.options is not constructed during the Fabric client entrypoint.
  start=key("story_start",GLFW.GLFW_KEY_ENTER,category);
  cUp=key("c_up",GLFW.GLFW_KEY_UP,category);cDown=key("c_down",GLFW.GLFW_KEY_DOWN,category);cLeft=key("c_left",GLFW.GLFW_KEY_LEFT,category);cRight=key("c_right",GLFW.GLFW_KEY_RIGHT,category);
  ClientTickEvents.END_CLIENT_TICK.register(this::tick);
  net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(mc->stop());
  WorldRenderEvents.END_MAIN.register(context->render());
  WorldRenderEvents.END_EXTRACTION.register(context->{if(active()&&owner!=null)context.worldState().entityRenderStates.removeIf(s->s instanceof net.minecraft.client.renderer.entity.state.AvatarRenderState a&&a.id==owner.getId());});
  net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((dispatcher,access)->dispatcher.register(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("ootstory").executes(ctx->{begin(false);return active()?1:0;})));
 }
 private boolean sharedScene(){return worldMode&&origin!=null&&frame!=null&&frame.state()[0]>0&&frame.state()[37]==0&&frame.state()[38]!=1;}
 private static KeyMapping key(String name,int code,KeyMapping.Category category){return KeyBindingHelper.registerKeyBinding(new KeyMapping("key.ocarina."+name,InputConstants.Type.KEYSYM,code,category));}
 private static void message(String text){var mc=Minecraft.getInstance();if(mc.player!=null)mc.player.displayClientMessage(Component.literal(text),false);}
 private void begin(boolean world){
  var mc=Minecraft.getInstance();
  try{
   if(mc.player==null||mc.level==null||!mc.hasSingleplayerServer()||mc.getSingleplayerServer().isPublished())throw new IllegalStateException("Use an unshared single-player world.");
   stop();releaseLink.run();worldMode=world;origin=null;originScene=-1;lastWorldPose=null;Properties properties=new Properties();try(var reader=Files.newBufferedReader(mc.gameDirectory.toPath().resolve("config/ocarina.properties"))){properties.load(reader);}
   String configured=properties.getProperty("storyRuntimeDirectory");if(configured==null)throw new IllegalStateException("The full story runtime has not been installed.");
   Path runtime=Path.of(configured).toAbsolutePath().normalize();for(String name:new String[]{"soh.exe","oot.o2r","soh.o2r"})if(!Files.isRegularFile(runtime.resolve(name)))throw new IllegalStateException("Missing story runtime file: "+name);
   lockFile=FileChannel.open(runtime.resolve("minecraft-story.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);lock=lockFile.tryLock();if(lock==null)throw new IllegalStateException("A story session is already running.");
   Path directory=runtime.resolve("sessions").resolve(UUID.randomUUID().toString());channel=new StoryChannel(directory);renderer=new StoryRenderer(directory);
   var builder=new ProcessBuilder(runtime.resolve("soh.exe").toString()).directory(runtime.toFile());builder.environment().put("OOT_STORY_BRIDGE",directory.toString());builder.redirectErrorStream(true).redirectOutput(directory.resolve("guest.log").toFile());guest=builder.start();
   owner=mc.player;home=owner.position();dimension=mc.level.dimension();gravity=owner.isNoGravity();physics=owner.noPhysics;hideGui=mc.options.hideGui;previousCamera=mc.options.getCameraType();if(worldMode)mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);owner.setNoGravity(true);owner.noPhysics=true;mc.options.hideGui=true;
   long token=++session;var uuid=owner.getUUID();mc.getSingleplayerServer().execute(()->{if(token!=session)return;var p=mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);if(p!=null){serverOwner=p;serverGravity=p.isNoGravity();serverPhysics=p.noPhysics;p.setNoGravity(true);p.noPhysics=true;p.setPos(home);p.connection.resetPosition();}});
   startNanos=lastFrameNanos=System.nanoTime();received=0;
   message((worldMode?"Minecraft world story: move the mouse to look around. ":"")+"Original story preview: Enter = Start/pause, Space = A, J = sword/B, K = shield, Z = target, arrows = C buttons. /ootreturn exits. Story progress uses separate saves. Image-based interiors retain their original view for now.");
   LOG.info("Original engine started pid={} session={}",guest.pid(),directory);
  }catch(Exception e){LOG.error("Story start failed",e);message("Story could not start: "+e.getMessage());stop();}
 }
 private void tick(Minecraft mc){
  if(!active())return;
  try{
   if(mc.player!=owner||mc.level==null||!mc.hasSingleplayerServer()||mc.getSingleplayerServer().isPublished()||!mc.level.dimension().equals(dimension)){stop();return;}
   if(!guest.isAlive())throw new IllegalStateException("Original engine exited ("+guest.exitValue()+").");
   boolean paused=mc.isPaused()||mc.screen!=null;
   int buttons=(mc.options.keyJump.isDown()?0x8000:0)|(sword.isDown()?0x4000:0)|(target.isDown()?0x2000:0)|(start.isDown()?0x1000:0)|(shield.isDown()?0x10:0)|(cUp.isDown()?8:0)|(cDown.isDown()?4:0)|(cLeft.isDown()?2:0)|(cRight.isDown()?1:0);
   int x=((mc.options.keyRight.isDown()?1:0)-(mc.options.keyLeft.isDown()?1:0))*80,y=((mc.options.keyUp.isDown()?1:0)-(mc.options.keyDown.isDown()?1:0))*80;
   float[] cameraInput=null;
   if(sharedScene()){
    var camera=mc.gameRenderer.getMainCamera();var eye=camera.position().subtract(origin).scale(40);var forward=camera.forwardVector();var up=camera.upVector();
    cameraInput=new float[]{(float)eye.x,(float)eye.y,(float)eye.z,(float)eye.x+forward.x()*100,(float)eye.y+forward.y()*100,(float)eye.z+forward.z()*100,up.x(),up.y(),up.z(),mc.options.fov().get().floatValue()};
    double yaw=Math.toRadians(owner.getYRot()),nativeYaw=frame.state()[39]*Math.PI/32768.0;
    double wx=-Math.sin(yaw)*y-Math.cos(yaw)*x,wz=Math.cos(yaw)*y-Math.sin(yaw)*x;
    double angle=Math.atan2(wx,wz)-nativeYaw,magnitude=Math.min(80,Math.hypot(x,y));x=(int)Math.round(-Math.sin(angle)*magnitude);y=(int)Math.round(Math.cos(angle)*magnitude);
   }
   channel.input(paused?0:buttons,paused?0:x,paused?0:y,paused,false,sharedScene(),cameraInput,frame==null?0:(int)frame.state()[40],originScene,sharedScene()?minecraftCollision(mc):null);
   var next=channel.read();if(next!=null){if(worldMode&&next.state()[47]<2&&!next.draws().isEmpty())throw new IllegalStateException("The shared-world native runtime needs updating.");frame=next;if(!next.draws().isEmpty()){lastFrameNanos=System.nanoTime();received++;}if(!next.draws().isEmpty()&&(received==1||received%200==0))LOG.info("Original story frame={} scene={} active={} drawCalls={}",next.number(),next.state()[1],next.state()[0],next.draws().size());}
   if(!paused&&System.nanoTime()-lastFrameNanos>(received==0?60_000_000_000L:10_000_000_000L))throw new IllegalStateException("No new original-engine frames.");
   if(paused)lastFrameNanos=System.nanoTime();
   if(worldMode&&frame!=null&&frame.state()[0]>0&&(origin==null||originScene!=(int)frame.state()[1])){originScene=(int)frame.state()[1];origin=home.add(-frame.state()[8]/40.0,48-frame.state()[9]/40.0,-frame.state()[10]/40.0);}
   if(sharedScene())lastWorldPose=origin.add(frame.state()[8]/40.0,frame.state()[9]/40.0,frame.state()[10]/40.0);
   Vec3 pose=lastWorldPose!=null?lastWorldPose:home;
   owner.setPos(pose);owner.setDeltaMovement(Vec3.ZERO);owner.fallDistance=0;
   var server=mc.getSingleplayerServer();long token=session;server.execute(()->{if(token!=session||serverOwner==null)return;serverOwner.setPos(pose);serverOwner.setDeltaMovement(Vec3.ZERO);serverOwner.fallDistance=0;serverOwner.connection.resetPosition();serverOwner.connection.resetFlyingTicks();});
  }catch(Exception e){LOG.error("Story session stopped",e);message("Story stopped: "+e.getMessage());stop();}
 }
 private float[] minecraftCollision(Minecraft mc){
  if(frame.state()[46]<1)throw new IllegalStateException("The Minecraft block collision runtime needs updating.");
  var position=origin.add(frame.state()[8]/40.0,frame.state()[9]/40.0,frame.state()[10]/40.0);
  var region=new net.minecraft.world.phys.AABB(position.x-3,position.y-2,position.z-3,position.x+3,position.y+4,position.z+3);
  var boxes=new ArrayList<net.minecraft.world.phys.AABB>();
  for(var shape:mc.level.getBlockCollisions(owner,region))boxes.addAll(shape.toAabbs());
  // Nearest shapes take priority if an unusually complex block neighborhood exceeds the bound.
  boxes.sort(Comparator.comparingDouble(b->{double x=Math.clamp(position.x,b.minX,b.maxX)-position.x,y=Math.clamp(position.y,b.minY,b.maxY)-position.y,z=Math.clamp(position.z,b.minZ,b.maxZ)-position.z;return x*x+y*y+z*z;}));
  float[] result=new float[Math.min(64,boxes.size())*6];int count=0;
  for(var b:boxes){
   float[] values={(float)Math.floor((b.minX-origin.x)*40),(float)Math.floor((b.minY-origin.y)*40),(float)Math.floor((b.minZ-origin.z)*40),(float)Math.ceil((b.maxX-origin.x)*40),(float)Math.ceil((b.maxY-origin.y)*40),(float)Math.ceil((b.maxZ-origin.z)*40)};
   boolean valid=true;for(int a=0;a<3;a++)if(values[a]<-32000||values[a+3]>32000||values[a+3]-values[a]<1||values[a+3]-values[a]>1024)valid=false;
   if(valid){System.arraycopy(values,0,result,count++*6,6);if(count==64)break;}
  }
  return Arrays.copyOf(result,count*6);
 }
 private void render(){
  if(!active()||frame==null)return;
  var mc=Minecraft.getInstance();
  try{
   var target=mc.getMainRenderTarget();
   if(!(com.mojang.blaze3d.systems.RenderSystem.getDevice() instanceof com.mojang.blaze3d.opengl.GlDevice device)||!(target.getColorTexture() instanceof com.mojang.blaze3d.opengl.GlTexture color))throw new IllegalStateException("Story preview requires Minecraft's OpenGL renderer.");
   int previousDraw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),previousRead=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
   int mainTarget=StoryRenderer.mainTarget(frame);
   boolean hasWorld=frame.draws().stream().anyMatch(d->d.framebuffer()==mainTarget&&(d.flags()&16)!=0);
   float[] matrix=null;
   if(worldMode&&origin!=null&&hasWorld){
    var projection=StoryProjection.copy();if(projection==null)throw new IllegalStateException("Minecraft world camera has not been captured.");
    var camera=mc.gameRenderer.getMainCamera();var offset=origin.subtract(camera.position());
    matrix=projection.rotate(camera.rotation().conjugate(new org.joml.Quaternionf())).translate((float)offset.x,(float)offset.y,(float)offset.z).scale(1f/40f).get(new float[16]);
   }
   // Render the mode actually encoded in this complete frame; do not flash Minecraft alone during menu transitions.
   if(hasWorld&&matrix==null)return;
   try{GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,color.getFbo(device.directStateAccess(),target.getDepthTexture()));renderer.draw(frame,target.width,target.height,matrix==null,matrix);}
   finally{GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,previousDraw);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,previousRead);}
  }catch(Exception e){LOG.error("Original mesh replay failed",e);message("Story renderer stopped: "+e.getMessage());stop();}
 }
 public void stop(){
  ++session;frame=null;origin=null;originScene=-1;
  if(channel!=null){try{channel.close();}catch(Exception e){LOG.warn("Story channel cleanup",e);}channel=null;}
  var closing=guest;guest=null;var closingLock=lock;var closingFile=lockFile;lock=null;lockFile=null;
  if(closing!=null){Thread.ofPlatform().daemon().name("Ocarina story shutdown").start(()->{try{if(!closing.waitFor(3,java.util.concurrent.TimeUnit.SECONDS)){closing.destroy();closing.waitFor(3,java.util.concurrent.TimeUnit.SECONDS);}}catch(InterruptedException e){Thread.currentThread().interrupt();closing.destroy();}finally{releaseLock(closingLock,closingFile);}});}else releaseLock(closingLock,closingFile);
  if(renderer!=null){LOG.info("Story geometry cache: {}",renderer.cacheStats());renderer.close();renderer=null;}
  var mc=Minecraft.getInstance();if(owner!=null){owner.setPos(home);owner.setNoGravity(gravity);owner.noPhysics=physics;owner.setDeltaMovement(Vec3.ZERO);mc.options.hideGui=hideGui;if(previousCamera!=null)mc.options.setCameraType(previousCamera);previousCamera=null;owner=null;}
  var restoring=serverOwner;var restoreHome=home;serverOwner=null;boolean restoreGravity=serverGravity,restorePhysics=serverPhysics;var server=mc.getSingleplayerServer();if(restoring!=null&&server!=null)server.execute(()->{if(restoreHome!=null)restoring.setPos(restoreHome);restoring.setNoGravity(restoreGravity);restoring.noPhysics=restorePhysics;restoring.setDeltaMovement(Vec3.ZERO);restoring.connection.resetPosition();});

 }
 private static void releaseLock(FileLock lock,FileChannel file){try{if(lock!=null)lock.close();if(file!=null)file.close();}catch(Exception e){LOG.warn("Story lock cleanup",e);}}
}
