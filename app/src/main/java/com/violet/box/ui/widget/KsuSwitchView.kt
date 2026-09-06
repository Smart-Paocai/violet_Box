package com.violet.box.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import com.violet.box.ui.component.material.ExpressiveSwitch

/** Java 侧使用的布尔回调（SAM，可用 lambda）。 */
fun interface OnCheckedChange {
    fun accept(checked: Boolean)
}

/**
 * XML 侧可用的 KernelSU ExpressiveSwitch 封装。
 * 内部用 ComposeView 承载与 KernelSU 管理器完全一致的 Material 3 Expressive 开关。
 */
class KsuSwitchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val checkedState = mutableStateOf(false)
    private val enabledState = mutableStateOf(true)
    private var listener: OnCheckedChange? = null

    init {
        val compose = ComposeView(context)
        // 组合随 Activity 生命周期销毁，RecyclerView 回收复用时仅更新状态，不再销毁重建
        compose.setViewCompositionStrategy(
            androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        addView(compose, FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        compose.setContent {
            MaterialTheme {
                ExpressiveSwitch(
                    checked = checkedState.value,
                    onCheckedChange = { value ->
                        checkedState.value = value
                        listener?.accept(value)
                    },
                    enabled = enabledState.value
                )
            }
        }
    }

    /** 编程式设置，不触发监听器（供回收复用/初始化时同步状态）。 */
    fun setChecked(checked: Boolean) {
        checkedState.value = checked
    }

    fun isChecked(): Boolean = checkedState.value

    fun setOnCheckedChange(l: OnCheckedChange?) {
        listener = l
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        enabledState.value = enabled
    }
}
