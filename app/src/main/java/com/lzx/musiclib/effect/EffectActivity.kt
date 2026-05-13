package com.lzx.musiclib.effect

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.customview.customView
import com.afollestad.materialdialogs.customview.getCustomView
import com.lzx.musiclib.R
import com.lzx.musiclib.adapter.addItem
import com.lzx.musiclib.adapter.itemClicked
import com.lzx.musiclib.adapter.setText
import com.lzx.musiclib.adapter.setup
import com.lzx.musiclib.databinding.ActivityEffectBinding
import com.lzx.musiclib.weight.SpectrumDrawView
import com.lzx.starrysky.StarrySky
import com.lzx.starrysky.control.equalizerPresetName
import com.sdsmdg.harjot.crollerTest.Croller
import com.sdsmdg.harjot.crollerTest.OnCrollerChangeListener
import kotlin.math.roundToInt

class EffectActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEffectBinding

    private val allPresetName = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEffectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        StarrySky.saveEffectConfig(true)
        val effectSwitch = StarrySky.getEffectSwitch()
        binding.swEnable.isChecked = effectSwitch
        if (effectSwitch) {
            StarrySky.effect().attachAudioEffect(StarrySky.with().getAudioSessionId())
            binding.equalizerBands.initData()
            initBassCroller()
            initVirtualizerCroller()
        }

        //获取均衡器支持的预设总数
        val numberOfPresets = StarrySky.effect().equalizerNumberOfPresets()
        //获取当前的预设
        val currentPreset = StarrySky.effect().equalizerCurrentPreset()

        allPresetName.clear()
        allPresetName.add("自定义")
        for (preset in 0 until numberOfPresets) {
            //获取预设名字
            val presetName = StarrySky.effect().equalizerPresetName(preset.toShort())
            allPresetName.add(presetName.equalizerPresetName())
        }
        binding.frameLayout.text = allPresetName.getOrNull(currentPreset + 1)

        binding.frameLayout.setOnClickListener {
            showEffectDialog()
        }

        binding.swEnable.setOnCheckedChangeListener { _, isChecked ->
            StarrySky.effectSwitch(isChecked)

            if (isChecked) {
                StarrySky.effect().attachAudioEffect(StarrySky.with().getAudioSessionId())
                binding.equalizerBands.initData()
                initBassCroller()
                initVirtualizerCroller()
                val preset = StarrySky.effect().equalizerCurrentPreset()
                binding.frameLayout.text = allPresetName.getOrNull(preset + 1)
            } else {
                StarrySky.effect().attachAudioEffect(0)
            }
            binding.frameLayout.isEnabled = isChecked
            binding.equalizerBands.isEnabled = isChecked
            binding.bassCroller.isEnabled = isChecked
            binding.virtualizerCroller.isEnabled = isChecked
        }
    }

    private var dialog: MaterialDialog? = null

    /**
     * 音效选择弹窗
     */
    private fun showEffectDialog() {
        dialog = MaterialDialog(this, BottomSheet(LayoutMode.WRAP_CONTENT)).show {
            title(R.string.dialog_title2)
            customView(R.layout.dialog_song_list, scrollable = true, horizontalPadding = true)
        }
        val customView = dialog?.getCustomView()
        val recycleView: RecyclerView? = customView?.findViewById(R.id.recycleView)
        val indexBtn: Button? = customView?.findViewById(R.id.indexBtn)
        indexBtn?.visibility = View.GONE

        recycleView?.setup<String> {
            dataSource(allPresetName)
            adapter {
                addItem(R.layout.item_dialog_song_list) {
                    bindViewHolder { data, position, holder ->
                        val btnClose = holder.findViewById<ImageView>(R.id.btnClose)
                        val imgAnim = holder.findViewById<SpectrumDrawView>(R.id.imgAnim)
                        btnClose.visibility = View.GONE
                        imgAnim.visibility = View.GONE
                        setText(R.id.songName to data)
                        itemClicked {
                            if (position == 0) {
                                binding.frameLayout.text = data
                                dialog?.dismiss()
                                return@itemClicked
                            }
                            StarrySky.effect().equalizerUsePreset((position - 1).toShort())
                            StarrySky.effect().applyChanges()
                            binding.equalizerBands.notifyEqualizerSettingChanged()
                            binding.frameLayout.text = data
                            dialog?.dismiss()
                        }
                    }
                }
            }
        }
    }

    private fun initBassCroller() {
        val strength = StarrySky.effect().bassBoostRoundedStrength()
        val percent = strength * 1.0 / 1000

        // 因为 Croller 无法滑动到 0，因此将 min 设为 1，将 max 设置为 26
        val rangeSize = 25
        val min = 1
        val max = min + rangeSize
        binding.bassCroller.min = min
        binding.bassCroller.max = max
        binding.bassCroller.progress = min + (percent * rangeSize).roundToInt()
        binding.bassCroller.setOnCrollerChangeListener(object : OnCrollerChangeListener {
            override fun onProgressChanged(croller: Croller, progress: Int) {
                val strength = ((progress - min) * (1000 / rangeSize)).toShort()
                StarrySky.effect().bassBoostStrength(strength)
            }

            override fun onStartTrackingTouch(croller: Croller) {
                // ignore
            }

            override fun onStopTrackingTouch(croller: Croller) {
                StarrySky.effect().applyChanges()
            }
        })
    }

    private fun initVirtualizerCroller() {
        val strength = StarrySky.effect().virtualizerStrength()
        val percent = strength * 1.0 / 1000

        // 因为 Croller 无法滑动到 0，因此将 min 设为 1，将 max 设置为 26
        val rangeSize = 25
        val min = 1
        val max = min + rangeSize
        binding.virtualizerCroller.min = min
        binding.virtualizerCroller.max = max
        binding.virtualizerCroller.progress = min + (percent * binding.virtualizerCroller.max).toInt()
        binding.virtualizerCroller.setOnCrollerChangeListener(object : OnCrollerChangeListener {
            override fun onProgressChanged(croller: Croller, progress: Int) {
                val strength = ((progress - min) * (1000 / rangeSize)).toShort()
                StarrySky.effect().virtualizerStrength(strength)
            }

            override fun onStartTrackingTouch(croller: Croller) {
                // ignore
            }

            override fun onStopTrackingTouch(croller: Croller) {
                StarrySky.effect().applyChanges()
            }
        })
    }
}
