package com.ct.explorer.ui.components.office

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val FormulaBarBg = Color(0xFFFFFFFF)
val FormulaBorder = Color(0xFFD4D4D4)
val FormulaFxGreen = Color(0xFF107C41)

@Composable
fun MsExcelFormulaBar(
    cellRef: String,
    cellValue: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .background(FormulaBarBg)
            .border(0.5.dp, FormulaBorder)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Name Box (e.g. "A1")
        Box(
            modifier = Modifier
                .widthIn(min = 48.dp)
                .height(26.dp)
                .background(Color(0xFFF8FAFC))
                .border(0.5.dp, FormulaBorder, RoundedCornerShape(2.dp))
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = cellRef.ifBlank { "A1" },
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF1E293B)
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // 2. Action Icons: ✕, ✓, fx
        Text(
            text = "✕",
            fontSize = 10.sp,
            color = Color(0xFF94A3B8),
            modifier = Modifier.padding(horizontal = 3.dp)
        )
        Text(
            text = "✓",
            fontSize = 11.sp,
            color = Color(0xFF94A3B8),
            modifier = Modifier.padding(horizontal = 3.dp)
        )
        Text(
            text = "fx",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontStyle = FontStyle.Italic,
            color = FormulaFxGreen,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Spacer(modifier = Modifier.width(4.dp))

        // 3. Formula / Cell Value input line
        Box(
            modifier = Modifier
                .weight(1f)
                .height(26.dp)
                .background(Color.White)
                .border(0.5.dp, FormulaBorder, RoundedCornerShape(2.dp))
                .clickable {
                    if (cellValue.isNotBlank()) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Excel Cell", cellValue))
                        Toast.makeText(context, "Copied \"$cellValue\"", Toast.LENGTH_SHORT).show()
                    }
                }
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = cellValue.ifBlank { "(Select any cell to inspect)" },
                fontSize = 11.5.sp,
                color = if (cellValue.isBlank()) Color(0xFF94A3B8) else Color(0xFF0F172A),
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (cellValue.isNotBlank()) {
            IconButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Excel Cell", cellValue))
                    Toast.makeText(context, "Copied \"$cellValue\"", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    tint = FormulaFxGreen,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
