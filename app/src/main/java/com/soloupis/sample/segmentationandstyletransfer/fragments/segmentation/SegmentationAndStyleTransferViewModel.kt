package com.soloupis.sample.segmentationandstyletransfer.fragments.segmentation

import android.app.Application
import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenterResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.KoinComponent
import org.koin.core.get
import java.io.IOException


class SegmentationAndStyleTransferViewModel(application: Application) :
    AndroidViewModel(application),
    KoinComponent {

    private lateinit var imageSegmenter: ImageSegmenter
    private lateinit var scaledMaskBitmap: Bitmap
    var startTime: Long = 0L
    var inferenceTime = 0L
    lateinit var scaledBitmapObject: Bitmap

    var stylename = String()
    var seekBarProgress: Float = 0F

    private var _currentList: ArrayList<String> = ArrayList()
    val currentList: ArrayList<String>
        get() = _currentList

    private val _totalTimeInference = MutableLiveData<Int>()
    val totalTimeInference: LiveData<Int>
        get() = _totalTimeInference

    private val _styledBitmap = MutableLiveData<ModelExecutionResult>()
    val styledBitmap: LiveData<ModelExecutionResult>
        get() = _styledBitmap

    private val _inferenceDone = MutableLiveData<Boolean>()
    val inferenceDone: LiveData<Boolean>
        get() = _inferenceDone

    val styleTransferModelExecutor: StyleTransferModelExecutor

    // private val ALPHA_COLOR = 128
    /*private val labelColors = listOf(
        -16777216,
        -8388608,
        -16744448,
        -8355840,
        -16777088,
        -8388480,
        -16744320,
        -8355712,
        -12582912,
        -4194304,
        -12550144,
        -4161536,
        -12582784,
        -4194176,
        -12550016,
        -4161408,
        -16760832,
        -8372224,
        -16728064,
        -8339456,
        -16760704
    )*/

    init {

        stylename = "mona.JPG"

        _currentList.addAll(application.assets.list("thumbnails")!!)

        styleTransferModelExecutor = get()

    }

    fun setStyleName(string: String) {
        stylename = string
    }

    fun setTheSeekBarProgress(progress: Float) {
        seekBarProgress = progress
    }

    fun onApplyStyle(
        context: Context,
        contentBitmap: Bitmap,
        styleFilePath: String
    ) {

        viewModelScope.launch(Dispatchers.Default) {
            inferenceExecute(contentBitmap, styleFilePath, context)
        }
    }

    private fun inferenceExecute(
        contentBitmap: Bitmap,
        styleFilePath: String,
        context: Context
    ) {


        val result =
            styleTransferModelExecutor.executeWithMLBinding(contentBitmap, styleFilePath, context)

        _totalTimeInference.postValue(result.totalExecutionTime.toInt())
        _styledBitmap.postValue(result)
        _inferenceDone.postValue(true)
    }

    fun cropPersonFromPhoto(bitmap: Bitmap): Pair<Bitmap?, Long> {
        try {
            // Initialization
            // Task library implementation.
            /*val baseOptions = BaseOptions.builder().useGpu().build()
            val options =
                ImageSegmenter.ImageSegmenterOptions.builder()
                    .setOutputType(OutputType.CATEGORY_MASK)
                    .setBaseOptions(baseOptions)
                    .build()
            imageSegmenter =
                ImageSegmenter.createFromFileAndOptions(
                    getApplication(),
                    "deeplabv3.tflite",
                    options
                )*/
            // MediaPipe implementation.
            startTime = SystemClock.uptimeMillis()
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("deeplabv3.tflite")
                .setDelegate(Delegate.CPU)
                .build()
            val options =
                ImageSegmenter.ImageSegmenterOptions.builder()
                    .setOutputType(ImageSegmenter.ImageSegmenterOptions.OutputType.CATEGORY_MASK)
                    .setBaseOptions(baseOptions)
                    .setResultListener(this::returnLivestreamResult)
                    .setErrorListener(this::returnLivestreamError)
                    .build()
            imageSegmenter =
                ImageSegmenter.createFromOptions(
                    getApplication(),
                    options
                )

            // Run inference
            //val tensorImage = TensorImage.fromBitmap(bitmap)
            val image = BitmapImageBuilder(bitmap).build()
            imageSegmenter.segment(image)
            //Log.v("segmenter_image",imageSegmenterResult.)
            /*Log.i("LIST", results[0].toString())
            val result = results[0]
            val tensorMask = result.masks[0]
            Log.i("RESULT", result.coloredLabels.toString())
            val rawMask = tensorMask.tensorBuffer.intArray
            Log.i("NUMBER", rawMask.size.toString())
            Log.i("VALUES", rawMask.contentToString())

            val output = Bitmap.createBitmap(
                tensorMask.width,
                tensorMask.height,
                Bitmap.Config.ARGB_8888
            )
            for (y in 0 until tensorMask.height) {
                for (x in 0 until tensorMask.width) {
                    output.setPixel(
                        x,
                        y,
                        if (rawMask[y * tensorMask.width + x] == 0) Color.TRANSPARENT else Color.BLACK
                    )
                }
            }
            scaledMaskBitmap =
                Bitmap.createScaledBitmap(output, bitmap.getWidth(), bitmap.getHeight(), true)
            inferenceTime = SystemClock.uptimeMillis() - startTime*/
        } catch (e: IOException) {
            Log.e("ImageSegmenter", "Error: ", e)
        }

        return Pair(cropBitmapWithMask(bitmap, scaledMaskBitmap), inferenceTime)
    }

    // MPImage isn't necessary for this example, but the listener requires it
    private fun returnLivestreamResult(
        result: ImageSegmenterResult, image: MPImage
    ) {
        // We only need the first mask for this sample because we are using
        // the OutputType CATEGORY_MASK, which only provides a single mask.
        val mPImage = result.segmentations().first()
        val pixels = IntArray(ByteBufferExtractor.extract(mPImage).capacity())
        for (i in pixels.indices) {
            val index = ByteBufferExtractor.extract(mPImage).get(i).toInt()
            val color =
                if (index in 1..20) Color.BLACK else Color.TRANSPARENT
            pixels[i] = color
        }
        val imageFromPaul = Bitmap.createBitmap(
            pixels,
            mPImage.width,
            mPImage.height,
            Bitmap.Config.ARGB_8888
        )
        scaledMaskBitmap = imageFromPaul
        inferenceTime = SystemClock.uptimeMillis() - startTime
        Log.v("inference", inferenceTime.toString())
    }

    /*private fun Int.toColor(): Int {
        return Color.argb(
            ALPHA_COLOR, Color.red(this), Color.green(this), Color.blue(this)
        )
    }*/

    /*fun setResults(
        byteBuffer: ByteBuffer,
        outputWidth: Int,
        outputHeight: Int
    ) {
        // Create the mask bitmap with colors and the set of detected labels.
        val pixels = IntArray(byteBuffer.capacity())
        for (i in pixels.indices) {
            val index = byteBuffer.get(i).toInt()
            val color =
                if (index in 1..20) labelColors[index].toColor() else Color.TRANSPARENT
            pixels[i] = color
        }
        val image = Bitmap.createBitmap(
            pixels,
            outputWidth,
            outputHeight,
            Bitmap.Config.ARGB_8888
        )

        val scaleFactor = when (runningMode) {
            RunningMode.IMAGE,
            RunningMode.VIDEO -> {
                min(width * 1f / outputWidth, height * 1f / outputHeight)
            }
            RunningMode.LIVE_STREAM -> {
                // PreviewView is in FILL_START mode. So we need to scale up the
                // landmarks to match with the size that the captured images will be
                // displayed.
                max(width * 1f / outputWidth, height * 1f / outputHeight)
            }
        }

        val scaleWidth = (outputWidth * scaleFactor).toInt()
        val scaleHeight = (outputHeight * scaleFactor).toInt()

        scaleBitmap = Bitmap.createScaledBitmap(
            image, scaleWidth, scaleHeight, false
        )
        invalidate()
    }*/

    // Return errors thrown during segmentation to this
    // ImageSegmenterHelper's caller
    private fun returnLivestreamError(error: RuntimeException) {
        /*imageSegmenterListener?.onError(
            error.message ?: "An unknown error has occurred"
        )*/
    }


    fun cropBitmapWithMask(original: Bitmap, mask: Bitmap?): Bitmap? {
        if (mask == null
        ) {
            return null
        }
        Log.i("ORIGINAL_WIDTH", original.width.toString())
        Log.i("ORIGINAL_HEIGHT", original.height.toString())
        Log.i("MASK_WIDTH", original.width.toString())
        Log.i("MASK_HEIGHT", original.height.toString())
        val w = original.width
        val h = original.height
        if (w <= 0 || h <= 0) {
            return null
        }
        val cropped: Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Log.i("CROPPED_WIDTH", cropped.width.toString())
        Log.i("CROPPED_HEIGHT", cropped.height.toString())
        val canvas = Canvas(cropped)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        canvas.drawBitmap(original, 0f, 0f, null)
        canvas.drawBitmap(mask, 0f, 0f, paint)
        paint.xfermode = null

        return cropped
    }

    fun cropBitmapWithMaskForStyle(original: Bitmap, mask: Bitmap?): Bitmap? {
        if (mask == null
        ) {
            return null
        }
        val w = original.width
        val h = original.height
        if (w <= 0 || h <= 0) {
            return null
        }

        val scaledBitmap = Bitmap.createScaledBitmap(
            mask,
            w,
            h, true
        )

        val cropped = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(cropped)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
        canvas.drawBitmap(original, 0f, 0f, null)
        canvas.drawBitmap(scaledBitmap, 0f, 0f, paint)
        paint.xfermode = null
        return cropped
    }

    override fun onCleared() {
        super.onCleared()
        styleTransferModelExecutor.close()
    }

}