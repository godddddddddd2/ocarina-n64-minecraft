package dev.ocarina;

import java.nio.FloatBuffer;import java.util.*;
import org.lwjgl.opengl.*;import org.lwjgl.system.MemoryUtil;

/** Bounded GPU storage for unchanged vertex attributes, shared by consecutive game frames. */
public final class StoryGeometryCache implements AutoCloseable {
 public record Stats(long uploads,long hits,long uploadedBytes,long residentBytes,int buffers) {}
 private static final long DEFAULT_BYTES=64L*1024*1024;
 private static final class Key {
  final float[] values;final int hash;
  Key(float[] values){this.values=values;hash=Arrays.hashCode(values);}
  public int hashCode(){return hash;}
  public boolean equals(Object o){return o instanceof Key k&&Arrays.equals(values,k.values);}
 }
 private static final class Entry {final Key key;int buffer;Entry(Key key,int buffer){this.key=key;this.buffer=buffer;}}
 private final LinkedHashMap<Key,Entry> buffers=new LinkedHashMap<>(128,0.75f,true);
 private final IdentityHashMap<StoryChannel.Draw,Entry[]> prepared=new IdentityHashMap<>();
 private final Set<Entry> protectedEntries=Collections.newSetFromMap(new IdentityHashMap<>());
 private final long budget;private final int maxBuffers;private long bytes,uploads,hits,uploadedBytes;
 private StoryChannel.Frame frame;private int scene=Integer.MIN_VALUE,epoch=Integer.MIN_VALUE;private FloatBuffer scratch;
 public StoryGeometryCache(){this(DEFAULT_BYTES,16384);}
 public StoryGeometryCache(long budget,int maxBuffers){if(budget<1024||maxBuffers<1)throw new IllegalArgumentException();this.budget=budget;this.maxBuffers=maxBuffers;}
 public Stats stats(){return new Stats(uploads,hits,uploadedBytes,bytes,buffers.size());}
 public void begin(StoryChannel.Frame next){
  if(frame==next)return;
  int nextScene=next.state()[0]>0?(int)next.state()[1]:-1,nextEpoch=(int)next.state()[40];
  if(nextScene!=scene||nextEpoch!=epoch){clearBuffers();scene=nextScene;epoch=nextEpoch;}
  frame=next;prepared.clear();
 }
 public void bind(StoryChannel.Draw draw){
  protectedEntries.clear();Entry[] bindings=prepared.get(draw);boolean reused=bindings!=null;
  if(bindings==null){
   bindings=new Entry[draw.sizes().length];int offset=0,vertices=draw.triangles()*3;
   for(int a=0;a<bindings.length;a++){
    int size=draw.sizes()[a];if(draw.locations()[a]>=0){float[] values=new float[vertices*size];for(int v=0;v<vertices;v++)for(int c=0;c<size;c++)values[v*size+c]=draw.vertices().get(v*draw.stride()+offset+c);bindings[a]=acquire(new Key(values));protectedEntries.add(bindings[a]);}offset+=size;
   }
   prepared.put(draw,bindings);
  }
  for(Entry e:bindings)if(e!=null&&e.buffer!=0)protectedEntries.add(e);
  for(int a=0;a<16;a++)GL20.glDisableVertexAttribArray(a);
  for(int a=0;a<bindings.length;a++)if(bindings[a]!=null){
   Entry e=bindings[a];if(e.buffer==0)e=bindings[a]=acquire(e.key);else{buffers.get(e.key);if(reused)hits++;}protectedEntries.add(e);
   GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,e.buffer);int location=draw.locations()[a];GL20.glEnableVertexAttribArray(location);GL20.glVertexAttribPointer(location,draw.sizes()[a],GL11.GL_FLOAT,false,0,0L);
  }
  protectedEntries.clear();
 }
 private Entry acquire(Key key){
  Entry existing=buffers.get(key);if(existing!=null){hits++;return existing;}
  long size=key.values.length*4L;
  // One attribute cannot exceed the bounded transport frame. Keep it even for a deliberately small test budget.
  while(!buffers.isEmpty()&&(bytes+size>budget||buffers.size()>=maxBuffers)){var iterator=buffers.entrySet().iterator();Map.Entry<Key,Entry> oldest=null;while(iterator.hasNext()){var candidate=iterator.next();if(!protectedEntries.contains(candidate.getValue())){oldest=candidate;break;}}if(oldest==null)break;Entry e=oldest.getValue();GL15.glDeleteBuffers(e.buffer);e.buffer=0;bytes-=oldest.getKey().values.length*4L;iterator.remove();}
  if(scratch==null||scratch.capacity()<key.values.length){if(scratch!=null)MemoryUtil.memFree(scratch);scratch=MemoryUtil.memAllocFloat(Math.max(4096,key.values.length));}
  scratch.clear();scratch.put(key.values).flip();int buffer=GL15.glGenBuffers();GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,buffer);GL15.glBufferData(GL15.GL_ARRAY_BUFFER,scratch,GL15.GL_STATIC_DRAW);
  Entry entry=new Entry(key,buffer);buffers.put(key,entry);bytes+=size;uploads++;uploadedBytes+=size;return entry;
 }
 private void clearBuffers(){for(var e:buffers.values()){GL15.glDeleteBuffers(e.buffer);e.buffer=0;}buffers.clear();prepared.clear();bytes=0;}
 public void close(){clearBuffers();frame=null;if(scratch!=null){MemoryUtil.memFree(scratch);scratch=null;}}
}
