package com.example.livegps.ui.components

import android.content.Context
import android.widget.Toast

/**
 * Shows a brief "Coming soon" toast. Used for controls that are intentionally
 * not implemented yet — so they give clear feedback instead of doing nothing
 * (or crashing on a half-built feature).
 */
fun Context.comingSoon() {
    Toast.makeText(this, "Coming soon", Toast.LENGTH_SHORT).show()
}
