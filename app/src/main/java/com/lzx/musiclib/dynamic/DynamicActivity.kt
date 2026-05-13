package com.lzx.musiclib.dynamic

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import com.lzx.musiclib.R
import com.lzx.musiclib.databinding.ActivityDynamicBinding
import com.lzx.musiclib.dp
import com.lzx.musiclib.loadImage
import com.lzx.starrysky.StarrySky
import com.lzx.starrysky.manager.PlaybackStage

class DynamicActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDynamicBinding

    private var categoryList = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDynamicBinding.inflate(layoutInflater)
        setContentView(binding.root)
        categoryList.add("推荐")
        categoryList.add("最新")
        val adapter = DynamicCategoryAdapter(supportFragmentManager, categoryList)
        binding.viewpager.removeAllViews()
        binding.viewpager.removeAllViewsInLayout()
        binding.viewpager.adapter = adapter
        binding.tabLayout.setViewPager(binding.viewpager)

        StarrySky.closeNotification()
        StarrySky.setIsOpenNotification(false)

        StarrySky.with().playbackState().observe(this, {
            when (it.stage) {
                PlaybackStage.BUFFERING -> {
                    binding.btnPro.visibility = View.VISIBLE
                }
                PlaybackStage.PLAYING -> {
                    binding.userHeader.loadImage(it.songInfo?.songCover)
                    binding.songName.text = it.songInfo?.songName
                    binding.btnPro.visibility = View.GONE
                    binding.btnPlay.setImageResource(R.drawable.icon_dynamic_top_stop)
                    showVoiceBar()
                }
                PlaybackStage.ERROR,
                PlaybackStage.PAUSE,
                PlaybackStage.IDLE -> {
                    binding.btnPlay.setImageResource(R.drawable.icon_dynamic_top_play)
                }
            }
        })

        binding.btnPlay.setOnClickListener {
            if (StarrySky.with().isPlaying()) {
                StarrySky.with().pauseMusic()
            } else {
                StarrySky.with().restoreMusic()
            }
        }
        binding.btnNext.setOnClickListener {
            StarrySky.with().skipToNext()
        }
        binding.btnClose.setOnClickListener {
            StarrySky.with().stopMusic()
            hideVoiceBar()
        }
    }

    fun showVoiceBar() {
        if (binding.voiceBar.translationY == 0f) {
            return
        }
        val anim = ObjectAnimator.ofFloat(binding.voiceBar, "translationY", (-50f).dp, 0f)
        anim.duration = 500
        anim.interpolator = LinearInterpolator()
        anim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                super.onAnimationStart(animation)
                binding.voiceBar.visibility = View.VISIBLE
            }
        })
        anim.start()
    }

    private fun hideVoiceBar() {
        if (binding.voiceBar.translationY == -50f) {
            return
        }
        val anim = ObjectAnimator.ofFloat(binding.voiceBar, "translationY", 0f, (-50f).dp)
        anim.duration = 500
        anim.interpolator = LinearInterpolator()
        anim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                super.onAnimationEnd(animation)
                binding.voiceBar.visibility = View.GONE
            }
        })
        anim.start()
    }
}

class DynamicCategoryAdapter(
    fm: FragmentManager?,
    private var categoryList: MutableList<String>
) : FragmentStatePagerAdapter(fm!!) {

    private val fragmentMap = hashMapOf<String, Fragment>()

    override fun getItem(position: Int): Fragment {
        val category = categoryList[position]
        if (fragmentMap[category] != null) {
            return fragmentMap[category]!!
        }
        val fragment = DynamicFragment.newInstance(category)
        fragmentMap[category] = fragment
        return fragment
    }

    override fun getCount(): Int = categoryList.size

    override fun getPageTitle(position: Int): CharSequence? {
        return categoryList[position]
    }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        super.destroyItem(container, position, `object`)
        val category = categoryList[position]
        if (fragmentMap[category] != null) {
            fragmentMap.remove(category)
        }
    }

    var currFragment: DynamicFragment? = null

    override fun setPrimaryItem(container: ViewGroup, position: Int, obj: Any) {
        if (obj is DynamicFragment) {
            currFragment = obj
        }
        super.setPrimaryItem(container, position, obj)
    }
}
