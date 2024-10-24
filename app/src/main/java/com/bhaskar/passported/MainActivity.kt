package com.bhaskar.passported

// Android Framework
import android.app.Activity // Base class for activities
import android.app.Application // Base class for application
import android.content.ContentResolver // Content Resolver for data access
import android.content.ContentValues // Values for content provider
import android.content.Context // Context for app resources
import android.content.Intent // Intent for launching activities
import android.content.SharedPreferences // Preferences storage
import android.graphics.Bitmap // Bitmap image representation
import android.graphics.Canvas // Canvas for drawing
import android.graphics.Color.WHITE // White color constant
import android.graphics.Matrix // Matrix for transformations
import android.graphics.RectF // Rectangular area
import android.media.MediaScannerConnection // Media scanner connection
import android.net.Uri // URI representation
import android.os.Bundle // Bundle for activity state
import android.os.Environment // Environment for storage paths
import android.provider.MediaStore // MediaStore for media access
import android.widget.Toast // Toast messages

// Jetpack Compose
import androidx.activity.ComponentActivity // Base activity for Compose
import androidx.activity.compose.ManagedActivityResultLauncher // Activity result launcher
import androidx.activity.compose.rememberLauncherForActivityResult // Remember launcher for result
import androidx.activity.compose.setContent // Set content for activity
import androidx.activity.result.contract.ActivityResultContracts // Contracts for result handling
import androidx.compose.foundation.Image // Image component
import androidx.compose.foundation.background // Background modifier
import androidx.compose.foundation.layout.Arrangement // Arrangement for layout
import androidx.compose.foundation.layout.Box // Box layout
import androidx.compose.foundation.layout.Column // Column layout
import androidx.compose.foundation.layout.Row // Row layout
import androidx.compose.foundation.layout.Spacer // Spacer for layout
import androidx.compose.foundation.layout.fillMaxSize // Fill maximum size
import androidx.compose.foundation.layout.fillMaxWidth // Fill maximum width
import androidx.compose.foundation.layout.height // Height modifier
import androidx.compose.foundation.layout.padding // Padding modifier
import androidx.compose.foundation.layout.width // Width modifier
import androidx.compose.foundation.rememberScrollState // Scroll state
import androidx.compose.foundation.verticalScroll // Vertical scrolling modifier
import androidx.compose.material.icons.Icons // Material icons
import androidx.compose.material.icons.filled.Face // Face icon
import androidx.compose.material.icons.filled.Person // Person icon
import androidx.compose.material.icons.filled.Settings // Settings icon
import androidx.compose.material3.Button // Button component
import androidx.compose.material3.ButtonDefaults // Default button styles
import androidx.compose.material3.Icon // Icon component
import androidx.compose.material3.MaterialTheme // Material theme
import androidx.compose.material3.MaterialTheme.colorScheme // Color scheme of Material theme
import androidx.compose.material3.NavigationBar // Navigation bar component
import androidx.compose.material3.NavigationBarItem // Navigation bar item component
import androidx.compose.material3.Text // Text component
import androidx.compose.runtime.Composable // Composable function annotation
import androidx.compose.runtime.collectAsState // Collect state from flow
import androidx.compose.runtime.getValue // Get value from state
import androidx.compose.runtime.mutableStateOf // Mutable state holder
import androidx.compose.runtime.remember // Remember state
import androidx.compose.runtime.setValue // Set value in state
import androidx.compose.ui.Alignment // Alignment options
import androidx.compose.ui.Modifier // Modifier for UI components
import androidx.compose.ui.graphics.Color // Color representation
import androidx.compose.ui.graphics.asImageBitmap // Convert to ImageBitmap
import androidx.compose.ui.graphics.toArgb // Convert color to ARGB
import androidx.compose.ui.platform.LocalContext // Current context
import androidx.compose.ui.res.painterResource // Resource painter
import androidx.compose.ui.res.stringResource // String resource access
import androidx.compose.ui.unit.dp // Density-independent pixels
import androidx.compose.ui.unit.sp // Scaled pixels
import androidx.core.content.FileProvider // File provider support
import androidx.lifecycle.AndroidViewModel // ViewModel with Android context
import androidx.lifecycle.ViewModel // Base class for ViewModel
import androidx.lifecycle.ViewModelProvider // ViewModel provider
import androidx.lifecycle.viewModelScope // ViewModel scope for coroutines
import androidx.lifecycle.viewmodel.compose.viewModel // ViewModel for Compose
import androidx.navigation.NavHostController // Navigation host controller
import androidx.navigation.compose.NavHost // Navigation host composable
import androidx.navigation.compose.composable // Composable for navigation
import androidx.navigation.compose.currentBackStackEntryAsState // Get current back stack entry
import androidx.navigation.compose.rememberNavController // Remember navigation controller

