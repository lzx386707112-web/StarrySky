# 注意事项与排错

## 1. `StarrySky.with()` 与 `StarrySkyPlayer` 混用

**现象**：能出声，但进度、`pause`、`stop` 无效。  
**原因**：两套 **`PlayerControl` / 引擎**。  
**解决**：同一业务只选一种入口；`StarrySkyPlayer` 的全部操作使用 **`player.with()`**。

---

## 2. 进度监听

- 使用 **`setOnPlayProgressListener(listener, tag)`**，**`tag` 勿为 null**；建议在 **`onResume` 注册**、**`onPause` `removeProgressListener(tag)`**。  
- 与播放、**`seekTo`** 必须使用 **同一套** `StarrySky.with()`（或同一 `StarrySkyPlayer.with()`）。

---

## 3. 明文 HTTP

浏览器能播、App 报 **`ERROR`** 时，检查是否 **`CLEARTEXT`**：使用 HTTPS，或在宿主配置 **`usesCleartextTraffic` / `networkSecurityConfig`**。

---

## 4. `isConnectionService` 与定时关播

**`stopByTimedOff`** 中依赖 **播完当前曲再停** 等逻辑，需要 **`MusicService`**；**`isConnectionService = false`** 时相关能力为 **no-op**。

---

## 5. 缓存热切换

**`cacheSwitch`** 主要切换内部开关；已创建的解码管道可能不会立刻重建，必要时 **`stopMusic()`** 后重新播放。

---

## 6. 与其它 ExoPlayer / Media3 版本冲突

全工程对齐 **同一 Media3 版本**；出现 DEX / 类冲突时按 AGP 文档调整依赖排除或版本对齐。

---

## 7. 权限

库 **不代替宿主申请** `POST_NOTIFICATIONS`、蓝牙等运行时权限；**业务权限**（存储、麦克风等）请在 **`StarrySkyInterceptor`** 或业务层处理，勿假设库内会弹窗。

---

## 8. Log 排查

开启 **`StarrySkyInstall` → `isDebug = true`**，过滤 Logcat 标签 **`StarrySky`**、**`StarrySky.ExoPlayback`**、**`StarrySky.Playback`**，查看 **`errorMsg`**、HTTP 状态码与异常链。
