package com.example.pdfrenderer

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Base64
import android.util.Log
import android.webkit.WebView
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xml.sax.InputSource
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URL
import java.nio.BufferOverflowException
import java.util.zip.GZIPInputStream


class MainActivity : AppCompatActivity() {
    private lateinit var pdfImageView: ImageView
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) { this.setShowWhenLocked(true); this.setTurnScreenOn(true) };
        setContentView(R.layout.activity_main)
        Log.e("OnCreate","entered")
        pdfImageView = findViewById(R.id.pdfImageView)
        webView = findViewById(R.id.webView)

        GlobalScope.launch(Dispatchers.IO) {
            val pdfUrl = "https://www.jumpskunk.com/test.pdf"
            val gzUrl = "https://www.jumpskunk.com/test.gz"
            val pdfFile: File

            val mode=0

            if(mode==0){ //direct pdf download
                Log.e("onCreate","GZIPPED FALSE")
                 pdfFile = downloadInsuranceFile(pdfUrl)
            }
            else if (mode==1){ //gzip extract method 1
                Log.e("onCreate","GZIPPED TRUE")
                val gzFile = downloadInsuranceFile(gzUrl)

                Log.e("onCreate","Entering GZIP")
                pdfFile =unzipGzipFile(gzFile)
                Log.e("onCreate","Exiting GZIP")
            }
            else{ //gzip extract method 2
                pdfFile =DownloadAndUnzip(gzUrl)
            }

            val bitmap = renderPdfPage(pdfFile)
            val base64Image: String = bitmapToBase64(bitmap)
            displayBitmapInWebView(base64Image)

        }

    }//end onCreate()

    fun unzipGzipFile(gzipFile: File) :File {
        Log.e("unzipGzipFile","Entered")
        val outputFile = File(cacheDir, "temp.pdf")
        try {
            val gzipInputStream = GZIPInputStream(FileInputStream(gzipFile))

            val outputFile = File(cacheDir, "temp.pdf")
            val outputStream = FileOutputStream(outputFile)
            val buffer = ByteArray(1024)
            var bytesRead: Int

            while (gzipInputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }

            gzipInputStream.close()
            outputStream.close()

           Log.e("unzipGzipFile","Success")
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e("unzipGzipFile","Failed:"+e.toString())

        }
        Log.e("unzipGzipFile","Exiting")
        return outputFile
    }
    private suspend fun downloadInsuranceFile(gzUrl: String): File {
        Log.e("downloadGZFile","entered")
        val pdfFileName = "temp.gz"
        val file = File(cacheDir, pdfFileName)

        try {
            val url = URL(gzUrl)
            val connection = url.openConnection()
            connection.connect()

            val inputStream: InputStream = connection.getInputStream()
            val outputStream = FileOutputStream(file)

            val buffer = ByteArray(1024)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }

            outputStream.close()
            inputStream.close()
            Log.e("downloadGZFile","Success")
        } catch (e: IOException) {
            Log.e("downloadGZFile","exception")

            e.printStackTrace()
        }

        return file
    }
    private fun renderPdfPage(pdfFile: File): Bitmap? {
        Log.e("renderPdfPage","entered")
        var bitmap: Bitmap? = null
        try {
            val fileDescriptor: ParcelFileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val pdfRenderer = PdfRenderer(fileDescriptor)
            val page: PdfRenderer.Page = pdfRenderer.openPage(0)
            val width: Int = page.width*3;           val height: Int=page.height*3
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            page.close()
            pdfRenderer.close()
            fileDescriptor.close()
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e("renderPdfPage","exception:"+e.toString())
        }
        pdfFile.delete()
        return bitmap
    }
    private suspend fun displayBitmapInImageView(bitmap: Bitmap?) {
        withContext(Dispatchers.Main) {
            if (bitmap != null) {
                Log.e("displayBitmap","bitmap not null")
                pdfImageView.setImageBitmap(bitmap)
            }
            else Log.e("displayBitmap","pdf is NULL")
        }
    }

    private suspend fun displayBitmapInWebView(base64Image: String) {
        withContext(Dispatchers.Main) {
            if (base64Image != null) {
                val finalWidth: Int=(resources.displayMetrics.widthPixels/resources.displayMetrics.density).toInt()
                Log.e("metrics","wp:"+resources.displayMetrics.widthPixels+"  hp:"+ resources.displayMetrics.heightPixels + "    dp:"+resources.displayMetrics.densityDpi+"   d:"+resources.displayMetrics.density)

                val htmlContent = """
                    <html>
                    <body>
                    <img src="data:image/png;base64,$base64Image" alt="Bitmap Image" width="$finalWidth" >
                    </body>
                    </html>
                """.trimIndent()

                webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
                webView.getSettings().setLoadWithOverviewMode(true);
            }
        }
    }


    private fun bitmapToBase64(bitmap: Bitmap?): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        if (bitmap != null) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
        }
        val byteArray: ByteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }
    protected fun DownloadAndUnzip(vararg sUrl: String?) :File {
        val pdfFileName = "temp.pdf"
        val file = File(cacheDir, pdfFileName)
        try {
            val url = URL(sUrl[0])
            val connection = url.openConnection()
            var stream = connection.getInputStream()
            stream = GZIPInputStream(stream)
            val `is` = InputSource(stream)
            val input: InputStream = BufferedInputStream(`is`.byteStream)

            val output: OutputStream = FileOutputStream(file)


            val data = ByteArray(2097152)
            var total: Long = 0
            var count: Int
            while (input.read(data).also { count = it } != -1) {
                total += count.toLong()
                output.write(data, 0, count)
            }
            output.flush()
            output.close()
            input.close()
        } catch (e: BufferOverflowException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()

        }
        return file
    }


}