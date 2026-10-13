import dev.ocarina.NativeBridge;
import java.nio.file.*;
public final class BridgeTarget {
 static long h;
 static float[] tick(int buttons){NativeBridge.step(h,0,0,0,1,buttons);return NativeBridge.lockOn(h);}
 static void require(boolean ok,String what){if(!ok)throw new AssertionError(what);}
 public static void main(String[] args)throws Exception{
  NativeBridge.load(Path.of(args[0]));h=NativeBridge.create(Files.readAllBytes(Path.of(args[1])));
  try{
   NativeBridge.world(h,new int[]{-1000,0,-1000,-1000,0,1000,1000,0,1000,-1000,0,-1000,1000,0,1000,1000,0,-1000});NativeBridge.spawn(h);
   require(NativeBridge.lockOn(h)[0]==0,"Fresh Link has no lock");
   NativeBridge.actors(h,new int[]{42},new float[]{0,0,100,25,90});
   for(int i=0;i<30;i++)tick(0);
   float[] lock=null;for(int i=0;i<12;i++)lock=tick(4);
   require(lock[0]!=0,"Z acquires a host mob");
   require(Math.abs(lock[1])<0.01&&Math.abs(lock[2]-67.5)<0.01&&Math.abs(lock[3]-100)<0.01,"Native focus coordinate "+java.util.Arrays.toString(lock));
   NativeBridge.actors(h,new int[]{42},new float[]{20,0,110,25,90});
   for(int i=0;i<5;i++)lock=tick(4);
   require(lock[0]!=0&&Math.abs(lock[1]-20)<0.01&&Math.abs(lock[3]-110)<0.01,"Marker follows moving target");
   NativeBridge.actors(h,new int[0],new float[0]);for(int i=0;i<10;i++)lock=tick(4);
   require(lock[0]==0,"Removed target releases marker");
   System.out.println("PASS: no initial marker; Z acquires native host mob; exact focus and moving-target tracking; removal clears lock.");
  }finally{NativeBridge.destroy(h);}
 }
}
