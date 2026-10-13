package dev.ocarina;

import java.nio.file.Path;

/** All calls are serialized on the Minecraft client thread. */
public final class NativeBridge {
 private NativeBridge() {}
 public static void load(Path nativeDirectory) {
  System.load(nativeDirectory.resolve("liboot.dll").toAbsolutePath().toString());
  System.load(nativeDirectory.resolve("ocarina_bridge.dll").toAbsolutePath().toString());
 }
 public static native long create(byte[] rom);
 public static native void world(long handle, int[] triangles);
 public static native float[] scene(long handle,int scene,int room,int spawn,boolean child);
 public static native void spawn(long handle);
 public static native float[] step(long handle,float stickX,float stickY,float camX,float camZ,int buttons);
 public static native int[] texture(long handle,int index);
 public static native int textureRevision(long handle,int index);
 public static native void actors(long handle,int[] ids,float[] cylinders);
 /** Last simulation step: active, target focus X/Y/Z in ROM units. */
 public static native float[] lockOn(long handle);
 public static native int[] contacts(long handle);
 public static native byte[] audio(long handle,int frames,float gain);
 public static native int[] exitEvent(long handle);
 public static native int[] entrance(long handle,int entranceId);
 public static native void pose(long handle,float x,float y,float z,int yaw);
 public static native void worldWater(long handle,int[] triangles,int[] waterBoxes);
 public static native void destroy(long handle);
}
