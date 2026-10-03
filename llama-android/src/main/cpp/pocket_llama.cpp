#include <jni.h>
#include <android/log.h>
#include <unistd.h>
#include <atomic>
#include <string>
#include <vector>
#include "llama.h"

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR,"PocketLlama",__VA_ARGS__)
static llama_model* model=nullptr; static llama_context* ctx=nullptr; static llama_sampler* sampler=nullptr;
static llama_batch batch{}; static const llama_vocab* vocab=nullptr; static std::atomic<bool> aborted{false};
static int pos=0, generated=0, limit=0; static std::string utf8cache;

static void clear_runtime(){ if(sampler){llama_sampler_free(sampler);sampler=nullptr;} if(ctx){llama_free(ctx);ctx=nullptr;} if(model){llama_model_free(model);model=nullptr;} vocab=nullptr; pos=generated=limit=0; utf8cache.clear(); }
static bool valid_utf8(const std::string&s){ const unsigned char*p=(const unsigned char*)s.c_str(); while(*p){int n=(*p<0x80)?1:((*p&0xE0)==0xC0?2:((*p&0xF0)==0xE0?3:((*p&0xF8)==0xF0?4:0))); if(!n)return false; for(int i=1;i<n;i++) if((p[i]&0xC0)!=0x80)return false; p+=n;} return true; }
static std::vector<std::pair<std::string,std::string>> unpack(const std::string&s){std::vector<std::pair<std::string,std::string>> out; size_t p=0; while(p<s.size()){auto r=s.find('\x1e',p); auto e=s.find('\x1f',r==std::string::npos?p:r+1); if(r==std::string::npos||e==std::string::npos)break; out.push_back({s.substr(p,r-p),s.substr(r+1,e-r-1)}); p=e+1;} return out;}

extern "C" JNIEXPORT void JNICALL Java_com_arm_aichat_PocketLlama_nativeInit(JNIEnv*,jobject,jstring){ llama_backend_init(); }
extern "C" JNIEXPORT jint JNICALL Java_com_arm_aichat_PocketLlama_loadNative(JNIEnv* env,jobject,jstring jp,jint nctx,jint ngl){
 clear_runtime(); const char*p=env->GetStringUTFChars(jp,nullptr); auto mp=llama_model_default_params(); mp.n_gpu_layers=ngl; model=llama_model_load_from_file(p,mp); env->ReleaseStringUTFChars(jp,p); if(!model)return 1; vocab=llama_model_get_vocab(model);
 auto cp=llama_context_default_params(); cp.n_ctx=nctx; cp.n_batch=std::min(512,nctx); int th=std::max(2,std::min(6,(int)sysconf(_SC_NPROCESSORS_ONLN)-2)); cp.n_threads=th; cp.n_threads_batch=th; ctx=llama_init_from_model(model,cp); if(!ctx){clear_runtime();return 2;} batch=llama_batch_init(512,0,1); return 0;
}
extern "C" JNIEXPORT jint JNICALL Java_com_arm_aichat_PocketLlama_beginNative(JNIEnv* env,jobject,jstring packed,jint maxTok,jfloat temp,jfloat topP,jint topK){
 if(!ctx||!model)return 1; aborted=false; generated=0; limit=maxTok; llama_memory_clear(llama_get_memory(ctx),true); pos=0;
 if(sampler)llama_sampler_free(sampler); sampler=llama_sampler_chain_init(llama_sampler_chain_default_params()); if(topK>0)llama_sampler_chain_add(sampler,llama_sampler_init_top_k(topK)); llama_sampler_chain_add(sampler,llama_sampler_init_top_p(topP,1)); llama_sampler_chain_add(sampler,llama_sampler_init_temp(temp)); llama_sampler_chain_add(sampler,llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
 const char* raw=env->GetStringUTFChars(packed,nullptr); auto turns=unpack(raw); env->ReleaseStringUTFChars(packed,raw); std::vector<llama_chat_message> msgs; msgs.reserve(turns.size()); for(auto&x:turns)msgs.push_back({x.first.c_str(),x.second.c_str()});
 const char* tmpl=llama_model_chat_template(model,nullptr); int need=llama_chat_apply_template(tmpl,msgs.data(),msgs.size(),true,nullptr,0); if(need<0)return 2; std::vector<char> formatted(need+1); int got=llama_chat_apply_template(tmpl,msgs.data(),msgs.size(),true,formatted.data(),formatted.size()); if(got<0)return 2; std::string prompt(formatted.data(),got);
 int nt=-llama_tokenize(vocab,prompt.c_str(),prompt.size(),nullptr,0,true,true); if(nt<=0||nt>=llama_n_ctx(ctx))return 3; std::vector<llama_token> toks(nt); if(llama_tokenize(vocab,prompt.c_str(),prompt.size(),toks.data(),nt,true,true)<0)return 4;
 for(int off=0;off<nt;off+=512){int n=std::min(512,nt-off); llama_batch_clear(batch); for(int i=0;i<n;i++){batch.token[i]=toks[off+i];batch.pos[i]=pos+i;batch.n_seq_id[i]=1;batch.seq_id[i][0]=0;batch.logits[i]=false;} batch.logits[n-1]=true;batch.n_tokens=n;if(llama_decode(ctx,batch)!=0)return 5;pos+=n;} return 0;
}
extern "C" JNIEXPORT jstring JNICALL Java_com_arm_aichat_PocketLlama_nextNative(JNIEnv* env,jobject){
 if(!ctx||!sampler||aborted||generated>=limit)return nullptr; auto id=llama_sampler_sample(sampler,ctx,-1); llama_sampler_accept(sampler,id); if(llama_vocab_is_eog(vocab,id))return nullptr; if(pos+1>=llama_n_ctx(ctx))return nullptr;
 llama_batch_clear(batch); batch.token[0]=id;batch.pos[0]=pos;batch.n_seq_id[0]=1;batch.seq_id[0][0]=0;batch.logits[0]=true;batch.n_tokens=1;if(llama_decode(ctx,batch)!=0)return nullptr;pos++;generated++;
 char buf[512];int n=llama_token_to_piece(vocab,id,buf,sizeof(buf),0,true);if(n<0){std::vector<char>b(-n);n=llama_token_to_piece(vocab,id,b.data(),b.size(),0,true);if(n>0)utf8cache.append(b.data(),n);}else utf8cache.append(buf,n); if(!valid_utf8(utf8cache))return env->NewStringUTF(""); auto out=env->NewStringUTF(utf8cache.c_str());utf8cache.clear();return out;
}
extern "C" JNIEXPORT void JNICALL Java_com_arm_aichat_PocketLlama_abortNative(JNIEnv*,jobject){aborted=true;}
extern "C" JNIEXPORT void JNICALL Java_com_arm_aichat_PocketLlama_unloadNative(JNIEnv*,jobject){clear_runtime();}
extern "C" JNIEXPORT jstring JNICALL Java_com_arm_aichat_PocketLlama_systemInfoNative(JNIEnv*env,jobject){return env->NewStringUTF(llama_print_system_info());}
