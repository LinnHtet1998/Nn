package com.example.core.pdf

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.example.core.i18n.AppLanguage
import com.example.core.i18n.MmkFormatter
import com.example.domain.model.SubscriptionPayment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReceiptPdfGenerator {
    fun writeSubscriptionReceiptPdf(
        context: Context,
        outputUri: Uri,
        payment: SubscriptionPayment,
    ): Result<Unit> = runCatching {
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas

        val titlePaint = Paint().apply {
            color = Color.rgb(15, 118, 110)
            textSize = 22f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val headingPaint = Paint().apply {
            color = Color.rgb(22, 31, 34)
            textSize = 14f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val bodyPaint = Paint().apply {
            color = Color.rgb(55, 65, 81)
            textSize = 12f
            isAntiAlias = true
        }
        val dividerPaint = Paint().apply {
            color = Color.rgb(209, 213, 219)
            strokeWidth = 1.2f
        }

        var y = 64f
        canvas.drawText("MINI MART PRO", 48f, y, titlePaint)
        y += 22f
        canvas.drawText("OFFICIAL SUBSCRIPTION PAYMENT RECEIPT", 48f, y, headingPaint)
        y += 18f
        canvas.drawLine(48f, y, 547f, y, dividerPaint)
        y += 28f

        val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val lines = listOf(
            "Receipt Number" to payment.receiptNumber.ifBlank { "PENDING-${payment.id.takeLast(6)}" },
            "Verification Status" to payment.status,
            "Business Name" to payment.companyName,
            "Business Owner" to "${payment.ownerName} (${payment.ownerEmail})",
            "Subscription Plan" to "${payment.planNameEn} (${payment.durationDays} days)",
            "Amount Paid (MMK)" to MmkFormatter.formatMmk(payment.amountMmk, AppLanguage.ENGLISH),
            "Payment Method" to payment.paymentMethodName,
            "Transaction Reference" to payment.transactionRef,
            "Submitted Date" to payment.paymentDateText,
            "Subscription Start" to (payment.grantedStartAt?.toDate()?.let { dateFmt.format(it) } ?: "-"),
            "Subscription Expiry" to (payment.grantedExpiresAt?.toDate()?.let { dateFmt.format(it) } ?: "-"),
            "Verified By" to (payment.verifiedByName ?: "Platform Super Admin"),
            "Generated At" to dateFmt.format(Date()),
        )

        lines.forEach { (label, value) ->
            canvas.drawText("$label:", 48f, y, headingPaint)
            canvas.drawText(value, 220f, y, bodyPaint)
            y += 24f
        }

        y += 14f
        canvas.drawLine(48f, y, 547f, y, dividerPaint)
        y += 24f
        canvas.drawText(
            "Thank you for using Mini Mart Pro Enterprise Multi-Branch POS.",
            48f,
            y,
            bodyPaint,
        )

        doc.finishPage(page)
        context.contentResolver.openOutputStream(outputUri)?.use { stream ->
            doc.writeTo(stream)
        } ?: error("Unable to open output stream for PDF receipt.")
        doc.close()
    }
}
