import dev.ocarina.*;
import java.nio.file.*;
public final class BridgeCombat {
 public static void main(String[] args)throws Exception{
  int[] tile={2,2,1,1,7,0xff000001,0xff000002,0xff000003,0xff000004};
  int[] expected={0xff000001,0xff000002,0xff000002,0xff000001,0xff000003,0xff000004,0xff000004,0xff000003,0xff000003,0xff000004,0xff000004,0xff000003,0xff000001,0xff000002,0xff000002,0xff000001};
  if(!java.util.Arrays.equals(TexturePixels.expand(tile),expected))throw new AssertionError("Mirror expansion");
  NativeBridge.load(Path.of(args[0]));long h=NativeBridge.create(Files.readAllBytes(Path.of(args[1])));
  try{
   NativeBridge.world(h,new int[]{-1000,0,-1000,-1000,0,1000,1000,0,1000,-1000,0,-1000,1000,0,1000,1000,0,-1000});NativeBridge.spawn(h);
   NativeBridge.actors(h,new int[]{42},new float[]{0,0,60,25,90});
   boolean swing=false,shield=false;int hits=0;float[] f=null;
   for(int i=0;i<240;i++){
    int buttons=i>=40&&i<180&&i%30<8?2:0;
    f=NativeBridge.step(h,0,0,0,1,buttons);if(f!=null)swing|=f[10]!=0;
    for(int id:NativeBridge.contacts(h)){if(id!=42)throw new AssertionError("Wrong target");hits++;}
   }
   for(int i=0;i<45;i++){f=NativeBridge.step(h,0,0,0,1,8);if(f!=null)shield|=f[11]!=0;}
   for(int i=0;i<20;i++)f=NativeBridge.step(h,0,0,0,1,0);
   if(!swing||hits==0||!shield||f[11]!=0)throw new AssertionError("swing="+swing+" hits="+hits+" shield="+shield+" release="+f[11]);
   NativeBridge.actors(h,new int[0],new float[0]);
   java.util.Set<Integer> ids=new java.util.HashSet<>();for(int i=16;i<f.length;i+=42)ids.add((int)f[i+12]);
   for(int id:ids)if(id!=65535){int[] data=NativeBridge.texture(h,id);if(data==null||data.length!=5+data[0]*data[1]||data[4]!=NativeBridge.textureRevision(h,id))throw new AssertionError("Texture packet "+id);TexturePixels.expand(data);}
   System.out.println("PASS mirror pixels; native sword swing="+swing+" hits="+hits+"; shield raised/released; textures="+ids.size());
  }finally{NativeBridge.destroy(h);}
 }
}
