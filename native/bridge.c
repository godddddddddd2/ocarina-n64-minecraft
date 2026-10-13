#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <math.h>
#include "liboot_engine.h"
#include "rom_util.h"
#include <string.h>

typedef struct {const int16_t *pcm; uint32_t count; double cursor, step; float gain,position[3]; uint16_t id;} Voice;
typedef struct { OoTEngine *engine; OoTEngineHostActor actors[64]; int ids[64]; float spawn[3]; int16_t yaw; int child,linkActive; struct OoTSfxEvent events[128]; uint32_t eventCount,droppedEvents; Voice voices[16]; uint8_t entrances[0x614*4]; int entrancesReady; float lockOn[4]; } Bridge;
static void capture_sfx(void *user,const struct OoTSfxEvent *event){Bridge*b=user;if(!event)return;if(b->eventCount<128)b->events[b->eventCount++]=*event;else b->droppedEvents++;}
/* Identify the retail entrance table inside the user's own decompressed code file.
   No ROM records are shipped in the mod. */
static void read_entrances(Bridge*b,const uint8_t*input,size_t size){
 uint8_t*rom=malloc(size);if(!rom)return;memcpy(rom,input,size);
 if(!rom_normalize(rom,size)){free(rom);return;}const uint8_t*dm=rom_find_dmadata(rom,size);LibootDmaEntry e;
 for(uint32_t f=0;f<64&&dma_get(rom,size,dm,f,&e);f++){
  if(e.vromEnd-e.vromStart<0xc0000||e.vromEnd-e.vromStart>0x300000)continue;
  size_t n=0;uint8_t*code=rom_read_file(rom,size,f,&n);if(!code)continue;
  const uint8_t groups[]={0,1,11,20,5};
  for(size_t off=0;off+sizeof(b->entrances)<=n;off+=4){int good=1;
   for(int j=0;j<20;j++)if(code[off+j*4]!=groups[j/4]||code[off+j*4+1]!=0){good=0;break;}
   if(!good)continue;for(int j=0;j<0x614;j++)if(code[off+j*4]>127||code[off+j*4+1]>63){good=0;break;}
   if(good){memcpy(b->entrances,code+off,sizeof(b->entrances));b->entrancesReady=1;break;}
  }free(code);if(b->entrancesReady)break;
 }free(rom);
}
static int check(JNIEnv *env, OoTResult result) {
 if(result==OOT_ENGINE_RESULT_OK) return 1;
 jclass cls=(*env)->FindClass(env,"java/lang/IllegalStateException");
 (*env)->ThrowNew(env,cls,oot_engine_result_string(result)); return 0;
}
static Bridge *get(JNIEnv *env,jlong ptr) {
 if(!ptr){jclass cls=(*env)->FindClass(env,"java/lang/IllegalStateException");(*env)->ThrowNew(env,cls,"Link runtime is closed");return NULL;}
 return (Bridge*)(intptr_t)ptr;
}
JNIEXPORT jlong JNICALL Java_dev_ocarina_NativeBridge_create(JNIEnv *env,jclass cls,jbyteArray rom) {
 (void)cls; if(!rom) return 0;
 jsize len=(*env)->GetArrayLength(env,rom);
 jbyte *data=(*env)->GetByteArrayElements(env,rom,NULL); if(!data) return 0;
 Bridge *b=calloc(1,sizeof(*b)); if(!b){(*env)->ReleaseByteArrayElements(env,rom,data,JNI_ABORT);return 0;}
 OoTEngineConfig config; OoTResult result=oot_engine_config_init(&config);
 if(result==OOT_ENGINE_RESULT_OK){config.romData=(const uint8_t*)data;config.romSize=(size_t)len;config.renderFlags=0;config.sceneTriangleCapacity=131072;config.sfxCallback=capture_sfx;config.sfxUserData=b;result=oot_engine_create(&config,&b->engine);}
 if(result==OOT_ENGINE_RESULT_OK)read_entrances(b,(const uint8_t*)data,(size_t)len);
 (*env)->ReleaseByteArrayElements(env,rom,data,JNI_ABORT);
 if(!check(env,result)){free(b);return 0;} return (jlong)(intptr_t)b;
}
JNIEXPORT void JNICALL Java_dev_ocarina_NativeBridge_world(JNIEnv *env,jclass cls,jlong ptr,jintArray triangles) {
 (void)cls; Bridge*b=get(env,ptr);if(!b||!triangles)return;
 jsize n=(*env)->GetArrayLength(env,triangles);
 if(n%9||n>900000){check(env,OOT_ENGINE_RESULT_INVALID_ARGUMENT);return;}
 struct OoTSurface *surfaces=calloc((size_t)(n/9)+1,sizeof(*surfaces));if(!surfaces){check(env,OOT_ENGINE_RESULT_INVALID_ARGUMENT);return;}
 jint *data=(*env)->GetIntArrayElements(env,triangles,NULL);if(!data){free(surfaces);return;}
 for(int i=0;i<n/9;i++){surfaces[i].type=OOT_SURFACE_DEFAULT;for(int j=0;j<9;j++)surfaces[i].vertices[j/3][j%3]=data[i*9+j];}
 (*env)->ReleaseIntArrayElements(env,triangles,data,JNI_ABORT);
 OoTResult result=oot_engine_static_world_load(b->engine,surfaces,(uint32_t)n/9,NULL,0);free(surfaces);check(env,result);
}
JNIEXPORT void JNICALL Java_dev_ocarina_NativeBridge_spawn(JNIEnv *env,jclass cls,jlong ptr) {
 (void)cls;Bridge*b=get(env,ptr);if(!b)return;
 if(!check(env,oot_engine_link_create(b->engine,b->spawn[0],b->spawn[1],b->spawn[2])))return;
 memset(b->lockOn,0,sizeof(b->lockOn));b->linkActive=1;
 if(b->child)check(env,oot_engine_link_set_age(b->engine,OOT_AGE_CHILD));
 check(env,oot_engine_link_set_equipment(b->engine,b->child?OOT_SWORD_KOKIRI:OOT_SWORD_MASTER,b->child?OOT_SHIELD_DEKU:OOT_SHIELD_HYLIAN,OOT_TUNIC_KOKIRI,OOT_BOOTS_KOKIRI));
 check(env,oot_engine_link_set_pose(b->engine,b->spawn[0],b->spawn[1],b->spawn[2],b->yaw));
}
JNIEXPORT jfloatArray JNICALL Java_dev_ocarina_NativeBridge_step(JNIEnv *env,jclass cls,jlong ptr,jfloat sx,jfloat sy,jfloat camx,jfloat camz,jint buttons) {
 (void)cls;Bridge*b=get(env,ptr);if(!b)return NULL;
 OoTEngineInput input; if(!check(env,oot_engine_input_init(&input)))return NULL;
 input.stickX=sx;input.stickY=sy;input.camLookX=camx;input.camLookZ=camz;input.buttons=(uint32_t)buttons;
 const OoTEngineFrame*f=NULL;uint32_t steps=0;
 if(!check(env,oot_engine_advance(b->engine,0.05f,&input,&steps,&f))||!f)return NULL;
 if(f->geometry.numTriangles>8192){check(env,OOT_ENGINE_RESULT_INVALID_ARGUMENT);return NULL;}
 b->lockOn[0]=f->link.lockOnActive; for(int i=0;i<3;i++)b->lockOn[i+1]=f->link.lockOnPos[i];
 size_t len=16+(size_t)f->geometry.numTriangles*3*14;float*out=calloc(len,sizeof(float));if(!out)return NULL;
 for(int i=0;i<3;i++)out[i]=f->link.position[i];
 out[3]=f->link.faceAngle;out[4]=f->link.health;out[5]=f->link.healthCapacity;out[6]=f->link.action;out[7]=f->link.animId;out[8]=(float)f->simulationTick;out[9]=f->geometry.numTriangles;out[10]=f->link.meleeWeaponState;out[11]=(f->link.stateFlags1 & (1u<<22))!=0;out[12]=f->link.heldItemAction;out[13]=f->link.age;out[14]=f->link.inWater;out[15]=f->link.waterSurfaceY;
 for(uint32_t i=0;i<f->geometry.numTriangles*3;i++){size_t o=16+(size_t)i*14;for(int j=0;j<3;j++){out[o+j]=f->geometry.position[i*3+j];out[o+3+j]=f->geometry.normal[i*3+j];out[o+6+j]=f->geometry.color[i*3+j];}out[o+9]=f->geometry.alpha?f->geometry.alpha[i]:1;out[o+10]=f->geometry.uv[i*2];out[o+11]=f->geometry.uv[i*2+1];out[o+12]=f->geometry.triTexture?f->geometry.triTexture[i/3]:65535;out[o+13]=f->geometry.triFlags?f->geometry.triFlags[i/3]:0;}
 for(uint32_t k=0;k<f->geometryBatchCount;k++){const struct OoTGeometryBatch*batch=&f->geometryBatches[k];if(batch->renderPass==OOT_GEOMETRY_PASS_TRANSLUCENT)for(uint32_t t=batch->firstTriangle;t<batch->firstTriangle+batch->numTriangles&&t<f->geometry.numTriangles;t++)out[16+(size_t)t*42+13]=(float)((int)out[16+(size_t)t*42+13]|16);}
 jfloatArray result=(*env)->NewFloatArray(env,(jsize)len);if(result)(*env)->SetFloatArrayRegion(env,result,0,(jsize)len,out);free(out);return result;
}
JNIEXPORT jintArray JNICALL Java_dev_ocarina_NativeBridge_texture(JNIEnv *env,jclass cls,jlong ptr,jint id) {
 (void)cls;Bridge*b=get(env,ptr);if(!b)return NULL;OoTEngineTexture tex={0};tex.structSize=sizeof(tex);
 if(oot_engine_texture_get(b->engine,(uint32_t)id,&tex)!=OOT_ENGINE_RESULT_OK)return NULL;
 size_t pixels=(size_t)tex.width*tex.height;if(!pixels||pixels>1048576||tex.rgbaSize<pixels*4)return NULL;
 jint*out=malloc((pixels+5)*sizeof(jint));if(!out)return NULL;out[0]=tex.width;out[1]=tex.height;out[2]=tex.wrapS;out[3]=tex.wrapT;out[4]=tex.revision;
 for(size_t i=0;i<pixels;i++){const uint8_t*p=tex.rgbaPixels+i*4;out[i+5]=(jint)(((uint32_t)p[3]<<24)|((uint32_t)p[0]<<16)|((uint32_t)p[1]<<8)|p[2]);}
 jintArray result=(*env)->NewIntArray(env,(jsize)pixels+5);if(result)(*env)->SetIntArrayRegion(env,result,0,(jsize)pixels+5,out);free(out);return result;
}
JNIEXPORT void JNICALL Java_dev_ocarina_NativeBridge_destroy(JNIEnv *env,jclass cls,jlong ptr) {
 (void)cls;if(!ptr)return;Bridge*b=(Bridge*)(intptr_t)ptr;if(check(env,oot_engine_destroy(b->engine)))free(b);
}

