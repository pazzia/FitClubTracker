package com.fitclub.tracker.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import com.fitclub.tracker.FitApp
import com.fitclub.tracker.data.repo.ProgramPhotos
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = FitApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProgramPhotosTest {
    @Test fun selectedPhotoIsCopiedAndSurvivesSourceRemoval() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = File(context.cacheDir, "selected-photo.png")
        val image = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888)
        source.outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        image.recycle()
        val name = ProgramPhotos.importPhoto(context, Uri.fromFile(source))
        source.delete()
        val saved = ProgramPhotos.file(context, name)!!
        assertTrue(saved.isFile)
        val decoded = BitmapFactory.decodeFile(saved.absolutePath)
        assertEquals(64, decoded.width)
        assertEquals(32, decoded.height)
        decoded.recycle()
        saved.delete()
        Unit
    }
    @Test fun nonImageIsRejectedAndTraversalIsRefused() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = File(context.cacheDir, "not-an-image.txt").apply { writeText("hello") }
        try {
            ProgramPhotos.importPhoto(context, Uri.fromFile(source))
            fail("Not an image")
        } catch (_: IllegalArgumentException) { } finally { source.delete() }
        assertNull(ProgramPhotos.file(context, "../outside.jpg"))
    }
    @Test fun cameraProviderSharesOnlyCaptureFolder() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val capture = File(context.cacheDir, "cover_captures/capture.jpg").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1)) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", capture)
        assertEquals("content", uri.scheme)
        assertEquals("${context.packageName}.files", uri.authority)
        try {
            FileProvider.getUriForFile(context, "${context.packageName}.files", File(context.cacheDir, "private.txt"))
            fail("Provider exposed unrelated cache")
        } catch (_: IllegalArgumentException) { } finally { capture.delete() }
    }
}
