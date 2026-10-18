#pragma once

// RetroAchievements (rcheevos rc_client) integration. All functions must be called on the emulation thread.

#include <cstdint>
#include <functional>
#include <string>
#include <vector>

namespace ra {

using Poster = std::function<void(std::function<void()>)>;   // runs a task on the emulation thread
using MemoryReader = std::function<uint8_t(uint32_t busAddress)>; // side-effect free bus read

void Init(Poster post, MemoryReader read, const std::string &userAgent);
void DoFrame();
void Idle();
void Reset();
void LoadGame(const std::string &hash); // RetroAchievements hash of the loaded disc (empty = none)
void UnloadGame();
bool IsHardcore();
std::vector<uint8_t> SerializeProgress();
void DeserializeProgress(const std::vector<uint8_t> &data);
std::string AchievementList();

} // namespace ra
