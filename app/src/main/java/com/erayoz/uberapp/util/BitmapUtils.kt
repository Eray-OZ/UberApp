package com.erayoz.uberapp.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory

object BitmapUtils {
    fun bitmapDescriptorFromVector(context: Context, vectorResId: Int): BitmapDescriptor? {
        return try {
            val drawable = ContextCompat.getDrawable(context, vectorResId) ?: return null
            
            val width = if (drawable.intrinsicWidth <= 0) 100 else drawable.intrinsicWidth
            val height = if (drawable.intrinsicHeight <= 0) 100 else drawable.intrinsicHeight
            
            drawable.setBounds(0, 0, width, height)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.draw(canvas)
            BitmapDescriptorFactory.fromBitmap(bitmap)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
