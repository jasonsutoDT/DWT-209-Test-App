package com.example.pdfrenderer
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Base64
import android.webkit.WebView
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URL

class MainActivity : AppCompatActivity() {
    private lateinit var pdfImageView: ImageView
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) { this.setShowWhenLocked(true); this.setTurnScreenOn(true) };
        setContentView(R.layout.activity_main)
        pdfImageView = findViewById(R.id.pdfImageView)
        webView = findViewById(R.id.webView)

        GlobalScope.launch(Dispatchers.IO) {
             //val gzUrl = "https://www.jumpskunk.com/test.pdf"
            //val gzUrl = "https://www.jumpskunk.com/adobe.pdf"
            //val gzUrl = "https://www.jumpskunk.com/adobe-zipped.gz"
            val gzUrl = "https://www.jumpskunk.com/test3.gz"
            var  pdfFile = downloadInsuranceFile(gzUrl)

            val bitmap = renderPdfPage(pdfFile)
            val base64Image: String = bitmapToBase64(bitmap)
            displayBitmapInWebView(base64Image)


        }

    }//end onCreate()


    private fun downloadInsuranceFile(gzUrl: String): File {
        val downloadedFileName = "temp3.pdf"
        val file = File(cacheDir, downloadedFileName)

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
            val fileSize = file.length()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return file
    }
    private fun renderPdfPage(pdfFile: File): Bitmap? {
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
        }
        pdfFile.delete()
        return bitmap
    }
    private suspend fun displayBitmapInWebView(base64Image: String) {
        withContext(Dispatchers.Main) {
            if (base64Image != null) {
                val finalWidth: Int=(resources.displayMetrics.widthPixels/resources.displayMetrics.density).toInt()
                //Log.e("metrics","wp:"+resources.displayMetrics.widthPixels+"  hp:"+ resources.displayMetrics.heightPixels + "    dp:"+resources.displayMetrics.densityDpi+"   d:"+resources.displayMetrics.density)

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


}