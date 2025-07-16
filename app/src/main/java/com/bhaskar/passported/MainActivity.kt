package com.bhaskar.passported

import android.app.Activity
import android.app.Application
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color.WHITE
import android.graphics.Color.BLACK
import android.graphics.Color.argb
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.bhaskar.passported.ui.theme.PassportedTheme
import com.google.android.material.color.DynamicColors
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.Locale
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale

// -------------------------------------------------- Classes --------------------------------------------------
// Language Manager
class LanguagePreferenceManager(context: Context) {
    private val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    companion object {
        @Volatile
        private var INSTANCE: LanguagePreferenceManager? = null

        fun getInstance(context: Context): LanguagePreferenceManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LanguagePreferenceManager(context).also { INSTANCE = it }
            }
        }
    }

    var selectedLanguage: Flow<String> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "selected_language") {
                trySend(getLanguage()).isSuccess
            }
        }

        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)

        // Send the initial value
        trySend(getLanguage()).isSuccess

        awaitClose { sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun setLanguage(languageCode: String) {
        sharedPreferences.edit { putString("selected_language", languageCode) }
    }

    fun getLanguage(): String {
        return sharedPreferences.getString("selected_language", "en") ?: "en"
    }
}

@Suppress("UNCHECKED_CAST")
class LanguageViewModelFactory(
    private val application: Application // Pass Application instead of LanguagePreferenceManager
) : ViewModelProvider.Factory { override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LanguageViewModel::class.java)) {
            return LanguageViewModel(application) as T// Pass Application to LanguageViewModel
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    } }

// Language Model
class LanguageViewModel(application: Application) : AndroidViewModel(application) {
    private val languageManager = LanguagePreferenceManager.getInstance(application)

    val selectedLanguage: StateFlow<String> = languageManager.selectedLanguage.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = languageManager.getLanguage()
    )

    fun setLanguage(languageCode: String) {
        languageManager.setLanguage(languageCode)
    }
}

// MainActivity
class MainActivity : ComponentActivity() {
    private lateinit var languageManager: LanguagePreferenceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        // Enable Material You (Dynamic Colors)
        DynamicColors.applyToActivityIfAvailable(this)

        super.onCreate(savedInstanceState)

        languageManager = LanguagePreferenceManager.getInstance(this)
        val initialLanguage = languageManager.getLanguage()
        setAppLocale(initialLanguage)

        setContent {
            PassportedTheme {
                // Set status bar color to match theme background
                window.statusBarColor = colorScheme.background.toArgb()

                MainScreen(currentLanguage = initialLanguage) { newLanguage ->
                    if (newLanguage != initialLanguage) {
                        languageManager.setLanguage(newLanguage)
                        setAppLocale(newLanguage)
                    }
                }
            }
        }
    }

    private fun setAppLocale(language: String) {
        val locale = Locale(language)
        Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
    }
}


// -------------------------------------------------- User Interface --------------------------------------------------
// Main Screen: Home Model, Language Filter
@Composable
fun MainScreen(currentLanguage: String, onLanguageSelected: (String) -> Unit) {
    val navController = rememberNavController()

    // Get the Application context using LocalContext.current
    val context = LocalContext.current.applicationContext as Application

    // Create LanguageViewModel using LanguageViewModelFactory, passing Application
    val languageViewModel: LanguageViewModel = viewModel(factory = LanguageViewModelFactory(context))

    // Observe selectedLanguage state from ViewModel
    val selectedLanguage by languageViewModel.selectedLanguage.collectAsState()

    // Only update UI when selectedLanguage changes
    if (selectedLanguage != currentLanguage) {
        onLanguageSelected(selectedLanguage)
    }

    val activity = LocalContext.current as Activity

    // Fixed Top Passported Banner
    Column(modifier = Modifier
        .fillMaxSize()
        .background(color = colorScheme.background)
    ) {
        TopBanner()

        Box(modifier = Modifier.weight(1f)) {
            NavHost(navController, startDestination = "passport_photo_mode") {
                composable("passport_photo_mode") { PassportPhotoHomeScreen() }
                composable("Aadhaar_card_mode") { AadhaarCardHomeScreen() }
                composable("collage_mode") { CollageScreen() }
                composable("khasra_map_mode") { KhasraMapScreen() }
                composable("user_settings") {
                    SettingsScreen(
                        selectedLanguage = selectedLanguage,
                        onLanguageChange = { newLanguage ->
                            languageViewModel.setLanguage(newLanguage) // Save new language
                            activity.recreate() // Restart the activity to apply the language change
                        },
                        activity = activity // Pass the current activity
                    )
                }
            }
        }

        // Fixed Bottom Navigation Bar
        AppNavigationBar(navController)
    }
}

// Top Banner
@Composable
fun TopBanner() {
    Image(
        painter = painterResource(id = R.drawable.passported_banner), // Replace with your banner image resource ID
        contentDescription = "Banner",
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp) // Adjust the height as needed
    )
}

