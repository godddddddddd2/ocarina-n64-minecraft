#include <stdio.h>
#include <stdlib.h>
#include "liboot_engine.h"
int main(int argc,char**argv){
 if(argc!=2)return 2;FILE*f=fopen(argv[1],"rb");if(!f)return 3;fseek(f,0,SEEK_END);long size=ftell(f);rewind(f);unsigned char*rom=malloc(size);if(fread(rom,1,size,f)!=(size_t)size)return 4;fclose(f);
 int scenes[]={0x55,0x34,0x00,0x51};int failed=0;
 for(int i=0;i<4;i++){OoTEngineConfig c;oot_engine_config_init(&c);c.romData=rom;c.romSize=size;c.sceneTriangleCapacity=131072;OoTEngine*e=NULL;OoTResult r=oot_engine_create(&c,&e);if(r){printf("create: %s\n",oot_engine_result_string(r));return 5;}int32_t native=0;r=oot_engine_scene_load(e,scenes[i],-1,&native);printf("scene=%02x result=%d native=%d",scenes[i],r,native);
 if(r==OOT_ENGINE_RESULT_OK){const OoTEngineSceneGeometry*g=NULL;oot_engine_scene_get_geometry(e,&g);uint32_t dropped=0,actors=0;oot_engine_scene_get_dropped_triangles(e,&dropped);oot_engine_scene_get_actor_count(e,&actors);float p[3];int16_t yaw;OoTResult spawn=oot_engine_scene_get_spawn(e,0,p,&yaw);printf(" triangles=%u dropped=%u actors=%u spawnResult=%d xyz=%.1f,%.1f,%.1f",g?g->numTriangles:0,dropped,actors,spawn,p[0],p[1],p[2]);if(!g||!g->numTriangles||dropped||spawn)failed++;}else failed++;
 puts("");oot_engine_destroy(e);}
 free(rom);return failed?1:0;
}
