import dev.ocarina.NativeBridge;
import java.nio.file.*;
public class BridgeWorldFeatures {
 static int[] floor(int y){return new int[]{-2000,y,-2000,-2000,y,2000,2000,y,2000,-2000,y,-2000,2000,y,2000,2000,y,-2000};}
 public static void main(String[] args)throws Exception{
  NativeBridge.load(Path.of(args[0]));long h=NativeBridge.create(Files.readAllBytes(Path.of(args[1])));
  try{
   int[] route=NativeBridge.entrance(h,0);if(route==null||route[0]!=0||route[1]!=0)throw new AssertionError("ROM entrance table absent");
   int count=0;for(int i=0;i<0x614;i++)if(NativeBridge.entrance(h,i)!=null)count++;
   NativeBridge.world(h,floor(0));NativeBridge.spawn(h);long energy=0;int audible=0;
   for(int i=0;i<160;i++){NativeBridge.step(h,0,i<60?.5f:0,0,1,i>=70&&i%30<5?2:0);byte[] a=NativeBridge.audio(h,2400,1);if(a==null||a.length!=9600)throw new AssertionError("Audio frame length");long chunk=0;for(int j=0;j<a.length;j+=2)chunk+=Math.abs((short)((a[j]&255)|(a[j+1]<<8)));energy+=chunk;if(chunk>0)audible++;}
   if(energy<10000||audible<4)throw new AssertionError("Silent native SFX: "+energy+" chunks="+audible);
   NativeBridge.worldWater(h,floor(-250),new int[]{-2000,-2000,4000,4000,0});NativeBridge.pose(h,0,-30,0,0);
   boolean water=false;float[] f=null;for(int i=0;i<140;i++){f=NativeBridge.step(h,0,.3f,0,1,i==50?1:0);if(f!=null)water|=f[14]!=0;NativeBridge.audio(h,2400,0);}
   if(!water||f==null||f[1]<-180)throw new AssertionError("Swimming failed: "+(f==null?"null":f[1]));
   System.out.println("PASS ROM entrances="+count+" audibleChunks="+audible+" energy="+energy+" swimmingY="+f[1]);
   for(int scene:new int[]{85,52,85,88,0}){float[] mesh=NativeBridge.scene(h,scene,-1,0,true);NativeBridge.spawn(h);for(int i=0;i<8;i++)NativeBridge.step(h,0,0,0,1,0);if(mesh.length<=16)throw new AssertionError("Missing scene mesh");System.out.println("PASS live scene transition "+scene);}
  }finally{NativeBridge.destroy(h);}
 }
}