JNIEXPORT jint JNICALL Java_dev_ocarina_NativeBridge_textureRevision(JNIEnv *env,jclass cls,jlong ptr,jint id){
 (void)cls;Bridge*b=get(env,ptr);if(!b)return -1;OoTEngineTexture t={0};t.structSize=sizeof(t);if(oot_engine_texture_get(b->engine,(uint32_t)id,&t)!=OOT_ENGINE_RESULT_OK)return -1;return (jint)t.revision;
}
JNIEXPORT void JNICALL Java_dev_ocarina_NativeBridge_actors(JNIEnv *env,jclass cls,jlong ptr,jintArray ids,jfloatArray values){
 (void)cls;Bridge*b=get(env,ptr);if(!b||!ids||!values)return;int n=(*env)->GetArrayLength(env,ids);if(n>64||(*env)->GetArrayLength(env,values)!=n*5){check(env,OOT_ENGINE_RESULT_INVALID_ARGUMENT);return;}
 jint*id=(*env)->GetIntArrayElements(env,ids,NULL);jfloat*v=(*env)->GetFloatArrayElements(env,values,NULL);if(!id||!v){if(id)(*env)->ReleaseIntArrayElements(env,ids,id,JNI_ABORT);if(v)(*env)->ReleaseFloatArrayElements(env,values,v,JNI_ABORT);return;}
 for(int slot=0;slot<64;slot++){int found=0;for(int k=0;k<n;k++)if(id[k]==b->ids[slot])found=1;if(b->actors[slot]&&!found){oot_engine_host_actor_remove(b->engine,b->actors[slot]);b->actors[slot]=0;b->ids[slot]=0;}}
 for(int k=0;k<n;k++){int slot=-1;for(int j=0;j<64;j++)if(b->actors[j]&&b->ids[j]==id[k]){slot=j;break;}if(slot<0)for(int j=0;j<64;j++)if(!b->actors[j]){slot=j;break;}if(slot<0)continue;
 struct OoTHostActorState s={0};s.structSize=sizeof(s);s.version=OOT_HOST_ACTOR_STATE_VERSION;s.userTag=(uint64_t)id[k];s.flags=OOT_HOST_ACTOR_ENABLED|OOT_HOST_ACTOR_TARGETABLE|OOT_HOST_ACTOR_HOSTILE|OOT_HOST_ACTOR_HURT;s.room=-1;s.attentionRange=3;for(int j=0;j<3;j++)s.position[j]=v[k*5+j];s.hurtRadius=v[k*5+3];s.hurtHeight=v[k*5+4];s.focusOffset[1]=s.hurtHeight*0.75f;
 OoTResult r=b->actors[slot]?oot_engine_host_actor_update(b->engine,b->actors[slot],&s):oot_engine_host_actor_create(b->engine,&s,&b->actors[slot]);if(r!=OOT_ENGINE_RESULT_OK){check(env,r);break;}b->ids[slot]=id[k];}
 (*env)->ReleaseIntArrayElements(env,ids,id,JNI_ABORT);(*env)->ReleaseFloatArrayElements(env,values,v,JNI_ABORT);
}
JNIEXPORT jintArray JNICALL Java_dev_ocarina_NativeBridge_contacts(JNIEnv *env,jclass cls,jlong ptr){
 (void)cls;Bridge*b=get(env,ptr);if(!b)return NULL;jint ids[256];int n=0;OoTEngineActorContact c={0};c.structSize=sizeof(c);c.version=OOT_HOST_ACTOR_CONTACT_VERSION;while(n<256&&oot_engine_host_actor_poll_contact(b->engine,&c)==OOT_ENGINE_RESULT_OK){if(c.source==OOT_HOST_CONTACT_MELEE)ids[n++]=(jint)c.userTag;}jintArray out=(*env)->NewIntArray(env,n);if(out&&n)(*env)->SetIntArrayRegion(env,out,0,n,ids);return out;
}

