// RetroAchievements integration (rcheevos rc_client).
//
// Threading: every rc_client call runs on the emulation thread (through the poster installed by the JNI bridge).
// HTTP requests are executed by the Kotlin side (NativeBridge.raHttp) and their responses are posted back to the
// emulation thread. Events for the UI are queued and polled by Kotlin.
#include "gbsat_ra.hpp"

#include <jni.h>

#include <android/log.h>

#include <rc_client.h>
#include <rc_consoles.h>

#include <atomic>
#include <cstdio>
#include <cstring>
#include <deque>
#include <map>
#include <mutex>
#include <string>

#define RA_LOG(...) __android_log_print(ANDROID_LOG_INFO, "GBSat-RA", __VA_ARGS__)

namespace ra {
namespace {

JavaVM *g_vm = nullptr;
jclass g_bridge = nullptr;
jmethodID g_httpMethod = nullptr;

rc_client_t *g_client = nullptr;
Poster g_post;
MemoryReader g_read;
std::string g_userAgent = "GBSat";
std::string g_lastHash; // hash of the running disc (emulation thread)

void RC_CCONV LoadGameCallback(int result, const char *error, rc_client_t *client, void *);

struct PendingCall {
    rc_client_server_callback_t callback;
    void *data;
};
std::mutex g_pendingMutex;
std::map<int, PendingCall> g_pending;
std::atomic<int> g_nextId{1};

std::mutex g_eventMutex;
std::deque<std::string> g_events;

std::mutex g_statusMutex;
std::string g_loginState = "none"; // none | pending | ok | failed
std::string g_loginError;
std::string g_gameState = "none";  // none | pending | ok | failed | nogame
std::string g_gameError;

std::string Clean(const char *s) {
    std::string out = s ? s : "";
    for (char &c : out) {
        if (c == '\t' || c == '\n' || c == '\r') {
            c = ' ';
        }
    }
    return out;
}

void PushEvent(const std::string &line) {
    std::lock_guard lk(g_eventMutex);
    g_events.push_back(line);
    while (g_events.size() > 64) {
        g_events.pop_front();
    }
}

JNIEnv *Env() {
    JNIEnv *env = nullptr;
    if (g_vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) == JNI_EDETACHED) {
        g_vm->AttachCurrentThread(&env, nullptr);
    }
    return env;
}

uint32_t RC_CCONV ReadMemory(uint32_t address, uint8_t *buffer, uint32_t numBytes, rc_client_t *) {
    if (!g_read) {
        return 0;
    }
    for (uint32_t i = 0; i < numBytes; ++i) {
        const uint32_t a = address + i;
        if (a >= 0x200000) {
            return i;
        }
        // RetroAchievements Saturn sets use the RAM layout exposed by Beetle Saturn: work RAM low then high, stored as
        // host-endian 16-bit words, i.e. each pair of bytes is swapped relative to the Saturn bus.
        const uint32_t busBase = a < 0x100000 ? 0x00200000 + a : 0x06000000 + (a - 0x100000);
        buffer[i] = g_read(busBase ^ 1);
    }
    return numBytes;
}

void RC_CCONV ServerCall(const rc_api_request_t *request, rc_client_server_callback_t callback, void *callbackData,
                         rc_client_t *) {
    const int id = g_nextId++;
    {
        std::lock_guard lk(g_pendingMutex);
        g_pending[id] = {callback, callbackData};
    }
    JNIEnv *env = Env();
    jstring url = env->NewStringUTF(request->url);
    jstring post = request->post_data ? env->NewStringUTF(request->post_data) : nullptr;
    jstring type = request->content_type ? env->NewStringUTF(request->content_type) : nullptr;
    jstring ua = env->NewStringUTF(g_userAgent.c_str());
    env->CallStaticVoidMethod(g_bridge, g_httpMethod, id, url, post, type, ua);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }
    env->DeleteLocalRef(url);
    if (post) env->DeleteLocalRef(post);
    if (type) env->DeleteLocalRef(type);
    env->DeleteLocalRef(ua);
}

std::string AchievementLine(const char *kind, const rc_client_achievement_t *a) {
    char buf[64];
    std::snprintf(buf, sizeof(buf), "%u", a ? a->points : 0);
    return std::string(kind) + "\t" + Clean(a ? a->title : "") + "\t" + Clean(a ? a->description : "") + "\t" + buf +
           "\t" + Clean(a ? a->badge_url : "");
}

