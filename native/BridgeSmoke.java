import dev.ocarina.NativeBridge;
import java.nio.file.*;
public class BridgeSmoke {
 public static void main(String[] args)throws Exception {
  NativeBridge.load(Path.of(args[0]));
  long handle=NativeBridge.create(Files.readAllBytes(Path.of(args[1])));
  if(handle==0)throw new AssertionError("create returned null");
  try {
   NativeBridge.world(handle,new int[]{-1000,0,-1000,-1000,0,1000,1000,0,1000,-1000,0,-1000,1000,0,1000,1000,0,-1000});
   NativeBridge.spawn(handle);float[] frame=null;
   for(int i=0;i<120;i++) {frame=NativeBridge.step(handle,0,i<30?1:0,0,1,i==40?1:0);}
   if(frame==null||frame[9]<100||Math.abs(frame[1])>5)throw new AssertionError("Invalid pose/geometry");
   java.util.Set<Integer> ids=new java.util.HashSet<>();for(int i=16;i<frame.length;i+=13){for(int j=0;j<12;j++)if(!Float.isFinite(frame[i+j]))throw new AssertionError("Non-finite vertex");int id=(int)frame[i+12];if(id!=65535)ids.add(id);}
   int textures=0;for(int id:ids){int[] t=NativeBridge.texture(handle,id);if(t==null||t.length!=t[0]*t[1]+4)throw new AssertionError("Texture "+id);textures++;}
   System.out.printf("PASS: tick=%.0f position=(%.2f,%.2f,%.2f), triangles=%.0f, textures=%d%n",frame[8],frame[0],frame[1],frame[2],frame[9],textures);
  }finally{NativeBridge.destroy(handle);}
 }
}
