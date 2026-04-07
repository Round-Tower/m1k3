/**
 * ma_bridge.cpp - Ma JNI bridge to llama.cpp
 *
 * Uses llama.cpp's stable C API (latest main, ~b9000+).
 * API notes vs older llama.cpp:
 *   llama_load_model_from_file  → llama_model_load_from_file
 *   llama_free_model            → llama_model_free
 *   llama_token_is_eog          → llama_vocab_is_eog (via llama_model_get_vocab)
 *   llama_tokenize(model, ...)  → llama_tokenize(vocab, ...)
 *   llama_token_to_piece(model) → llama_token_to_piece(vocab, ...)
 *
 * UTF-8 safety: token pieces are accumulated in a buffer. NewStringUTF()
 * is called only after the buffer ends on a complete UTF-8 boundary.
 * This prevents the SIGABRT that plagued Llamatik's streaming path.
 *
 * MurphySig: kev+claude / confidence 0.82 / 2026-04-06
 * Design: own the stack, CPU-only, GGUF universal
 */

#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <algorithm>
#include <thread>

#include "llama.h"

#define LOG_TAG "MaBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// ---------------------------------------------------------------------------
// MaContext - holds model + context (returned as an opaque jlong handle)
// ---------------------------------------------------------------------------

struct MaContext {
    llama_model   *model   = nullptr;
    llama_context *ctx     = nullptr;
};

static MaContext* handle_to_ctx(jlong handle) {
    return reinterpret_cast<MaContext*>(static_cast<uintptr_t>(handle));
}

// ---------------------------------------------------------------------------
// UTF-8 boundary check
// Returns true if [data, data+len) is a complete, valid UTF-8 sequence.
// ---------------------------------------------------------------------------
static bool is_complete_utf8(const char* data, size_t len) {
    if (len == 0) return true;
    const unsigned char* p = reinterpret_cast<const unsigned char*>(data);
    size_t i = 0;
    while (i < len) {
        unsigned char c = p[i];
        int seq_len;
        if      (c < 0x80)               seq_len = 1;
        else if ((c & 0xE0) == 0xC0)     seq_len = 2;
        else if ((c & 0xF0) == 0xE0)     seq_len = 3;
        else if ((c & 0xF8) == 0xF0)     seq_len = 4;
        else return false;                // invalid lead byte
        if (i + seq_len > len) return false; // truncated sequence
        i += seq_len;
    }
    return true;
}

// ---------------------------------------------------------------------------
// JNI: init
// ---------------------------------------------------------------------------

extern "C" JNIEXPORT jlong JNICALL
Java_app_m1k3_ai_assistant_ai_ma_MaBridge_init(
        JNIEnv *env, jobject /*thiz*/, jstring jModelPath, jint nCtx) {

    const char *modelPath = env->GetStringUTFChars(jModelPath, nullptr);
    LOGI("init: loading %s", modelPath);

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0; // CPU-only on Android

    llama_model *model = llama_model_load_from_file(modelPath, mparams);
    env->ReleaseStringUTFChars(jModelPath, modelPath);

    if (!model) {
        LOGE("init: failed to load model");
        return 0L;
    }

    const int n_threads = std::min(4, (int)std::thread::hardware_concurrency());

    llama_context_params cparams = llama_context_default_params();
    // nCtx passed from Kotlin: 2048 for ≤6GB RAM, 4096 for ≥8GB (flagship).
    // Larger ctx = richer conversational memory but bigger KV cache.
    cparams.n_ctx            = (uint32_t)nCtx;
    cparams.n_threads        = n_threads;
    cparams.n_threads_batch  = n_threads;
    cparams.flash_attn_type  = LLAMA_FLASH_ATTN_TYPE_DISABLED; // CPU-only: no flash attn

    llama_context *ctx = llama_new_context_with_model(model, cparams);
    if (!ctx) {
        LOGE("init: failed to create context");
        llama_model_free(model);
        return 0L;
    }

    auto *ma = new MaContext{model, ctx};
    LOGI("init: success (handle=%p, threads=%d, n_ctx=%d)", ma, n_threads, cparams.n_ctx);
    return static_cast<jlong>(reinterpret_cast<uintptr_t>(ma));
}

// ---------------------------------------------------------------------------
// JNI: nativeGenerate
// ---------------------------------------------------------------------------

