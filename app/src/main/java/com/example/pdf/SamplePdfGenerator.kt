package com.example.pdf

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

object SamplePdfGenerator {

    private const val SAMPLE_FILE_NAME = "Welcome_and_User_Guide.pdf"

    fun getOrGenerateSamplePdf(context: Context): File {
        val file = File(context.filesDir, SAMPLE_FILE_NAME)
        if (file.exists() && file.length() > 0) {
            return file
        }

        generateSamplePdf(file)
        return file
    }

    private fun generateSamplePdf(destinationFile: File) {
        val document = PdfDocument()

        val pageWidth = 595 // A4 standard pt at 72dpi
        val pageHeight = 842

        val titlePaint = Paint().apply {
            color = Color.parseColor("#C62828")
            textSize = 28f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val subtitlePaint = Paint().apply {
            color = Color.parseColor("#4A6572")
            textSize = 16f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val bodyPaint = Paint().apply {
            color = Color.parseColor("#1D1B1E")
            textSize = 13f
            isAntiAlias = true
        }

        val bodyBoldPaint = Paint().apply {
            color = Color.parseColor("#1D1B1E")
            textSize = 13f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val accentCardPaint = Paint().apply {
            color = Color.parseColor("#FFF3F2")
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val accentBorderPaint = Paint().apply {
            color = Color.parseColor("#EF9A9A")
            style = Paint.Style.STROKE
            strokeWidth = 2f
            isAntiAlias = true
        }

        val grayCardPaint = Paint().apply {
            color = Color.parseColor("#F5F5F7")
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val footerPaint = Paint().apply {
            color = Color.parseColor("#8E8E93")
            textSize = 10f
            isAntiAlias = true
        }

        // --- PAGE 1: Welcome & Features Overview ---
        run {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            // Header Banner
            val bannerRect = RectF(40f, 40f, pageWidth - 40f, 150f)
            canvas.drawRoundRect(bannerRect, 16f, 16f, accentCardPaint)
            canvas.drawRoundRect(bannerRect, 16f, 16f, accentBorderPaint)

            canvas.drawText("PDF READER", 60f, 85f, titlePaint)
            canvas.drawText("Standard Fast & Modern Document Viewer for Android", 60f, 118f, subtitlePaint)

            var y = 190f
            canvas.drawText("Welcome to your lightweight, high-performance PDF Reader!", 40f, y, bodyBoldPaint)
            y += 24f
            canvas.drawText("This application gives you complete, seamless access to read any PDF file on your device.", 40f, y, bodyPaint)
            y += 18f
            canvas.drawText("It uses Android's native graphics engine for zero-lag rendering and crisp text display.", 40f, y, bodyPaint)

            y += 40f
            canvas.drawText("Key Features Built Right In:", 40f, y, subtitlePaint)
            y += 28f

            val features = listOf(
                "Device File Access: Open any PDF from Downloads, Internal Storage, or SD card.",
                "Storage Access Framework (SAF): Safe, zero-permission document picking.",
                "Dual Viewing Modes: Continuous vertical scroll or single-page book swipe.",
                "Pinch to Zoom & Pan: Smooth multitouch zoom up to 400% with double-tap reset.",
                "3 Reading Themes: Day (pure white), Sepia (paper tint), Night (dark invert).",
                "Jump to Page & Scrubber: Instant navigation with page slider and dialog.",
                "Smart Bookmarks: Save memorable pages with custom notes.",
                "Auto Resume: Remembers your last read page and progress automatically."
            )

            for (feat in features) {
                val bulletRect = RectF(42f, y - 10f, 48f, y - 4f)
                canvas.drawRect(bulletRect, titlePaint)
                canvas.drawText(feat, 58f, y, bodyPaint)
                y += 26f
            }

            // Quick Tip Box
            y += 20f
            val tipRect = RectF(40f, y, pageWidth - 40f, y + 80f)
            canvas.drawRoundRect(tipRect, 12f, 12f, grayCardPaint)
            canvas.drawText("Pro Tip:", 56f, y + 30f, bodyBoldPaint)
            canvas.drawText("Tap anywhere in the center of the screen while reading to toggle toolbars", 56f, y + 50f, bodyPaint)
            canvas.drawText("and enter full-bleed reading mode!", 56f, y + 68f, bodyPaint)

            canvas.drawText("Page 1 of 4  •  PDF Reader Quick Start Guide", 40f, pageHeight - 30f, footerPaint)
            document.finishPage(page)
        }

        // --- PAGE 2: Device File Access Guide ---
        run {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 2).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            canvas.drawText("DEVICE FILE ACCESS", 40f, 70f, titlePaint)
            canvas.drawText("Opening and Managing Documents from Storage", 40f, 100f, subtitlePaint)

            var y = 140f
            canvas.drawText("1. Open Document Picker (SAF)", 40f, y, subtitlePaint)
            y += 22f
            canvas.drawText("Tap the red 'Open PDF' button or the floating action button at the bottom of the Library.", 40f, y, bodyPaint)
            y += 18f
            canvas.drawText("Android's system document selector will open, allowing you to choose any PDF from:", 40f, y, bodyPaint)
            y += 24f
            canvas.drawText(" • Downloads folder", 60f, y, bodyPaint); y += 20f
            canvas.drawText(" • Internal device storage", 60f, y, bodyPaint); y += 20f
            canvas.drawText(" • Google Drive / Cloud drives", 60f, y, bodyPaint); y += 20f
            canvas.drawText(" • SD Card and OTG USB drives", 60f, y, bodyPaint); y += 35f

            canvas.drawText("2. Persistent Access & Automatic History", 40f, y, subtitlePaint)
            y += 22f
            canvas.drawText("When you pick a file, the app takes persistable URI permissions. This means:", 40f, y, bodyPaint)
            y += 20f
            canvas.drawText(" • The document appears in your 'Recent' tab automatically.", 60f, y, bodyPaint); y += 20f
            canvas.drawText(" • You can tap and re-open it anytime without selecting it again.", 60f, y, bodyPaint); y += 20f
            canvas.drawText(" • Your reading progress is persisted across app restarts.", 60f, y, bodyPaint); y += 35f

            canvas.drawText("3. Opening PDFs from Other Apps", 40f, y, subtitlePaint)
            y += 22f
            canvas.drawText("When downloading PDFs in Chrome, receiving attachments in Gmail, or opening files", 40f, y, bodyPaint)
            y += 18f
            canvas.drawText("in WhatsApp, tap 'Open with...' and select 'PDF Reader'.", 40f, y, bodyPaint)

            canvas.drawText("Page 2 of 4  •  Device File Access Guide", 40f, pageHeight - 30f, footerPaint)
            document.finishPage(page)
        }

        // --- PAGE 3: Gestures & Reading Modes ---
        run {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 3).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            canvas.drawText("READING CONTROLS & THEMES", 40f, 70f, titlePaint)
            canvas.drawText("Customizing your viewing experience for maximum comfort", 40f, 100f, subtitlePaint)

            var y = 140f
            canvas.drawText("Gesture Controls:", 40f, y, subtitlePaint)
            y += 24f
            val gestures = listOf(
                "Pinch to Zoom: Use two fingers to enlarge text, diagrams, and small footnotes.",
                "Pan & Drag: Easily move around zoomed pages.",
                "Double Tap: Quickly zoom to 200% or reset back to 100% fit-to-screen.",
                "Single Tap: Show or hide top toolbar and bottom page controls.",
                "Swipe / Scroll: Move fluidly between pages according to your selected mode."
            )
            for (g in gestures) {
                canvas.drawText("• $g", 40f, y, bodyPaint)
                y += 24f
            }

            y += 25f
            canvas.drawText("3 Specialized Reading Themes:", 40f, y, subtitlePaint)
            y += 24f

            // 3 boxes representing themes
            val boxWidth = (pageWidth - 80f - 24f) / 3f

            // Day Box
            val dayRect = RectF(40f, y, 40f + boxWidth, y + 100f)
            canvas.drawRoundRect(dayRect, 8f, 8f, grayCardPaint)
            canvas.drawText("DAY MODE", 52f, y + 30f, bodyBoldPaint)
            canvas.drawText("High contrast", 52f, y + 55f, bodyPaint)
            canvas.drawText("Crisp paper white", 52f, y + 75f, footerPaint)

            // Sepia Box
            val sepiaPaint = Paint().apply { color = Color.parseColor("#F8F1E5"); style = Paint.Style.FILL }
            val sepiaRect = RectF(40f + boxWidth + 12f, y, 40f + (boxWidth * 2f) + 12f, y + 100f)
            canvas.drawRoundRect(sepiaRect, 8f, 8f, sepiaPaint)
            canvas.drawText("SEPIA MODE", 40f + boxWidth + 24f, y + 30f, bodyBoldPaint)
            canvas.drawText("Warm tint", 40f + boxWidth + 24f, y + 55f, bodyPaint)
            canvas.drawText("Reduces eye strain", 40f + boxWidth + 24f, y + 75f, footerPaint)

            // Night Box
            val nightPaint = Paint().apply { color = Color.parseColor("#212529"); style = Paint.Style.FILL }
            val nightTextPaint = Paint().apply { color = Color.WHITE; textSize = 13f; isFakeBoldText = true }
            val nightSubPaint = Paint().apply { color = Color.parseColor("#ADB5BD"); textSize = 11f }
            val nightRect = RectF(40f + (boxWidth * 2f) + 24f, y, pageWidth - 40f, y + 100f)
            canvas.drawRoundRect(nightRect, 8f, 8f, nightPaint)
            canvas.drawText("NIGHT MODE", 40f + (boxWidth * 2f) + 36f, y + 30f, nightTextPaint)
            canvas.drawText("Dark inversion", 40f + (boxWidth * 2f) + 36f, y + 55f, nightSubPaint)
            canvas.drawText("OLED battery saver", 40f + (boxWidth * 2f) + 36f, y + 75f, nightSubPaint)

            y += 140f
            canvas.drawText("Page Navigation Tools:", 40f, y, subtitlePaint)
            y += 24f
            canvas.drawText(" • Slider Scrubber: Slide across the bottom bar to preview and jump to any page.", 40f, y, bodyPaint); y += 20f
            canvas.drawText(" • Jump to Page Dialog: Enter an exact page number directly.", 40f, y, bodyPaint); y += 20f
            canvas.drawText(" • Thumbnails Grid: View all page previews in a bottom drawer.", 40f, y, bodyPaint); y += 20f
            canvas.drawText(" • Bookmarks: Star any page with a label for one-touch recall.", 40f, y, bodyPaint)

            canvas.drawText("Page 3 of 4  •  Reading Controls & Themes", 40f, pageHeight - 30f, footerPaint)
            document.finishPage(page)
        }

        // --- PAGE 4: Typography & Clarity Verification ---
        run {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 4).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            canvas.drawText("SAMPLE READING PASSAGE", 40f, 70f, titlePaint)
            canvas.drawText("Testing Typography, Scaling & Sharpness", 40f, 100f, subtitlePaint)

            var y = 140f
            val sampleParagraphs = listOf(
                "Digital document readers bridge the gap between physical paper and computational convenience. Whether you are reviewing an architectural blueprint, studying an academic journal, checking a financial invoice, or reading an ebook on the go, fidelity and speed are essential.",
                "Android's native PdfRenderer leverages the Google Skia graphics engine to render vector fonts, glyphs, and line art directly into high-density framebuffers. This guarantees that whether you view this document on a compact phone or an expanded tablet, the edges remain razor-sharp.",
                "You can test zooming on this page right now! Double-tap anywhere to zoom in on this paragraph, or pinch with two fingers to examine fine detail. When you are finished, tap the back arrow in the top bar to return to your library and open your own personal documents."
            )

            for (p in sampleParagraphs) {
                // Word wrap simulation for paragraph
                val words = p.split(" ")
                var line = ""
                for (w in words) {
                    val testLine = if (line.isEmpty()) w else "$line $w"
                    if (bodyPaint.measureText(testLine) > (pageWidth - 80f)) {
                        canvas.drawText(line, 40f, y, bodyPaint)
                        y += 18f
                        line = w
                    } else {
                        line = testLine
                    }
                }
                if (line.isNotEmpty()) {
                    canvas.drawText(line, 40f, y, bodyPaint)
                    y += 18f
                }
                y += 14f
            }

            y += 20f
            val endCard = RectF(40f, y, pageWidth - 40f, y + 60f)
            canvas.drawRoundRect(endCard, 10f, 10f, accentCardPaint)
            canvas.drawText("✓ All systems verified and ready for reading.", 60f, y + 36f, bodyBoldPaint)

            canvas.drawText("Page 4 of 4  •  Sample Reading Passage", 40f, pageHeight - 30f, footerPaint)
            document.finishPage(page)
        }

        try {
            FileOutputStream(destinationFile).use { out ->
                document.writeTo(out)
            }
        } finally {
            document.close()
        }
    }
}
