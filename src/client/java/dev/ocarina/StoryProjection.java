package dev.ocarina;
import org.joml.Matrix4f;
/** Exact matrix passed to Minecraft's world projection buffer, including view effects. */
public final class StoryProjection {
 private static final Matrix4f projection=new Matrix4f();private static boolean ready;
 public static void capture(Matrix4f value){projection.set(value);ready=true;}
 public static Matrix4f copy(){return ready?new Matrix4f(projection):null;}
}