extern "C" JNIEXPORT jstring JNICALL
Java_app_m1k3_ai_assistant_ai_ma_MaBridge_nativeGenerate(
        JNIEnv *env, jobject /*thiz*/,
        jlong   handle,
        jstring jPrompt,
        jint    maxTokens,
        jfloat  temperature,
        jfloat  topP,
        jint    topK,
        jfloat  repeatPenalty,
        jobject callback) {

    MaContext *ma = handle_to_ctx(handle);
    if (!ma || !ma->model || !ma->ctx) {
        LOGE("nativeGenerate: invalid handle");
        return env->NewStringUTF("");
    }

    const llama_vocab *vocab = llama_model_get_vocab(ma->model);

    // --- Streaming callback setup ---
    jclass    cbClass  = nullptr;
    jmethodID cbMethod = nullptr;
    if (callback != nullptr) {
        cbClass  = env->GetObjectClass(callback);
        cbMethod = env->GetMethodID(cbClass, "onToken", "(Ljava/lang/String;)V");
        if (!cbMethod) {
            LOGE("nativeGenerate: onToken method not found on callback object");
            callback = nullptr;
        }
    }

    // --- Tokenize prompt ---
    const char *prompt_c = env->GetStringUTFChars(jPrompt, nullptr);
    const int n_ctx = (int)llama_n_ctx(ma->ctx);
    const int n_prompt_max = n_ctx - (int)maxTokens - 10;

    std::vector<llama_token> prompt_tokens(n_ctx);
    int n_prompt = llama_tokenize(
            vocab,
            prompt_c, (int32_t)strlen(prompt_c),
            prompt_tokens.data(), (int32_t)prompt_tokens.size(),
            /*add_special=*/true, /*parse_special=*/true);

    env->ReleaseStringUTFChars(jPrompt, prompt_c);

    if (n_prompt < 0) {
        LOGE("nativeGenerate: tokenization failed (n=%d)", n_prompt);
        return env->NewStringUTF("");
    }
    if (n_prompt > n_prompt_max && n_prompt_max > 0) {
        // Truncate prompt to fit context window
        LOGI("nativeGenerate: truncating prompt %d → %d tokens", n_prompt, n_prompt_max);
        n_prompt = n_prompt_max;
    }
    prompt_tokens.resize(n_prompt);

    LOGD("nativeGenerate: %d prompt tokens, maxTokens=%d", n_prompt, (int)maxTokens);

    // --- Decode prompt tokens ---
    llama_batch batch = llama_batch_get_one(prompt_tokens.data(), n_prompt);
    if (llama_decode(ma->ctx, batch) != 0) {
        LOGE("nativeGenerate: initial decode failed");
        return env->NewStringUTF("");
    }

    // --- Sampler chain ---
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    llama_sampler *smpl = llama_sampler_chain_init(sparams);

    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(topK));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(topP, /*min_keep=*/1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(smpl, llama_sampler_init_penalties(
            /*penalty_last_n=*/64,
            /*penalty_repeat=*/repeatPenalty,
            /*penalty_freq=*/0.0f,
            /*penalty_present=*/0.0f));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    // --- Generation loop ---
    std::string accumulated;
    std::string utf8_buf; // holds pieces until a complete UTF-8 boundary
    char piece_buf[256];

    const int n_max = std::min((int)maxTokens, n_ctx - n_prompt - 4);

    for (int i = 0; i < n_max; ++i) {
        llama_token token = llama_sampler_sample(smpl, ma->ctx, -1);

        if (llama_vocab_is_eog(vocab, token)) {
            LOGD("nativeGenerate: EOG at position %d", i);
            break;
        }

        int n_piece = llama_token_to_piece(
                vocab, token,
                piece_buf, (int32_t)sizeof(piece_buf) - 1,
                /*lstrip=*/0, /*special=*/true);

        if (n_piece <= 0) {
            // Still advance position even on empty piece
            llama_batch next = llama_batch_get_one(&token, 1);
            llama_decode(ma->ctx, next);
            continue;
        }
        piece_buf[n_piece] = '\0';

        accumulated += piece_buf;
        utf8_buf    += piece_buf;

        // Emit buffered pieces only on complete UTF-8 boundary
        if (callback && is_complete_utf8(utf8_buf.c_str(), utf8_buf.size())) {
            jstring jPiece = env->NewStringUTF(utf8_buf.c_str());
            if (jPiece) {
                env->CallVoidMethod(callback, cbMethod, jPiece);
                env->DeleteLocalRef(jPiece);
            }
            utf8_buf.clear();
        }

        // Decode next token
        llama_batch next = llama_batch_get_one(&token, 1);
        if (llama_decode(ma->ctx, next) != 0) {
            LOGE("nativeGenerate: decode failed at position %d", i);
            break;
        }
    }

    // Flush any residual buffered bytes
    if (callback && !utf8_buf.empty()) {
        jstring jPiece = env->NewStringUTF(utf8_buf.c_str());
        if (jPiece) {
            env->CallVoidMethod(callback, cbMethod, jPiece);
            env->DeleteLocalRef(jPiece);
        }
    }

    llama_sampler_free(smpl);

    LOGD("nativeGenerate: done, %zu chars", accumulated.size());
    return env->NewStringUTF(accumulated.c_str());
}

// ---------------------------------------------------------------------------
// JNI: release
// ---------------------------------------------------------------------------

extern "C" JNIEXPORT void JNICALL
Java_app_m1k3_ai_assistant_ai_ma_MaBridge_release(
        JNIEnv */*env*/, jobject /*thiz*/, jlong handle) {

    MaContext *ma = handle_to_ctx(handle);
    if (!ma) return;

    LOGI("release: freeing context (handle=%p)", ma);
    if (ma->ctx)   llama_free(ma->ctx);
    if (ma->model) llama_model_free(ma->model);
    delete ma;
}
