#include <jni.h>
#include <string>
#include <vector>

#include "llama.h"

static JavaVM* g_vm = nullptr;

extern "C" jint JNI_OnLoad(JavaVM* vm, void*) {
    g_vm = vm;
    return JNI_VERSION_1_6;
}

static std::string j2s(JNIEnv* env, jstring js) {
    if (!js) return "";
    const char* c = env->GetStringUTFChars(js, nullptr);
    std::string out = c ? c : "";
    if (c) env->ReleaseStringUTFChars(js, c);
    return out;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_termux_app_ai_LlamaBridge_nativeInit(JNIEnv* env, jclass, jstring modelPath, jint nCtx) {
    std::string path = j2s(env, modelPath);

    llama_backend_init();

    llama_model_params mparams = llama_model_default_params();
    llama_model* model = llama_load_model_from_file(path.c_str(), mparams);
    if (!model) return 0;

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = (int)nCtx;

    llama_context* ctx = llama_new_context_with_model(model, cparams);
    if (!ctx) {
        llama_free_model(model);
        return 0;
    }

    uintptr_t packed = ((uintptr_t)model);
    packed ^= ((uintptr_t)ctx << 1);
    packed |= 1;

    struct Holder { llama_model* m; llama_context* c; };
    Holder* h = new Holder();
    h->m = model;
    h->c = ctx;
    return (jlong)(uintptr_t)h;
}

extern "C" JNIEXPORT void JNICALL
Java_com_termux_app_ai_LlamaBridge_nativeFree(JNIEnv*, jclass, jlong handle) {
    if (!handle) return;
    struct Holder { llama_model* m; llama_context* c; };
    Holder* h = (Holder*)(uintptr_t)handle;
    if (h->c) llama_free(h->c);
    if (h->m) llama_free_model(h->m);
    delete h;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_termux_app_ai_LlamaBridge_nativeComplete(JNIEnv* env, jclass, jlong handle, jstring prompt, jint nPredict) {
    if (!handle) return env->NewStringUTF("");
    struct Holder { llama_model* m; llama_context* c; };
    Holder* h = (Holder*)(uintptr_t)handle;
    if (!h || !h->m || !h->c) return env->NewStringUTF("");

    std::string p = j2s(env, prompt);

    const llama_vocab* vocab = llama_model_get_vocab(h->m);

    std::vector<llama_token> tokens;
    tokens.resize((size_t)p.size() + 8);

    int n = llama_tokenize(vocab, p.c_str(), (int)p.size(), tokens.data(), (int)tokens.size(), true, true);
    if (n < 0) return env->NewStringUTF("");
    tokens.resize((size_t)n);

    llama_batch batch = llama_batch_init((int)tokens.size(), 0, 1);
    for (int i = 0; i < (int)tokens.size(); i++) {
        batch.token[i] = tokens[i];
        batch.pos[i] = i;
        batch.n_seq_id[i] = 1;
        batch.seq_id[i][0] = 0;
        batch.logits[i] = 0;
    }
    batch.logits[(int)tokens.size() - 1] = 1;

    if (llama_decode(h->c, batch) != 0) {
        llama_batch_free(batch);
        return env->NewStringUTF("");
    }

    std::string out;
    out.reserve(4096);

    llama_sampler* sampler = llama_sampler_init_greedy();

    int n_cur = (int)tokens.size();
    for (int i = 0; i < (int)nPredict; i++) {
        llama_token t = llama_sampler_sample(sampler, h->c, -1);
        if (t == llama_token_eos(vocab)) break;

        llama_batch batch2 = llama_batch_init(1, 0, 1);
        batch2.token[0] = t;
        batch2.pos[0] = n_cur;
        batch2.n_seq_id[0] = 1;
        batch2.seq_id[0][0] = 0;
        batch2.logits[0] = 1;

        if (llama_decode(h->c, batch2) != 0) {
            llama_batch_free(batch2);
            break;
        }
        llama_batch_free(batch2);

        char buf[4096];
        int w = llama_token_to_piece(vocab, t, buf, sizeof(buf), 0, true);
        if (w > 0) out.append(buf, (size_t)w);

        n_cur++;
    }

    llama_sampler_free(sampler);
    llama_batch_free(batch);

    return env->NewStringUTF(out.c_str());
}
