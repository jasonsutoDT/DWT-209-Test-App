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
    private val tempFile="temp.pdf"
    private var  allowCaching=false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) { this.setShowWhenLocked(true); this.setTurnScreenOn(true) };
        setContentView(R.layout.activity_main)
        pdfImageView = findViewById(R.id.pdfImageView)
        webView = findViewById(R.id.webView)
        //val gzUrl = "https://www.jumpskunk.com/adobe8.gz"
        val gzUrl = "https://www.jumpskunk.com/ins.gz"

        GlobalScope.launch(Dispatchers.IO) {

            var  pdfFile = downloadInsuranceFile(gzUrl)
            val bitmaps = renderMultiplePdfPages(pdfFile)
            val base64Images  = bitmapsToBase64(bitmaps)
            displayBitmapsInWebView(base64Images)

        }
    }//end onCreate()

    private fun downloadInsuranceFile(gzUrl: String): File {
       // val downloadedFileName = "temp3.pdf"
        val file = File(cacheDir, tempFile)

       if(!allowCaching&& file.exists()) file.delete()

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

    private fun renderMultiplePdfPages(pdfFile: File): List<Bitmap>? {
        var bitmap: Bitmap? = null
        val bitmapList = mutableListOf<Bitmap>()
        try {
            val fileDescriptor: ParcelFileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val pdfRenderer = PdfRenderer(fileDescriptor)

            for (i in 1..pdfRenderer.pageCount) {
                //todo: get all pages, return LISTOF bitmaps
                val page: PdfRenderer.Page = pdfRenderer.openPage(i-1)
                //The multiplyer determines final image resolution. "1" is too blurry when zoomed, 10 creates a huge image. 3 seems good.
                val resolutionMult=3
                val width: Int = page.width*resolutionMult;           val height: Int=page.height*resolutionMult
                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                bitmapList.add(bitmap)
                page.close()

            }
            pdfRenderer.close()
            fileDescriptor.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return bitmapList
    }

    private fun bitmapsToBase64(bitmapList: List<Bitmap>?): List<String> {
        val stringList=  mutableListOf<String>()

        if (bitmapList != null) {
            for (bitmap in bitmapList) {
                val byteArrayOutputStream = ByteArrayOutputStream()
                if (bitmap != null) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 60, byteArrayOutputStream)
                    bitmap.recycle()
                }
                val byteArray: ByteArray = byteArrayOutputStream.toByteArray()
                stringList.add(Base64.encodeToString(byteArray, Base64.DEFAULT))
            }
        }
        return stringList
    }

    private suspend fun displayBitmapsInWebView(base64ImageList: List<String>) {
        withContext(Dispatchers.Main) {
            if (base64ImageList != null) {
                val finalWidth: Int=(resources.displayMetrics.widthPixels/resources.displayMetrics.density).toInt()
                //Can use stringbuilder if you wish
                var htmlMiddle: String=""
                for (imageString in base64ImageList) {
                    htmlMiddle+= """
                    <img src="data:image/png;base64,$imageString" alt="Bitmap Image" width="$finalWidth" ><br>
                    """.trimIndent()
                }
                val htmlContent=     """
                    <html>
                    <body>$htmlMiddle</body>
                    </html>
                   """.trimIndent()


                webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
                webView.getSettings().setLoadWithOverviewMode(true);
            }
        }
    }

}