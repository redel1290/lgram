package com.lgram.app.ui

import android.content.Context
import android.view.LayoutInflater
import android.widget.LinearLayout
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.lgram.app.databinding.ItemActionRowBinding

data class ActionItem(
    val icon: String,
    val label: String,
    val destructive: Boolean = false,
    val onClick: () -> Unit
)

/** Контекстне меню у вигляді bottom sheet з іконками — заміна простого
 *  AlertDialog.setItems(), яке виглядало як список із початку 2000-х. */
object ActionSheet {
    fun show(context: Context, actions: List<ActionItem>) {
        val dialog = BottomSheetDialog(context)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 24)
        }
        val inflater = LayoutInflater.from(context)
        actions.forEach { action ->
            val rowBinding = ItemActionRowBinding.inflate(inflater, container, false)
            rowBinding.tvIcon.text = action.icon
            rowBinding.tvLabel.text = action.label
            if (action.destructive) {
                rowBinding.tvLabel.setTextColor(0xFFE8503A.toInt())
            }
            rowBinding.root.setOnClickListener {
                dialog.dismiss()
                action.onClick()
            }
            container.addView(rowBinding.root)
        }
        dialog.setContentView(container)
        dialog.show()
    }
}
