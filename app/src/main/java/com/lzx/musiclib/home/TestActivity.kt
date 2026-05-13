package com.lzx.musiclib.home

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.lzx.musiclib.showToast
import com.lzx.starrysky.OnPlayProgressListener
import com.lzx.starrysky.SongInfo
import com.lzx.starrysky.StarrySky
import com.lzx.starrysky.StarrySkyPlayer
import com.lzx.starrysky.control.RepeatMode
import com.lzx.starrysky.intercept.InterceptCallback
import com.lzx.starrysky.intercept.InterceptorThread
import com.lzx.starrysky.intercept.StarrySkyInterceptor
import com.lzx.starrysky.manager.PlaybackStage
import com.lzx.starrysky.notification.INotification
import com.lzx.starrysky.utils.MainLooper
import com.lzx.starrysky.utils.formatTime
import com.lzx.starrysky.utils.md5
import com.lzx.musiclib.databinding.ActivityTestBinding


open class TestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTestBinding

    val z = "https://storage.googleapis.com/exoplayer-test-media-0/play.mp3"
    val a = "https://storage.googleapis.com/exoplayer-test-media-0/Jazz_In_Paris.mp3"
    val b = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"
    val c = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3"
    val d = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3"
    val e = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3"
    val f = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3"
    val g = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-6.mp3"

    val test = "https://storage.googleapis.com/exoplayer-test-media-0/play.mp3"

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val songList = mutableListOf<SongInfo>()
        songList.add(SongInfo("z", z, "z"))
        songList.add(SongInfo("a", a, "a"))
        songList.add(SongInfo("b", b, "b"))
        songList.add(SongInfo("c", c, "c"))
        songList.add(SongInfo("d", d, "d"))
        songList.add(SongInfo("e", e, "e"))
        songList.add(SongInfo("f", f, "f"))
        songList.add(SongInfo("g", g, "g"))

        val soundPoolList = mutableListOf<String>()
        soundPoolList.add("hglo1.ogg")
        soundPoolList.add("hglo2.ogg")
        soundPoolList.add("hglo3.ogg")
        soundPoolList.add("hglo4.ogg")
        soundPoolList.add("hglo5.ogg")
        soundPoolList.add("hglo6.ogg")
        soundPoolList.add("hglo7.ogg")
        soundPoolList.add("hglo8.ogg")

        val m3u8 = mutableListOf<Pair<String, String>>()
        m3u8.add(Pair("CRI汉语环球", "http://sk.cri.cn/hyhq.m3u8"))
        m3u8.add(Pair("CRI环球资讯", "http://sk.cri.cn/nhzs.m3u8"))
        m3u8.add(Pair("CRI劲曲调频", "http://sk.cri.cn/887.m3u8"))
        m3u8.add(Pair("CRI怀旧金曲", "http://sk.cri.cn/oldies.m3u8"))
        m3u8.add(Pair("CRI客家之声", "http://sk.cri.cn/hakka.m3u8"))
        m3u8.add(Pair("CRI闽南之音", "http://sk.cri.cn/minnan.m3u8"))
        m3u8.add(Pair("CRI世界华声", "http://sk.cri.cn/hxfh.m3u8"))
        m3u8.add(Pair("CRl News", "http://sk.cri.cn/905.m3u8"))
        m3u8.add(Pair("CRI EZFM", "http://sk.cri.cn/915.m3u8"))
        m3u8.add(Pair("CRI Nairobi 91.9", "http://sk.cri.cn/kenya.m3u8"))
        m3u8.add(Pair("CRI music", "http://sk.cri.cn/am1008.m3u8"))

        val m3u8List = mutableListOf<SongInfo>()
        m3u8.forEach {
            val songInfo = SongInfo(it.second.md5(), it.second)
            songInfo.artist = it.first
            songInfo.songName = it.first
            songInfo.songCover = "https://blog.xmcdn.com/wp-content/uploads/2014/07/%E5%BD%95%E9%9F%B3.jpg"
            m3u8List.add(songInfo)
        }

        val rtmp = mutableListOf<Pair<String, String>>()
        rtmp.add(Pair("香港财经", "rtmp://202.69.69.180:443/webcast/bshdlive-pc"))
        rtmp.add(Pair("韩国GoodTV", "rtmp://mobliestream.c3tv.com:554/live/goodtv.sdp"))
        rtmp.add(Pair("韩国朝鲜日报", "rtmp://live.chosun.gscdn.com/live/tvchosun1.stream"))
        rtmp.add(Pair("美国1", "rtmp://ns8.indexforce.com/home/mystream"))
        rtmp.add(Pair("美国2", "rtmp://media3.scctv.net/live/scctv_800"))
        rtmp.add(Pair("美国中文电视", "rtmp://media3.sinovision.net:1935/live/livestream"))
        rtmp.add(Pair("湖南卫视", "rtmp://58.200.131.2:1935/livetv/hunantv"))

        val rtmpList = mutableListOf<SongInfo>()
        rtmp.forEach {
            val songInfo = SongInfo(it.second.md5(), it.second)
            songInfo.artist = it.first
            songInfo.songName = it.first
            songInfo.songCover = "https://img95.699pic.com/photo/50052/5059.jpg_wh300.jpg"
            rtmpList.add(songInfo)
        }

        binding.playMusicById.setOnClickListener {
            StarrySky.with().playMusicById("z")
        }

        val player = StarrySkyPlayer.create()
            .setAutoManagerFocus(false)
        binding.playMusicByUrl.setOnClickListener {
//            StarrySky.with().playMusicByUrl(test)
            player.with().playMusicByUrl(a)
        }
        binding.playMusicByInfo.setOnClickListener {
//            StarrySky.with().playMusicByInfo(SongInfo("a", a))
            StarrySky.with().playMusicByInfo(SongInfo("a", a))
        }
        binding.playMusic.setOnClickListener {
            StarrySky.with().playMusic(songList, 0)
        }
        binding.pauseMusic.setOnClickListener {
            StarrySky.with().pauseMusic()
        }
        binding.restoreMusic.setOnClickListener {
            StarrySky.with().restoreMusic()
        }
        binding.stopMusic.setOnClickListener {
            StarrySky.with().stopMusic()
        }
        binding.skipToNext.setOnClickListener {
            StarrySky.with().skipToNext()
        }
        binding.skipToPrevious.setOnClickListener {
            StarrySky.with().skipToPrevious()
        }
        binding.setRepeatMode.setOnClickListener {
            val mode = StarrySky.with().getRepeatMode()
            when (mode.repeatMode) {
                RepeatMode.REPEAT_MODE_NONE -> {
                    StarrySky.with().setRepeatMode(RepeatMode.REPEAT_MODE_ONE, false)
                }
                RepeatMode.REPEAT_MODE_ONE -> {
                    StarrySky.with().setRepeatMode(RepeatMode.REPEAT_MODE_SHUFFLE, false)
                }
                RepeatMode.REPEAT_MODE_SHUFFLE -> {
                    StarrySky.with().setRepeatMode(RepeatMode.REPEAT_MODE_REVERSE, false)
                }
                RepeatMode.REPEAT_MODE_REVERSE -> {
                    StarrySky.with().setRepeatMode(RepeatMode.REPEAT_MODE_NONE, false)
                }
            }
            getRepeatModelImpl()
        }
        binding.getRepeatMode.setOnClickListener {
            getRepeatModelImpl()
        }
        binding.getPlayList.setOnClickListener {
            val list = StarrySky.with().getPlayList()
            showToast(list.size.toString())
        }
        binding.getNowPlayingSongInfo.setOnClickListener {
            val info = StarrySky.with().getNowPlayingSongInfo()
            showToast(info?.songId)
        }
        binding.getNowPlayingIndex.setOnClickListener {
            val index = StarrySky.with().getNowPlayingIndex()
            showToast(index.toString())
        }

        binding.isSkipToNextEnabled.setOnClickListener {
            val isSkipToNextEnabled = StarrySky.with().isSkipToNextEnabled()
            showToast("isSkipToNextEnabled = $isSkipToNextEnabled")
        }
        binding.isSkipToPreviousEnabled.setOnClickListener {
            val isSkipToPreviousEnabled = StarrySky.with().isSkipToPreviousEnabled()
            showToast("isSkipToPreviousEnabled = $isSkipToPreviousEnabled")
        }
        binding.getAudioSessionId.setOnClickListener {
            val getAudioSessionId = StarrySky.with().getAudioSessionId()
            showToast("getAudioSessionId = $getAudioSessionId")
        }
        binding.querySongInfoInLocal.setOnClickListener {
            val list = StarrySky.with().querySongInfoInLocal(this)
            showToast("size = ${list.size}")
        }
        binding.cacheSwitch.text = if (StarrySky.isOpenCache()) "缓存开" else "缓存关"
        binding.cacheSwitch.setOnClickListener {
            StarrySky.with().cacheSwitch(!StarrySky.isOpenCache())
            binding.cacheSwitch.text = if (StarrySky.isOpenCache()) "缓存开" else "缓存关"
        }
        binding.interceptor.setOnClickListener {
            StarrySky.with()
                .addInterceptor(InterceptorA())
                .addInterceptor(InterceptorB(), InterceptorThread.IO)
                .playMusic(songList, 0)
        }
        var index = 0
        binding.soundPool.setOnClickListener {
            if (StarrySky.with().isPlaying()) {
                StarrySky.with().stopMusic()
            }
            StarrySky.soundPool()?.prepareForAssets(soundPoolList) {
                if (index > soundPoolList.lastIndex) {
                    index = 0
                }
                it.playSound(index)
                index++
            }
        }
        binding.notifySwitch.setOnClickListener {
            val type = StarrySky.getNotificationType()
            if (type == INotification.SYSTEM_NOTIFICATION) {
                StarrySky.changeNotification(INotification.CUSTOM_NOTIFICATION)
                showToast("当前使用自定义通知栏")
            } else {
                StarrySky.changeNotification(INotification.SYSTEM_NOTIFICATION)
                showToast("当前使用系统通知栏")
            }
        }
        binding.updateList.setOnClickListener {
            val list = mutableListOf<SongInfo>()
            songList.add(SongInfo("f", f, "f"))
            songList.add(SongInfo("g", g, "g"))
            StarrySky.with().updatePlayList(list)

            val size = StarrySky.with().getPlayList().size
            showToast("size = $size")
        }

        binding.flac.setOnClickListener {
            val list = mutableListOf<SongInfo>()
            val info = SongInfo()
            info.songId = "11111"
            info.songUrl = "https://github.com/EspoirX/lzxTreasureBox/raw/master/%E6%83%B3%E4%B8%8D%E5%88%B0.flac"
            info.songName = "庄心妍-想不到"
            info.artist = "庄心妍"
            info.songCover = "https://y.gtimg.cn/music/photo_new/T001R300x300M000003Cn3Yh16q1MO.jpg?max_age=2592000"
            list.add(info)
            StarrySky.with().playMusic(list, 0)
        }

        binding.dash.setOnClickListener {
            val info = SongInfo()
            info.songId = "32313"
            info.songUrl = "https://storage.googleapis.com/wvmedia/clear/hevc/tears/tears.mpd"
            StarrySky.with().playMusicByInfo(info)
        }

        binding.m3u8Btn.setOnClickListener {
            StarrySky.with().playMusic(m3u8List, 0)
        }

        binding.rtmpBtn.setOnClickListener {
            StarrySky.with().playMusic(rtmpList, 0)
        }
        binding.delete.setOnClickListener {
            val list = StarrySky.with().getPlayList()
            StarrySky.with().removeSongInfo(list.getOrNull(1)?.songId)
            showToast("已删除")
        }
        binding.newPlayer1.setOnClickListener {
            val info = SongInfo(a.md5(), a)
//            StarrySky.newPlayer(0)?.play(info, true)
        }
        binding.newPlayer2.setOnClickListener {
            val info = SongInfo(b.md5(), b)
//            StarrySky.newPlayer(1)?.play(info, true)
        }
        binding.stopNewPlayer1.setOnClickListener {
//            StarrySky.newPlayer(0)?.stop()
        }
        binding.stopNewPlayer2.setOnClickListener {
//            StarrySky.newPlayer(1)?.stop()
        }
        binding.closeN.setOnClickListener {
            StarrySky.closeNotification()
        }
        binding.openN.setOnClickListener {
            StarrySky.openNotification()
        }
        binding.replay.setOnClickListener {
            StarrySky.with().replayCurrMusic()
        }

        StarrySky.with().setOnPlayProgressListener(object : OnPlayProgressListener {
            @SuppressLint("SetTextI18n")
            override fun onPlayProgress(currPos: Long, duration: Long) {
                if (binding.seekBarPro.max.toLong() != duration) {
                    binding.seekBarPro.max = duration.toInt()
                }
                binding.seekBarPro.progress = currPos.toInt()
                binding.tvPro.text = "进度：" + currPos.formatTime() + " / " + duration.formatTime()
            }
        })
        binding.seekBarPro.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                StarrySky.with().seekTo(seekBar.progress.toLong())
            }
        })

        binding.seekBarVolume.progress = (StarrySky.with().getVolume() * 100f).toInt()
        binding.tvVolume.text = "音量：" + binding.seekBarVolume.progress + " %"
        binding.seekBarVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                StarrySky.with().setVolume(progress.toFloat() / 100f)
                binding.tvVolume.text = "音量：$progress %"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        //seekBarSpeed配置最大速度是当前2倍
        binding.seekBarSpeed.progress = StarrySky.with().getPlaybackSpeed().toInt() * 100
        binding.tvSpeed.text = "音速：" + binding.seekBarSpeed.progress + " %"
        binding.seekBarSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                StarrySky.with().onDerailleur(false, progress.toFloat() / 100)
                binding.tvSpeed.text = "音速：$progress %"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        StarrySky.with().playbackState().observe(this) {
            when (it.stage) {
                PlaybackStage.PLAYING -> {
                    binding.seekBarVolume.progress = (StarrySky.with().getVolume() * 100f).toInt()
                    binding.tvVolume.text = "音量：" + binding.seekBarVolume.progress + " %"

                    binding.seekBarSpeed.progress = StarrySky.with().getPlaybackSpeed().toInt() * 100
                    binding.tvSpeed.text = "音速：" + binding.seekBarSpeed.progress + " %"
                }
                PlaybackStage.SWITCH -> {
                    showToast("切歌:last=" + it.lastSongInfo?.songName + " curr=" + it.songInfo?.songName)
                }
                PlaybackStage.IDLE -> {
                    binding.seekBarPro.progress = 0
                    binding.tvPro.text = "进度："
                    binding.seekBarVolume.progress = 0
                    binding.tvVolume.text = "音量："
                    binding.seekBarSpeed.progress = 0
                    binding.tvSpeed.text = "音速："
                }
            }
        }
    }

    private fun getRepeatModelImpl() {
        val mode = StarrySky.with().getRepeatMode()
        val result = when (mode.repeatMode) {
            RepeatMode.REPEAT_MODE_NONE -> "顺序播放"
            RepeatMode.REPEAT_MODE_ONE -> "单曲播放"
            RepeatMode.REPEAT_MODE_SHUFFLE -> "随机播放"
            RepeatMode.REPEAT_MODE_REVERSE -> "倒序播放"
            else -> ""
        }
        showToast("当前：$result")
    }

    private class InterceptorA : StarrySkyInterceptor() {
        override fun process(songInfo: SongInfo?, callback: InterceptCallback) {
            val isInMainThread = MainLooper.instance.isInMainThread()
            Log.i("TestActivity", "InterceptorA#isInMainThread = $isInMainThread")
            callback.onNext(songInfo)
        }

        override fun getTag(): String = "InterceptorA"
    }

    private class InterceptorB : StarrySkyInterceptor() {
        override fun process(songInfo: SongInfo?, callback: InterceptCallback) {
            val isInMainThread = MainLooper.instance.isInMainThread()
            Log.i("TestActivity", "InterceptorA#isInMainThread = $isInMainThread")
            callback.onNext(songInfo)
        }

        override fun getTag(): String = "InterceptorB"
    }
}