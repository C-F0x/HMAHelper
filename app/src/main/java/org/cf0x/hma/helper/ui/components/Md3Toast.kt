package org.cf0x.hma.helper.ui.components

import android.content.Context
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import org.cf0x.hma.helper.R

/**
 * Material3-styled toast: rounded pill with a dark surface, instead of the
 * legacy square system toast. Call on a thread with a Looper (main thread).
 */
object Md3Toast {

    fun show(context: Context, text: String, long: Boolean = false) {
        val view = LayoutInflater.from(context).inflate(R.layout.toast_md3, null)
        view.findViewById<TextView>(R.id.toast_text).text = text
        Toast(context).apply {
            duration = if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
            this.view = view
            show()
        }
    }
}