// UI Theme
import com.bhaskar.passported.ui.theme.PassportedTheme // Theme for the app

// Image Cropping Library
import com.yalantis.ucrop.UCrop // Image cropping functionality

// Kotlin Coroutines
import kotlinx.coroutines.channels.awaitClose // Close channel on completion
import kotlinx.coroutines.flow.Flow // Flow interface for streams
import kotlinx.coroutines.flow.SharingStarted // Sharing started options
import kotlinx.coroutines.flow.StateFlow // StateFlow interface for state management
import kotlinx.coroutines.flow.callbackFlow // Create a callback flow
import kotlinx.coroutines.flow.stateIn // Convert flow to state
import java.io.ByteArrayOutputStream // Byte array output stream
import java.io.File // File representation
import java.io.FileOutputStream // File output stream
import java.io.IOException // Exception handling
import java.io.OutputStream // Output stream representation
import java.util.Locale // Locale support

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
        sharedPreferences.edit().putString("selected_language", languageCode).apply()
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
        super.onCreate(savedInstanceState)

        languageManager = LanguagePreferenceManager.getInstance(this)

        // Initialize the language based on the stored preference
        val initialLanguage = languageManager.getLanguage() // Fetch initial language from preferences
        setAppLocale(initialLanguage) // Set the locale based on initial language

        setContent {
            PassportedTheme {
                window.statusBarColor = colorScheme.background.toArgb()
                MainScreen(currentLanguage = initialLanguage) { newLanguage ->
                    // Update the app's language preference
                    if (newLanguage != initialLanguage) {
                        languageManager.setLanguage(newLanguage)
                        setAppLocale(newLanguage) // Update the locale for the app
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
                composable("aadhar_card_mode") { AadharCardHomeScreen() }
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

// Navigation Bar
@Composable
fun AppNavigationBar(navController: NavHostController) {
    // Get the current back stack entry and route
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    NavigationBar {
        NavigationBarItem(
            label = { Text(stringResource(R.string.passport_photo)) },
            icon = { Icon(Icons.Default.Face, contentDescription = null) },
            selected = currentRoute == "passport_photo_mode",  // Check if the route matches
            onClick = {
                if (currentRoute != "passport_photo_mode") {  // Prevent navigation if already on the same screen
                    navController.navigate("passport_photo_mode") {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        )
        NavigationBarItem(
            label = { Text(stringResource(R.string.aadhar_card)) },
            icon = { Icon(Icons.Default.Person, contentDescription = null) },
            selected = currentRoute == "aadhar_card_mode",  // Check if the route matches
            onClick = {
                if (currentRoute != "aadhar_card_mode") {  // Prevent navigation if already on the same screen
                    navController.navigate("aadhar_card_mode") {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        )
        NavigationBarItem(
            label = { Text(stringResource(R.string.settings)) },
            icon = { Icon(imageVector = Icons.Default.Settings, contentDescription = null) },
            selected = currentRoute == "user_settings",  // Check if the route matches
            onClick = {
                if (currentRoute != "user_settings") {  // Prevent navigation if already on the same screen
                    navController.navigate("user_settings") {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
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
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = stringResource(
            id = R.string.settings),
            fontSize = 32.sp,
            color = colorScheme.onBackground,)

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
    return Uri.parse(path)
}


// -------------------------------------------------- Passport Photo Tools --------------------------------------------------
// Passport Photo Screen
@Composable
fun PassportPhotoHomeScreen() {
    var selectedMode by remember { mutableStateOf<String?>(null) }

    when (selectedMode) {
        "8xMode" -> LargePassportScreen { selectedMode = null }
        "12xMode" -> SmallPassportScreen { selectedMode = null }
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
                    verticalArrangement = Arrangement.Top,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Text(
                        text = stringResource(id=R.string.select_passport_photo_size),
                        style = MaterialTheme.typography.headlineMedium,
                        color = colorScheme.onBackground,
                        modifier = Modifier.padding(bottom = 20.dp, top = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(onClick = { selectedMode = "8xMode"}) {
                        Text(stringResource(id=R.string.eight_large_photo))
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(onClick = {selectedMode = "12xMode"}) {
                        Text(stringResource(id=R.string.twelve_small_passport))
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
        put(MediaStore.Images.Media.DISPLAY_NAME, "cropped_image.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Passported")
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


// -------------------------------------------------- Large Passport Tools --------------------------------------------------
// Large Passport Screen
@Composable
fun LargePassportScreen(onBack: () -> Unit) {
    val bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var finalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val context = LocalContext.current

    // Launcher to handle crop result
    val cropImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { intent ->
                UCrop.getOutput(intent)?.let { resultUri ->
                    val croppedBitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, resultUri)
                    finalBitmap = passportPageLayoutLarge(croppedBitmap)
                }
            }
        }
    }

    // Launcher to pick the image
    val pickImageLauncher: ManagedActivityResultLauncher<String, Uri?> = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            uri?.let {
                cropImageLauncher.launch(createCropLargePassport(context, it))
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
            text = stringResource(id=R.string.eight_large_photo),
            style = MaterialTheme.typography.headlineMedium,
            color = colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 20.dp)

        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(onClick = {pickImageLauncher.launch("image/*") }) {
            Text(stringResource(id=R.string.select_image))
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(onClick = onBack) {
            Text(stringResource(id=R.string.back))
        }

        Spacer(modifier = Modifier.height(20.dp))
        finalBitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Final Grid Layout",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            )
        } ?: bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Cropped Image",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        finalBitmap?.let { bitmap ->
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
                    Text(stringResource(id=R.string.save_image))
                }

                Button(onClick = {
                    context.run { shareImage(this,bitmap) }
                    Toast.makeText(context, "Sharing Image", Toast.LENGTH_SHORT).show()
                }) {
                    Text(stringResource(id=R.string.share_image))
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
    val layoutBitmap = Bitmap.createBitmap(layoutWidth, layoutHeight, Bitmap.Config.ARGB_8888)
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
fun SmallPassportScreen(onBack: () -> Unit) {
    val bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var finalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val context = LocalContext.current

    // Launcher to handle crop result
    val cropImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { intent ->
                UCrop.getOutput(intent)?.let { resultUri ->
                    val croppedBitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, resultUri)
                    finalBitmap = passportPageLayoutSmall(croppedBitmap)
                }
            }
        }
    }

    // Launcher to pick the image
    val pickImageLauncher: ManagedActivityResultLauncher<String, Uri?> = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            uri?.let {
                cropImageLauncher.launch(createCropSmallPassport(context, it))
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
            text = "Choose 12x Passport Photo",
            style = MaterialTheme.typography.headlineMedium,
            color = colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(onClick = {pickImageLauncher.launch("image/*") }) {
            Text(stringResource(id=R.string.select_image))
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(onClick = onBack) {
            Text(stringResource(id=R.string.back))
        }

        Spacer(modifier = Modifier.height(20.dp))
        finalBitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Final Grid Layout",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            )
        } ?: bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Cropped Image",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        finalBitmap?.let { bitmap ->
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
                    Text(stringResource(id=R.string.save_image))
                }

                Button(onClick = {
                    context.run { shareImage(context = this, bitmap = bitmap) }
                    Toast.makeText(context, "Sharing Image", Toast.LENGTH_SHORT).show()
                }) {
                    Text(stringResource(id=R.string.share_image))
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
    val layoutBitmap = Bitmap.createBitmap(pageWidth, pageHeight, Bitmap.Config.ARGB_8888)
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


// -------------------------------------------------- Aadhar Card Tools --------------------------------------------------
// Aadhar Card Screen
@Composable
fun AadharCardHomeScreen() {
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
                    createAadharFinalBitmap(context, frontImageUri, backImageUri)?.let {
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
                    createAadharFinalBitmap(context, frontImageUri, backImageUri)?.let {
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
                cropImageLauncherFront.launch(createCropAadharFront(context, it))
            }
        }
    )

    val pickImageLauncherBack = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            uri?.let {
                cropImageLauncherBack.launch(createCropAadharBack(context, it))
            }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = stringResource(id = R.string.select_aadhar_card_images),
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
                        saveAadharImage(context, bitmap)
                        Toast.makeText(context, "Image Saved to File", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text(stringResource(id=R.string.save_image))
                }

                Button(onClick = {
                    finalBitmap?.let { bitmap ->
                        shareAadharImage(context, bitmap)
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
                    contentDescription = "Final Aadhar Layout",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                )
            }
        }
    }
}

// Front Cropping Tool
fun createCropAadharFront(context: Context, uri: Uri): Intent {
    val destinationUri = Uri.fromFile(File(context.cacheDir, "cropped_${System.currentTimeMillis()}.jpg"))
    return UCrop.of(uri, destinationUri)
        .withAspectRatio(8.5f, 5.5f)
        .withMaxResultSize(1275, 825)
        .getIntent(context)
}

// Back Cropping Tool
fun createCropAadharBack(context: Context, uri: Uri): Intent {
    val destinationUri = Uri.fromFile(File(context.cacheDir, "cropped_${System.currentTimeMillis()}.jpg"))
    return UCrop.of(uri, destinationUri)
        .withAspectRatio(8.5f, 5.5f)
        .withMaxResultSize(1275, 825)
        .getIntent(context)
}

// Aadhar A4 Layout Tool
fun aadharPageLayout(frontBitmap: Bitmap, backBitmap: Bitmap): Bitmap {
    // Page dimensions in pixels (21 cm width x 29.7 cm height, scaled down for Aadhar layout)
    val layoutWidth = 3150 // Width in pixels
    val layoutHeight = 4455 // Height in pixels

    // Create a bitmap to hold the final layout
    val layoutBitmap = Bitmap.createBitmap(layoutWidth, layoutHeight, Bitmap.Config.ARGB_8888)
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

// Aadhar Finalizing Tool
fun createAadharFinalBitmap(context: Context, frontUri: Uri?, backUri: Uri?): Bitmap? {
    return if (frontUri != null && backUri != null) {
        val frontBitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, frontUri)
        val backBitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, backUri)
        aadharPageLayout(frontBitmap, backBitmap)
    } else {
        null
    }
}

// Image Saving Tool
fun saveAadharImage(context: Context, combinedBitmap: Bitmap) {
    val fileName = "Aadhar_${System.currentTimeMillis()}.jpg"
    val directory = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "AadharImages")

    if (!directory.exists()) {
        directory.mkdirs()
    }

    val file = File(directory, fileName)

    try {
        val outputStream = FileOutputStream(file)
        combinedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        outputStream.let { it1 -> combinedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, it1) }
        outputStream.close()

        // Notify the gallery about the new file so it is immediately available to the user
        MediaScannerConnection.scanFile(context, arrayOf(file.toString()), null, null)

        Toast.makeText(context, "Image saved to ${file.absolutePath}", Toast.LENGTH_LONG).show()
    } catch (e: IOException) {
        e.printStackTrace()
        Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
    }
}

// Image Sharing Tool
fun shareAadharImage(context: Context, combinedBitmap: Bitmap) {
    val fileName = "Aadhar_${System.currentTimeMillis()}.jpg"
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

// -------------------------------------------------- End of Code --------------------------------------------------