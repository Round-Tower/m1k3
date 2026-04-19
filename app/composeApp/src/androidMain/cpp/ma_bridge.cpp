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
#include "chat.h"                  // common/chat.h — native chat-template rendering + tool-call parsing
#include "nlohmann/json.hpp"       // vendored in llama.cpp/common/vendor

#define LOG_TAG "MaBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// ---------------------------------------------------------------------------
// MaContext - holds model + context (returned as an opaque jlong handle)
// ---------------------------------------------------------------------------

struct MaContext {
    llama_model               *model = nullptr;
    llama_context             *ctx   = nullptr;
    common_chat_templates_ptr  tmpls;  // jinja templates from GGUF metadata (null if init failed)
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
// JNI: nativeInit
// ---------------------------------------------------------------------------

extern "C" JNIEXPORT jlong JNICALL
Java_app_m1k3_ai_assistant_ai_ma_MaBridge_nativeInit(
        JNIEnv *env, jobject /*thiz*/,
        jstring jModelPath,
        jint    jNCtx,
        jint    jNBatch,
        jint    jNUbatch,
        jint    jThreadsGen,
        jint    jThreadsBatch,
        jboolean jUseFlashAttn,
        jint    jKvQuantOrdinal,
        jboolean jUseMlock) {

    const char *modelPath = env->GetStringUTFChars(jModelPath, nullptr);
    LOGI("init: loading %s", modelPath);

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0; // CPU-only on Android
    mparams.use_mlock    = jUseMlock == JNI_TRUE;

    llama_model *model = llama_model_load_from_file(modelPath, mparams);
    env->ReleaseStringUTFChars(jModelPath, modelPath);

    if (!model) {
        LOGE("init: failed to load model");
        return 0L;
    }

    // Thread selection: Kotlin side sends tier-aware counts (4 gen / 6 batch on
    // flagships, 2/3 on low-end). Fall back to hw-derived defaults when 0.
    const int hw_threads      = (int)std::thread::hardware_concurrency();
    const int n_threads       = jThreadsGen   > 0 ? (int)jThreadsGen   : std::min(4, hw_threads);
    const int n_threads_batch = jThreadsBatch > 0 ? (int)jThreadsBatch : std::min(hw_threads, 6);

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx            = (uint32_t)jNCtx;
    cparams.n_batch          = (uint32_t)jNBatch;
    cparams.n_ubatch         = (uint32_t)jNUbatch;
    cparams.n_threads        = n_threads;
    cparams.n_threads_batch  = n_threads_batch;

    // Phase 2: HIGH_END+ send useFlashAttn=true and kvQuantOrdinal=1 (Q8_0).
    // Upstream couples them (llama-context.cpp rejects quantized V without FA),
    // so we only apply Q8_0 when FA is also requested.
    const bool wantFlashAttn = jUseFlashAttn == JNI_TRUE;
    const bool wantQ8KV      = jKvQuantOrdinal == 1 && wantFlashAttn;

    cparams.flash_attn_type = wantFlashAttn
                                  ? LLAMA_FLASH_ATTN_TYPE_AUTO
                                  : LLAMA_FLASH_ATTN_TYPE_DISABLED;
    if (wantQ8KV) {
        cparams.type_k = GGML_TYPE_Q8_0;
        cparams.type_v = GGML_TYPE_Q8_0;
    }

    // Retry-on-null safety net: if the aggressive config (FA / Q8_0) fails on
    // this device's kernel variant, reset to F16 + FA disabled and retry once.
    // Never fail init over a tuning choice.
    llama_context *ctx = llama_new_context_with_model(model, cparams);
    bool fellBack = false;
    if (!ctx && (wantFlashAttn || wantQ8KV)) {
        LOGE("init: aggressive context (fa=%d kv=%d) failed; retrying with F16 + FA disabled",
             wantFlashAttn ? 1 : 0, wantQ8KV ? 1 : 0);
        cparams.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_DISABLED;
        cparams.type_k = GGML_TYPE_F16;
        cparams.type_v = GGML_TYPE_F16;
        ctx = llama_new_context_with_model(model, cparams);
        fellBack = true;
    }
    if (!ctx) {
        LOGE("init: failed to create context");
        llama_model_free(model);
        return 0L;
    }

    auto *ma = new MaContext();
    ma->model = model;
    ma->ctx   = ctx;

    // Load chat templates from GGUF metadata.
    // Works for any model that ships a `tokenizer.chat_template`: Qwen, Llama, Mistral, etc.
    // Falls back to CHATML when metadata is absent. Never throws, but may return a template
    // whose `apply()` will reject our inputs — we handle that per-call.
    try {
        ma->tmpls = common_chat_templates_init(model, "");
        LOGI("init: chat templates loaded");
    } catch (const std::exception &e) {
        LOGE("init: common_chat_templates_init threw: %s — native chat path disabled", e.what());
    }

    const int effectiveFa = (cparams.flash_attn_type == LLAMA_FLASH_ATTN_TYPE_AUTO) ? 1 : 0;
    const int effectiveKv = (cparams.type_k == GGML_TYPE_Q8_0) ? 1 : 0;
    LOGI("init: success (handle=%p, n_ctx=%d, n_batch=%d, n_ubatch=%d, threads=%d/%d, fa=%d, kv=%d, mlock=%d%s)",
         ma, cparams.n_ctx, cparams.n_batch, cparams.n_ubatch,
         n_threads, n_threads_batch,
         effectiveFa,
         effectiveKv,
         jUseMlock == JNI_TRUE ? 1 : 0,
         fellBack ? " FALLBACK" : "");
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
        jfloat  minP,
        jfloat  repeatPenalty,
        jobject callback,
        jstring jGrammar) {

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

    // Grammar sampler goes FIRST so invalid tokens are masked before other
    // filters see them. Lazy mode: grammar only engages once the trigger
    // pattern appears in the output, so free prose is unconstrained.
    if (jGrammar != nullptr) {
        const char *grammar_c = env->GetStringUTFChars(jGrammar, nullptr);
        static const char *trigger_patterns[] = { "<tool_call>" };
        llama_sampler *grammar_smpl = llama_sampler_init_grammar_lazy_patterns(
                vocab,
                grammar_c,
                /*grammar_root=*/"root",
                trigger_patterns,
                /*num_trigger_patterns=*/1,
                /*trigger_tokens=*/nullptr,
                /*num_trigger_tokens=*/0);
        env->ReleaseStringUTFChars(jGrammar, grammar_c);

        if (grammar_smpl != nullptr) {
            llama_sampler_chain_add(smpl, grammar_smpl);
            LOGI("nativeGenerate: lazy grammar sampler installed (trigger=<tool_call>)");
        } else {
            LOGE("nativeGenerate: grammar compilation failed; continuing unconstrained");
        }
    }

    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(topK));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(topP, /*min_keep=*/1));
    // min_p between top_p and temp — filters low-probability tails more cleanly
    // than top_p alone on small models. Disabled when minP <= 0.
    if (minP > 0.0f) {
        llama_sampler_chain_add(smpl, llama_sampler_init_min_p(minP, /*min_keep=*/1));
    }
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
// Helper: install a lazy grammar sampler with triggers from common_chat_params.
// Returns true if installed OR if there's no grammar to install (success).
// Returns false only if grammar was specified but failed to compile.
// ---------------------------------------------------------------------------
static bool install_chat_grammar_sampler(
        llama_sampler            *smpl,
        const llama_vocab        *vocab,
        const common_chat_params &params) {
    if (params.grammar.empty()) return true;

    std::vector<std::string> trigger_patterns;
    std::vector<llama_token> trigger_tokens;
    for (const auto &trigger : params.grammar_triggers) {
        switch (trigger.type) {
            case COMMON_GRAMMAR_TRIGGER_TYPE_WORD:
                // Word triggers are escaped so regex meta-chars in the word don't blow up
                // the pattern. (Ported from common/sampling.cpp — regex_escape is internal
                // so we do a minimal inline escape for the characters that actually matter
                // in the trigger words chat.cpp emits: `[ ] { } | ( ) . + * ? ^ $ \`.)
                {
                    std::string escaped;
                    escaped.reserve(trigger.value.size() * 2);
                    for (char c : trigger.value) {
                        if (strchr(R"([]{}|().+*?^$\)", c)) escaped.push_back('\\');
                        escaped.push_back(c);
                    }
                    trigger_patterns.push_back(std::move(escaped));
                }
                break;
            case COMMON_GRAMMAR_TRIGGER_TYPE_PATTERN:
                trigger_patterns.push_back(trigger.value);
                break;
            case COMMON_GRAMMAR_TRIGGER_TYPE_PATTERN_FULL: {
                const auto &pattern = trigger.value;
                std::string anchored = "^$";
                if (!pattern.empty()) {
                    anchored = (pattern.front() != '^' ? "^" : "") + pattern +
                               (pattern.back()  != '$' ? "$" : "");
                }
                trigger_patterns.push_back(std::move(anchored));
                break;
            }
            case COMMON_GRAMMAR_TRIGGER_TYPE_TOKEN:
                trigger_tokens.push_back(trigger.token);
                break;
        }
    }

    std::vector<const char *> trigger_patterns_c;
    trigger_patterns_c.reserve(trigger_patterns.size());
    for (const auto &p : trigger_patterns) trigger_patterns_c.push_back(p.c_str());

    llama_sampler *grammar_smpl = nullptr;
    if (params.grammar_lazy) {
        grammar_smpl = llama_sampler_init_grammar_lazy_patterns(
                vocab, params.grammar.c_str(), "root",
                trigger_patterns_c.data(), trigger_patterns_c.size(),
                trigger_tokens.data(), trigger_tokens.size());
    } else {
        grammar_smpl = llama_sampler_init_grammar(vocab, params.grammar.c_str(), "root");
    }

    if (!grammar_smpl) {
        LOGE("install_chat_grammar_sampler: grammar compilation failed");
        return false;
    }
    llama_sampler_chain_add(smpl, grammar_smpl);
    LOGI("install_chat_grammar_sampler: installed (%s, %zu patterns, %zu tokens)",
         params.grammar_lazy ? "lazy" : "strict",
         trigger_patterns.size(), trigger_tokens.size());
    return true;
}

