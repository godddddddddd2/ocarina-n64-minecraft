import dev.ocarina.NativeBridge;
import java.nio.file.*;
public final class BridgeBehavior {
 static int[] floor(int y){return new int[]{-1000,y,-1000,-1000,y,1000,1000,y,1000,-1000,y,-1000,1000,y,1000,1000,y,-1000};}
 public static void main(String[] args)throws Exception{
  NativeBridge.load(Path.of(args[0]));byte[] rom=Files.readAllBytes(Path.of(args[1]));
  for(int cycle=0;cycle<2;cycle++){
   long h=NativeBridge.create(rom);if(h==0)throw new AssertionError("No engine");
   try{
    int[] base=floor(0),wall={-1000,0,100,-1000,1000,100,1000,1000,100,-1000,0,100,1000,1000,100,1000,0,100};
    int[] collision=new int[base.length+wall.length];System.arraycopy(base,0,collision,0,base.length);System.arraycopy(wall,0,collision,base.length,wall.length);
    NativeBridge.world(h,collision);NativeBridge.spawn(h);
    float[] f=null;boolean rolled=false;
    for(int i=0;i<150;i++){f=NativeBridge.step(h,0,1,0,1,i==12?1:0);if(f!=null&&f[6]==3)rolled=true;}
    if(f==null||f[2]>100||Math.abs(f[1])>2)throw new AssertionError("Wall/floor collision failed");
    System.out.printf("cycle %d wall: z=%.2f y=%.2f roll=%s%n",cycle,f[2],f[1],rolled);
    NativeBridge.world(h,floor(-80));
    for(int i=0;i<150;i++)f=NativeBridge.step(h,0,0,0,1,0);
    if(f==null||Math.abs(f[1]+80)>2)throw new AssertionError("Collision refresh failed");
    System.out.printf("cycle %d collision refresh: y=%.2f%n",cycle,f[1]);
   }finally{NativeBridge.destroy(h);}
  }
  System.out.println("PASS: wall collision, runtime terrain replacement, and destroy/recreate.");
 }
}