//Navigation Bar
@Composable
fun AppNavigationBar(navController: NavHostController) {
    // Get the current back stack entry and route
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    // Make the NavigationBar scrollable
    ScrollableTabRow(
        edgePadding = 0.dp,
        selectedTabIndex = when (currentRoute) {
            "passport_photo_mode" -> 0
            "Aadhaar_card_mode" -> 1
            "collage_mode" -> 2
            "khasra_map_mode" -> 3
            "user_settings" -> 4
            else -> 0 // Default case if no route matches
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Tab(
            selected = currentRoute == "passport_photo_mode",
            onClick = {
                if (currentRoute != "passport_photo_mode") {
                    navController.navigate("passport_photo_mode") {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            },
            text = {
                Text(stringResource(R.string.passport_photo))
            },
            icon = {
                Icon(Icons.Default.Face, contentDescription = null)
            }
        )
        Tab(
            selected = currentRoute == "Aadhaar_card_mode",
            onClick = {
                if (currentRoute != "Aadhaar_card_mode") {
                    navController.navigate("Aadhaar_card_mode") {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            },
            text = {
                Text(stringResource(R.string.aadhaar_card))
            },
            icon = {
                Icon(Icons.Default.Person, contentDescription = null)
            }
        )
        Tab(
            selected = currentRoute == "collage_mode",
            onClick = {
                if (currentRoute != "collage_mode") {
                    navController.navigate("collage_mode") {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            },
            text = {
                Text(stringResource(R.string.collage))
            },
            icon = {
                Icon(imageVector = Icons.Default.AccountBox, contentDescription = null)
            }
        )
        Tab(
            selected = currentRoute == "khasra_map_mode",
            onClick = {
                if (currentRoute != "khasra_map_mode") {
                    navController.navigate("khasra_map_mode") {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            },
            text = {
                Text(stringResource(R.string.khasra_map))
            },
            icon = {
                Icon(imageVector = Icons.Default.Favorite, contentDescription = null)
            }
        )
        Tab(
            selected = currentRoute == "user_settings",
            onClick = {
                if (currentRoute != "user_settings") {
                    navController.navigate("user_settings") {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            },
            text = {
                Text(stringResource(R.string.settings))
            },
            icon = {
                Icon(imageVector = Icons.Default.Settings, contentDescription = null)
            }
        )
    }
}

// Settings Screen
@Composable
fun SettingsScreen(selectedLanguage: String, onLanguageChange: (String) -> Unit, activity: Activity) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(
                id = R.string.settings
            ),
            fontSize = 32.sp,
            color = colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(text = stringResource(
            id = R.string.select_language),
            fontSize = 18.sp,
            color = colorScheme.onBackground)
        Row(
            modifier = Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(onClick = {
                onLanguageChange("en") // Change to English
                activity.recreate() // Restart the activity
            }) {
                Text(stringResource(id = R.string.english))
            }

            Spacer(modifier = Modifier.width(16.dp))

            Button(onClick = {
                onLanguageChange("hi") // Change to Hindi
                activity.recreate() // Restart the activity
            }) {
                Text(stringResource(id = R.string.hindi))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "${stringResource(id = R.string.current_language)}: ${
            if (selectedLanguage == "en") stringResource(id = R.string.english)
            else stringResource(id = R.string.hindi)}",
            color = colorScheme.onBackground
        )
    }
}


// -------------------------------------------------- All Purpose Tools --------------------------------------------------
// Function to get Uri from Bitmap
fun getImageUri(context: Context, bitmap: Bitmap): Uri {
    val bytes = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes)
    val path = MediaStore.Images.Media.insertImage(context.contentResolver, bitmap, "Shared Image", null)
    return path.toUri()
}


// -------------------------------------------------- Passport Photo Tools --------------------------------------------------
// Passport Photo Screen
@Composable
fun PassportPhotoHomeScreen() {
    var selectedMode by remember { mutableStateOf<String?>(null) }
    var isBackgroundRemoved by remember { mutableStateOf(true) }
    var isBorderNeeded by remember { mutableStateOf(true) }

    when (selectedMode) {
        "8xMode" -> LargePassportScreen(
            onBack = { selectedMode = null },
            isBackgroundRemoved = isBackgroundRemoved,
            isBorderNeeded = isBorderNeeded
        )

        "12xMode" -> SmallPassportScreen(
            onBack = { selectedMode = null },
            isBackgroundRemoved = isBackgroundRemoved,
            isBorderNeeded = isBorderNeeded
        )

        else -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Text(
                        text = stringResource(id = R.string.select_passport_photo_size),
                        style = MaterialTheme.typography.headlineMedium,
                        color = colorScheme.onBackground,
                        modifier = Modifier.padding(bottom = 20.dp, top = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // 🌟 Switch 1: Remove Background
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Text(
                            text = "Remove Background",
                            color = colorScheme.onBackground,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = isBackgroundRemoved,
                            onCheckedChange = { isBackgroundRemoved = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colorScheme.primary,
                                uncheckedThumbColor = colorScheme.onSurfaceVariant,
                                checkedTrackColor = colorScheme.primary.copy(alpha = 0.5f),
                                uncheckedTrackColor = colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                        )

                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 🌟 Switch 2: Add Border
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Text(
                            text = "Add Border",
                            color = colorScheme.onBackground,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = isBorderNeeded,
                            onCheckedChange = { isBorderNeeded = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colorScheme.primary,
                                uncheckedThumbColor = colorScheme.onSurfaceVariant,
                                checkedTrackColor = colorScheme.primary.copy(alpha = 0.5f),
                                uncheckedTrackColor = colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                        )

                    }

                    Spacer(modifier = Modifier.height(30.dp))

                    Button(onClick = { selectedMode = "8xMode" }) {
                        Text(stringResource(id = R.string.eight_large_photo))
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(onClick = { selectedMode = "12xMode" }) {
                        Text(stringResource(id = R.string.twelve_small_passport))
                    }
                }
            }
        }
    }
}

// Image Saving Tool
fun saveImage(context: Context, bitmap: Bitmap) {
    val contentResolver: ContentResolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "Passport_${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Passported/PassportPhotos")
    }
    val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    uri?.let {
        val outputStream: OutputStream? = contentResolver.openOutputStream(it)
        outputStream?.let { it1 -> bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it1) }
        outputStream?.close()
    }
}

// Image Sharing Tool
fun shareImage(context: Context, bitmap: Bitmap) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, getImageUri(context, bitmap))
        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share Image via"))
}

// Background Remover Tool
fun removeBackground(inputBitmap: Bitmap, onResult: (Bitmap) -> Unit, onError: (Exception) -> Unit) {
    val options = SelfieSegmenterOptions.Builder()
        .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
        .build()

    val segmenter = Segmentation.getClient(options)
    val image = InputImage.fromBitmap(inputBitmap, 0)

    segmenter.process(image)
        .addOnSuccessListener { result ->
            val mask = result.buffer
            val width = result.width
            val height = result.height
            val maskBitmap = createBitmap(width, height)

            mask.rewind()
            val pixels = IntArray(width * height)
            for (i in pixels.indices) {
                val confidence = mask.float
                val alpha = if (confidence > 0.7f) 255 else 0
                pixels[i] = argb(alpha,30,30,30)
            }

            maskBitmap.setPixels(pixels, 0, width, 0, 0, width, height)

            val output = createBitmap(width, height)
            val canvas = Canvas(output)
            val paint = Paint()
            canvas.drawBitmap(inputBitmap, 0f, 0f, null)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            canvas.drawBitmap(maskBitmap, 0f, 0f, paint)
            paint.xfermode = null

            onResult(output)
        }
        .addOnFailureListener { e ->
            onError(e)
        }
}

// Border Adding Tool
fun addBorder(bitmap: Bitmap, borderSize: Int, borderColor: Int): Bitmap {
    val borderedBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(borderedBitmap)

    val paint = Paint().apply {
        color = borderColor
        style = Paint.Style.STROKE
        strokeWidth = borderSize.toFloat()
        isAntiAlias = true
    }

    val halfBorder = borderSize / 2f
    val rect = RectF(
        halfBorder,
        halfBorder,
        bitmap.width - halfBorder,
        bitmap.height - halfBorder
    )

    canvas.drawRect(rect, paint)

    return borderedBitmap
}

// -------------------------------------------------- Large Passport Tools --------------------------------------------------
// Large Passport Screen
@Composable
fun LargePassportScreen(
    onBack: () -> Unit,
    isBackgroundRemoved: Boolean,
    isBorderNeeded: Boolean
) {
    val context = LocalContext.current

    val bitmap = remember { mutableStateOf<Bitmap?>(null) }
    val finalBitmap = remember { mutableStateOf<Bitmap?>(null) }

    fun processFinalBitmap(original: Bitmap) {
        var workingBitmap = original

        // Optional background removal
        if (isBackgroundRemoved) {
            removeBackground(
                inputBitmap = workingBitmap,
                onResult = { noBgBitmap ->
                    workingBitmap = noBgBitmap

                    // Optional border
                    if (isBorderNeeded) {
                        workingBitmap = addBorder(workingBitmap, 8, BLACK)
                    }

                    val layoutBitmap = passportPageLayoutLarge(workingBitmap)
                    bitmap.value = layoutBitmap
                    finalBitmap.value = layoutBitmap
                },
                onError = { error ->
                    Toast.makeText(context, "Background removal failed: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            )
        } else {
            // No background removal
            if (isBorderNeeded) {
                workingBitmap = addBorder(workingBitmap, 4, BLACK)
            }

            val layoutBitmap = passportPageLayoutLarge(workingBitmap)
            bitmap.value = layoutBitmap
            finalBitmap.value = layoutBitmap
        }
    }

    val cropImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { intent ->
                UCrop.getOutput(intent)?.let { resultUri ->
                    val source = ImageDecoder.createSource(context.contentResolver, resultUri)
                    val croppedBitmap = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.isMutableRequired = true
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                    bitmap.value = croppedBitmap
                    processFinalBitmap(croppedBitmap)
                }
            }
        }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            cropImageLauncher.launch(createCropLargePassport(context, it))
        }
    }

    // UI layout (unchanged)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(id = R.string.eight_large_photo),
            style = MaterialTheme.typography.headlineMedium,
            color = colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(onClick = { pickImageLauncher.launch("image/*") }) {
            Text(stringResource(id = R.string.select_image))
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(onClick = onBack) {
            Text(stringResource(id = R.string.back))
        }

        Spacer(modifier = Modifier.height(20.dp))

        finalBitmap.value?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Final Grid Layout",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            )
        } ?: bitmap.value?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Cropped Image",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        finalBitmap.value?.let { bitmap ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(onClick = {
                    saveImage(context, bitmap)
                    Toast.makeText(context, "Image Saved to File", Toast.LENGTH_SHORT).show()
                }) {
                    Text(stringResource(id = R.string.save_image))
                }

                Button(onClick = {
                    shareImage(context, bitmap)
                    Toast.makeText(context, "Sharing Image", Toast.LENGTH_SHORT).show()
                }) {
                    Text(stringResource(id = R.string.share_image))
                }
            }
        }
    }
}

