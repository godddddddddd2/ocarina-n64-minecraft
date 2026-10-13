package dev.ocarina;

import java.io.IOException;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;

/** Replays native triangle streams in the host GL context; never uploads a game screenshot. */
public final class StoryRenderer implements AutoCloseable {
 private record Program(int id) {}
 private record Texture(int id,int revision) {}
 private final Path resources;
 private final Map<Integer,Program> programs=new HashMap<>();
 private final Map<Integer,Texture> textures=new HashMap<>();
 private int vao;private final StoryGeometryCache geometry=new StoryGeometryCache();
 public StoryGeometryCache.Stats cacheStats(){return geometry.stats();}
 public StoryRenderer(Path directory){resources=directory.resolve("resources");}
 public void draw(StoryChannel.Frame frame,int width,int height)throws IOException{draw(frame,width,height,false);}
 public void draw(StoryChannel.Frame frame,int width,int height,boolean clear)throws IOException{draw(frame,width,height,clear,null);}
 public void draw(StoryChannel.Frame frame,int width,int height,boolean clear,float[] worldMatrix)throws IOException{
  if(frame==null||frame.draws().isEmpty())return;
  try(var saved=new State()){
   if(vao==0)vao=GL30.glGenVertexArrays();
   GL30.glBindVertexArray(vao);geometry.begin(frame);
   GL11.glDisable(GL11.GL_CULL_FACE);GL11.glDisable(GL30.GL_FRAMEBUFFER_SRGB);GL11.glEnable(GL11.GL_SCISSOR_TEST);
   GL11.glColorMask(true,true,true,true);
   if(clear){GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDepthMask(true);GL11.glClearColor(0,0,0,1);GL11.glClearDepth(1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);GL11.glEnable(GL11.GL_SCISSOR_TEST);}GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD,GL14.GL_FUNC_ADD);
   GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);
   int target=mainTarget(frame);
   boolean exactTextures=frame.state()[45]>=1;
   int sourceWidth=1,sourceHeight=1;
   for(var d:frame.draws())if(d.framebuffer()==target){sourceWidth=Math.max(sourceWidth,d.viewport()[0]+d.viewport()[2]);sourceHeight=Math.max(sourceHeight,d.viewport()[1]+d.viewport()[3]);}
   boolean seenWorld=false;
   for(var d:frame.draws()){
    if(d.framebuffer()!=target||d.triangles()==0)continue;
    if(worldMatrix!=null){if((d.flags()&16)!=0)seenWorld=true;else if(!seenWorld)continue;if((d.flags()&16)!=0&&(d.flags()&3)==0)continue;}
    if(d.framebuffer()!=target||d.triangles()==0)continue;
    int program=program(d.shader());GL20.glUseProgram(program);
    boolean world=(d.flags()&16)!=0;
    if(world&&worldMatrix==null)continue;
    uniform(program,"ootWorldEnabled",world?1:0);if(world)GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(program,"ootWorldMatrix"),false,worldMatrix);
    uniform(program,"frame_count",(int)frame.number());uniform(program,"noise_scale",d.noise());uniform(program,"prim_depth",d.depth());
    for(int t=0;t<6;t++){
     var texture=d.textures()[t];GL13.glActiveTexture(GL13.GL_TEXTURE0+t);GL33.glBindSampler(t,0);
     int id=texture.id()==0||texture.revision()==0?0:texture(texture,exactTextures);
     GL11.glBindTexture(GL11.GL_TEXTURE_2D,id);
     if(id!=0){GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,wrap(texture.wrapS()));GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,wrap(texture.wrapT()));}
    }
    String[] samplers={"uTex0","uTex1","uTexMask0","uTexMask1","uTexBlend0","uTexBlend1"};for(int t=0;t<6;t++)uniform(program,samplers[t],t);
    GL20.glUniform1iv(GL20.glGetUniformLocation(program,"texture_width"),new int[]{d.textures()[0].width(),d.textures()[1].width()});
    GL20.glUniform1iv(GL20.glGetUniformLocation(program,"texture_height"),new int[]{d.textures()[0].height(),d.textures()[1].height()});
    GL20.glUniform1iv(GL20.glGetUniformLocation(program,"texture_filtering"),new int[]{d.textures()[0].linear()!=0?0:1,d.textures()[1].linear()!=0?0:1});
    set(GL11.GL_DEPTH_TEST,world||(d.flags()&3)!=0);GL11.glDepthMask((d.flags()&2)!=0);
    GL11.glDepthFunc(!world&&(d.flags()&1)==0?GL11.GL_ALWAYS:(d.flags()&8)!=0?GL11.GL_LEQUAL:GL11.GL_LESS);
    set(GL11.GL_BLEND,(d.flags()&4)!=0);set(GL11.GL_POLYGON_OFFSET_FILL,(d.flags()&8)!=0);GL11.glPolygonOffset(-2,-2);
    int[] vp=d.viewport(),sc=d.scissor();GL11.glViewport(scale(vp[0],width,sourceWidth),scale(vp[1],height,sourceHeight),scale(vp[2],width,sourceWidth),scale(vp[3],height,sourceHeight));
    GL11.glScissor(scale(sc[0],width,sourceWidth),scale(sc[1],height,sourceHeight),Math.max(0,scale(sc[2],width,sourceWidth)),Math.max(0,scale(sc[3],height,sourceHeight)));
    if(world){GL11.glViewport(0,0,width,height);GL11.glScissor(0,0,width,height);}
    int cull=world?d.flags()&96:0;set(GL11.GL_CULL_FACE,cull!=0);GL11.glFrontFace(GL11.GL_CCW);if(cull!=0)GL11.glCullFace(cull==96?GL11.GL_FRONT_AND_BACK:cull==64?GL11.GL_FRONT:GL11.GL_BACK);
    geometry.bind(d);
    GL11.glDrawArrays(GL11.GL_TRIANGLES,0,d.triangles()*3);
   }
  }
 }
 public static int mainTarget(StoryChannel.Frame frame){
  if(frame.state()[44]>=1)return (int)frame.state()[43];
  return frame.draws().stream().mapToInt(StoryChannel.Draw::framebuffer).min().orElse(0);
 }
 private int program(int id)throws IOException{
  Program previous=programs.get(id);if(previous!=null)return previous.id();
  byte[] bytes=bounded(resources.resolve("shader-"+id+".bin"),2*1024*1024);ByteBuffer b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
  if(bytes.length<20||b.getInt()!=0x52444853||b.getInt()!=1)throw new IOException("Invalid original shader");
  int vsLength=b.getInt(),fsLength=b.getInt(),count=b.getInt();
  if(count<0||count>32||vsLength<0||fsLength<0||20L+count*68L+vsLength+fsLength!=bytes.length)throw new IOException("Invalid original shader lengths");
  int[] locations=new int[count];String[] names=new String[count];for(int i=0;i<count;i++){locations[i]=b.getInt();byte[] name=new byte[64];b.get(name);int end=0;while(end<64&&name[end]!=0)end++;names[i]=new String(name,0,end,StandardCharsets.UTF_8);}
  byte[] v=new byte[vsLength],f=new byte[fsLength];b.get(v).get(f);
  int vertex=compile(GL20.GL_VERTEX_SHADER,source(new String(v,StandardCharsets.UTF_8),true)),fragment=0,program=0;
  try{
   fragment=compile(GL20.GL_FRAGMENT_SHADER,source(new String(f,StandardCharsets.UTF_8),false));program=GL20.glCreateProgram();
   GL20.glAttachShader(program,vertex);GL20.glAttachShader(program,fragment);
   for(int i=0;i<count;i++)if(locations[i]>=0)GL20.glBindAttribLocation(program,locations[i],names[i]);
   GL20.glLinkProgram(program);if(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)==0)throw new IOException("Original shader link: "+GL20.glGetProgramInfoLog(program));
   programs.put(id,new Program(program));return program;
  }catch(IOException e){if(program!=0)GL20.glDeleteProgram(program);throw e;}finally{GL20.glDeleteShader(vertex);if(fragment!=0)GL20.glDeleteShader(fragment);}
 }
 private static String source(String original,boolean vertex){
  String s=original.replaceFirst("#version[^\n]*","#version 150 core").replace("texture2D(","texture(");
  s=s.replaceAll("\\battribute\\b","in").replaceAll("\\bvarying\\b",vertex?"out":"in");
  if(!vertex&&s.contains("gl_FragColor")){s=s.replace("gl_FragColor","ootFragmentColor");int end=s.indexOf('\n');s=s.substring(0,end+1)+"out vec4 ootFragmentColor;\n"+s.substring(end+1);}
  if(vertex){s=s.replaceFirst("void\\s+main\\s*\\(","void ootOriginalMain(");s+="\n uniform int ootWorldEnabled; uniform mat4 ootWorldMatrix; void main(){ootOriginalMain();if(ootWorldEnabled!=0)gl_Position=ootWorldMatrix*gl_Position;}\n";}
  return s;
 }
 private static int compile(int type,String source)throws IOException{
  int shader=GL20.glCreateShader(type);GL20.glShaderSource(shader,source);GL20.glCompileShader(shader);
  if(GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS)==0){String error=GL20.glGetShaderInfoLog(shader);GL20.glDeleteShader(shader);throw new IOException("Original shader compile: "+error);}return shader;
 }
 private int texture(StoryChannel.Texture input,boolean exact)throws IOException{
  Texture previous=textures.get(input.id());if(previous!=null&&previous.revision()==input.revision())return previous.id();
  byte[] bytes=bounded(resources.resolve("texture-"+input.id()+(exact?"-"+input.revision():"")+".bin"),64*1024*1024+20);ByteBuffer b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
  if(bytes.length<20||b.getInt()!=0x52545854||b.getInt()!=1)throw new IOException("Invalid original texture");
  int width=b.getInt(),height=b.getInt(),revision=b.getInt();if(width<1||height<1||width>4096||height>4096||20L+width*height*4L!=bytes.length)throw new IOException("Invalid original texture dimensions");
  int texture=previous==null?GL11.glGenTextures():previous.id();GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT,1);GL11.glPixelStorei(GL12.GL_UNPACK_ROW_LENGTH,0);GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_ROWS,0);GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_PIXELS,0);GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,0);
  ByteBuffer pixels=MemoryUtil.memAlloc(bytes.length-20);try{pixels.put(bytes,20,bytes.length-20).flip();GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,width,height,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);}finally{MemoryUtil.memFree(pixels);}
  GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
  textures.put(input.id(),new Texture(texture,revision));return texture;
 }
 private static byte[] bounded(Path path,int limit)throws IOException{long size=Files.size(path);if(size>limit)throw new IOException("Oversized story resource");return Files.readAllBytes(path);}
 private static int wrap(int value){return switch(value){case 1->GL14.GL_MIRRORED_REPEAT;case 2->GL12.GL_CLAMP_TO_EDGE;case 3->0x8743;default->GL11.GL_REPEAT;};}
 private static int scale(int v,int target,int source){return Math.round((float)v*target/source);}
 private static void uniform(int program,String name,int value){GL20.glUniform1i(GL20.glGetUniformLocation(program,name),value);}
 private static void uniform(int program,String name,float value){GL20.glUniform1f(GL20.glGetUniformLocation(program,name),value);}
 private static void set(int flag,boolean value){if(value)GL11.glEnable(flag);else GL11.glDisable(flag);}
 public void close(){for(var p:programs.values())GL20.glDeleteProgram(p.id());for(var t:textures.values())GL11.glDeleteTextures(t.id());geometry.close();if(vao!=0)GL30.glDeleteVertexArrays(vao);programs.clear();textures.clear();vao=0;}
 private static final class State implements AutoCloseable {
  final int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),vao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),buffer=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING),active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),unpack=GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT),depthFunction=GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
  final int unpackBuffer=GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING),unpackRow=GL11.glGetInteger(GL12.GL_UNPACK_ROW_LENGTH),unpackSkipRows=GL11.glGetInteger(GL12.GL_UNPACK_SKIP_ROWS),unpackSkipPixels=GL11.glGetInteger(GL12.GL_UNPACK_SKIP_PIXELS);
  final int cullMode=GL11.glGetInteger(GL11.GL_CULL_FACE_MODE),frontFace=GL11.glGetInteger(GL11.GL_FRONT_FACE);
  final float[] clearColor=new float[4];final double clearDepth=GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE);
  final int[] viewport=new int[4],scissor=new int[4],textures=new int[6],samplers=new int[6];
  final int[] blend={GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA),GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB),GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA)};
  final int[] flags={GL11.GL_DEPTH_TEST,GL11.GL_BLEND,GL11.GL_CULL_FACE,GL11.GL_SCISSOR_TEST,GL11.GL_POLYGON_OFFSET_FILL,GL30.GL_FRAMEBUFFER_SRGB};final boolean[] enabled=new boolean[6];
  final boolean depthMask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);final ByteBuffer colorMask=MemoryUtil.memAlloc(4);
  final float factor=GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_FACTOR),units=GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_UNITS);
  State(){GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE,clearColor);GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX,scissor);GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK,colorMask);for(int i=0;i<6;i++){enabled[i]=GL11.glIsEnabled(flags[i]);GL13.glActiveTexture(GL13.GL_TEXTURE0+i);textures[i]=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);samplers[i]=GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);}}
  public void close(){GL11.glCullFace(cullMode);GL11.glFrontFace(frontFace);GL11.glClearColor(clearColor[0],clearColor[1],clearColor[2],clearColor[3]);GL11.glClearDepth(clearDepth);GL20.glUseProgram(program);GL30.glBindVertexArray(vao);GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,buffer);for(int i=0;i<6;i++){GL13.glActiveTexture(GL13.GL_TEXTURE0+i);GL11.glBindTexture(GL11.GL_TEXTURE_2D,textures[i]);GL33.glBindSampler(i,samplers[i]);set(flags[i],enabled[i]);}GL13.glActiveTexture(active);GL11.glViewport(viewport[0],viewport[1],viewport[2],viewport[3]);GL11.glScissor(scissor[0],scissor[1],scissor[2],scissor[3]);GL11.glDepthFunc(depthFunction);GL11.glDepthMask(depthMask);GL14.glBlendFuncSeparate(blend[0],blend[1],blend[2],blend[3]);GL20.glBlendEquationSeparate(blend[4],blend[5]);GL11.glPolygonOffset(factor,units);GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT,unpack);GL11.glPixelStorei(GL12.GL_UNPACK_ROW_LENGTH,unpackRow);GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_ROWS,unpackSkipRows);GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_PIXELS,unpackSkipPixels);GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,unpackBuffer);GL11.glColorMask(colorMask.get(0)!=0,colorMask.get(1)!=0,colorMask.get(2)!=0,colorMask.get(3)!=0);MemoryUtil.memFree(colorMask);}
 }
}
