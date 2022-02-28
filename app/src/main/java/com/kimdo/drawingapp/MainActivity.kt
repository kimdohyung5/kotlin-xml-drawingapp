package com.kimdo.drawingapp

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.get

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception


class MainActivity : AppCompatActivity() {
    private var drawingView: DrawingView? = null
    private var mImageButtonCurrentPaint: ImageButton? = null

    var customProgressDialog: Dialog? = null

    val openGalleryLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){ result ->
        if(result.resultCode == RESULT_OK && result.data != null) {
            val imageBackground: ImageView = findViewById(R.id.iv_background)
            imageBackground.setImageURI(result.data?.data)
        }

    }

    val requestPermission : ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                val perMissionName = it.key
                val isGranted = it.value
                if(isGranted) {
                    Toast.makeText(
                        this@MainActivity,
                        "Permission granted now you can read the storage files.",
                        Toast.LENGTH_LONG
                    ).show()
                    val pickIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    //Todo 6: using the intent launcher created above launch the pick intent
                    openGalleryLauncher.launch(pickIntent)
                } else {
                    if (perMissionName == Manifest.permission.READ_EXTERNAL_STORAGE)
                            Toast.makeText(
                                this@MainActivity,
                                "Oops you just denied the permission.",
                                Toast.LENGTH_LONG
                            ).show()
                }
            }

        }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawingView = findViewById(R.id.drawing_view)
        val ibBrush: ImageButton = findViewById(R.id.ib_brush)
        drawingView?.setSizeForBrush(20.toFloat())
        val linearLayoutPaintColors = findViewById<LinearLayout>(R.id.ll_paint_colors)
        mImageButtonCurrentPaint = linearLayoutPaintColors[1] as ImageButton
        mImageButtonCurrentPaint?.setImageDrawable(
            ContextCompat.getDrawable(this, R.drawable.pallet_pressed)
        )
        ibBrush.setOnClickListener {
            showBrushSizeChooserDialog()
        }

        val ibGallery: ImageButton = findViewById(R.id.ib_gallery)
        ibGallery.setOnClickListener {
            requestStoragePermission()
        }

        val ibUndo: ImageButton = findViewById(R.id.ib_undo)
        ibUndo.setOnClickListener {
            drawingView?.onClickUndo()
        }

        val ibSave:ImageButton = findViewById(R.id.ib_save)
        ibSave.setOnClickListener{
            if(isReadStorageAllowed()) {
                showProgressDialog()
                lifecycleScope.launch {
                    val fDrawingView: FrameLayout = findViewById(R.id.fl_drawing_view_container)
                    saveBitmapFile(getBitmapFromView(fDrawingView))
                }
            }
        }
    }

    private fun showBrushSizeChooserDialog() {
        var brushDialog = Dialog(this)
        brushDialog.setContentView(R.layout.dialog_brush_size)
        brushDialog.setTitle("Brush size...")
        val smallBtn: ImageButton = brushDialog.findViewById(R.id.ib_small_brush)
        smallBtn.setOnClickListener(View.OnClickListener {
            drawingView?.setSizeForBrush(10.toFloat())
            brushDialog.dismiss()
        })
        val mediumButton:ImageButton = brushDialog.findViewById(R.id.ib_medium_brush)
        mediumButton.setOnClickListener(View.OnClickListener {
            drawingView?.setSizeForBrush(20.toFloat())
            brushDialog.dismiss()
        })
        val largeButton:ImageButton = brushDialog.findViewById(R.id.ib_large_brush)
        largeButton.setOnClickListener(View.OnClickListener {
            drawingView?.setSizeForBrush(30.toFloat())
            brushDialog.dismiss()
        })
        brushDialog.show();
    }

    fun paintClicked(view: View) {
        if(view !== mImageButtonCurrentPaint) {
            val imageButton = view as ImageButton
            val colorTag = imageButton.tag.toString()
            drawingView?.setColor(colorTag)
            imageButton.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.pallet_pressed))
            mImageButtonCurrentPaint?.setImageDrawable(
                ContextCompat.getDrawable(this, R.drawable.pallet_normal)
            )
            mImageButtonCurrentPaint = view
        }
    }

    private fun isReadStorageAllowed():Boolean {
        val result = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
        return result == PackageManager.PERMISSION_GRANTED
    }

    private fun requestStoragePermission() {
        if(ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
            showRationaleDialog("Drawing A", " needs to access external storage.")
        } else {
            requestPermission.launch( arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ))
        }
    }


    private fun showRationaleDialog(title:String, message:String) {
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle(title)
            .setMessage(message)
            .setPositiveButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
        builder.create().show()
    }

    private fun getBitmapFromView(view: View): Bitmap {
        val returnedBitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(returnedBitmap)
        val bgDrawable = view.background
        if(bgDrawable!= null) {
            bgDrawable.draw(canvas)
        } else {
            canvas.drawColor(Color.WHITE)
        }

        view.draw(canvas)
        return returnedBitmap

    }

    private suspend fun saveBitmapFile(mBitmap: Bitmap?):String {
        var result = ""
        withContext(Dispatchers.IO) {
            if(mBitmap != null) {
                try {
                    val bytes = ByteArrayOutputStream()
                    mBitmap.compress(Bitmap.CompressFormat.PNG, 90, bytes)

                    val f = File(
                        externalCacheDir?.absoluteFile.toString()
                        + File.separator + "DrawingApp_" + System.currentTimeMillis() / 1000 + ".jpg"
                    )

                    val fo = FileOutputStream(f)
                    fo.write(bytes.toByteArray())
                    fo.close()
                    result = f.absolutePath
                    runOnUiThread {
                        cancelProgressDialog()
                        Log.i("kimdo", "path=${result}")
                        if (!result.isEmpty()) {
                            Toast.makeText(
                                this@MainActivity,
                                "File saved successfully :$result",
                                Toast.LENGTH_SHORT
                            ).show()
                            shareImage(result)
                        } else {
                            Toast.makeText(
                                this@MainActivity,
                                "Something went wrong while saving the file.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } catch( e: Exception) {
                    result = ""
                    e.printStackTrace()
                }
            }
        }
        return result
    }

    private fun shareImage(result:String) {
        MediaScannerConnection.scanFile( this@MainActivity, arrayOf(result), null) {
            path, uri ->
            val shareIntent = Intent()
            shareIntent.action = Intent.ACTION_SEND
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
            shareIntent.type = "image/png"
            startActivity(Intent.createChooser( shareIntent, "Share"))
        }
    }

    private fun showProgressDialog() {
        customProgressDialog = Dialog(this@MainActivity)
        customProgressDialog?.setContentView(R.layout.dialog_custom_progress)
        customProgressDialog?.show()
    }

    private fun cancelProgressDialog() {
        if(customProgressDialog != null) {
            customProgressDialog?.dismiss()
            customProgressDialog = null
        }
    }
}