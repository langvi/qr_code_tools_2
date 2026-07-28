package com.aifeii.qrcode.tools

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.NonNull
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.io.File
import java.io.FileInputStream
import java.util.*
import kotlin.collections.ArrayList

class QrCodeToolsPlugin : FlutterPlugin, MethodCallHandler {

    /// The MethodChannel that will the communication between Flutter and native Android
    ///
    /// This local reference serves to register the plugin with the Flutter Engine and unregister it
    /// when the Flutter Engine is detached from the Activity
    private lateinit var channel: MethodChannel

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "qr_code_tools")
        channel.setMethodCallHandler(this)
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        if (call.method == "decoder") {
            val filePath = call.argument<String>("file")
            val file = File(filePath)
            if (!file.exists()) {
                result.error("File not found. filePath: $filePath", null, null)
                return
            }

            val bitmap = decodeSampledBitmap(file, MAX_DECODE_DIMENSION, MAX_DECODE_DIMENSION)
            if (bitmap == null) {
                result.error("Unable to decode image. filePath: $filePath", null, null)
                return
            }

            val w = bitmap.width
            val h = bitmap.height
            val pixels = IntArray(w * h)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
            bitmap.recycle()
            val source = RGBLuminanceSource(w, h, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

            val hints = Hashtable<DecodeHintType, Any>()
            val decodeFormats = ArrayList<BarcodeFormat>()
            decodeFormats.add(BarcodeFormat.QR_CODE)
            hints[DecodeHintType.POSSIBLE_FORMATS] = decodeFormats
            hints[DecodeHintType.CHARACTER_SET] = "utf-8"
            hints[DecodeHintType.TRY_HARDER] = true

            try {
                val decodeResult = MultiFormatReader().decode(binaryBitmap, hints)
                result.success(decodeResult.text)
            } catch (e: NotFoundException) {
                result.error("Not found data", null, null)
            }
        } else {
            result.notImplemented()
        }
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    /**
     * Decodes [file] into a Bitmap downsampled so neither dimension exceeds [reqWidth] x
     * [reqHeight], avoiding loading the image at full resolution into memory.
     */
    private fun decodeSampledBitmap(file: File, reqWidth: Int, reqHeight: Int): Bitmap? {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        FileInputStream(file).use { BitmapFactory.decodeStream(it, null, boundsOptions) }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(boundsOptions, reqWidth, reqHeight)
        }
        return FileInputStream(file).use { BitmapFactory.decodeStream(it, null, decodeOptions) }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    companion object {
        // QR codes decode reliably well below full camera/gallery resolution; capping the
        // decoded bitmap here keeps memory usage bounded (per the BitmapFactory.Options
        // guidance) without hurting decode accuracy.
        private const val MAX_DECODE_DIMENSION = 1920
    }

}
