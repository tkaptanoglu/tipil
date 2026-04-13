package com.tipil.app.ui.scanner

import android.util.Log
import com.tipil.app.BuildConfig
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.tipil.app.data.local.MediaType
import com.tipil.app.data.repository.BookLookupResult
import com.tipil.app.ui.theme.LocalExtraColors
import com.tipil.app.util.IsbnValidator
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel,
    userId: String,
    initialMediaType: MediaType = MediaType.BOOK,
    onNavigateBack: () -> Unit
) {
    val scanState by viewModel.scanState.collectAsState()
    val extra = LocalExtraColors.current

    // The user can switch between Book and CD on the scanner screen itself.
    // This is local state so it survives recomposition but not navigation.
    var selectedMediaType by remember { mutableStateOf(initialMediaType) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (selectedMediaType.isMusic) "Scan Album" else "Scan ${selectedMediaType.label}")
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ── Media type selector (Book / CD / Cassette / Vinyl) ──
            // Always visible while scanning so the user can switch what they're scanning.
            if (scanState is ScanState.Scanning || scanState is ScanState.Looking) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val scanTypes = listOf(MediaType.BOOK, MediaType.CD, MediaType.CASSETTE, MediaType.VINYL)
                    scanTypes.forEach { type ->
                        FilterChip(
                            selected = selectedMediaType == type,
                            onClick = {
                                if (selectedMediaType != type) {
                                    selectedMediaType = type
                                    viewModel.resetScanner()
                                }
                            },
                            label = { Text(type.label) }
                        )
                    }
                }
            }

            // Camera preview area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (scanState is ScanState.Scanning || scanState is ScanState.Looking) {
                    // The camera accepts ALL valid barcodes (ISBN + UPC/EAN).
                    // The selectedMediaType determines which API the ViewModel calls.
                    CameraPreview(
                        onBarcodeDetected = { barcode ->
                            viewModel.onBarcodeDetected(barcode, userId, selectedMediaType)
                        }
                    )

                    // Scanning overlay
                    if (scanState is ScanState.Looking) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(if (selectedMediaType.isMusic) "Looking up album..." else "Looking up...")
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.List,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Result area
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (val state = scanState) {
                    is ScanState.Scanning -> {
                        Spacer(modifier = Modifier.height(32.dp))
                        Text(
                            if (selectedMediaType.isMusic)
                                "Point your camera at the album's barcode"
                            else
                                "Point your camera at a ${selectedMediaType.label.lowercase()}'s barcode",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    is ScanState.Looking -> { /* Handled in camera overlay */ }

                    is ScanState.Found -> {
                        // Auto-add is handled by the ViewModel; this state is
                        // only transiently visible if the add hasn't completed yet.
                        BookPreviewCard(result = state.result)
                        Spacer(modifier = Modifier.height(16.dp))
                        CircularProgressIndicator()
                    }

                    is ScanState.AlreadyInLibrary -> {
                        Spacer(modifier = Modifier.height(32.dp))
                        Icon(
                            Icons.Default.Done,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            if (selectedMediaType.isMusic)
                                "This ${selectedMediaType.label} is already in your library"
                            else
                                "This ${selectedMediaType.label.lowercase()} is already in your library",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.resetScanner() }) {
                            Text("Scan Another")
                        }
                    }

                    is ScanState.NotFound -> {
                        Spacer(modifier = Modifier.height(32.dp))
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            if (selectedMediaType.isMusic) "Album not found" else "${selectedMediaType.label} not found",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            if (selectedMediaType.isMusic) "Barcode: ${state.isbn}" else "ISBN: ${state.isbn}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.resetScanner() }) {
                            Text("Try Again")
                        }
                    }

                    is ScanState.Error -> {
                        Spacer(modifier = Modifier.height(32.dp))
                        Text(
                            state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.resetScanner() }) {
                            Text("Try Again")
                        }
                    }

                    is ScanState.Added -> {
                        Spacer(modifier = Modifier.height(32.dp))
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = extra.readIndicator
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            if (selectedMediaType.isMusic)
                                "${selectedMediaType.label} added to your library!"
                            else
                                "${selectedMediaType.label} added to your library!",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { viewModel.resetScanner() }) {
                                Text("Scan Another")
                            }
                            Button(onClick = onNavigateBack) {
                                Text("Back to Library")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookPreviewCard(result: BookLookupResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            if (result.coverUrl.isNotBlank()) {
                AsyncImage(
                    model = result.coverUrl,
                    contentDescription = "Cover",
                    modifier = Modifier
                        .size(width = 80.dp, height = 120.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(16.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(result.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (result.subtitle.isNotBlank()) {
                    Text(result.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(result.authors, style = MaterialTheme.typography.bodyMedium)

                Spacer(modifier = Modifier.height(8.dp))

                val isMusic = result.mediaType.isMusic

                // Format badge for music items
                if (isMusic) {
                    AssistChip(
                        onClick = { },
                        label = {
                            Text(
                                result.mediaType.label.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        modifier = Modifier.height(28.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                if (result.publisher.isNotBlank()) {
                    DetailRow(if (isMusic) "Label" else "Publisher", result.publisher)
                }
                if (result.publishedYear.isNotBlank()) {
                    DetailRow("Year", result.publishedYear)
                }
                if (result.pageCount > 0) {
                    DetailRow(if (isMusic) "Tracks" else "Pages", result.pageCount.toString())
                }
                // Tier 1: Fiction / Non-Fiction (only show for books)
                if (!isMusic) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AssistChip(
                        onClick = { },
                        label = {
                            Text(
                                if (result.isFiction) "FICTION" else "NON-FICTION",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        modifier = Modifier.height(28.dp)
                    )
                }

                // Tier 2: Genre tags
                if (result.genres.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        result.genres.forEach { genre ->
                            AssistChip(
                                onClick = { },
                                label = { Text(genre, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.height(28.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row {
        Text(
            "$label: ",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun CameraPreview(
    onBarcodeDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose { cameraExecutor.shutdown() }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val options = BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(
                        Barcode.FORMAT_EAN_13,
                        Barcode.FORMAT_EAN_8,
                        Barcode.FORMAT_UPC_A,
                        Barcode.FORMAT_UPC_E
                    )
                    .build()
                val barcodeScanner = BarcodeScanning.getClient(options)

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        val inputImage = InputImage.fromMediaImage(
                            mediaImage,
                            imageProxy.imageInfo.rotationDegrees
                        )
                        barcodeScanner.process(inputImage)
                            .addOnSuccessListener { barcodes ->
                                for (barcode in barcodes) {
                                    barcode.rawValue?.let { value ->
                                        if (isValidBarcode(value)) {
                                            onBarcodeDetected(value)
                                        }
                                    }
                                }
                            }
                            .addOnCompleteListener {
                                imageProxy.close()
                            }
                    } else {
                        imageProxy.close()
                    }
                }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) {
                        Log.e("CameraPreview", "Camera binding failed", e)
                    }
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

/**
 * Accepts any barcode that ML Kit decoded successfully and looks like a
 * product barcode: all digits, length 8 / 10 / 12 / 13.
 *
 * The media-type-specific validation (ISBN checksum for books, UPC/EAN for
 * CDs) is intentionally NOT done here. ML Kit already validated the barcode
 * format; further filtering here would silently drop valid CDs or books.
 * The ViewModel routes to the correct API based on the user's selected
 * media type.
 */
private fun isValidBarcode(value: String): Boolean {
    if (!value.all { it.isDigit() }) return false
    return value.length in listOf(8, 10, 12, 13)
}