// Cropping Tool
fun createCropLargePassport(context: Context, uri: Uri): Intent {
    val destinationUri = Uri.fromFile(File(context.cacheDir, "cropped_${System.currentTimeMillis()}.jpg"))
    return UCrop.of(uri, destinationUri)
        .withAspectRatio(3.5f, 4.5f)
        .withMaxResultSize(414, 530)
        .getIntent(context)
}

// Rotating Tool
fun rotateBitmap(bitmap: Bitmap, angle: Float): Bitmap {
    val matrix = Matrix().apply {
        postRotate(angle)
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

// Large Passport Layout Tool
fun passportPageLayoutLarge(bitmap: Bitmap): Bitmap {
    val layoutWidth = 1181 // Page width in pixels (rotated width)
    val layoutHeight = 1772 // Page height in pixels (rotated height)

    // Create a new bitmap with the layout size and white background
    val layoutBitmap = createBitmap(layoutWidth, layoutHeight)
    val canvas = Canvas(layoutBitmap)
    canvas.drawColor(WHITE) // Set background color to white

    // Define the image size (rotated dimensions)
    val imageWidth = 500 // 4.5 cm in pixels (300 dpi)
    val imageHeight = 390 // 3.5 cm in pixels (300 dpi)

    // Define the image margins
    val pageMargin = 80f
    val imageMargin = 50f

    // Define the positions for the 2x4 grid (rotated 90 degrees)
    val positions = listOf(
        RectF(pageMargin, imageMargin, imageMargin + imageWidth, imageMargin + imageHeight),
        RectF(pageMargin, imageMargin -15 + imageHeight + imageMargin, imageMargin + imageWidth, imageMargin + 2 * imageHeight + imageMargin),
        RectF(pageMargin + imageWidth + imageMargin, imageMargin, imageMargin + 2 * imageWidth + imageMargin, imageMargin + imageHeight),
        RectF(pageMargin + imageWidth + imageMargin, imageMargin -15 + imageHeight + imageMargin, imageMargin + 2 * imageWidth + imageMargin, imageMargin + 2 * imageHeight + imageMargin),
        RectF(pageMargin, imageMargin -30 + 2 * (imageHeight + imageMargin), imageMargin + imageWidth, imageMargin + 3 * imageHeight + 2 * imageMargin),
        RectF(pageMargin + imageWidth + imageMargin, imageMargin -30 + 2 * (imageHeight + imageMargin), imageMargin + 2 * imageWidth + imageMargin, imageMargin + 3 * imageHeight + 2 * imageMargin),
        RectF(pageMargin, imageMargin - 45 + 3 * (imageHeight + imageMargin), imageMargin + imageWidth, imageMargin + 4 * imageHeight + 3 * imageMargin),
        RectF(pageMargin + imageWidth + imageMargin, imageMargin -45 + 3 * (imageHeight + imageMargin), imageMargin + 2 * imageWidth + imageMargin, imageMargin + 4 * imageHeight + 3 * imageMargin)
    )

    // Draw the images in the grid with rotation
    for (position in positions) {
        // Create a rotated bitmap
        val rotatedBitmap = rotateBitmap(bitmap, 90f)

        // Calculate the position to center the rotated bitmap
        val left = position.left + (position.width() - rotatedBitmap.width) / 2
        val top = position.top + (position.height() - rotatedBitmap.height) / 2

        // Create a RectF for the rotated bitmap's position
        val rotatedPosition = RectF(left, top, left + rotatedBitmap.width, top + rotatedBitmap.height)

        // Draw the rotated bitmap
        canvas.drawBitmap(rotatedBitmap, null, rotatedPosition, null)
    }

    return layoutBitmap
}


//-------------------------------------------------- Small Passport Tools --------------------------------------------------
// Small Passport Screen
@Composable
fun SmallPassportScreen(
    onBack: () -> Unit,
    isBackgroundRemoved: Boolean,
    isBorderNeeded: Boolean
) {
    val context = LocalContext.current

    val bitmap = remember { mutableStateOf<Bitmap?>(null) }
    val finalBitmap = remember { mutableStateOf<Bitmap?>(null) }

    fun processFinalBitmap(original: Bitmap) {
        var workingBitmap = original

        if (isBackgroundRemoved) {
            removeBackground(
                inputBitmap = workingBitmap,
                onResult = { noBgBitmap ->
                    workingBitmap = noBgBitmap

                    if (isBorderNeeded) {
                        workingBitmap = addBorder(workingBitmap, 4, Color.Black.toArgb())
                    }

                    val layoutBitmap = passportPageLayoutSmall(workingBitmap)
                    bitmap.value = layoutBitmap
                    finalBitmap.value = layoutBitmap
                },
                onError = { error ->
                    Toast.makeText(context, "Background removal failed: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            )
        } else {
            if (isBorderNeeded) {
                workingBitmap = addBorder(workingBitmap, 4, Color.Black.toArgb())
            }

            val layoutBitmap = passportPageLayoutSmall(workingBitmap)
            bitmap.value = layoutBitmap
            finalBitmap.value = layoutBitmap
        }
    }

    // Image crop launcher
    val cropImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { intent ->
                UCrop.getOutput(intent)?.let { resultUri ->
                    val source = ImageDecoder.createSource(context.contentResolver, resultUri)
                    val croppedBitmap = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.isMutableRequired = true
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                    bitmap.value = croppedBitmap
                    processFinalBitmap(croppedBitmap)
                }
            }
        }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            cropImageLauncher.launch(createCropSmallPassport(context, it))
        }
    }

    // UI Layout
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = stringResource(id = R.string.twelve_small_passport),
            style = MaterialTheme.typography.headlineMedium,
            color = colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(onClick = { pickImageLauncher.launch("image/*") }) {
            Text(stringResource(id = R.string.select_image))
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(onClick = onBack) {
            Text(stringResource(id = R.string.back))
        }

        Spacer(modifier = Modifier.height(20.dp))

        finalBitmap.value?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Final Grid Layout",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            )
        } ?: bitmap.value?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Cropped Image",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        finalBitmap.value?.let { bitmap ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(onClick = {
                    saveImage(context, bitmap)
                    Toast.makeText(context, "Image Saved to File", Toast.LENGTH_SHORT).show()
                }) {
                    Text(stringResource(id = R.string.save_image))
                }

                Button(onClick = {
                    shareImage(context, bitmap)
                    Toast.makeText(context, "Sharing Image", Toast.LENGTH_SHORT).show()
                }) {
                    Text(stringResource(id = R.string.share_image))
                }
            }
        }
    }
}