void RC_CCONV EventHandler(const rc_client_event_t *event, rc_client_t *client) {
    switch (event->type) {
    case RC_CLIENT_EVENT_ACHIEVEMENT_TRIGGERED: PushEvent(AchievementLine("unlocked", event->achievement)); break;
    case RC_CLIENT_EVENT_GAME_COMPLETED: {
        const rc_client_game_t *game = rc_client_get_game_info(client);
        PushEvent(std::string("completed\t") + Clean(game ? game->title : "") + "\t\t0\t" +
                  Clean(game ? game->badge_url : ""));
        break;
    }
    case RC_CLIENT_EVENT_ACHIEVEMENT_CHALLENGE_INDICATOR_SHOW:
        PushEvent(AchievementLine("challenge", event->achievement));
        break;
    case RC_CLIENT_EVENT_ACHIEVEMENT_PROGRESS_INDICATOR_SHOW:
    case RC_CLIENT_EVENT_ACHIEVEMENT_PROGRESS_INDICATOR_UPDATE:
        if (event->achievement) {
            PushEvent(std::string("progress\t") + Clean(event->achievement->title) + "\t" +
                      Clean(event->achievement->measured_progress) + "\t0\t" + Clean(event->achievement->badge_url));
        }
        break;
    case RC_CLIENT_EVENT_LEADERBOARD_STARTED:
        if (event->leaderboard) {
            PushEvent(std::string("lbstart\t") + Clean(event->leaderboard->title) + "\t" +
                      Clean(event->leaderboard->description) + "\t0\t");
        }
        break;
    case RC_CLIENT_EVENT_LEADERBOARD_FAILED:
        if (event->leaderboard) {
            PushEvent(std::string("lbfail\t") + Clean(event->leaderboard->title) + "\t\t0\t");
        }
        break;
    case RC_CLIENT_EVENT_LEADERBOARD_SUBMITTED:
        if (event->leaderboard) {
            PushEvent(std::string("lbsubmit\t") + Clean(event->leaderboard->title) + "\t" +
                      Clean(event->leaderboard->tracker_value) + "\t0\t");
        }
        break;
    case RC_CLIENT_EVENT_SERVER_ERROR:
        if (event->server_error) {
            PushEvent(std::string("error\t") + Clean(event->server_error->api) + "\t" +
                      Clean(event->server_error->error_message) + "\t0\t");
        }
        break;
    case RC_CLIENT_EVENT_DISCONNECTED: PushEvent("info\tRetroAchievements\tDisconnected; unlocks will be retried\t0\t"); break;
    case RC_CLIENT_EVENT_RECONNECTED: PushEvent("info\tRetroAchievements\tReconnected\t0\t"); break;
    case RC_CLIENT_EVENT_RESET: PushEvent("reset\t\t\t0\t"); break;
    default: break;
    }
}

void RC_CCONV LogMessage(const char *message, const rc_client_t *) {
    RA_LOG("%s", message);
}

void RC_CCONV LoginCallback(int result, const char *error, rc_client_t *client, void *) {
    std::lock_guard lk(g_statusMutex);
    if (result == RC_OK) {
        g_loginState = "ok";
        g_loginError.clear();
        const rc_client_user_t *user = rc_client_get_user_info(client);
        // A game started before logging in: load its achievements now
        if (!g_lastHash.empty() && !rc_client_is_game_loaded(client) && g_post) {
            g_gameState = "pending";
            g_post([client] {
                if (!g_lastHash.empty() && !rc_client_is_game_loaded(client)) {
                    rc_client_begin_load_game(client, g_lastHash.c_str(), LoadGameCallback, nullptr);
                }
            });
        }
        if (user) {
            PushEvent(std::string("login\t") + Clean(user->display_name) + "\t" + Clean(user->token) + "\t" +
                      std::to_string(user->score) + "\t" + Clean(user->avatar_url));
        }
    } else {
        g_loginState = "failed";
        g_loginError = error ? error : "Login failed";
        PushEvent(std::string("loginfail\t") + Clean(g_loginError.c_str()) + "\t\t0\t");
    }
}

void RC_CCONV LoadGameCallback(int result, const char *error, rc_client_t *client, void *) {
    std::lock_guard lk(g_statusMutex);
    if (result == RC_OK) {
        g_gameState = "ok";
        g_gameError.clear();
        const rc_client_game_t *game = rc_client_get_game_info(client);
        rc_client_user_game_summary_t summary{};
        rc_client_get_user_game_summary(client, &summary);
        char buf[128];
        std::snprintf(buf, sizeof(buf), "%u/%u achievements, %u/%u points", summary.num_unlocked_achievements,
                      summary.num_core_achievements, summary.points_unlocked, summary.points_core);
        PushEvent(std::string("game\t") + Clean(game ? game->title : "") + "\t" + buf + "\t0\t" +
                  Clean(game ? game->badge_url : ""));
    } else {
        g_gameState = result == RC_NO_GAME_LOADED ? "nogame" : "failed";
        g_gameError = error ? error : "Game not recognized";
        PushEvent(std::string("gamefail\t") + Clean(g_gameError.c_str()) + "\t\t0\t");
    }
}

} // namespace

