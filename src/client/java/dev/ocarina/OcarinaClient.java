package dev.ocarina;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.CameraType;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.Direction;
import java.nio.file.*;
import java.util.*;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class OcarinaClient implements ClientModInitializer {
 private static final Logger LOG=LoggerFactory.getLogger("ocarina");
 private static final float SCALE=40f;
 // The N64 adapter already shades vertex colors. Preserve them instead of lighting twice.
 private static final com.mojang.blaze3d.pipeline.RenderPipeline OPAQUE=net.minecraft.client.renderer.RenderPipelines.register(
  com.mojang.blaze3d.pipeline.RenderPipeline.builder(net.minecraft.client.renderer.RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
   .withLocation(Identifier.fromNamespaceAndPath("ocarina","pipeline/rom_opaque")).withShaderDefine("NO_CARDINAL_LIGHTING").withShaderDefine("NO_OVERLAY")
   .withShaderDefine("ALPHA_CUTOUT",0.1f).withCull(false).build());
 private static final com.mojang.blaze3d.pipeline.RenderPipeline ALPHA=net.minecraft.client.renderer.RenderPipelines.register(
  com.mojang.blaze3d.pipeline.RenderPipeline.builder(net.minecraft.client.renderer.RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
   .withLocation(Identifier.fromNamespaceAndPath("ocarina","pipeline/rom_alpha")).withShaderDefine("NO_CARDINAL_LIGHTING").withShaderDefine("NO_OVERLAY")
   .withShaderDefine("ALPHA_CUTOUT",0.01f).withBlend(com.mojang.blaze3d.pipeline.BlendFunction.TRANSLUCENT)
   .withCull(false).withDepthWrite(false).build());
 private StoryClient story;
 private long handle;
 private boolean loaded, previousGravity, previousPhysics;
 private CameraType previousCamera;
 private net.minecraft.client.player.LocalPlayer owner;
 private KeyMapping toggle, attack, shield, target;
 private Vec3 origin=Vec3.ZERO;
 private int ticks, playerId;
 private BlockPos collisionCenter;
 private net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
 private final Map<Integer,Texture> textures=new HashMap<>();
 private volatile Snapshot snapshot;
 private volatile Snapshot sceneSnapshot;
 private volatile Vec3 targetFocus;
 private int requestedScene=-1, requestedSpawn, transitionCooldown;
 private int[] pendingExit;
 private RomAudio audio;
 private boolean audioPaused;
 private record ServerState(net.minecraft.server.level.ServerPlayer player,boolean gravity,boolean physics) {}
 private ServerState serverState;
 private Vec3 returnPosition;
 private boolean sceneMode;
 private volatile long session;
 private record Texture(Identifier id,int wrapS,int wrapT,int revision,
  net.minecraft.client.renderer.rendertype.RenderType material,net.minecraft.client.renderer.rendertype.RenderType translucent) {}
 private record Snapshot(float[] frame,Vec3 origin,Map<Integer,Texture> textures,int light) {}
 public void onInitializeClient(){
  var category=KeyMapping.Category.register(Identifier.fromNamespaceAndPath("ocarina","controls"));
  toggle=key("toggle",GLFW.GLFW_KEY_O,category);
  attack=key("sword",GLFW.GLFW_KEY_J,category);
  shield=key("shield",GLFW.GLFW_KEY_K,category);
  target=key("target",GLFW.GLFW_KEY_Z,category);
  story=new StoryClient(()->{stop(Minecraft.getInstance());requestedScene=-1;},attack,shield,target);story.initialize();
  ClientTickEvents.END_CLIENT_TICK.register(this::tick);
  net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register(this::allowDamage);
  WorldRenderEvents.END_EXTRACTION.register(context->{
   if(handle!=0)context.worldState().entityRenderStates.removeIf(s->s instanceof AvatarRenderState a && a.id==playerId);
  });
  WorldRenderEvents.AFTER_ENTITIES.register(context->{draw(context,sceneSnapshot);draw(context,snapshot);drawTarget(context);});
  net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((dispatcher,access)->{
   dispatcher.register(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("ootscene")
    .executes(ctx->enterScene(Minecraft.getInstance(),85))
    .then(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument("id",com.mojang.brigadier.arguments.IntegerArgumentType.integer(0,109))
     .executes(ctx->enterScene(Minecraft.getInstance(),com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx,"id")))));
   dispatcher.register(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("ootreturn").executes(ctx->{stop(Minecraft.getInstance());requestedScene=-1;return 1;}));
  });
  LOG.info("Ocarina 0.5.4-preview native bridge ready; use the Switch Link / Minecraft binding (default O) in a single-player world.");
 }
 private KeyMapping key(String name,int code,KeyMapping.Category category){
  return KeyBindingHelper.registerKeyBinding(new KeyMapping("key.ocarina."+name,InputConstants.Type.KEYSYM,code,category));
 }
 private void message(Minecraft mc,String text){if(mc.player!=null)mc.player.displayClientMessage(Component.literal(text),false);}
 private void start(Minecraft mc)throws Exception{
  if(!mc.hasSingleplayerServer()||mc.getSingleplayerServer().isPublished())throw new IllegalStateException("Use an unshared single-player world.");
  Properties properties=new Properties();
  try(var reader=Files.newBufferedReader(mc.gameDirectory.toPath().resolve("config/ocarina.properties"))){properties.load(reader);}
  Path rom=Path.of(properties.getProperty("rom"));
  if(!loaded){NativeBridge.load(Path.of(properties.getProperty("nativeDirectory")));loaded=true;}
  owner=mc.player;playerId=owner.getId();dimension=mc.level.dimension();origin=owner.position();ticks=0;
  returnPosition=owner.position();sceneMode=requestedScene>=0;
  previousGravity=owner.isNoGravity();previousPhysics=owner.noPhysics;previousCamera=mc.options.getCameraType();
  handle=NativeBridge.create(Files.readAllBytes(rom));if(handle==0)throw new IllegalStateException("Could not create Link runtime.");
  if(sceneMode){
   float[] scene=NativeBridge.scene(handle,requestedScene,-1,requestedSpawn,true);
   origin=new Vec3(returnPosition.x-scene[0]/SCALE,256-scene[1]/SCALE,returnPosition.z-scene[2]/SCALE);
   var ids=new HashSet<Integer>();for(int i=16;i<scene.length;i+=42)ids.add((int)scene[i+12]);
   for(int id:ids)ensureTexture(mc,id);
   sceneSnapshot=new Snapshot(scene,origin,Map.copyOf(textures),0x00f000f0);
   message(mc,"Original ROM scene "+requestedScene+" loaded. Exploration build: story actors and cutscenes are not implemented.");
  }else rebuildCollision(mc);
  NativeBridge.spawn(handle);
  owner.setNoGravity(true);owner.noPhysics=true;mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
  ++session;transitionCooldown=10;pendingExit=null;audio=new RomAudio();audioPaused=false;acquireServerPhysics(mc);
  message(mc,"Link enabled: WASD move | Space roll | J sword | K shield | Z target | use Switch Link / Minecraft to return.");
  LOG.info("Link enabled at {} using user ROM {}",origin,rom.getFileName());
 }
 private void stop(Minecraft mc){
  if(story!=null)story.stop();
  snapshot=null;sceneSnapshot=null;targetFocus=null;guard=null;pendingExit=null;++session;
  if(audio!=null){audio.close();audio=null;}
  var stoppingServer=mc.getSingleplayerServer();if(stoppingServer!=null)stoppingServer.execute(this::restoreServerPhysics);
  if(sceneMode&&returnPosition!=null&&owner!=null){
   owner.setPos(returnPosition);var server=mc.getSingleplayerServer();var uuid=owner.getUUID();var home=returnPosition;var homeDim=dimension;
   if(server!=null)server.execute(()->{var p=server.getPlayerList().getPlayer(uuid);if(p!=null&&p.level().dimension().equals(homeDim))p.setPos(home);});
  }
  sceneMode=false;
  if(handle!=0){long old=handle;handle=0;try{NativeBridge.destroy(old);}catch(Throwable t){LOG.error("Link cleanup failed",t);}}
  if(owner!=null){owner.setNoGravity(previousGravity);owner.noPhysics=previousPhysics;owner.setDeltaMovement(Vec3.ZERO);}
  if(previousCamera!=null)mc.options.setCameraType(previousCamera);
  for(Texture t:textures.values())mc.getTextureManager().release(t.id());textures.clear();
  owner=null;collisionCenter=null;previousCamera=null;
 }
 private void tick(Minecraft mc){
  try{
   while(toggle.consumeClick()){if(story.active()){story.stop();continue;}if(handle==0&&mc.player!=null&&mc.level!=null)start(mc);else stop(mc);}
   if(handle!=0&&(mc.player!=owner||mc.level==null||!mc.hasSingleplayerServer()||!mc.level.dimension().equals(dimension)||!owner.isAlive())){stop(mc);return;}
   if(handle==0)return;
   if(mc.isPaused()||mc.screen!=null){if(audio!=null&&!audioPaused){audio.pause();audioPaused=true;}return;}
   audioPaused=false;
   if(owner.position().distanceTo(origin)>600){message(mc,"Link reached the prototype area limit; returning to Minecraft.");stop(mc);return;}
   if(!sceneMode&&(ticks%10==0||collisionCenter.distSqr(owner.blockPosition())>4))rebuildCollision(mc);
   ticks++;
   float sx=(mc.options.keyRight.isDown()?1:0)-(mc.options.keyLeft.isDown()?1:0);
   float sy=(mc.options.keyUp.isDown()?1:0)-(mc.options.keyDown.isDown()?1:0);
   int buttons=(mc.options.keyJump.isDown()?1:0)|(attack.isDown()?2:0)|(target.isDown()?4:0)|(shield.isDown()?8:0);
   double yaw=Math.toRadians(owner.getYRot());
   updateActors(mc);
   float[] frame=NativeBridge.step(handle,sx,sy,(float)-Math.sin(yaw),(float)Math.cos(yaw),buttons);
   if(audio!=null)audio.submit(NativeBridge.audio(handle,2400,mc.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER)*mc.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.PLAYERS)));
   if(frame==null)return;
   if(sceneMode&&handleWorldEvent(mc))return;
   float[] lock=NativeBridge.lockOn(handle);
   targetFocus=lock!=null&&lock.length==4&&lock[0]!=0&&Float.isFinite(lock[1])&&Float.isFinite(lock[2])&&Float.isFinite(lock[3])
    ?origin.add(lock[1]/SCALE,lock[2]/SCALE,lock[3]/SCALE):null;
   if(targetFocus!=null)ensureTexture(mc,65535);
   applyCombat(mc,frame);
   Vec3 pos=origin.add(frame[0]/SCALE,frame[1]/SCALE,frame[2]/SCALE);
   if(!Double.isFinite(pos.x)||!Double.isFinite(pos.y)||!Double.isFinite(pos.z))throw new IllegalStateException("Invalid Link position");
   if(pos.y<mc.level.getMinY()+2){message(mc,"Link left the test terrain; returning to Minecraft.");stop(mc);return;}
   owner.setPos(pos);owner.setDeltaMovement(Vec3.ZERO);owner.fallDistance=0;
   var server=mc.getSingleplayerServer();var uuid=owner.getUUID();var dim=dimension;long token=session;
   server.execute(()->{if(token!=session)return;var p=server.getPlayerList().getPlayer(uuid);if(p!=null&&p.level().dimension().equals(dim)){p.setNoGravity(true);p.noPhysics=true;p.setPos(pos);p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;p.connection.resetPosition();p.connection.resetFlyingTicks();}});
   var frameTextures=new HashSet<Integer>();
   for(int i=16;i<frame.length;i+=42)frameTextures.add((int)frame[i+12]);
   for(int id:frameTextures)ensureTexture(mc,id);
   int light=net.minecraft.client.renderer.LevelRenderer.getLightColor(mc.level,owner.blockPosition().above());
   snapshot=new Snapshot(frame,origin,Map.copyOf(textures),light);
   if(ticks%100==0)LOG.info("Link tick={} triangles={} pose={}",(int)frame[8],(int)frame[9],pos);
  }catch(Throwable t){LOG.error("Link disabled after error",t);message(mc,"Link stopped: "+t.getMessage());stop(mc);}
 }
 private int enterScene(Minecraft mc,int id){
  if(mc.player==null||mc.level==null)return 0;
  stop(mc);requestedScene=id;requestedSpawn=0;
  try{start(mc);return 1;}catch(Throwable t){LOG.error("Scene load failed",t);message(mc,"Scene failed: "+t.getMessage());stop(mc);return 0;}
 }

 private void acquireServerPhysics(Minecraft mc){
  var server=mc.getSingleplayerServer();var uuid=owner.getUUID();long token=session;
  server.execute(()->{if(token!=session)return;restoreServerPhysics();var p=server.getPlayerList().getPlayer(uuid);if(p!=null){serverState=new ServerState(p,p.isNoGravity(),p.noPhysics);p.setNoGravity(true);p.noPhysics=true;p.connection.resetPosition();}});
 }
 private void restoreServerPhysics(){ServerState state=serverState;serverState=null;if(state!=null){var p=state.player();p.setNoGravity(state.gravity());p.noPhysics=state.physics();p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;p.connection.resetPosition();}}
 private boolean handleWorldEvent(Minecraft mc){
  int[] event=NativeBridge.exitEvent(handle);if(event!=null)pendingExit=event;
  if(transitionCooldown>0){transitionCooldown--;return false;}event=pendingExit;pendingExit=null;if(event==null)return false;
  if(event[0]==1){if(event[2]<0||event[2]>109){message(mc,"This exit has no supported destination in this ROM.");LOG.warn("Unsupported entrance {}",event[1]);return false;}loadDestination(mc,event[2],event[3]);}
  else {loadDestination(mc,requestedScene,requestedSpawn);message(mc,"Returned to the scene entrance after a fall.");}
  return true;
 }
 private void loadDestination(Minecraft mc,int sceneId,int spawn){
  snapshot=null;sceneSnapshot=null;targetFocus=null;guard=null;++session;
  if(audio!=null)audio.pause();
  for(Texture t:textures.values())mc.getTextureManager().release(t.id());textures.clear();
  float[] scene=NativeBridge.scene(handle,sceneId,-1,spawn,true);requestedScene=sceneId;requestedSpawn=spawn;
  origin=new Vec3(returnPosition.x-scene[0]/SCALE,256-scene[1]/SCALE,returnPosition.z-scene[2]/SCALE);
  var ids=new HashSet<Integer>();for(int i=16;i<scene.length;i+=42)ids.add((int)scene[i+12]);for(int id:ids)ensureTexture(mc,id);
  sceneSnapshot=new Snapshot(scene,origin,Map.copyOf(textures),0x00f000f0);NativeBridge.spawn(handle);transitionCooldown=10;pendingExit=null;
  owner.setPos(origin.add(scene[0]/SCALE,scene[1]/SCALE,scene[2]/SCALE));owner.setDeltaMovement(Vec3.ZERO);
  LOG.info("ROM exit loaded scene={} spawn={} triangles={}",sceneId,spawn,(int)scene[9]);
 }

 private void ensureTexture(Minecraft mc,int id){
  int revision=id==65535?0:NativeBridge.textureRevision(handle,id);
  Texture old=textures.get(id);if(old!=null&&old.revision()==revision)return;
  int[] data=id==65535?new int[]{1,1,2,2,0,0xffffffff}:NativeBridge.texture(handle,id);
  if(data==null)throw new IllegalStateException("Missing ROM texture "+id);
  int w=data[0]*(data[2]==1?2:1),h=data[1]*(data[3]==1?2:1);
  int[] pixels=TexturePixels.expand(data);
  NativeImage image=new NativeImage(w,h,false);
  for(int y=0;y<h;y++)for(int x=0;x<w;x++)image.setPixel(x,y,pixels[y*w+x]);
  Identifier location=Identifier.fromNamespaceAndPath("ocarina","runtime/texture_"+id);
  mc.getTextureManager().register(location,new DynamicTexture(()->"Ocarina ROM texture "+id,image));
  var setup=net.minecraft.client.renderer.rendertype.RenderSetup.builder(OPAQUE)
   .withTexture("Sampler0",location,()->com.mojang.blaze3d.systems.RenderSystem.getSamplerCache().getSampler(
    data[2]==2?com.mojang.blaze3d.textures.AddressMode.CLAMP_TO_EDGE:com.mojang.blaze3d.textures.AddressMode.REPEAT,
    data[3]==2?com.mojang.blaze3d.textures.AddressMode.CLAMP_TO_EDGE:com.mojang.blaze3d.textures.AddressMode.REPEAT,
    com.mojang.blaze3d.textures.FilterMode.NEAREST,com.mojang.blaze3d.textures.FilterMode.NEAREST,false))
   .useLightmap().useOverlay().createRenderSetup();
  var material=net.minecraft.client.renderer.rendertype.RenderType.create("ocarina_"+id,setup);
  var alphaSetup=net.minecraft.client.renderer.rendertype.RenderSetup.builder(ALPHA)
   .withTexture("Sampler0",location,()->com.mojang.blaze3d.systems.RenderSystem.getSamplerCache().getSampler(
    data[2]==2?com.mojang.blaze3d.textures.AddressMode.CLAMP_TO_EDGE:com.mojang.blaze3d.textures.AddressMode.REPEAT,
    data[3]==2?com.mojang.blaze3d.textures.AddressMode.CLAMP_TO_EDGE:com.mojang.blaze3d.textures.AddressMode.REPEAT,
    com.mojang.blaze3d.textures.FilterMode.NEAREST,com.mojang.blaze3d.textures.FilterMode.NEAREST,false))
   .useLightmap().useOverlay().sortOnUpload().createRenderSetup();
  var alpha=net.minecraft.client.renderer.rendertype.RenderType.create("ocarina_alpha_"+id,alphaSetup);
  textures.put(id,new Texture(location,data[2],data[3],data[4],material,alpha));
 }
 private void draw(WorldRenderContext context,Snapshot snap){
  if(snap==null)return;
  float[] f=snap.frame();Vec3 camera=context.worldState().cameraRenderState.pos;
  var matrices=context.matrices();matrices.pushPose();
  matrices.translate(snap.origin().x-camera.x,snap.origin().y-camera.y,snap.origin().z-camera.z);
  var pose=matrices.last();
  for(int pass=0;pass<2;pass++)for(var entry:snap.textures().entrySet()){
   var tex=entry.getValue();VertexConsumer consumer=context.consumers().getBuffer(pass==0?tex.material():tex.translucent());
   for(int triangle=0;triangle<(int)f[9];triangle++){
    int base=16+triangle*42;if((int)f[base+12]!=entry.getKey()||(((int)f[base+13]&16)!=0?1:0)!=pass)continue;
    for(int corner=0;corner<4;corner++){int o=base+Math.min(corner,2)*14;
     consumer.addVertex(pose,f[o]/SCALE,f[o+1]/SCALE,f[o+2]/SCALE)
      .setColor(f[o+6],f[o+7],f[o+8],f[o+9]).setUv(f[o+10]/(tex.wrapS()==1?2:1),f[o+11]/(tex.wrapT()==1?2:1))
      .setOverlay(OverlayTexture.NO_OVERLAY).setLight(snap.light()).setNormal(pose,f[o+3],f[o+4],f[o+5]);
    }
   }
  }
  matrices.popPose();
 }
 // Four inward gold pointers face the viewer and remain depth-tested against the world.
 private void drawTarget(WorldRenderContext context){
  Vec3 focus=targetFocus;Snapshot snap=snapshot;
  if(handle==0||focus==null||snap==null||Minecraft.getInstance().options.hideGui)return;
  Texture white=snap.textures().get(65535);if(white==null)return;
  Vec3 camera=context.worldState().cameraRenderState.pos;
  Vec3 center=focus.add(0,0.7,0),forward=camera.subtract(center).normalize();
  Vec3 right=new Vec3(forward.z,0,-forward.x);
  right=right.lengthSqr()<0.00001?new Vec3(1,0,0):right.normalize();
  Vec3 up=forward.cross(right).normalize();
  // A small displacement toward the camera keeps the reticle clear of the target surface.
  center=center.add(forward.scale(0.15)).subtract(camera);
  var pose=context.matrices().last();var consumer=context.consumers().getBuffer(white.material());
  for(int side=0;side<4;side++){
   double angle=side*Math.PI/2,c=Math.cos(angle),s=Math.sin(angle);
   double[][] corners={{0,0.15},{-0.09,0.33},{0.09,0.33}};
   for(int corner=0;corner<4;corner++){
    double[] v=corners[Math.min(corner,2)];double x=v[0]*c-v[1]*s,y=v[0]*s+v[1]*c;
    Vec3 point=center.add(right.scale(x)).add(up.scale(y));
    consumer.addVertex(pose,(float)point.x,(float)point.y,(float)point.z)
     .setColor(255,210,35,255).setUv(0.5f,0.5f).setOverlay(OverlayTexture.NO_OVERLAY)
     .setLight(0x00f000f0).setNormal(pose,(float)forward.x,(float)forward.y,(float)forward.z);
   }
  }
 }
 private record Guard(UUID player,net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
                      double forwardX,double forwardZ,long expires) {}
 private volatile Guard guard;
 private void updateActors(Minecraft mc){
  var nearby=mc.level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,owner.getBoundingBox().inflate(5),
   e->e!=owner&&e.isAlive()&&!(e instanceof net.minecraft.world.entity.player.Player));
  nearby.sort(Comparator.comparingDouble(e->e.distanceToSqr(owner)));
  int n=Math.min(64,nearby.size());int[] ids=new int[n];float[] cylinders=new float[n*5];
  for(int i=0;i<n;i++){var e=nearby.get(i);ids[i]=e.getId();var v=e.position().subtract(origin);
   cylinders[i*5]=(float)v.x*SCALE;cylinders[i*5+1]=(float)v.y*SCALE;cylinders[i*5+2]=(float)v.z*SCALE;
   cylinders[i*5+3]=e.getBbWidth()*SCALE/2;cylinders[i*5+4]=e.getBbHeight()*SCALE;}
  NativeBridge.actors(handle,ids,cylinders);
 }
 private void applyCombat(Minecraft mc,float[] frame){
  double angle=frame[3]*Math.PI/32768.0;
  guard=frame[11]!=0?new Guard(owner.getUUID(),dimension,Math.sin(angle),Math.cos(angle),System.nanoTime()+300_000_000L):null;
  int[] contacts=NativeBridge.contacts(handle);if(contacts.length==0)return;
  var server=mc.getSingleplayerServer();var uuid=owner.getUUID();var dim=dimension;long token=session;
  server.execute(()->{if(token!=session)return;var player=server.getPlayerList().getPlayer(uuid);
   if(player==null||!player.level().dimension().equals(dim))return;var level=player.level();
   for(int id:contacts){var entity=level.getEntity(id);
    if(entity instanceof net.minecraft.world.entity.LivingEntity victim&&!(victim instanceof net.minecraft.world.entity.player.Player)
      &&victim.isAlive()&&victim.distanceToSqr(player)<25&&player.hasLineOfSight(victim)){
     if(victim.hurtServer(level,player.damageSources().playerAttack(player),6.0f)){
      victim.knockback(0.35,player.getX()-victim.getX(),player.getZ()-victim.getZ());
      LOG.info("Link sword hit entity={} remainingHealth={}",id,victim.getHealth());
     }
    }
   }
  });
 }
 private boolean allowDamage(net.minecraft.world.entity.LivingEntity entity,net.minecraft.world.damagesource.DamageSource source,float amount){
  Guard g=guard;if(g==null||System.nanoTime()>g.expires()||!entity.getUUID().equals(g.player())||!entity.level().dimension().equals(g.dimension()))return true;
  if(source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_SHIELD))return true;
  var from=source.getSourcePosition();if(from==null)return true;
  var delta=from.subtract(entity.position());double length=Math.hypot(delta.x,delta.z);
  if(length<0.0001||(delta.x*g.forwardX()+delta.z*g.forwardZ())/length<0.15)return true;
  entity.level().playSound(null,entity.blockPosition(),net.minecraft.sounds.SoundEvents.SHIELD_BLOCK.value(),net.minecraft.sounds.SoundSource.PLAYERS,1.0f,1.0f);
  LOG.info("Link shield blocked damage={}",amount);return false;
 }
 private void rebuildCollision(Minecraft mc){
  collisionCenter=owner.blockPosition();ArrayList<Integer> data=new ArrayList<>();
  for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++)for(int y=-4;y<=5;y++){
   BlockPos pos=collisionCenter.offset(x,y,z);var state=mc.level.getBlockState(pos);var shape=state.getCollisionShape(mc.level,pos);
   if(shape.isEmpty())continue;boolean full=Block.isShapeFullBlock(shape);
   for(AABB box:shape.toAabbs()){
    double a=pos.getX()+box.minX,b=pos.getX()+box.maxX,c=pos.getY()+box.minY,d=pos.getY()+box.maxY,e=pos.getZ()+box.minZ,g=pos.getZ()+box.maxZ;
    double[][] v={{a,c,e},{b,c,e},{b,c,g},{a,c,g},{a,d,e},{b,d,e},{b,d,g},{a,d,g}};
    int[][] faces={{0,1,2,3},{4,7,6,5},{0,4,5,1},{3,2,6,7},{0,3,7,4},{1,5,6,2}};
    Direction[] dirs={Direction.DOWN,Direction.UP,Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST};
    for(int face=0;face<6;face++){
     BlockPos neighbor=pos.relative(dirs[face]);
     if(full&&Block.isShapeFullBlock(mc.level.getBlockState(neighbor).getCollisionShape(mc.level,neighbor)))continue;
     int[] q=faces[face];triangle(data,v[q[0]],v[q[1]],v[q[2]]);triangle(data,v[q[0]],v[q[2]],v[q[3]]);
    }
   }
  }
  if(data.size()>900000)throw new IllegalStateException("Too much collision in this area");
  ArrayList<Integer> water=new ArrayList<>();
  for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++){
   for(int y=16;y>=-4;y--){BlockPos p=collisionCenter.offset(x,y,z);var fluid=mc.level.getFluidState(p);
    if(!fluid.is(net.minecraft.tags.FluidTags.WATER)||mc.level.getFluidState(p.above()).is(net.minecraft.tags.FluidTags.WATER))continue;
    water.add((int)Math.round((p.getX()-origin.x)*SCALE));water.add((int)Math.round((p.getZ()-origin.z)*SCALE));water.add((int)SCALE);water.add((int)SCALE);
    water.add((int)Math.round((p.getY()+fluid.getHeight(mc.level,p)-origin.y)*SCALE));break;
   }
  }
  NativeBridge.worldWater(handle,data.stream().mapToInt(Integer::intValue).toArray(),water.stream().mapToInt(Integer::intValue).toArray());
 }
 private void triangle(List<Integer> out,double[]a,double[]b,double[]c){for(double[]v:new double[][]{a,b,c}){out.add((int)Math.round((v[0]-origin.x)*SCALE));out.add((int)Math.round((v[1]-origin.y)*SCALE));out.add((int)Math.round((v[2]-origin.z)*SCALE));}}
}