// Cropping Tool
fun createCropSmallPassport(context: Context, uri: Uri): Intent {
    val destinationUri = Uri.fromFile(File(context.cacheDir, "cropped_${System.currentTimeMillis()}.jpg"))
    return UCrop.of(uri, destinationUri)
        .withAspectRatio(1.21f, 1.4f)
        .withMaxResultSize(363, 421)
        .getIntent(context)
}

// Small Passport Layout Tool
fun passportPageLayoutSmall(bitmap: Bitmap): Bitmap {
    // Define page dimensions in pixels (300 dpi)
    val pageWidth = 1181 // 10 cm in pixels
    val pageHeight = 1772 // 15 cm in pixels

    val imageWidth = 360
    val imageHeight = 420

    // Calculate margins
    val horizontalMargin = (pageWidth - 3 * imageWidth) / 4
    val verticalMargin = (pageHeight - 4 * imageHeight) / 5

    // Create a new bitmap with the layout size and white background
    val layoutBitmap = createBitmap(pageWidth, pageHeight)
    val canvas = Canvas(layoutBitmap)
    canvas.drawColor(WHITE) // Set background color to white

    // Draw the images in the grid without rotation
    for (row in 0 until 4) {
        for (col in 0 until 3) {
            val left = horizontalMargin + col * (imageWidth + horizontalMargin)
            val top = verticalMargin + row * (imageHeight + verticalMargin)
            val right = left + imageWidth
            val bottom = top + imageHeight

            val rect = RectF(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
            canvas.drawBitmap(bitmap, null, rect, null)
        }
    }

    return layoutBitmap
}


// -------------------------------------------------- Aadhaar Card Tools --------------------------------------------------
// Aadhaar Card Screen
@Composable
fun AadhaarCardHomeScreen() {
    var frontImageUri by remember { mutableStateOf<Uri?>(null) }
    var backImageUri by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current
    var finalBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Launchers to crop the images
    val cropImageLauncherFront = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { intent ->
                UCrop.getOutput(intent)?.let { resultUri ->
                    frontImageUri = resultUri
                    createAadhaarFinalBitmap(context, frontImageUri, backImageUri)?.let {
                        finalBitmap = it
                    }
                }
            }
        }
    }

    val cropImageLauncherBack = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { intent ->
                UCrop.getOutput(intent)?.let { resultUri ->
                    backImageUri = resultUri
                    createAadhaarFinalBitmap(context, frontImageUri, backImageUri)?.let {
                        finalBitmap = it
                    }
                }
            }
        }
    }

    // Launchers to pick the front and back images
    val pickImageLauncherFront = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            uri?.let {
                cropImageLauncherFront.launch(createCropAadhaarFront(context, it))
            }
        }
    )

    val pickImageLauncherBack = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            uri?.let {
                cropImageLauncherBack.launch(createCropAadhaarBack(context, it))
            }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = stringResource(id = R.string.select_aadhaar_card_images),
            color = colorScheme.onBackground,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 20.dp, top = 16.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))
        // Button to pick the front image
        Button(
            onClick = { pickImageLauncherFront.launch("image/*") },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (frontImageUri != null) Color(0xFF3DDC84) else Color.Gray
            )
        ) {
            Text(stringResource(id=R.string.select_front_image))
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Button to pick the back image
        Button(
            onClick = { pickImageLauncherBack.launch("image/*") },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (backImageUri != null) Color(0xFF3DDC84) else Color.Gray
            )
        ) {
            Text(stringResource(id=R.string.select_back_image))
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Save and Share buttons, shown only when both images are selected
        if (finalBitmap != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(onClick = {
                    finalBitmap?.let { bitmap ->
                        saveAadhaarImage(context, bitmap)
                        Toast.makeText(context, "Image Saved to File", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text(stringResource(id=R.string.save_image))
                }

                Button(onClick = {
                    finalBitmap?.let { bitmap ->
                        shareAadhaarImage(context, bitmap)
                        Toast.makeText(context, "Sharing Image", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text(stringResource(id=R.string.share_image))
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Display the final image
            finalBitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Final Aadhaar Layout",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                )
            }
        }
    }
}

// Front Cropping Tool
fun createCropAadhaarFront(context: Context, uri: Uri): Intent {
    val destinationUri = Uri.fromFile(File(context.cacheDir, "cropped_${System.currentTimeMillis()}.jpg"))
    return UCrop.of(uri, destinationUri)
        .withAspectRatio(8.5f, 5.5f)
        .withMaxResultSize(1275, 825)
        .getIntent(context)
}

// Back Cropping Tool
fun createCropAadhaarBack(context: Context, uri: Uri): Intent {
    val destinationUri = Uri.fromFile(File(context.cacheDir, "cropped_${System.currentTimeMillis()}.jpg"))
    return UCrop.of(uri, destinationUri)
        .withAspectRatio(8.5f, 5.5f)
        .withMaxResultSize(1275, 825)
        .getIntent(context)
}

// Aadhaar A4 Layout Tool
fun aadhaarPageLayout(frontBitmap: Bitmap, backBitmap: Bitmap): Bitmap {
    // Page dimensions in pixels (21 cm width x 29.7 cm height, scaled down for Aadhaar layout)
    val layoutWidth = 3150 // Width in pixels
    val layoutHeight = 4455 // Height in pixels

    // Create a bitmap to hold the final layout
    val layoutBitmap = createBitmap(layoutWidth, layoutHeight)
    val canvas = Canvas(layoutBitmap)
    canvas.drawColor(WHITE) // Set background color to white

    // Image dimensions (8.5 cm x 5.5 cm converted to pixels)
    val imageWidth = 1275 // 8.5 cm in pixels
    val imageHeight = 825 // 5.5 cm in pixels

    // Margins in pixels
    val topMargin = 400f // Top margin
    val sideMargin = (layoutWidth - (2 * imageWidth)) / 3f // Left and right margins between the images

    // Positions for front and back images
    val frontPosition = RectF(sideMargin, topMargin, sideMargin + imageWidth, topMargin + imageHeight)
    val backPosition = RectF(2 * sideMargin + imageWidth, topMargin, 2 * sideMargin + 2 * imageWidth, topMargin + imageHeight)

    // Draw the images on the canvas
    canvas.drawBitmap(frontBitmap, null, frontPosition, null)
    canvas.drawBitmap(backBitmap, null, backPosition, null)

    return layoutBitmap
}

// Aadhaar Finalizing Tool
fun createAadhaarFinalBitmap(context: Context, frontUri: Uri?, backUri: Uri?): Bitmap? {
    return if (frontUri != null && backUri != null) {
        val frontSource = ImageDecoder.createSource(context.contentResolver, frontUri)
        val frontBitmap = ImageDecoder.decodeBitmap(frontSource) { decoder, _, _ ->
            decoder.isMutableRequired = true
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }

        val backSource = ImageDecoder.createSource(context.contentResolver, backUri)
        val backBitmap = ImageDecoder.decodeBitmap(backSource) { decoder, _, _ ->
            decoder.isMutableRequired = true
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        aadhaarPageLayout(frontBitmap, backBitmap)
    } else {
        null
    }
}

// Image Saving Tool
fun saveAadhaarImage(context: Context, combinedBitmap: Bitmap) {
    val fileName = "Aadhaar_${System.currentTimeMillis()}.jpg"
    val directory = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "AadhaarImages")
    val contentResolver: ContentResolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "Aadhaar_${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Passported/AadhaarPhotos")
    }
    val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

    val file = File(directory, fileName)

    try {
        uri?.let {
            val outputStream: OutputStream? = contentResolver.openOutputStream(it)
            outputStream?.let { it1 -> combinedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, it1) }
            outputStream?.close()
        }

        // Notify the gallery about the new file so it is immediately available to the user
        MediaScannerConnection.scanFile(context, arrayOf(file.toString()), null, null)

        Toast.makeText(context, "Image saved to Pictures/Passported/AadhaarPhotos", Toast.LENGTH_LONG).show()
    } catch (e: IOException) {
        e.printStackTrace()
        Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
    }
}

// Image Sharing Tool
fun shareAadhaarImage(context: Context, combinedBitmap: Bitmap) {
    val fileName = "Aadhaar_${System.currentTimeMillis()}.jpg"
    val directory = File(context.cacheDir, "images")
    val file = File(directory, fileName)

    try {
        if (!directory.exists()) {
            directory.mkdirs()
        }

        FileOutputStream(file).use { outputStream ->
            combinedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            outputStream.flush()
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(shareIntent, "Share Image"))

    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Failed to share image: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}


// -------------------------------------------------- Collage Tools --------------------------------------------------
// Collage Screen
@Composable
fun CollageScreen() {
    val context = LocalContext.current
    val images = remember { mutableStateListOf<Bitmap>() } // List to store selected images
    var collageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val scaleFactors = remember { mutableStateListOf<Float>() }

    // Image Picker
    val pickImagesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
        onResult = { uris: List<Uri> ->
            images.clear()
            scaleFactors.clear()
            uris.take(7).forEach { uri ->
                val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                images.add(bitmap)
                scaleFactors.add(1f) // Initialize scale factor for each image
            }
            collageBitmap = generateCollageBitmap(images, scaleFactors) // Initial collage generation
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(id=R.string.create_collage),
            style = MaterialTheme.typography.headlineMedium,
            color = colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        Button(onClick = { pickImagesLauncher.launch("image/*") }) {
            Text(stringResource(id=R.string.select_images))
        }

        Spacer(modifier = Modifier.height(20.dp))

        images.forEachIndexed { index, image ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    bitmap = image.asImageBitmap(),
                    contentDescription = "Selected Image Thumbnail",
                    modifier = Modifier.size(50.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Slider for each image to control size
                Slider(
                    value = scaleFactors[index],
                    onValueChange = { newScale ->
                        scaleFactors[index] = newScale
                        adjustOtherScales(scaleFactors, index, newScale) // Adjust other sliders
                        collageBitmap = generateCollageBitmap(images, scaleFactors) // Update collage dynamically
                    },
                    valueRange = calculateScaleRange(images.size, image), // Calculate min-max based on number of images
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Display generated collage
        collageBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Collage",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Save and Share buttons for the collage
        collageBitmap?.let { bitmap ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(onClick = {
                    saveImage(context, bitmap)
                    Toast.makeText(context, "Image Saved", Toast.LENGTH_SHORT).show()
                }) {
                    Text(stringResource(id=R.string.save_image))
                }

                Button(onClick = {
                    shareImage(context, bitmap)
                    Toast.makeText(context, "Sharing Image", Toast.LENGTH_SHORT).show()
                }) {
                    Text(stringResource(id=R.string.share_image))
                }
            }
        }
    }
}

// Min-Max Scale Calculator
fun calculateScaleRange(imageCount: Int, image: Bitmap): ClosedFloatingPointRange<Float> {
    // Minimum scale to ensure at least 100 pixels in one dimension
    val minScale = maxOf(100f / image.width, 100f / image.height)

    // Maximum scale to ensure no dimension exceeds 2000 pixels
    val maxScaleForWidth = 2000f / image.width
    val maxScaleForHeight = 2000f / image.height
    val maxScale = minOf(maxScaleForWidth, maxScaleForHeight).coerceAtMost(
        when (imageCount) {
            1 -> 2f
            2 -> 1.5f
            in 3..4 -> 1.2f
            else -> 1f
        }
    )
    return minScale..maxScale
}

// Dynamic Scale Adjuster
fun adjustOtherScales(scaleFactors: MutableList<Float>, index: Int, newScale: Float) {
    val maxTotalScale = 7f // Set a max cumulative scale based on layout requirements

    // Adjust all other scale factors to fit within maxTotalScale
    val totalScale = scaleFactors.sum() - scaleFactors[index] + newScale
    if (totalScale > maxTotalScale) {
        val excess = totalScale - maxTotalScale
        for (i in scaleFactors.indices) {
            if (i != index) {
                scaleFactors[i] -= (excess / (scaleFactors.size - 1)).coerceAtLeast(0f)
            }
        }
    }
}

// Bitmap Generator
fun generateCollageBitmap(images: List<Bitmap>, scaleFactors: List<Float>): Bitmap {
    val a4Width = 2480 // Width in pixels for A4 (300 DPI)
    val a4Height = 3508 // Height in pixels for A4 (300 DPI)

    val collageBitmap = createBitmap(a4Width, a4Height)
    val canvas = Canvas(collageBitmap)
    canvas.drawColor(WHITE) // Set background color

    val margin = 70f
    var currentY = margin
    var currentX: Float = margin
    var rowHeight = 0 // Keep track of the tallest image in the current row

    images.forEachIndexed { index, image ->
        val scaleFactor = scaleFactors[index]
        val scaledWidth = (image.width * scaleFactor).toInt().coerceAtLeast(100)
        val scaledHeight = (image.height * scaleFactor).toInt().coerceAtLeast(100)

        // Find the best position for the image
        if (currentX + scaledWidth > a4Width) {
            // Move to the next row if there is not enough space in the current row
            currentY += rowHeight + margin
            currentX = margin // Reset X position
            rowHeight = 0 // Reset row height
        } else {
            currentX = if (currentX == margin) margin else currentX + margin // Start at margin or add margin
        }

        // Draw the image on the canvas
        val resizedImage = image.scale(scaledWidth, scaledHeight)
        canvas.drawBitmap(resizedImage, currentX, currentY, null)

        // Update currentX and rowHeight for the next image
        currentX += scaledWidth
        rowHeight = maxOf(rowHeight, scaledHeight) // Update row height
    }

    return collageBitmap
}

// -------------------------------------------------- Khasra Map Tools --------------------------------------------------
@Composable
fun KhasraMapScreen() {
    var selectedGiscode by remember { mutableStateOf("690802.084") }
    var plotNumber by remember { mutableStateOf(TextFieldValue("")) }

    val giscodeList = listOf(
        "Mukta" to "690802.084",
        "Pendruan" to "690802.100",
        "Sursi" to "690802.085",
        "Binoundha" to "690802.099",
    )

    val context = LocalContext.current

    val constructedUrl = "https://bhunaksha.cg.nic.in/22/plotreportCG.jsp?state=22&giscode=$selectedGiscode&plotno=${plotNumber.text}"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(id = R.string.khasra_map),
            style = MaterialTheme.typography.headlineMedium,
            color = colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        DropdownMenuExample(
            giscodeList = giscodeList,
            selectedGiscode = selectedGiscode,
            onGiscodeSelected = { selectedGiscode = it }
        )

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = plotNumber,
            onValueChange = { plotNumber = it },
            label = { Text(stringResource(id = R.string.enter_plot_no)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "${stringResource(id = R.string.constructed_url)}: $constructedUrl",
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.primary,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        Button(
            onClick = {
                if (plotNumber.text.isNotEmpty()) {
                    val intent = Intent(Intent.ACTION_VIEW, constructedUrl.toUri())
                    context.startActivity(intent)  // Open URL in browser
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(id=R.string.open_url))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownMenuExample(
    giscodeList: List<Pair<String, String>>,  // List of name-code pairs
    selectedGiscode: String,
    onGiscodeSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf(giscodeList.find { it.second == selectedGiscode }?.first ?: "") }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedItem,
            onValueChange = { /* No-op: disable user input */ },
            label = { Text(stringResource(id = R.string.select_village)) },
            readOnly = true,
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            giscodeList.forEach { (name, code) ->
                DropdownMenuItem(
                    text = { Text(text = name) },
                    onClick = {
                        selectedItem = name
                        onGiscodeSelected(code)  // Use the code for the selected item
                        expanded = false
                    }
                )
            }
        }
    }
}

// -------------------------------------------------- End of Code --------------------------------------------------