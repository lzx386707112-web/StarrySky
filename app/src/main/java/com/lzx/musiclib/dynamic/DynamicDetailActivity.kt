package com.lzx.musiclib.dynamic

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.os.Bundle
import android.view.animation.LinearInterpolator
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.lzx.musiclib.databinding.ActiviityDynamicDetailBinding
import com.lzx.musiclib.loadImage
import com.lzx.starrysky.OnPlayProgressListener
import com.lzx.starrysky.SongInfo
import com.lzx.starrysky.StarrySky
import com.lzx.starrysky.manager.PlaybackStage
import com.lzx.starrysky.utils.formatTime

class DynamicDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActiviityDynamicDetailBinding

    private var from: String? = null
    private var songInfo: SongInfo? = null
    private var rotationAnim: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActiviityDynamicDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        from = intent.getStringExtra("from")
        songInfo = intent.getParcelableExtra("songInfo")

        if (from == "dynamic") {
            StarrySky.closeNotification()
        }

        binding.cover.loadImage(songInfo?.songCover)
        binding.songName.text = songInfo?.songName

        rotationAnim = ObjectAnimator.ofFloat(binding.cover, "rotation", 0f, 359f)
        rotationAnim?.interpolator = LinearInterpolator()
        rotationAnim?.duration = 20000
        rotationAnim?.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                super.onAnimationEnd(animation)
                rotationAnim?.start()
            }
        })
        rotationAnim?.start()

        StarrySky.with().playbackState().observe(this, {
            if (it.stage == PlaybackStage.IDLE && !it.isStop) {
                //重播
                StarrySky.with()
                    .skipMediaQueue(true)
                    .playMusicByInfo(songInfo)
            }
        })
        StarrySky.with().setOnPlayProgressListener(object : OnPlayProgressListener {
            @SuppressLint("SetTextI18n")
            override fun onPlayProgress(currPos: Long, duration: Long) {
                if (binding.seekBar.max.toLong() != duration) {
                    binding.seekBar.max = duration.toInt()
                }
                binding.seekBar.progress = currPos.toInt()
                binding.progressText.text = currPos.formatTime()
                binding.timeText.text = " / " + duration.formatTime()
            }
        })
        //进度SeekBar
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                StarrySky.with().seekTo(seekBar.progress.toLong(), true)
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        rotationAnim?.cancel()
        rotationAnim?.removeAllListeners()
        rotationAnim = null
    }
}
