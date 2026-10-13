package dev.ocarina;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;

/** Versioned local mesh transport. No game files are embedded in the mod. */
public final class StoryChannel implements AutoCloseable {
 public static final int MAGIC=0x53544f4f, VERSION=1, CAPACITY=16*1024*1024, HEADER=320, DRAW_HEADER=384;
 private static final VarHandle INT=MethodHandles.byteBufferViewVarHandle(int[].class,ByteOrder.LITTLE_ENDIAN);
 private final FileChannel file;
 private final MappedByteBuffer memory;
 private long heartbeat,lastFrame;
 public final Path directory;
 public record Texture(int id,int revision,int width,int height,int wrapS,int wrapT,int linear) {}
 public record Draw(int shader,int flags,int framebuffer,int stride,int triangles,float noise,float depth,
  int[] viewport,int[] scissor,int[] sizes,int[] locations,Texture[] textures,FloatBuffer vertices) {}
 public record Frame(long number,float[] state,List<Draw> draws) {}
 public StoryChannel(Path directory)throws IOException{
  this.directory=directory.toAbsolutePath().normalize();Files.createDirectories(this.directory);
  file=FileChannel.open(this.directory.resolve("channel.bin"),StandardOpenOption.CREATE,StandardOpenOption.READ,StandardOpenOption.WRITE);
  memory=file.map(FileChannel.MapMode.READ_WRITE,0,CAPACITY+4096);memory.order(ByteOrder.LITTLE_ENDIAN);
  input(0,0,0,true,false);
 }
 public void input(int buttons,int x,int y,boolean paused,boolean stop){input(buttons,x,y,paused,stop,false,null);}
 public void input(int buttons,int x,int y,boolean paused,boolean stop,boolean world,float[] camera){
  input(buttons,x,y,paused,stop,world,camera,0,-1,null);
 }
 public void input(int buttons,int x,int y,boolean paused,boolean stop,boolean world,float[] camera,int epoch,int scene,float[] boxes){
  int count=boxes==null?0:boxes.length/6;
  if(boxes!=null&&(boxes.length%6!=0||count>64))throw new IllegalArgumentException("Too many story collision boxes");
  if(boxes!=null)for(int b=0;b<count;b++)for(int a=0;a<3;a++){float lo=boxes[b*6+a],hi=boxes[b*6+a+3];if(!Float.isFinite(lo)||!Float.isFinite(hi)||lo < -32000||hi > 32000||hi-lo<1||hi-lo>1024)throw new IllegalArgumentException("Invalid story collision box");}
  int seq=(int)INT.getVolatile(memory,8);INT.setVolatile(memory,8,(seq+1)|1);
  memory.putInt(0,MAGIC).putInt(4,VERSION).putInt(12,(paused?1:0)|(stop?2:0)|(world?4:0));
  memory.putLong(16,++heartbeat).putInt(24,buttons&0xffff).putInt(28,Math.clamp(x,-85,85)).putInt(32,Math.clamp(y,-85,85));
  for(int i=0;i<10;i++)memory.putFloat(40+i*4,camera!=null&&camera.length==10&&Float.isFinite(camera[i])?camera[i]:0f);
  memory.putInt(80,1).putInt(84,epoch).putInt(88,scene).putInt(92,count);
  for(int i=0;i<count*6;i++)memory.putFloat(96+i*4,boxes[i]);
  VarHandle.releaseFence();INT.setVolatile(memory,8,((seq+1)|1)+1);
 }
 public Frame read()throws IOException{
  for(int attempt=0;attempt<3;attempt++){
   int before=(int)INT.getVolatile(memory,4104);if(before==0||(before&1)!=0)return null;
   int bytes=memory.getInt(4108);long number=memory.getLong(4112);
   if(number==lastFrame)return null;
   if(bytes<HEADER||bytes>CAPACITY)continue;
   byte[] data=new byte[bytes];memory.get(4096,data);VarHandle.acquireFence();
   if(before!=(int)INT.getVolatile(memory,4104))continue;
   Frame result=decode(ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN));lastFrame=result.number();return result;
  }
  return null;
 }
 public static Frame decode(ByteBuffer input)throws IOException{
  ByteBuffer data=input.slice().order(ByteOrder.LITTLE_ENDIAN);
  if(data.remaining()<HEADER||data.getInt(0)!=MAGIC||data.getInt(4)!=VERSION)throw new IOException("Unsupported story frame");
  int bytes=data.getInt(12),count=data.getInt(24);
  if(bytes!=data.remaining()||bytes>CAPACITY||count<0||count>8192||data.getInt(28)!=0)throw new IOException("Incomplete story frame");
  float[] state=new float[48];for(int i=0;i<48;i++){state[i]=data.getFloat(64+i*4);if(!Float.isFinite(state[i]))throw new IOException("Invalid story pose");}
  List<Draw> draws=new ArrayList<>(count);int offset=HEADER;
  for(int i=0;i<count;i++){
   if(offset>bytes-DRAW_HEADER)throw new IOException("Truncated story draw");
   int length=data.getInt(offset),stride=data.getInt(offset+16),attributes=data.getInt(offset+20),triangles=data.getInt(offset+24),floats=data.getInt(offset+28);
   if(stride<4||stride>64||attributes<1||attributes>16||triangles<0||floats<0||
    (long)triangles*3*stride!=floats||(long)DRAW_HEADER+floats*4L!=length||length>bytes-offset)throw new IOException("Invalid story vertex count");
   int[] viewport=ints(data,offset+40,4),scissor=ints(data,offset+56,4),sizes=ints(data,offset+72,attributes),locations=ints(data,offset+136,attributes);
   int total=0;for(int j=0;j<attributes;j++){if(sizes[j]<1||sizes[j]>4||locations[j]<-1||locations[j]>31)throw new IOException("Invalid story vertex layout");total+=sizes[j];}
   if(total!=stride)throw new IOException("Mismatched story vertex stride");
   Texture[] textures=new Texture[6];
   for(int j=0;j<6;j++){
    int p=offset+200+j*28;int[] t=ints(data,p,7);
    if(t[0]<0||t[1]<0||t[2]<0||t[3]<0||t[2]>4096||t[3]>4096||t[4]<0||t[4]>3||t[5]<0||t[5]>3)throw new IOException("Invalid story texture");
    textures[j]=new Texture(t[0],t[1],t[2],t[3],t[4],t[5],t[6]);
   }
   ByteBuffer vertices=data.slice(offset+DRAW_HEADER,floats*4).order(ByteOrder.LITTLE_ENDIAN);
   FloatBuffer values=vertices.asFloatBuffer();
   boolean[] consumed=new boolean[stride];int component=0;for(int a=0;a<attributes;a++)for(int k=0;k<sizes[a];k++)consumed[component++]=locations[a]>=0;
   for(int j=0;j<values.limit();j++)if(!Float.isFinite(values.get(j))){if(consumed[j%stride])throw new IOException("Invalid active story vertex attribute");values.put(j,0f);}
   draws.add(new Draw(data.getInt(offset+4),data.getInt(offset+8),data.getInt(offset+12),stride,triangles,data.getFloat(offset+32),data.getFloat(offset+36),viewport,scissor,sizes,locations,textures,values.asReadOnlyBuffer()));
   offset+=length;
  }
  if(offset!=bytes)throw new IOException("Unexpected story frame tail");
  return new Frame(data.getLong(16),state,List.copyOf(draws));
 }
 private static int[] ints(ByteBuffer data,int offset,int count){int[] values=new int[count];for(int i=0;i<count;i++)values[i]=data.getInt(offset+i*4);return values;}
 public void close()throws IOException{input(0,0,0,true,true);file.close();}
}
