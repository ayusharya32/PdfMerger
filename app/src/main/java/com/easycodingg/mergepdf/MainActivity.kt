package com.easycodingg.mergepdf

import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfDocument.PageInfo
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

private const val A4_WIDTH = 595
private const val A4_HEIGHT = 842

class MainActivity : AppCompatActivity() {

    private var selectedPdfNameList: MutableList<String> = mutableListOf()
    private var selectedPdfUriList: MutableList<Uri> = mutableListOf()
    private lateinit var myAdapter: MergePdfAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupRecyclerView()

        btnChoosePdf.setOnClickListener {
            Intent(Intent.ACTION_GET_CONTENT).also {
                it.type = "application/pdf"
                it.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                startActivityForResult(it, 0)
            }
        }

        btnMergePdf.setOnClickListener {
            mergePDFs()
        }

    }

    private fun setupRecyclerView() {
        myAdapter = MergePdfAdapter(listOf())

        rvPdfItem.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = myAdapter
        }
    }

    private fun mergePDFs() {
        if (selectedPdfUriList.isNotEmpty()) {
            Toast.makeText(this, "Merging..", Toast.LENGTH_SHORT).show()

            try {
                CoroutineScope(Dispatchers.Main).launch {
                    val pdfDocument = PdfDocument()
                    for (i in selectedPdfUriList.indices) {

                        withContext(Dispatchers.IO) {

                            val parcelFileDescriptor: ParcelFileDescriptor? =
                                contentResolver.openFileDescriptor(selectedPdfUriList[i], "r")
                            if (parcelFileDescriptor != null) {
                                val renderer = PdfRenderer(parcelFileDescriptor)
                                val pageCount = renderer.pageCount
                                for (pageIndex in 0 until pageCount) {

                                    //GETTING IMAGES FROM PDF
                                    val page = renderer.openPage(pageIndex)
                                    val bitmap = Bitmap.createBitmap(
                                        page.width,
                                        page.height,
                                        Bitmap.Config.ARGB_8888
                                    )
                                    val canvas = Canvas(bitmap)
                                    canvas.drawColor(Color.WHITE)
                                    page.render(
                                        bitmap,
                                        null,
                                        null,
                                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                                    )

                                    //ADDING IMAGE TO PDF
                                    val pageInfo = PageInfo.Builder(A4_WIDTH, A4_HEIGHT, 1).create()
                                    val pdfPage = pdfDocument.startPage(pageInfo)
                                    val pdfCanvas = pdfPage.canvas
                                    pdfCanvas.drawBitmap(bitmap, 0f, 0f, null)
                                    pdfDocument.finishPage(pdfPage)
                                    page.close()
                                }
                                renderer.close()
                            }
                        }
                    }
                    savePdfDocument(pdfDocument)
                    pdfDocument.close()
                }
            } catch (e: Exception) {
                Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "Please select a PDF file first..", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun savePdfDocument(pdfDocument: PdfDocument) {

        val fileName = "MERGE" + SimpleDateFormat("ddMMyyyyhhmmss", Locale.UK).format(Calendar.getInstance().time)
        val filePath = getExternalFilesDir(null)?.absolutePath + "/$fileName.pdf"

        val file = File(filePath)

        try {
            pdfDocument.writeTo(FileOutputStream(file))
            Toast.makeText(this, "Merged PDF saved at $filePath", Toast.LENGTH_LONG).show()
        } catch (e: IOException) {
            Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
        }
        pdfDocument.close()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)
        if (resultCode == RESULT_OK && requestCode == 0) {

            selectedPdfNameList.clear()
            selectedPdfUriList.clear()

            if (intent!!.clipData != null) {
                val pdfCount = intent.clipData!!.itemCount
                for (i in 0 until pdfCount) {
                    val pdfUri = intent.clipData!!.getItemAt(i).uri
                    selectedPdfUriList.add(pdfUri)
                    selectedPdfNameList.add(getFileNameFromUri(pdfUri)!!)
                }
                myAdapter.list = selectedPdfNameList
                myAdapter.notifyDataSetChanged()
            } else {
                Toast.makeText(
                    this,
                    "Please Select more than one PDFs..",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        
        val uriString = uri.toString()
        var fileName: String? = ""
        if (uriString.startsWith("content://")) {
            var cursor: Cursor? = null
            try {
                cursor = contentResolver.query(uri, null, null, null, null)
                if (cursor != null && cursor.moveToFirst()) {
                    fileName = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                }
            } finally {
                cursor?.close()
            }
        } else if (uriString.startsWith("file://")) {
            fileName = File(uri.path!!).name
        }
        return fileName
    }
}