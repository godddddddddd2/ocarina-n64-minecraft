import dev.ocarina.*;import java.nio.file.*;import java.util.*;
public final class BridgeScenes{
 public static void main(String[]a)throws Exception{
  NativeBridge.load(Path.of(a[0]));byte[]rom=Files.readAllBytes(Path.of(a[1]));
  for(int id:new int[]{85,52,0,81}){long h=NativeBridge.create(rom);try{
   float[]s=NativeBridge.scene(h,id,-1,0,true);if(s==null||s[9]<=0||s.length!=16+(int)s[9]*42)throw new AssertionError("Scene "+id);
   Set<Integer>textures=new HashSet<>();int translucent=0;for(int i=16;i<s.length;i+=42){textures.add((int)s[i+12]);if(((int)s[i+13]&16)!=0)translucent++;}
   for(int t:textures)if(t!=65535)TexturePixels.expand(NativeBridge.texture(h,t));
   NativeBridge.spawn(h);float[]f=null;for(int i=0;i<80;i++)f=NativeBridge.step(h,0,0,0,1,0);
   if(f==null||f[13]!=1||!Float.isFinite(f[1])||f[1]<s[1]-1000)throw new AssertionError("Scene spawn/collision "+id);
   System.out.printf("PASS scene=%d triangles=%.0f textures=%d translucent=%d child=%.0f y=%.1f%n",id,s[9],textures.size(),translucent,f[13],f[1]);
  }finally{NativeBridge.destroy(h);}}
 }
}