// ---------------------------------------------------------------------------------------------------------------------

void Init(Poster post, MemoryReader read, const std::string &userAgent) {
    g_post = std::move(post);
    g_read = std::move(read);
    g_userAgent = userAgent;
    if (g_client == nullptr) {
        g_client = rc_client_create(ReadMemory, ServerCall);
        rc_client_set_event_handler(g_client, EventHandler);
        rc_client_enable_logging(g_client, RC_CLIENT_LOG_LEVEL_INFO, LogMessage);
        char clause[64] = {};
        rc_client_get_user_agent_clause(g_client, clause, sizeof(clause));
        g_userAgent += " ";
        g_userAgent += clause;
    }
}

void DoFrame() {
    if (g_client && rc_client_is_game_loaded(g_client)) {
        rc_client_do_frame(g_client);
    }
}

void Idle() {
    if (g_client) {
        rc_client_idle(g_client);
    }
}

void Reset() {
    if (g_client && rc_client_is_game_loaded(g_client)) {
        rc_client_reset(g_client);
    }
}

void LoadGame(const std::string &hash) {
    if (!g_client) {
        return;
    }
    rc_client_unload_game(g_client);
    g_lastHash = hash;
    if (!rc_client_get_user_info(g_client) || hash.empty()) {
        std::lock_guard lk(g_statusMutex);
        g_gameState = "none";
        return;
    }
    {
        std::lock_guard lk(g_statusMutex);
        g_gameState = "pending";
    }
    rc_client_begin_load_game(g_client, hash.c_str(), LoadGameCallback, nullptr);
}

void UnloadGame() {
    g_lastHash.clear();
    if (g_client) {
        rc_client_unload_game(g_client);
    }
    std::lock_guard lk(g_statusMutex);
    g_gameState = "none";
}

bool IsHardcore() {
    return g_client && rc_client_get_hardcore_enabled(g_client);
}

std::vector<uint8_t> SerializeProgress() {
    std::vector<uint8_t> out;
    if (g_client && rc_client_is_game_loaded(g_client)) {
        out.resize(rc_client_progress_size(g_client));
        if (rc_client_serialize_progress_sized(g_client, out.data(), out.size()) != RC_OK) {
            out.clear();
        }
    }
    return out;
}

void DeserializeProgress(const std::vector<uint8_t> &data) {
    if (g_client && rc_client_is_game_loaded(g_client)) {
        rc_client_deserialize_progress_sized(g_client, data.empty() ? nullptr : data.data(), data.size());
    }
}

} // namespace ra

// =====================================================================================================================
// JNI

using namespace ra;

#define RA_FN(ret, name) extern "C" JNIEXPORT ret JNICALL Java_app_gbsat_android_NativeBridge_##name

static std::string JS(JNIEnv *env, jstring s) {
    if (!s) return {};
    const char *c = env->GetStringUTFChars(s, nullptr);
    std::string r = c ? c : "";
    env->ReleaseStringUTFChars(s, c);
    return r;
}

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *) {
    g_vm = vm;
    JNIEnv *env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    jclass cls = env->FindClass("app/gbsat/android/NativeBridge");
    if (cls) {
        g_bridge = static_cast<jclass>(env->NewGlobalRef(cls));
        g_httpMethod = env->GetStaticMethodID(g_bridge, "raHttp",
                                              "(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
    }
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }
    return JNI_VERSION_1_6;
}

RA_FN(void, nativeRaHttpResponse)(JNIEnv *env, jclass, jint id, jint status, jbyteArray body) {
    PendingCall call{};
    {
        std::lock_guard lk(g_pendingMutex);
        auto it = g_pending.find(id);
        if (it == g_pending.end()) {
            return;
        }
        call = it->second;
        g_pending.erase(it);
    }
    std::string data;
    if (body) {
        const jsize n = env->GetArrayLength(body);
        data.resize(n);
        env->GetByteArrayRegion(body, 0, n, reinterpret_cast<jbyte *>(data.data()));
    }
    if (!g_post) {
        return;
    }
    g_post([call, status, data = std::move(data)] {
        rc_api_server_response_t response{};
        response.body = data.c_str();
        response.body_length = data.size();
        // Negative status = network failure; rc_client retries "no response" errors
        response.http_status_code = status > 0 ? status : RC_API_SERVER_RESPONSE_RETRYABLE_CLIENT_ERROR;
        call.callback(&response, call.data);
    });
}

