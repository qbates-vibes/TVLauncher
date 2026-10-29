package com.simple.tvlauncher.data

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputManager

data class TvInput(val id: String, val label: String)

object TvInputs {
    /** Physical inputs (HDMI, composite, ...) the TV reports. Empty on devices without live TV support. */
    fun list(context: Context): List<TvInput> {
        val manager = context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager ?: return emptyList()
        return try {
            manager.tvInputList
                // parentId != null means a device plugged into a port (HDMI-CEC); show the port itself.
                .filter { it.isPassthroughInput && it.parentId == null }
                .map { TvInput(it.id, (it.loadCustomLabel(context) ?: it.loadLabel(context)).toString()) }
                .sortedBy { it.label }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Asks the TV's built-in live TV app to switch to this input. */
    fun open(context: Context, input: TvInput): Boolean =
        startSafely(context, Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(input.id)))
}
