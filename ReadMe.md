## 概述

本项目基于百度音乐提供的 SDK（StreamPlayer） 实现播放器 APP，具体如下：

* 基本功能：
	* 播放、暂停、
	* 下一曲、上一曲、
	* 进度条、进度条拖动、
	* 显示播放总时长、当前播放时长、
	* 歌曲名、歌手名、
	* 后台可播放
* 手势功能：
	* 左右滑动快进快退、
	* 上下滑动调节音量
* 特殊事件处理：
	* 播放状态下来电话，自动暂停播放，一旦挂断电话，自动恢复播放；
	* 播放状态下拔掉耳机，自动暂停播放，插入耳机，播放继续；
	* 无网、网络数据错误导致无法播放时的异常提醒。
* 附加功能：
	* 当前播放歌曲专辑图片
	* 当前播放歌曲列表
	* 后台播放时显示通知，点击返回
	* 播放模式：随机播放、重复播放
	* UI：素材来自[GitHub开源项目](https://github.com/lizhuoli1126/Android-Media-Player)。

具体实现简单说明如下。

## 基本功能

播放器基于 StreamPlayer (com.baidu.music.player.StreamPlayer).

### 播放、暂停

预定义歌曲Id数组 mSongIds，记录当前播放器状态 status（播放、暂停）。

准备歌曲时调用 StreamPlayer 的 prepare 方法即可。

播放、暂停 调用 StreamPlayer 的 start,pause 方法。

status 在 PLAYING/PAUSED 之间切换时切换 play/pause 按钮处的图片。

### 当前播放时长、显示播放总时长

调用 StreamPlayer 的 position 方法、duration 方法可以获得当前播放时长、总时长。

上文中获得的数字为毫秒数，还要将其换算为 分：秒 形式。使用 TextView 显示这两个值。

使用 SeekBar 来直观化所占比例。 使用 TimerTask 和 Handler 实时更新 SeekBar。

### 进度条、进度条拖动

设定 OnPreparedListener，歌曲准备完毕时使 SeekBar 的最大值与当前歌曲时长对应。

进度条拖动使用 OnSeekBarChangeListener 实现，onStopTrackingTouch 获取当前进度条位置，调用 StreamPlayer 的 seek 方法设置歌曲播放时间。

### 下一曲、 上一曲

根据之前歌曲下标计算之后歌曲下标，首先 reset StreamPlayer, 并调用 prepare, start 等方法。

### 歌曲名、歌手名

调用 StreamPlayer 的 getMusicId、 getMusicAlbum、 getMusicArtist、 getMusicTitle 方法。

### 后台可播放

重写 onBackPressed，使用 AlertDialog 确定用户选择，使用 Intent 开启服务。

设定 android:persistent="true" 可避免后台任务被回收。

## 手势功能

使用 GestureDetector 的 SimpleOnGestureListener 监听 onFling 动作。

### 左右滑动快进快退

当前快进快退幅度定为 5s, 调用 StreamPlayer 的 seek 方法。

### 上下滑动调节音量

使用系统的 AudioManager 获取音量最大值、当前值，并根据手势加一或减一。

## 特殊事件处理

### 无网、网络数据错误导致无法播放时的异常页面显示

使用系统的 ConnectivityManager，遍历 getAllNetworkInfo 中元素检查是否 isConnected().

若网络无连接则弹出 Toast 进行说明。

### 播放状态下，拔掉耳机，自动暂停播放；播放状态下插入耳机，播放继续。

使用 IntentFilter 注册过滤器 Intent.ACTION_HEADSET_PLUG。
使用 BroadcastReceiver 其中 state 1 为耳机插入，0 为耳机拔出。
据此进行相应反馈。

### 播放状态下来电话，自动暂停播放；一旦挂掉电话，自动恢复播放。

来电广播为 "android.intent.action.PHONE_STATE"，
去电广播为 "android.intent.action.NEW_OUTGOING_CALL"。

BroadcastReceiver 中 Intent 的 Action 为 Intent.ACTION_NEW_OUTGOING_CALL 则为去电。

否则为来电，调用系统 TelephonyManager 监听 PhoneStateListener.LISTEN_CALL_STATE 的  onCallStateChanged。 TelephonyManager.CALL_STATE_RINGING 为电话等待接听，TelephonyManager.CALL_STATE_OFFHOOK 为 电话接听，TelephonyManager.CALL_STATE_IDLE 为 电话挂机。

## 附加功能：

### 播放列表

使用 ListActivity, ListAdapter, ListView 等显示当前所有歌曲的 MusicId.

在列表中点击 MusicId 会使播放器播放相应歌曲。

### 随机播放

增加随机播放、重复播放模式，影响歌曲播放结束、点击下一曲时所选取播放的歌曲。

### 专辑图

使用 SDK 中提供的 ImageManager，初始化图片格式、缓存路径、图片大小等。

根据 Title 和 Artist 由 SearchManager 调用 getLyricPicAsync 方法，成功后更新相应 ImageView.

### 通知

使用 NotificationManager 及 PendingIntent 实现 APP 后台运行时点击通知栏图标可返回。

### UI

素材来自[GitHub开源项目](https://github.com/lizhuoli1126/Android-Media-Player)，
按钮被点击或聚焦时有不同的显示效果。
