package com.piyushpatnaik.stashkeeper.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Parcelable
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts

/**
 * The system photo picker, tinted with the StashKeeper green.
 * Android 17 can also shape the picker: tall on a phone, unrestricted on a wide screen.
 */
class StashImagePicker : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit): Intent {
        val intent = if (ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context)) {
            Intent(MediaStore.ACTION_PICK_IMAGES).apply { type = "image/*" }
        } else {
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
            }
        }
        intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_ACCENT_COLOR, Color.valueOf(0xFF0C6B52.toInt()).pack())
        if (Build.VERSION.SDK_INT >= 37) {
            runCatching {
                val builder = Class.forName("android.widget.photopicker.PhotoPickerUiCustomizationParams\$Builder")
                    .getDeclaredConstructor()
                    .newInstance()
                val wide = context.resources.configuration.smallestScreenWidthDp >= 600
                val ratio = if (wide) -1 else 1
                builder.javaClass.getMethod("setAspectRatio", Int::class.javaPrimitiveType).invoke(builder, ratio)
                val params = builder.javaClass.getMethod("build").invoke(builder) as Parcelable
                intent.putExtra("android.provider.extra.PICK_IMAGES_UI_CUSTOMIZATION_PARAMS", params)
            }
        }
        return intent
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        if (resultCode != Activity.RESULT_OK) return null
        return intent?.data
    }
}