JNIEXPORT jfloatArray JNICALL Java_dev_ocarina_NativeBridge_scene(JNIEnv *env,jclass cls,jlong ptr,jint scene,jint room,jint spawn,jboolean child){
 (void)cls;Bridge*b=get(env,ptr);if(!b)return NULL;int32_t native=0;struct OoTSceneLoadOptions opt=OOT_SCENE_LOAD_OPTIONS_INIT(scene,room,child?0:2);
 if(b->linkActive){if(!check(env,oot_engine_link_delete(b->engine)))return NULL;b->linkActive=0;memset(b->actors,0,sizeof(b->actors));memset(b->ids,0,sizeof(b->ids));}
 b->eventCount=0;memset(b->voices,0,sizeof(b->voices));oot_engine_audio_stop_all(b->engine,0);
 if(!check(env,oot_engine_scene_load_ex(b->engine,&opt,&native)))return NULL;
 if(!check(env,oot_engine_scene_get_spawn(b->engine,spawn,b->spawn,&b->yaw)))return NULL;b->child=child;
 const OoTEngineSceneGeometry*g=NULL;if(!check(env,oot_engine_scene_get_geometry(b->engine,&g))||!g)return NULL;uint32_t dropped=0;oot_engine_scene_get_dropped_triangles(b->engine,&dropped);if(dropped){check(env,OOT_ENGINE_RESULT_INVALID_ARGUMENT);return NULL;}
 size_t len=16+(size_t)g->numTriangles*42;float*out=calloc(len,sizeof(float));if(!out)return NULL;for(int j=0;j<3;j++)out[j]=b->spawn[j];out[3]=b->yaw;out[9]=g->numTriangles;
 for(uint32_t i=0;i<g->numTriangles*3;i++){size_t o=16+(size_t)i*14;for(int j=0;j<3;j++){out[o+j]=g->position[i*3+j];out[o+3+j]=g->normal[i*3+j];out[o+6+j]=g->color[i*3+j];}out[o+9]=g->alpha?g->alpha[i]:1;out[o+10]=g->uv[i*2];out[o+11]=g->uv[i*2+1];out[o+12]=g->triTexture?g->triTexture[i/3]:65535;out[o+13]=(g->triFlags?g->triFlags[i/3]:0)|(i/3>=g->xluStartTriangle?16:0);}
 jfloatArray result=(*env)->NewFloatArray(env,(jsize)len);if(result)(*env)->SetFloatArrayRegion(env,result,0,(jsize)len,out);free(out);return result;
}

#include "features.inc"

/* Snapshot from the last successful step: active, focus X/Y/Z in ROM units. */
JNIEXPORT jfloatArray JNICALL Java_dev_ocarina_NativeBridge_lockOn(JNIEnv *env,jclass cls,jlong ptr){
 (void)cls;Bridge*b=get(env,ptr);if(!b)return NULL;
 jfloatArray out=(*env)->NewFloatArray(env,4);if(out)(*env)->SetFloatArrayRegion(env,out,0,4,b->lockOn);return out;
}
