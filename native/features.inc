
static float clamp_gain(float v){return isfinite(v)?fmaxf(0,fminf(1,v)):1;}
static void drain_sfx(Bridge*b){
 for(uint32_t i=0;i<b->eventCount;i++){const struct OoTSfxEvent*e=&b->events[i];uint16_t id=e->sfxId|0x800;
  if(e->action!=OOT_SFX_PLAY){oot_engine_audio_sfx_stop(b->engine,e->sfxId);for(int k=0;k<16;k++){Voice*v=&b->voices[k];int same=fabsf(v->position[0]-e->position[0])<.01f&&fabsf(v->position[1]-e->position[1])<.01f&&fabsf(v->position[2]-e->position[2])<.01f;if((e->action==OOT_SFX_STOP_ID&&v->id==id)||(e->action==OOT_SFX_STOP_POSITION&&same))v->pcm=NULL;}continue;}
  OoTEnginePcm pcm={0};pcm.structSize=sizeof(pcm);
  if(oot_engine_voice_get(b->engine,e->sfxId,&pcm)!=OOT_ENGINE_RESULT_OK||!pcm.samples||!pcm.sampleCount){oot_engine_audio_sfx_play(b->engine,e->sfxId,.5f,clamp_gain(e->volume));continue;}
  int slot=-1;if(e->isRefresh)for(int k=0;k<16;k++)if(b->voices[k].pcm&&b->voices[k].id==id){slot=k;break;}
  int refresh=slot>=0;
  if(slot<0)for(int k=0;k<16;k++)if(!b->voices[k].pcm){slot=k;break;}
  if(slot<0){slot=0;for(int k=1;k<16;k++)if(b->voices[k].count-b->voices[k].cursor<b->voices[slot].count-b->voices[slot].cursor)slot=k;}
  Voice*v=&b->voices[slot];v->pcm=pcm.samples;v->count=pcm.sampleCount;if(!refresh)v->cursor=0;v->step=pcm.sampleRate*(isfinite(e->freqScale)?fmaxf(.05f,fminf(4,e->freqScale)):1)/48000.0;v->gain=clamp_gain(e->volume)*.65f;v->id=id;memcpy(v->position,e->position,sizeof(v->position));
 }b->eventCount=0;
}
JNIEXPORT jbyteArray JNICALL Java_dev_ocarina_NativeBridge_audio(JNIEnv*env,jclass cls,jlong ptr,jint frames,jfloat gain){
 (void)cls;Bridge*b=get(env,ptr);if(!b)return NULL;if(frames<1||frames>4800){check(env,OOT_ENGINE_RESULT_INVALID_ARGUMENT);return NULL;}
 drain_sfx(b);int16_t*pcm=calloc((size_t)frames*2,sizeof(int16_t));if(!pcm)return NULL;uint32_t rendered=0;
 if(!check(env,oot_engine_audio_render_s16(b->engine,pcm,(uint32_t)frames,48000,&rendered))){free(pcm);return NULL;}
 gain=clamp_gain(gain);for(int n=0;n<frames;n++){float v=0;for(int k=0;k<16;k++){Voice*c=&b->voices[k];if(!c->pcm)continue;uint32_t at=(uint32_t)c->cursor;if(at>=c->count){c->pcm=NULL;continue;}v+=c->pcm[at]*c->gain;c->cursor+=c->step;}for(int j=0;j<2;j++){float a=(pcm[n*2+j]+v)*gain;pcm[n*2+j]=(int16_t)fmaxf(-32768,fminf(32767,a));}}
 jbyteArray out=(*env)->NewByteArray(env,frames*4);if(out)(*env)->SetByteArrayRegion(env,out,0,frames*4,(const jbyte*)pcm);free(pcm);return out;
}
JNIEXPORT jintArray JNICALL Java_dev_ocarina_NativeBridge_exitEvent(JNIEnv*env,jclass cls,jlong ptr){
 (void)cls;Bridge*b=get(env,ptr);if(!b)return NULL;struct OoTWorldEvent e={0};e.structSize=sizeof(e);e.version=OOT_WORLD_EVENT_VERSION;
 if(oot_engine_world_event_poll(b->engine,&e)!=OOT_ENGINE_RESULT_OK)return NULL;
 int id=e.entranceIndex;jint v[5]={(jint)e.kind,id,-1,-1,e.exitIndex};if(id>=0&&id<0x614&&b->entrancesReady){v[2]=b->entrances[id*4];v[3]=b->entrances[id*4+1];}
 jintArray out=(*env)->NewIntArray(env,5);if(out)(*env)->SetIntArrayRegion(env,out,0,5,v);return out;
}
JNIEXPORT jintArray JNICALL Java_dev_ocarina_NativeBridge_entrance(JNIEnv*env,jclass cls,jlong ptr,jint id){
 (void)cls;Bridge*b=get(env,ptr);if(!b||!b->entrancesReady||id<0||id>=0x614)return NULL;jint v[2]={b->entrances[id*4],b->entrances[id*4+1]};jintArray out=(*env)->NewIntArray(env,2);if(out)(*env)->SetIntArrayRegion(env,out,0,2,v);return out;
}
JNIEXPORT void JNICALL Java_dev_ocarina_NativeBridge_pose(JNIEnv*env,jclass cls,jlong ptr,jfloat x,jfloat y,jfloat z,jint yaw){(void)cls;Bridge*b=get(env,ptr);if(b)check(env,oot_engine_link_set_pose(b->engine,x,y,z,(int16_t)yaw));}
JNIEXPORT void JNICALL Java_dev_ocarina_NativeBridge_worldWater(JNIEnv*env,jclass cls,jlong ptr,jintArray triangles,jintArray water){
 (void)cls;Bridge*b=get(env,ptr);if(!b||!triangles||!water)return;jsize n=(*env)->GetArrayLength(env,triangles),nw=(*env)->GetArrayLength(env,water);
 if(n%9||n/9>OOT_ENGINE_MAX_STATIC_SURFACES||nw%5||nw/5>65535){check(env,OOT_ENGINE_RESULT_INVALID_ARGUMENT);return;}
 struct OoTSurface*surfaces=calloc((size_t)n/9+1,sizeof(*surfaces));struct OoTWaterBox*boxes=calloc((size_t)nw/5+1,sizeof(*boxes));if(!surfaces||!boxes){free(surfaces);free(boxes);check(env,OOT_ENGINE_RESULT_INVALID_ARGUMENT);return;}
 jint*data=(*env)->GetIntArrayElements(env,triangles,NULL);jint*w=(*env)->GetIntArrayElements(env,water,NULL);if(!data||!w)goto done;
 for(int i=0;i<n/9;i++){surfaces[i].type=OOT_SURFACE_DEFAULT;for(int j=0;j<9;j++)surfaces[i].vertices[j/3][j%3]=data[i*9+j];}
 for(int i=0;i<nw/5;i++){for(int j=0;j<5;j++)if(w[i*5+j]<-32768||w[i*5+j]>32767){check(env,OOT_ENGINE_RESULT_INVALID_ARGUMENT);goto done;}boxes[i].xMin=w[i*5];boxes[i].zMin=w[i*5+1];boxes[i].xLength=w[i*5+2];boxes[i].zLength=w[i*5+3];boxes[i].ySurface=w[i*5+4];}
 check(env,oot_engine_static_world_load(b->engine,surfaces,n/9,boxes,nw/5));
 done:if(data)(*env)->ReleaseIntArrayElements(env,triangles,data,JNI_ABORT);if(w)(*env)->ReleaseIntArrayElements(env,water,w,JNI_ABORT);free(surfaces);free(boxes);
}