RA_FN(void, nativeRaLogin)(JNIEnv *env, jclass, jstring user, jstring secret, jboolean isToken) {
    if (!g_post) return;
    const std::string u = JS(env, user), s = JS(env, secret);
    {
        std::lock_guard lk(g_statusMutex);
        g_loginState = "pending";
    }
    const bool token = isToken;
    g_post([u, s, token] {
        if (!g_client) return;
        if (token) {
            rc_client_begin_login_with_token(g_client, u.c_str(), s.c_str(), LoginCallback, nullptr);
        } else {
            rc_client_begin_login_with_password(g_client, u.c_str(), s.c_str(), LoginCallback, nullptr);
        }
    });
}

RA_FN(void, nativeRaLogout)(JNIEnv *, jclass) {
    {
        std::lock_guard lk(g_statusMutex);
        g_loginState = "none";
        g_gameState = "none";
    }
    if (!g_post) return;
    g_post([] {
        if (g_client) rc_client_logout(g_client);
    });
}

RA_FN(void, nativeRaSetHardcore)(JNIEnv *, jclass, jboolean on) {
    if (!g_post) return;
    g_post([on] {
        if (g_client) rc_client_set_hardcore_enabled(g_client, on ? 1 : 0);
    });
}

static jbyteArray Bytes(JNIEnv *env, const std::string &s) {
    jbyteArray arr = env->NewByteArray((jsize)s.size());
    env->SetByteArrayRegion(arr, 0, (jsize)s.size(), reinterpret_cast<const jbyte *>(s.data()));
    return arr;
}

RA_FN(jbyteArray, nativeRaPollEvents)(JNIEnv *env, jclass) {
    std::string out;
    {
        std::lock_guard lk(g_eventMutex);
        while (!g_events.empty()) {
            out += g_events.front();
            out += '\n';
            g_events.pop_front();
        }
    }
    return Bytes(env, out);
}

// "loginState\tgameState\terror"
RA_FN(jbyteArray, nativeRaState)(JNIEnv *env, jclass) {
    std::lock_guard lk(g_statusMutex);
    const std::string s = g_loginState + "\t" + g_gameState + "\t" + Clean((g_loginError + " " + g_gameError).c_str());
    return Bytes(env, s);
}

namespace ra {
// Builds the achievement list (emulation thread). Lines: id\ttitle\tdescription\tpoints\tunlocked\tbadge\tlockedBadge\tprogress\tbucket
std::string AchievementList() {
    std::string out;
    if (!g_client || !rc_client_is_game_loaded(g_client)) {
        return out;
    }
    const rc_client_game_t *game = rc_client_get_game_info(g_client);
    rc_client_user_game_summary_t summary{};
    rc_client_get_user_game_summary(g_client, &summary);
    char rp[256] = {};
    rc_client_get_rich_presence_message(g_client, rp, sizeof(rp));
    char head[512];
    std::snprintf(head, sizeof(head), "#\t%s\t%u\t%u\t%u\t%u\t%s\t%s\t%d\n", Clean(game ? game->title : "").c_str(),
                  summary.num_unlocked_achievements, summary.num_core_achievements, summary.points_unlocked,
                  summary.points_core, Clean(game ? game->badge_url : "").c_str(), Clean(rp).c_str(),
                  rc_client_get_hardcore_enabled(g_client));
    out += head;
    rc_client_achievement_list_t *list = rc_client_create_achievement_list(
        g_client, RC_CLIENT_ACHIEVEMENT_CATEGORY_CORE, RC_CLIENT_ACHIEVEMENT_LIST_GROUPING_PROGRESS);
    if (!list) {
        return out;
    }
    for (uint32_t b = 0; b < list->num_buckets; ++b) {
        const auto &bucket = list->buckets[b];
        for (uint32_t i = 0; i < bucket.num_achievements; ++i) {
            const rc_client_achievement_t *a = bucket.achievements[i];
            char line[96];
            std::snprintf(line, sizeof(line), "%u\t", a->id);
            out += line;
            out += Clean(a->title) + "\t" + Clean(a->description) + "\t" + std::to_string(a->points) + "\t" +
                   (a->unlocked ? "1" : "0") + "\t" + Clean(a->badge_url) + "\t" + Clean(a->badge_locked_url) + "\t" +
                   Clean(a->measured_progress) + "\t" + Clean(bucket.label) + "\n";
        }
    }
    rc_client_destroy_achievement_list(list);
    return out;
}
} // namespace ra
