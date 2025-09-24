package com.mv.floatingbuttonapp.screen

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 화면 캡처를 관리하는 클래스
 * MediaProjection API를 사용하여 화면을 캡처합니다.
 */
class ScreenCaptureManager(private val context: Context) {

    companion object {
        private const val TAG = "ScreenCaptureManager"
        private const val VIRTUAL_DISPLAY_NAME = "ScreenCapture"
        private const val VIRTUAL_DISPLAY_DPI = 160
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    /**
     * 화면 캡처를 시작합니다.
     * @param resultCode Activity의 result code
     * @param data Activity의 intent data
     * @return 캡처된 비트맵
     */
    suspend fun captureScreen(resultCode: Int, data: Intent): Bitmap {
        return suspendCancellableCoroutine { continuation ->
            try {
                setupMediaProjection(resultCode, data)
                val bitmap = captureScreenInternal()
                continuation.resume(bitmap)
            } catch (e: Exception) {
                Log.e(TAG, "화면 캡처 실패", e)
                continuation.resumeWithException(e)
            }
            
            continuation.invokeOnCancellation {
                cleanup()
            }
        }
    }

    /**
     * MediaProjection을 설정합니다.
     */
    private fun setupMediaProjection(resultCode: Int, data: Intent) {
        val mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
    }

    /**
     * 실제 화면 캡처를 수행합니다.
     */
    private fun captureScreenInternal(): Bitmap {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)

        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels

        // ImageReader 생성
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 1)

        // VirtualDisplay 생성
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            VIRTUAL_DISPLAY_NAME,
            width, height, VIRTUAL_DISPLAY_DPI,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )

        // 화면 캡처 대기 (약간의 지연 필요)
        Thread.sleep(100)

        // 이미지 읽기
        val image = imageReader?.acquireLatestImage()
        return if (image != null) {
            convertImageToBitmap(image)
        } else {
            throw RuntimeException("화면 캡처 실패")
        }
    }

    /**
     * Image를 Bitmap으로 변환합니다.
     */
    private fun convertImageToBitmap(image: Image): Bitmap {
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        image.close()

        return bitmap
    }

    /**
     * 리소스를 정리합니다.
     */
    fun cleanup() {
        try {
            virtualDisplay?.release()
            imageReader?.close()
            mediaProjection?.stop()
            
            virtualDisplay = null
            imageReader = null
            mediaProjection = null
        } catch (e: Exception) {
            Log.e(TAG, "리소스 정리 중 오류", e)
        }
    }
}
