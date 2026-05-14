# 注意事项与排错（FAQ）

## 1. `StarrySky.with()` 与 `StarrySkyPlayer` 混用

**现象**：声音在播，但进度条、`pause`、`stop` 无效。  
**原因**：`StarrySkyPlayer.create().with()` 与 `StarrySky.with()` 是两套 **`PlayerControl` / 引擎**。  
**解决**：同一界面只选一种入口；多实例则所有操作（含 `setOnPlayProgressListener`、`seekTo`）都用 **同一** `player.with()`。

---

## 2. 进度监听 `setOnPlayProgressListener` 无回调

**常见原因**：

1. **tag 为 null 导致早期版本未注册**：已在库内对空 tag 使用默认 key；仍建议在 **`onResume`** 注册并传入 **`this.toString()`**，在 **`onPause`** **`removeProgressListener`**。
2. **与播放入口不一致**：见第 1 条。
3. **定时器未启动**：缓冲阶段已支持在 `BUFFERING` 时启动进度任务；若仍无回调，确认是否进入 `PLAYING`/`BUFFERING` 且未 `setWithOutCallback(false)` 误关回调。

---

## 3. `bindService` 与 `isConnectionService`

| 配置 | 说明 |
|------|------|
| **`true`（默认）** | 异步 bind；需保证 **`MusicService`** 在 Manifest 中可用；前台与通知受系统限制 |
| **`false`** | 进程内 **`MusicPlaybackFacade`**，调试简单；**定时关播依赖 Service 的「播完再停」等为 no-op** |

`PlaybackManager` 已使用 **`playbackHost ?: StarrySkyInstall.playbackHost`** 解析宿主，缓解「过早 `StarrySky.with()`」问题；仍建议宿主在逻辑上避免在 `Application` 极早阶段长时间无 `playbackHost` 时大量发播放指令。

---

## 4. 明文 HTTP（cleartext）

**现象**：浏览器能播，`PlaybackStage = ERROR`，日志含 `CLEARTEXT` 等。  
**原因**：Android 9+ 默认禁止明文 HTTP。  
**解决**：宿主 `android:usesCleartextTraffic` 或 **`network-security-config`** 按域名放行；生产环境优先 **HTTPS**。

库模块自带 **`networkSecurityConfig`**，宿主可用 `tools:replace` 覆盖。

---

## 5. 存储与缓存目录

- 缓存写到 **`getExternalFilesDir`** 一般无需旧版读写外置存储权限。
- 若自定义路径到 **`Environment.getExternalStorageDirectory()`** 等公共目录，需自行处理 **分区存储 / 权限** 与失败回退。

---

## 6. `cacheSwitch` 与运行时切换

`cacheSwitch` 主要切换内部开关常量；**已创建的 `ExoPlayer` / DataSource 管道** 可能不会立刻重建。若需强一致，可在切换后停止并重新 `play`。

---

## 7. 与其它 ExoPlayer / Media3 依赖共存

多模块引入不同版本 ExoPlayer/Media3 可能导致 **DEX / 运行时冲突**。建议：

- 全工程对齐 **同一 `media3` 版本**；
- 若遇打包期崩溃，可参考历史经验在 `gradle.properties` 中调整 dex 相关开关（以 AGP 文档为准）。

---

## 8. 蓝牙与通知权限

- 库 Manifest 声明 **`BLUETOOTH` / `BLUETOOTH_CONNECT`** 等，**不代替宿主申请运行时权限**。
- **`POST_NOTIFICATIONS`**（API 33+）：宿主在合适时机申请。

---

## 9. 播放失败排查

1. 打开 **`StarrySkyInstall` 的 `isDebug`**，查看 Logcat 中 **`StarrySky`** / **`StarrySky.ExoPlayback`** / **`StarrySky.Playback`** 标签。  
2. 关注 **HTTP 状态码**、**cleartext**、**超时**、**格式不支持** 等日志。  
3. 确认 **`SongInfo.songUrl`** 可访问且 **User-Agent 未被 CDN 拒绝**（必要时在自定义 `Playback` / 数据源层调整）。

---

## 10. 开发自检清单

- [ ] `Application` 中已 **`StarrySkyInstall.init { ... }.apply()`**  
- [ ] 主进程初始化；非主进程不依赖播放单例  
- [ ] 通知、存储、蓝牙等权限与隐私政策一致  
- [ ] 网络 URL 使用 **HTTPS** 或已配置 cleartext  
- [ ] UI 只绑定 **一套** `PlayerControl`（全局或 `StarrySkyPlayer`）  
- [ ] 进度监听 **tag** 与生命周期配对  
