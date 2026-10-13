package dev.ocarina;
/** N64 mirrored repeat represented as a reflected double-width/height repeat tile. */
public final class TexturePixels {
 public static int[] expand(int[] data){
  int w=data[0],h=data[1],mw=data[2]==1?2:1,mh=data[3]==1?2:1;
  if(w<=0||h<=0||data.length!=5+w*h)throw new IllegalArgumentException("Invalid texture packet");
  int[] pixels=new int[w*mw*h*mh];
  for(int y=0;y<h*mh;y++)for(int x=0;x<w*mw;x++){
   int sx=x<w?x:2*w-1-x,sy=y<h?y:2*h-1-y;
   pixels[y*w*mw+x]=data[5+sy*w+sx];
  }
  return pixels;
 }
}