// ---------------------------------------------------------------------------
// JNI: nativeGenerateChat — native chat-template path
//
// Input:
//   jMessagesJson : OpenAI-style messages JSON array
//     [{"role":"system","content":"..."},{"role":"user","content":"..."}]
//   jToolsJson    : OpenAI-style tools JSON array (may be "" or "[]")
//     [{"type":"function","function":{"name":"...","description":"...","parameters":{...}}}]
//
// Output (JSON string):
//   {"content":"...", "reasoning":"...", "tool_calls":[{"name":"...","arguments":"..."}]}
//   or {"error":"..."} on failure.
//
// The chat template from the GGUF drives prompt rendering AND per-model grammar shape;
// common_chat_parse maps raw output back to structured fields.
// ---------------------------------------------------------------------------

extern "C" JNIEXPORT jstring JNICALL
Java_app_m1k3_ai_assistant_ai_ma_MaBridge_nativeGenerateChat(
        JNIEnv *env, jobject /*thiz*/,
        jlong    handle,
        jstring  jMessagesJson,
        jstring  jToolsJson,
        jint     maxTokens,
        jfloat   temperature,
        jfloat   topP,
        jint     topK,
        jfloat   minP,
        jfloat   repeatPenalty,
        jboolean enableThinking,
        jobject  callback) {

    MaContext *ma = handle_to_ctx(handle);
    if (!ma || !ma->model || !ma->ctx) {
        LOGE("nativeGenerateChat: invalid handle");
        return env->NewStringUTF("{\"error\":\"invalid handle\"}");
    }
    if (!ma->tmpls) {
        LOGE("nativeGenerateChat: no chat templates available");
        return env->NewStringUTF("{\"error\":\"no chat templates\"}");
    }

    const llama_vocab *vocab = llama_model_get_vocab(ma->model);

    // --- Parse inputs ---
    common_chat_templates_inputs inputs;
    inputs.add_generation_prompt = true;
    inputs.use_jinja             = true;
    inputs.enable_thinking       = enableThinking;

    const char *messages_c = env->GetStringUTFChars(jMessagesJson, nullptr);
    const char *tools_c    = jToolsJson ? env->GetStringUTFChars(jToolsJson, nullptr) : "";

    try {
        auto messages_json = nlohmann::ordered_json::parse(messages_c);
        inputs.messages = common_chat_msgs_parse_oaicompat(messages_json);

        if (tools_c && *tools_c) {
            auto tools_json = nlohmann::ordered_json::parse(tools_c);
            inputs.tools = common_chat_tools_parse_oaicompat(tools_json);
        }
    } catch (const std::exception &e) {
        env->ReleaseStringUTFChars(jMessagesJson, messages_c);
        if (jToolsJson) env->ReleaseStringUTFChars(jToolsJson, tools_c);
        LOGE("nativeGenerateChat: JSON parse failed: %s", e.what());
        return env->NewStringUTF("{\"error\":\"json parse failed\"}");
    }

    env->ReleaseStringUTFChars(jMessagesJson, messages_c);
    if (jToolsJson) env->ReleaseStringUTFChars(jToolsJson, tools_c);

    // --- Apply template ---
    common_chat_params params;
    try {
        params = common_chat_templates_apply(ma->tmpls.get(), inputs);
    } catch (const std::exception &e) {
        LOGE("nativeGenerateChat: templates_apply failed: %s", e.what());
        return env->NewStringUTF("{\"error\":\"template apply failed\"}");
    }

    LOGI("nativeGenerateChat: format=%s, prompt=%zu bytes, grammar=%zu bytes, triggers=%zu",
         common_chat_format_name(params.format),
         params.prompt.size(),
         params.grammar.size(),
         params.grammar_triggers.size());

    // Dump first chunk of the generated PEG parser to verify markers match model output.
    try {
        common_peg_arena arena;
        arena.load(params.parser);
        std::string dump = arena.dump(arena.root());
        LOGI("nativeGenerateChat: parser head: <<%.*s>>",
             (int)std::min<size_t>(dump.size(), 600),
             dump.data());
    } catch (const std::exception & e) {
        LOGE("nativeGenerateChat: parser dump failed: %s", e.what());
    }

    // --- Streaming callback setup ---
    jclass    cbClass  = nullptr;
    jmethodID cbMethod = nullptr;
    if (callback != nullptr) {
        cbClass  = env->GetObjectClass(callback);
        cbMethod = env->GetMethodID(cbClass, "onToken", "(Ljava/lang/String;)V");
        if (!cbMethod) {
            LOGE("nativeGenerateChat: onToken method not found on callback object");
            callback = nullptr;
        }
    }

    // --- Tokenize rendered prompt ---
    const int n_ctx        = (int)llama_n_ctx(ma->ctx);
    const int n_prompt_max = n_ctx - (int)maxTokens - 10;

    std::vector<llama_token> prompt_tokens(n_ctx);
    int n_prompt = llama_tokenize(
            vocab,
            params.prompt.c_str(), (int32_t)params.prompt.size(),
            prompt_tokens.data(), (int32_t)prompt_tokens.size(),
            /*add_special=*/false,   // template already handled BOS/EOS
            /*parse_special=*/true);

    if (n_prompt < 0) {
        LOGE("nativeGenerateChat: tokenization failed (n=%d)", n_prompt);
        return env->NewStringUTF("{\"error\":\"tokenize failed\"}");
    }
    if (n_prompt > n_prompt_max && n_prompt_max > 0) {
        LOGI("nativeGenerateChat: truncating prompt %d → %d tokens", n_prompt, n_prompt_max);
        n_prompt = n_prompt_max;
    }
    prompt_tokens.resize(n_prompt);

    LOGD("nativeGenerateChat: %d prompt tokens, maxTokens=%d", n_prompt, (int)maxTokens);

    // --- Decode prompt tokens ---
    llama_batch batch = llama_batch_get_one(prompt_tokens.data(), n_prompt);
    if (llama_decode(ma->ctx, batch) != 0) {
        LOGE("nativeGenerateChat: initial decode failed");
        return env->NewStringUTF("{\"error\":\"decode failed\"}");
    }

    // --- Sampler chain ---
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    llama_sampler *smpl = llama_sampler_chain_init(sparams);
    install_chat_grammar_sampler(smpl, vocab, params); // non-fatal on failure

    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(topK));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(topP, /*min_keep=*/1));
    if (minP > 0.0f) {
        llama_sampler_chain_add(smpl, llama_sampler_init_min_p(minP, /*min_keep=*/1));
    }
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(smpl, llama_sampler_init_penalties(
            /*penalty_last_n=*/64,
            /*penalty_repeat=*/repeatPenalty,
            /*penalty_freq=*/0.0f,
            /*penalty_present=*/0.0f));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    // --- Generation loop (mirrors nativeGenerate) ---
    std::string accumulated;
    std::string utf8_buf;
    char piece_buf[256];
    const int n_max = std::min((int)maxTokens, n_ctx - n_prompt - 4);

    for (int i = 0; i < n_max; ++i) {
        llama_token token = llama_sampler_sample(smpl, ma->ctx, -1);

        if (llama_vocab_is_eog(vocab, token)) {
            LOGD("nativeGenerateChat: EOG at position %d", i);
            break;
        }

        int n_piece = llama_token_to_piece(
                vocab, token,
                piece_buf, (int32_t)sizeof(piece_buf) - 1,
                /*lstrip=*/0, /*special=*/true);

        if (n_piece <= 0) {
            llama_batch next = llama_batch_get_one(&token, 1);
            llama_decode(ma->ctx, next);
            continue;
        }
        piece_buf[n_piece] = '\0';

        accumulated += piece_buf;
        utf8_buf    += piece_buf;

        if (callback && is_complete_utf8(utf8_buf.c_str(), utf8_buf.size())) {
            jstring jPiece = env->NewStringUTF(utf8_buf.c_str());
            if (jPiece) {
                env->CallVoidMethod(callback, cbMethod, jPiece);
                env->DeleteLocalRef(jPiece);
            }
            utf8_buf.clear();
        }

        llama_batch next = llama_batch_get_one(&token, 1);
        if (llama_decode(ma->ctx, next) != 0) {
            LOGE("nativeGenerateChat: decode failed at position %d", i);
            break;
        }
    }

    if (callback && !utf8_buf.empty()) {
        jstring jPiece = env->NewStringUTF(utf8_buf.c_str());
        if (jPiece) {
            env->CallVoidMethod(callback, cbMethod, jPiece);
            env->DeleteLocalRef(jPiece);
        }
    }

    llama_sampler_free(smpl);

    // --- Parse output via common_chat_parse ---
    nlohmann::ordered_json result;
    result["raw"] = accumulated;

    // Diagnostic: head/tail of accumulated + prompt tail to debug parser misses.
    {
        const size_t head_n = std::min<size_t>(accumulated.size(), 200);
        const size_t tail_n = accumulated.size() > 300 ? 300 : accumulated.size();
        const size_t tail_off = accumulated.size() > tail_n ? accumulated.size() - tail_n : 0;
        LOGI("nativeGenerateChat: format=%s", common_chat_format_name(params.format));
        LOGI("nativeGenerateChat: accumulated head: <<%.*s>>",
             (int)head_n, accumulated.data());
        LOGI("nativeGenerateChat: accumulated tail: <<%.*s>>",
             (int)(accumulated.size() - tail_off),
             accumulated.data() + tail_off);
        const size_t ptail_n = std::min<size_t>(params.prompt.size(), 200);
        const size_t ptail_off = params.prompt.size() - ptail_n;
        LOGI("nativeGenerateChat: prompt tail: <<%.*s>>",
             (int)ptail_n, params.prompt.data() + ptail_off);
    }

    try {
        common_chat_parser_params pparams(params);
        pparams.parse_tool_calls = true;
        common_chat_msg msg = common_chat_parse(accumulated, /*is_partial=*/false, pparams);

        LOGI("nativeGenerateChat: parsed content (%zu c): <<%.*s>>",
             msg.content.size(),
             (int)std::min<size_t>(msg.content.size(), 300),
             msg.content.data());
        LOGI("nativeGenerateChat: parsed reasoning (%zu c), tool_calls=%zu",
             msg.reasoning_content.size(), msg.tool_calls.size());
        for (size_t i = 0; i < msg.tool_calls.size(); ++i) {
            const auto & tc = msg.tool_calls[i];
            LOGI("nativeGenerateChat: tc[%zu] name=%s args=<<%.*s>>",
                 i, tc.name.c_str(),
                 (int)std::min<size_t>(tc.arguments.size(), 200),
                 tc.arguments.data());
        }

        result["content"]           = msg.content;
        result["reasoning_content"] = msg.reasoning_content;

        auto tool_calls = nlohmann::ordered_json::array();
        for (const auto &tc : msg.tool_calls) {
            tool_calls.push_back({
                {"name",      tc.name},
                {"arguments", tc.arguments},
                {"id",        tc.id},
            });
        }
        result["tool_calls"] = tool_calls;
    } catch (const std::exception &e) {
        LOGE("nativeGenerateChat: parse failed: %s", e.what());
        result["content"]    = accumulated; // fall back to raw
        result["tool_calls"] = nlohmann::ordered_json::array();
    }

    LOGD("nativeGenerateChat: done, %zu chars, %zu tool_calls",
         accumulated.size(),
         result.contains("tool_calls") ? result["tool_calls"].size() : 0);

    std::string out = result.dump();
    return env->NewStringUTF(out.c_str());
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
