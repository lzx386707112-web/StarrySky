package com.lzx.musiclib.home

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.animation.LinearInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.lzx.musiclib.weight.RoundedImageView
import com.lzx.musiclib.R
import com.lzx.musiclib.adapter.addItem
import com.lzx.musiclib.adapter.itemClicked
import com.lzx.musiclib.adapter.setText
import com.lzx.musiclib.adapter.setup
import com.lzx.musiclib.card.CardActivity
import com.lzx.musiclib.dynamic.DynamicActivity
import com.lzx.musiclib.getSelfViewModel
import com.lzx.musiclib.loadImage
import com.lzx.musiclib.navigationTo
import com.lzx.musiclib.showToast
import com.lzx.musiclib.user.UserInfoActivity
import com.lzx.musiclib.viewmodel.MusicViewModel
import com.lzx.starrysky.OnPlayProgressListener
import com.lzx.starrysky.SongInfo
import com.lzx.starrysky.StarrySky
import com.lzx.starrysky.manager.PlaybackStage
import com.lzx.musiclib.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var viewModel: MusicViewModel? = null
    private var rotationAnim: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.songCover.loadImage("http://img01.jituwang.com/190613/256558-1Z613225P691.jpg")
        rotationAnim = ObjectAnimator.ofFloat(binding.songCover, "rotation", 0f, 359f)
        rotationAnim?.interpolator = LinearInterpolator()
        rotationAnim?.duration = 20000
        rotationAnim?.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                super.onAnimationEnd(animation)
                rotationAnim?.start()
            }
        })

        viewModel = getSelfViewModel {
            val list = getHomeMusic()
            initRecycleView(list)
        }

        StarrySky.with().playbackState().observe(this) {
            if (it.songInfo?.tag != "home") return@observe
            when (it.stage) {
                PlaybackStage.PLAYING -> {
                    rotationAnim?.start()
                    binding.songCover.loadImage(it.songInfo?.songCover)
                }
                PlaybackStage.IDLE,
                PlaybackStage.ERROR,
                PlaybackStage.PAUSE -> {
                    rotationAnim?.cancel()
                    if (it.stage == PlaybackStage.ERROR) {
                        showToast("播放失败，请查看log了解原因")
                    }
                }
            }
        }
        StarrySky.with().setOnPlayProgressListener(object : OnPlayProgressListener {
            override fun onPlayProgress(currPos: Long, duration: Long) {
                val info = StarrySky.with().getNowPlayingSongInfo()
                if (info?.tag != "home") return
                if (binding.donutProgress.getMax().toLong() != duration) {
                    binding.donutProgress.setMax(duration.toInt())
                }
                binding.donutProgress.setProgress(currPos.toFloat())
            }
        })
        binding.songCover.setOnClickListener {
            StarrySky.with().getNowPlayingSongInfo()?.let {
                navigationTo<PlayDetailActivity>("songId" to it.songId)
            }
        }
        binding.card.setOnClickListener {
            navigationTo<CardActivity>()
        }
        binding.dynamic.setOnClickListener {
            StarrySky.with().stopMusic()
            StarrySky.closeNotification()
            navigationTo<DynamicActivity>()
        }
        binding.user.setOnClickListener {
            navigationTo<UserInfoActivity>()
        }
    }

    private fun initRecycleView(list: MutableList<SongInfo>) {
        binding.recycleView.setup<SongInfo> {
            dataSource(list)
            adapter {
                addItem(R.layout.item_home_music) {
                    bindViewHolder { info, position, holder ->
                        val icon = holder.findViewById<RoundedImageView>(R.id.cover)
                        icon.loadImage(info?.songCover)
                        setText(R.id.title to info?.songName, R.id.desc to info?.songName)
                        itemClicked {
                            StarrySky.with().playMusic(list, position)
                            navigationTo<PlayDetailActivity>("songId" to info?.songId)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        StarrySky.setIsOpenNotification(true)
        if (StarrySky.with().isPlaying()) {
            val info = StarrySky.with().getNowPlayingSongInfo()
            if (info?.tag == "home") {
                rotationAnim?.start()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        rotationAnim?.cancel()
        rotationAnim?.removeAllListeners()
        rotationAnim = null
    }
}
