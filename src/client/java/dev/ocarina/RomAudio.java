package dev.ocarina;
import javax.sound.sampled.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.slf4j.LoggerFactory;
/** The client thread produces PCM; this worker owns only the audio device, never JNI. */
final class RomAudio implements AutoCloseable {
 private record Chunk(byte[] pcm,long generation) {}
 private final ArrayBlockingQueue<Chunk> queue=new ArrayBlockingQueue<>(3);
 private volatile boolean closed;
 private volatile long generation;
 private volatile SourceDataLine line;
 private final Thread worker;
 RomAudio(){worker=new Thread(this::run,"Ocarina ROM audio");worker.setDaemon(true);worker.start();}
 void submit(byte[] pcm){if(closed||pcm==null)return;Chunk c=new Chunk(pcm,generation);if(!queue.offer(c)){queue.poll();queue.offer(c);}}
 void pause(){generation++;queue.clear();SourceDataLine l=line;if(l!=null&&l.isOpen()){l.stop();l.flush();}}
 private void run(){
  try{AudioFormat format=new AudioFormat(48000,16,2,true,false);SourceDataLine l=AudioSystem.getSourceDataLine(format);line=l;l.open(format,48000/10*4);
   while(!closed){Chunk c=queue.poll(200,TimeUnit.MILLISECONDS);if(c==null||c.generation()!=generation)continue;if(!l.isRunning())l.start();int offset=0;while(offset<c.pcm().length&&!closed&&c.generation()==generation){int available=l.available();if(available<4){Thread.sleep(2);continue;}int count=Math.min(c.pcm().length-offset,available)&~3;offset+=l.write(c.pcm(),offset,count);}}
  }catch(InterruptedException ignored){Thread.currentThread().interrupt();}catch(Exception e){LoggerFactory.getLogger("ocarina").warn("ROM audio output unavailable",e);}finally{closed=true;queue.clear();SourceDataLine l=line;if(l!=null){l.stop();l.flush();l.close();}}
 }
 public void close(){closed=true;pause();worker.interrupt();}
}
