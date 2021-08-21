# lrc-helper-android
歌词助手 for Android 9+

## 简介
- 无需 Root，在通知中心实时显示当前播放歌词的小插件，支持任何有系统通知样式的播放器；
- 通过 DIY，可配合其它通知读取应用，如 **Kustom Widget**、**Automate**，实现将歌词显示到桌面微件、将当前歌词状态发送到第三方服务如 IFTTT 等。

## 截图（不含微件）
<p>
<img width="400" alt="screenshot" src="https://user-images.githubusercontent.com/5051300/130321381-83a8aac2-fe0d-454c-ae7e-ddc9c3bd1175.png">
<img width="400" alt="screenshot" src="https://user-images.githubusercontent.com/5051300/130321628-0980329a-6e83-4b21-9972-5152ae8066a1.png">
</p>

## 使用
- 对于国产音乐 App，需要设置通知栏样式为系统通知栏；
- [下载 Release](https://github.com/rikumi/lrc-helper-android/releases/latest)；
- 安装后将会跳转通知读取权限，开启权限后可使用，建议手动隐藏启动器图标并关闭电池优化；
- 开启通知折叠：在 **应用管理 - 歌词助手 - 通知 - 歌词通知 - 行为** 中设置 **无声显示并将重要性级别最小化**，再次打开通知中心即可折叠通知，折叠后只显示一行，展开显示两行。

## 已知问题
- 由于网易云 API 限制，频繁搜索歌词会触发 IP 反爬机制，约 1 小时之内恢复，但按顺序聆听音乐过程中一般不会受此影响；
- 每 0.5 秒都会读取一次通知，对续航可能会有明显影响，如果需要暂停使用可以强行停止，需要使用时再打开；
- 调整播放进度、暂停、继续播放时，歌词时间可能会对不上，取决于播放器对媒体通知的支持情况；
- 目前是用「标题 - 艺术家」作为关键词来搜词的，搜词结果可能不准。

## 配合 Kustom Widget 使用
利用如下插值表达式可读取并实时显示当前歌词；同时需要在 Kustom Widget 中将更新模式设置为 **快速（总是每秒更新）**。

```
$ni(io.github.rikumi.lyrichelper, title, tu(rnd, 1, 1, 1))$
```

由于 Kustom Widget 中读取通知的宏是 **非响应式的**，不会随通知内容实时刷新，因此需要在表达式内通过增加冗余参数（`tu(rnd, 1, 1, 1)`），引入响应式因素，使表达式每秒重算。
