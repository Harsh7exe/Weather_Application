package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.database.WeatherDatabase
import com.example.data.model.DailyForecast
import com.example.data.model.HourlyForecast
import com.example.data.model.SavedCity
import com.example.data.model.WeatherReport
import com.example.data.repository.WeatherRepository
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.WeatherUiState
import com.example.ui.WeatherViewModel
import com.example.ui.WeatherViewModelFactory
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class ActiveTab {
    Current, Forecast, Map, Alerts
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Setup database and repository
        val database = WeatherDatabase.getDatabase(this)
        val repository = WeatherRepository(database.savedCityDao(), database.savedAlertDao())
        val factory = WeatherViewModelFactory(repository, applicationContext)

        setContent {
            MyApplicationTheme {
                MainAppScreen(factory)
            }
        }
    }
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun MainAppScreen(factory: WeatherViewModelFactory) {
    val context = LocalContext.current
    val viewModel: WeatherViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val savedCities by viewModel.savedCities.collectAsStateWithLifecycle()
    val severeAlerts by viewModel.severeAlerts.collectAsStateWithLifecycle()
    
    var activeTab by remember { mutableStateOf(ActiveTab.Current) }
    var showsHistoryDialog by remember { mutableStateOf(false) }

    // Request Notification permission for Android 13+ (API 33+)
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        if (isGranted) {
            Toast.makeText(context, "Weather Alerts Allowed!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Hardware notifications disabled. Alerts will still display in the app.", Toast.LENGTH_LONG).show()
        }
    }

    // Gradient Background (Fills screen beneath status bar)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DeepSlateBlue, SolidBlack)
                )
            )
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                BottomNavigationBar(
                    activeTab = activeTab,
                    onTabSelected = { activeTab = it },
                    alertsCount = severeAlerts.size
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding())
            ) {
                // App Branding Title Section
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 16.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = "Weather Logo",
                        tint = PaleSkyBlue,
                        modifier = Modifier.size(32.dp).padding(end = 8.dp)
                    )
                    Text(
                        text = "Harsh's Weather app",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp
                        ),
                        color = Color.White
                    )
                }

                // Top Search Bar & Actions Row
                TopSearchBarRow(
                    searchQuery = searchQuery,
                    onQueryChanged = { viewModel.updateSearchQuery(it) },
                    onSearchTriggered = { 
                        viewModel.searchCity(it)
                        showsHistoryDialog = false 
                    },
                    onHistoryClicked = { showsHistoryDialog = true }
                )

                // Render content depending on activeTab
                Box(modifier = Modifier.weight(1f)) {
                    when (activeTab) {
                        ActiveTab.Current -> {
                            CurrentWeatherContent(
                                uiState = uiState,
                                savedCities = savedCities,
                                viewModel = viewModel,
                                onHistoryItemClicked = { 
                                    viewModel.searchCity(it)
                                    viewModel.updateSearchQuery(it)
                                },
                                hasPermission = hasNotificationPermission,
                                onRequestPermission = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                            )
                        }
                        ActiveTab.Forecast -> {
                            ForecastContent(uiState = uiState)
                        }
                        ActiveTab.Map -> {
                            MapSimulationContent(uiState = uiState)
                        }
                        ActiveTab.Alerts -> {
                            AlertsHubContent(
                                severeAlerts = severeAlerts,
                                viewModel = viewModel,
                                onPermissionRequest = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Search History & Starred Drawer (Modal Overlay)
            if (showsHistoryDialog) {
                SavedCitiesOverlay(
                    savedCities = savedCities,
                    onDismiss = { showsHistoryDialog = false },
                    onCitySelected = { cityName ->
                        viewModel.searchCity(cityName)
                        viewModel.updateSearchQuery(cityName)
                        showsHistoryDialog = false
                    },
                    onDeleteCity = { viewModel.deleteCity(it) },
                    onToggleFavorite = { name, fav -> viewModel.toggleFavorite(name, fav) }
                )
            }
        }
    }
}

// --- TOP APP NAVIGATION & SEARCH ---

@Composable
fun TopSearchBarRow(
    searchQuery: String,
    onQueryChanged: (String) -> Unit,
    onSearchTriggered: (String) -> Unit,
    onHistoryClicked: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Search bar with white/10 fill background and backdrop blur effect
        Row(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .background(Color(0x1AFFFFFF), RoundedCornerShape(24.dp))
                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(24.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search icon",
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
            
            Spacer(modifier = Modifier.width(8.dp))

            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (searchQuery.isEmpty()) {
                    Text(
                        text = "Search city...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
                
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onQueryChanged,
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("search_field"),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (searchQuery.isNotBlank()) {
                                onSearchTriggered(searchQuery)
                                keyboardController?.hide()
                            }
                        }
                    ),
                    singleLine = true
                )
            }
            
            if (searchQuery.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChanged("") },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear search query",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Starred Cities Drawer Button
        IconButton(
            onClick = onHistoryClicked,
            modifier = Modifier
                .size(46.dp)
                .background(PaleSkyBlue.copy(alpha = 0.2f), CircleShape)
                .border(1.dp, Color(0x33FFFFFF), CircleShape)
                .testTag("bookmarks_button")
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Starred locations",
                tint = PaleSkyBlue
            )
        }
    }
}

// --- TAB DIRECTORY 1: CURRENT WEATHER SCREEN ---

@Composable
fun CurrentWeatherContent(
    uiState: WeatherUiState,
    savedCities: List<SavedCity>,
    viewModel: WeatherViewModel,
    onHistoryItemClicked: (String) -> Unit,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit
) {
    val context = LocalContext.current

    when (uiState) {
        is WeatherUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PaleSkyBlue)
            }
        }
        is WeatherUiState.Error -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Error icon",
                    tint = AlertBadgeOrange,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = uiState.message,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { viewModel.searchCity("San Francisco") },
                    colors = ButtonDefaults.buttonColors(containerColor = PaleSkyBlue, contentColor = DeepSlateBlue)
                ) {
                    Text("Retry default location")
                }
            }
        }
        is WeatherUiState.Success -> {
            val report = uiState.report
            val isFavorite = savedCities.find { it.name.equals(report.cityName, ignoreCase = true) }?.isFavorite ?: false

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Severe weather banner if applicable
                if (report.isAlertActive && report.activeAlertTitle != null) {
                    SevereAlertBadge(
                        title = report.activeAlertTitle,
                        desc = report.activeAlertDesc ?: "Warning active.",
                        onToggleSimulate = { viewModel.simulateSevereWeatherWarning(context, report.cityName) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Location Title & Favorite star icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = report.cityName,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 32.sp, fontWeight = FontWeight.Light),
                        color = Color.White,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { viewModel.toggleFavorite(report.cityName, !isFavorite) },
                        modifier = Modifier.testTag("favorite_button")
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                            contentDescription = "Favorite location toggle",
                            tint = if (isFavorite) WarnOrange else Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Giant temperature reading with elegant light-weight presentation
                Text(
                    text = "${report.temperature.toInt()}°",
                    style = MaterialTheme.typography.displayLarge,
                    color = Color.White,
                    modifier = Modifier.padding(top = 4.dp)
                )

                // Feels Like temperature display
                Text(
                    text = "Feels like: ${report.feelsLike.toInt()}°",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp),
                    color = PaleSkyBlue,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Condition subtitle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Icon(
                        imageVector = getWeatherIconByCondition(report.condition),
                        contentDescription = "Weather condition",
                        tint = getWeatherIconColor(report.condition),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = report.condition,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }

                // High / Low temp display
                Text(
                    text = "H: ${report.highTemp.toInt()}°   L: ${report.lowTemp.toInt()}°",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = Color.White.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // RUN SUITABILITY RADIAL COMPONENT (Unique Feature)
                ActivityScoreCard(
                    score = report.runSuitabilityScore,
                    runText = report.runSuitabilityText
                )

                Spacer(modifier = Modifier.height(20.dp))

                // AI SMART RECOMMENDATIONS CARD
                if (report.aiInsight != null) {
                    AiInsightsCard(insightText = report.aiInsight)
                    Spacer(modifier = Modifier.height(20.dp))
                }

                // GRID OF DETAILED METRICS
                MetricsGrid(report = report)

                // Simulated telemetry bar helper
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Updated: Just Now",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.4f)
                    )
                    if (!hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        TextButton(onClick = onRequestPermission) {
                            Text("Enable System Alerts", color = PaleSkyBlue, fontSize = 11.sp)
                        }
                    } else {
                        Button(
                            onClick = { viewModel.simulateSevereWeatherWarning(context, report.cityName) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AlertBadgeOrange.copy(alpha = 0.15f),
                                contentColor = Color.White
                            ),
                            border = BorderStroke(1.dp, AlertBadgeOrange.copy(alpha = 0.3f)),
                            modifier = Modifier.testTag("simulate_alert_btn")
                        ) {
                            Text("Trigger Test Alert", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// Severe Alert Badge with glow details
@Composable
fun SevereAlertBadge(title: String, desc: String, onToggleSimulate: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(AlertBadgeOrange.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .border(1.dp, AlertBadgeOrange.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .clickable { expanded = !expanded }
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Severe Alert Icon",
                    tint = WarnOrange,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    color = Color(0xFFFFCC80),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = if (expanded) "HIDE DETAILS" else "SEE DETAILS",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (expanded) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = desc,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = onToggleSimulate,
                modifier = Modifier.align(Alignment.End),
                colors = ButtonDefaults.textButtonColors(contentColor = WarnOrange)
            ) {
                Text("Test Alert Dispatch", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// Activity Rating custom layout drawing a custom Canvas ring
@Composable
fun ActivityScoreCard(score: Int, runText: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AccentIndigo.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
            .border(1.dp, AccentIndigo.copy(alpha = 0.25f), RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "RUN SUITABILITY",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = AccentIndigo,
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = runText,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = Color.White.copy(alpha = 0.95f)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Canvas drawing a dynamic circular track
        val ringColor = AccentIndigo
        val baseRingColor = Color.White.copy(alpha = 0.12f)
        val strokeWidth = 5.dp

        Box(
            modifier = Modifier.size(60.dp),
            contentAlignment = Alignment.Center
        ) {
            val sweepAngleAnim = remember { Animatable(0f) }
            LaunchedEffect(score) {
                sweepAngleAnim.animateTo(
                    targetValue = (score / 10f) * 360f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                )
            }

            Canvas(modifier = Modifier.size(52.dp)) {
                // Background Track
                drawCircle(
                    color = baseRingColor,
                    style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
                )
                // Active Score Arc
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = sweepAngleAnim.value,
                    useCenter = false,
                    style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
                )
            }
            
            Text(
                text = "$score/10",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

// AI Insights beautiful banner
@Composable
fun AiInsightsCard(insightText: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    colors = listOf(PaleSkyBlue.copy(alpha = 0.15f), AccentIndigo.copy(alpha = 0.15f))
                ),
                RoundedCornerShape(24.dp)
            )
            .border(
                BorderStroke(
                    1.dp, 
                    Brush.linearGradient(
                        colors = listOf(PaleSkyBlue.copy(alpha = 0.4f), AccentIndigo.copy(alpha = 0.2f))
                    )
                ), 
                RoundedCornerShape(24.dp)
            )
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Info, // AI info
                contentDescription = "AI intelligence info icon",
                tint = PaleSkyBlue,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "AI FORECAST ADVISOR",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = PaleSkyBlue,
                letterSpacing = 1.2.sp
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = insightText,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 13.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.Normal
        )
    }
}

// Metrics Grid (Uses 100% robust core icons)
@Composable
fun MetricsGrid(report: WeatherReport) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "HUMIDITY",
                value = "${report.humidityPercent}%",
                footer = "Dew point: ${(report.temperature - ((100 - report.humidityPercent)/5)).toInt()}°",
                icon = Icons.Default.Info
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "WIND SPEED",
                value = "${report.windSpeedMph.toInt()} mph",
                footer = "Direction: ${report.windDirection}",
                icon = Icons.Default.Refresh
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "UV INDEX",
                value = "${report.uvIndex}",
                footer = getUvIndexDescription(report.uvIndex),
                icon = Icons.Default.Info
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "PRESSURE",
                value = "${report.pressureMb} hPa",
                footer = "Stable atmospheric flow",
                icon = Icons.Default.KeyboardArrowDown
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "VISIBILITY",
                value = "${report.visibilityMiles.toInt()} mi",
                footer = if (report.visibilityMiles > 8) "Perfect vision range" else "Atmospheric fog layers",
                icon = Icons.Default.Search
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "FEELS LIKE",
                value = "${(report.temperature + (0.5 * (report.temperature - 60))).toInt()}°",
                footer = "Includes moisture",
                icon = Icons.Default.Info
            )
        }
    }
}

@Composable
fun MetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    footer: String,
    icon: ImageVector
) {
    Column(
        modifier = modifier
            .background(Color(0x0DFFFFFF), RoundedCornerShape(24.dp))
            .border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(24.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = title,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.5f),
                letterSpacing = 1.sp
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = value,
            fontSize = 22.sp,
            fontWeight = FontWeight.Light,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = footer,
            fontSize = 10.sp,
            color = Color.White.copy(alpha = 0.4f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}


// --- TAB DIRECTORY 2: FORECAST SCREEN ---

@Composable
fun ForecastContent(uiState: WeatherUiState) {
    when (uiState) {
        is WeatherUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PaleSkyBlue)
            }
        }
        is WeatherUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                Text("Error loading details. Use Search to inspect cities.", color = Color.White)
            }
        }
        is WeatherUiState.Success -> {
            val report = uiState.report

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Forecast for ${report.cityName}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light),
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                // Hourly Horizontal Panel
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x0DFFFFFF), RoundedCornerShape(24.dp))
                            .border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(24.dp))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "HOURLY TIMELINE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = PaleSkyBlue,
                            letterSpacing = 1.2.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(report.hourlyForecast) { hr ->
                                HourlyCardItem(hr = hr)
                            }
                        }
                    }
                }

                // 7-Day List Header
                item {
                    Text(
                        text = "EXTENDED MULTI-DAY FORECAST",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentIndigo,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                // Days rows
                items(report.dailyForecast) { day ->
                    DailyListRow(day = day)
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun HourlyCardItem(hr: HourlyForecast) {
    Column(
        modifier = Modifier
            .background(Color(0x0DFFFFFF), RoundedCornerShape(16.dp))
            .border(1.dp, Color(0x10FFFFFF), RoundedCornerShape(16.dp))
            .width(86.dp)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = hr.timeLabel,
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.6f),
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Icon(
            imageVector = getWeatherIconByCondition(hr.condition),
            contentDescription = null,
            tint = getWeatherIconColor(hr.condition),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${hr.temp.toInt()}°",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.WaterDrop,
                contentDescription = "Rain chance",
                tint = PaleSkyBlue,
                modifier = Modifier.size(10.dp)
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = "${hr.precipitationChance}%",
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Air,
                contentDescription = "Wind speed",
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(10.dp)
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = "${hr.windSpeedMph.toInt()}mph",
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun DailyListRow(day: DailyForecast) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x0DFFFFFF), RoundedCornerShape(20.dp))
            .border(1.dp, Color(0x0AFFFFFF), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = day.dayLabel,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.width(80.dp)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = getWeatherIconByCondition(day.condition),
                contentDescription = null,
                tint = getWeatherIconColor(day.condition),
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = day.condition,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.WaterDrop,
                        contentDescription = "Rain chance",
                        tint = PaleSkyBlue,
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = "Rain: ${day.precipitationChance}%",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "L: ${day.lowTemp.toInt()}°",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.4f)
            )
            Text(
                text = "H: ${day.highTemp.toInt()}°",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}


// --- TAB DIRECTORY 3: WEATHER RADAR MAP SIMULATOR ---

enum class MapAspect {
    Precipitation, Temperature, Moisture
}

@Composable
fun MapSimulationContent(uiState: WeatherUiState) {
    var selectedAspect by remember { mutableStateOf(MapAspect.Precipitation) }
    var scaleFactor by remember { mutableStateOf(1f) }

    when (uiState) {
        is WeatherUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PaleSkyBlue)
            }
        }
        is WeatherUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Search is required first to establish map coordinates.", color = Color.White)
            }
        }
        is WeatherUiState.Success -> {
            val report = uiState.report

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                
                // Map control headers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${report.cityName} Radar Screen",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    IconButton(
                        onClick = { scaleFactor = if (scaleFactor < 1.7f) scaleFactor + 0.3f else 1f }
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Zoom Radar Mapping", tint = PaleSkyBlue)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Selector tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x0DFFFFFF), RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MapAspect.values().forEach { aspect ->
                        val active = (selectedAspect == aspect)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (active) PaleSkyBlue else Color.Transparent)
                                .clickable { selectedAspect = aspect }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = aspect.name,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (active) DeepSlateBlue else Color.White.copy(alpha = 0.5f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // RADAR DECORATIVE CANVAS
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val mapSeed = report.cityName.hashCode()
                    
                    // Radar sweep rotation anime
                    val infiniteTransition = rememberInfiniteTransition()
                    val radarRotation by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(4000, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        )
                    )

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height
                        val centerX = canvasWidth / 2
                        val centerY = canvasHeight / 2

                        // 1. Draw coordinate grid lines (Simulated map longitude / latitude lines)
                        val gridStep = 80.dp.toPx()
                        var offsetGrid = 0f
                        while (offsetGrid < canvasWidth) {
                            drawLine(
                                color = Color.White.copy(alpha = 0.05f),
                                start = Offset(offsetGrid, 0f),
                                end = Offset(offsetGrid, canvasHeight),
                                strokeWidth = 1f
                            )
                            drawLine(
                                color = Color.White.copy(alpha = 0.05f),
                                start = Offset(0f, offsetGrid),
                                end = Offset(canvasWidth, offsetGrid),
                                strokeWidth = 1f
                            )
                            offsetGrid += gridStep
                        }

                        // 2. Draw radar target rings
                        drawCircle(
                            color = PaleSkyBlue.copy(alpha = 0.15f),
                            radius = 60.dp.toPx() * scaleFactor,
                            style = Stroke(width = 1.dp.toPx())
                        )
                        drawCircle(
                            color = PaleSkyBlue.copy(alpha = 0.1f),
                            radius = 120.dp.toPx() * scaleFactor,
                            style = Stroke(width = 1.dp.toPx())
                        )
                        drawCircle(
                            color = PaleSkyBlue.copy(alpha = 0.05f),
                            radius = 180.dp.toPx() * scaleFactor,
                            style = Stroke(width = 1.dp.toPx())
                        )

                        // 3. Draw simulated weather clouds onto the radar
                        val baseClr = when (selectedAspect) {
                            MapAspect.Precipitation -> Color(0xFF4CAF50) // Green rain clouds
                            MapAspect.Temperature -> Color(0xFFFF5722) // Red thermal zones
                            MapAspect.Moisture -> Color(0xFF2196F3) // Blue humidity moisture
                        }

                        // Localized weather hotspot points draw
                        val pointsCount = 4
                        for (i in 0 until pointsCount) {
                            val seedDelta = (mapSeed + i * 27)
                            val xPos = centerX + (seedDelta % 100 - 50) * 4.dp.toPx() * scaleFactor
                            val yPos = centerY + (seedDelta % 111 - 55) * 4.dp.toPx() * scaleFactor
                            val hRadius = (40 + (seedDelta % 35)).dp.toPx() * scaleFactor

                            // Soft glowing radar density cloud spots
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        baseClr.copy(alpha = 0.45f),
                                        baseClr.copy(alpha = 0.15f),
                                        Color.Transparent
                                    ),
                                    center = Offset(xPos, yPos),
                                    radius = hRadius
                                ),
                                center = Offset(xPos, yPos),
                                radius = hRadius
                            )
                        }

                        // 4. Draw Radar Sweep line (visual sweep overlay)
                        val sweepRad = 300.dp.toPx()
                        val angleRad = Math.toRadians(radarRotation.toDouble())
                        val sweepEndX = centerX + sweepRad * Math.cos(angleRad).toFloat()
                        val sweepEndY = centerY + sweepRad * Math.sin(angleRad).toFloat()

                        drawLine(
                            color = PaleSkyBlue.copy(alpha = 0.25f),
                            start = Offset(centerX, centerY),
                            end = Offset(sweepEndX, sweepEndY),
                            strokeWidth = 2.dp.toPx()
                        )
                        
                        // Small crosshead at physical center representing device orientation
                        drawCircle(
                            color = Color.White,
                            radius = 4.dp.toPx(),
                            center = Offset(centerX, centerY)
                        )
                    }

                    // Floating Legend indicator
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(14.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "RADAR INTENSITY",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(8.dp).background(
                                when(selectedAspect) {
                                    MapAspect.Precipitation -> Color(0xFF4CAF50)
                                    MapAspect.Temperature -> Color(0xFFFF5722)
                                    MapAspect.Moisture -> Color(0xFF2196F3)
                                }, CircleShape))
                            Text(
                                text = when(selectedAspect) {
                                    MapAspect.Precipitation -> "Moderate Rain (dBZ)"
                                    MapAspect.Temperature -> "Atmospheric Heat (°F)"
                                    MapAspect.Moisture -> "Relative Humidity (%)"
                                },
                                fontSize = 10.sp,
                                color = Color.White
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}


// --- TAB DIRECTORY 4: SEVERE ALERTS persistent hub & controller ---

@Composable
fun AlertsHubContent(
    severeAlerts: List<com.example.data.model.SavedAlert>,
    viewModel: WeatherViewModel,
    onPermissionRequest: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Severe Alerts Dispatch Console",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Generate and review simulated severe weather advisories below to test hardware response pathways.",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Trigger console actions card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0x0DFFFFFF), RoundedCornerShape(24.dp))
                .border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(24.dp))
                .padding(16.dp)
        ) {
            Text(
                text = "ALERTS SIMULATOR PANEL",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = AlertBadgeOrange,
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.simulateSevereWeatherWarning(context) },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertBadgeOrange, contentColor = Color.White),
                    modifier = Modifier.weight(1f).testTag("trigger_alert_full")
                ) {
                    Icon(imageVector = Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Trigger Alert", fontSize = 12.sp)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    OutlinedButton(
                        onClick = onPermissionRequest,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PaleSkyBlue),
                        border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Grant Permission", fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "LOG OF RECENT ALERTS (${severeAlerts.size})",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.5f),
                letterSpacing = 1.2.sp
            )
            if (severeAlerts.isNotEmpty()) {
                TextButton(
                    onClick = { viewModel.clearAllAlerts() },
                    colors = ButtonDefaults.textButtonColors(contentColor = AlertBadgeOrange)
                ) {
                    Text("Clear Log", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (severeAlerts.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0x05FFFFFF), RoundedCornerShape(24.dp))
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Notification bell icon",
                        tint = Color.White.copy(alpha = 0.2f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No severe alerts found",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Click 'Trigger Alert' above to simulate a tornado or blizzard emergency warning.",
                        color = Color.White.copy(alpha = 0.35f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(severeAlerts) { alert ->
                    AlertRowBox(
                        alert = alert,
                        onDelete = { viewModel.deleteAlert(alert.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun AlertRowBox(alert: com.example.data.model.SavedAlert, onDelete: () -> Unit) {
    val formatter = remember { SimpleDateFormat("MMM d, h:mm a", Locale.ROOT) }
    val formattedTime = formatter.format(Date(alert.time))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x0DFFFFFF), RoundedCornerShape(20.dp))
            .border(1.dp, AlertBadgeOrange.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = WarnOrange,
            modifier = Modifier.size(20.dp).padding(top = 2.dp)
        )
        
        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = alert.cityName,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = formattedTime,
                    fontSize = 9.sp,
                    color = Color.White.copy(alpha = 0.4f)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = alert.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = alert.description,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f),
                lineHeight = 18.sp
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete alarm element",
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}


// --- DETAIL DRAWER / SAVED CITIES BOTTOM DIALOG ---

@Composable
fun SavedCitiesOverlay(
    savedCities: List<SavedCity>,
    onDismiss: () -> Unit,
    onCitySelected: (String) -> Unit,
    onDeleteCity: (String) -> Unit,
    onToggleFavorite: (String, Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .background(Color(0xFF0F172A), RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .border(BorderStroke(1.dp, Color(0x33FFFFFF)), RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .clickable(enabled = false) { }
                .padding(20.dp)
        ) {
            // Drag pill header element
            Box(
                modifier = Modifier
                    .size(40.dp, 4.dp)
                    .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Search History & Bookmarks",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close bookmarks drawer", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (savedCities.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "History is empty. Search some cities to see them listed here!",
                        color = Color.White.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Starred items division
                    val favorites = savedCities.filter { city -> city.isFavorite }
                    val history = savedCities.filter { city -> !city.isFavorite }

                    if (favorites.isNotEmpty()) {
                        item {
                            Text(
                                text = "FAVORITE CITIES",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = WarnOrange,
                                letterSpacing = 1.2.sp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        items(favorites) { city ->
                            SavedCityRow(
                                city = city,
                                onSelected = onCitySelected,
                                onDelete = { onDeleteCity(city.name) },
                                onToggleFav = { onToggleFavorite(city.name, it) }
                            )
                        }
                    }

                    if (history.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "RECENT HISTORY",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = PaleSkyBlue,
                                letterSpacing = 1.2.sp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        items(history) { city ->
                            SavedCityRow(
                                city = city,
                                onSelected = onCitySelected,
                                onDelete = { onDeleteCity(city.name) },
                                onToggleFav = { onToggleFavorite(city.name, it) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SavedCityRow(
    city: SavedCity,
    onSelected: (String) -> Unit,
    onDelete: () -> Unit,
    onToggleFav: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x05FFFFFF), RoundedCornerShape(16.dp))
            .border(1.dp, Color(0x0DFFFFFF), RoundedCornerShape(16.dp))
            .clickable { onSelected(city.name) }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { onToggleFav(!city.isFavorite) }
        ) {
            Icon(
                imageVector = if (city.isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                contentDescription = "Quick Toggle Bookmark",
                tint = if (city.isFavorite) WarnOrange else Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = city.name,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = city.condition,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.5f)
            )
        }

        Text(
            text = "${city.temp.toInt()}°",
            fontSize = 20.sp,
            fontWeight = FontWeight.Light,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(18.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete record",
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}


// --- TAB NAVIGATION BAR ---

@Composable
fun BottomNavigationBar(
    activeTab: ActiveTab,
    onTabSelected: (ActiveTab) -> Unit,
    alertsCount: Int
) {
    NavigationBar(
        containerColor = Color(0x33000000), // white/5 like backdrop
        modifier = Modifier
            .border(TValues.BorderStrokeWidth, Color(0x12FFFFFF), RoundedCornerShape(0.dp))
            .navigationBarsPadding(),
        tonalElevation = 12.dp
    ) {
        NavigationBarItem(
            selected = (activeTab == ActiveTab.Current),
            onClick = { onTabSelected(ActiveTab.Current) },
            icon = { Icon(imageVector = Icons.Default.Place, contentDescription = "Active tab sun icon") },
            label = { Text("Current", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = DeepSlateBlue,
                selectedTextColor = Color.White,
                indicatorColor = PaleSkyBlue,
                unselectedIconColor = Color.White.copy(alpha = 0.5f),
                unselectedTextColor = Color.White.copy(alpha = 0.5f)
            ),
            modifier = Modifier.testTag("nav_current")
        )
        NavigationBarItem(
            selected = (activeTab == ActiveTab.Forecast),
            onClick = { onTabSelected(ActiveTab.Forecast) },
            icon = { Icon(imageVector = Icons.Default.List, contentDescription = "Forecast tab calendar month icon") },
            label = { Text("Forecast", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = DeepSlateBlue,
                selectedTextColor = Color.White,
                indicatorColor = PaleSkyBlue,
                unselectedIconColor = Color.White.copy(alpha = 0.5f),
                unselectedTextColor = Color.White.copy(alpha = 0.5f)
            ),
            modifier = Modifier.testTag("nav_forecast")
        )
        NavigationBarItem(
            selected = (activeTab == ActiveTab.Map),
            onClick = { onTabSelected(ActiveTab.Map) },
            icon = { Icon(imageVector = Icons.Default.Home, contentDescription = "Weather radar explorer map icon") },
            label = { Text("Radar Map", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = DeepSlateBlue,
                selectedTextColor = Color.White,
                indicatorColor = PaleSkyBlue,
                unselectedIconColor = Color.White.copy(alpha = 0.5f),
                unselectedTextColor = Color.White.copy(alpha = 0.5f)
            ),
            modifier = Modifier.testTag("nav_map")
        )
        NavigationBarItem(
            selected = (activeTab == ActiveTab.Alerts),
            onClick = { onTabSelected(ActiveTab.Alerts) },
            icon = {
                Box(contentAlignment = Alignment.TopEnd) {
                    Icon(imageVector = Icons.Default.Notifications, contentDescription = "Severe notifications alert hub icon")
                    if (alertsCount > 0) {
                        Box(
                            modifier = Modifier
                                .offset(x = 6.dp, y = (-2).dp)
                                .size(14.dp)
                                .background(AlertBadgeOrange, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$alertsCount",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            },
            label = { Text("Alerts", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = DeepSlateBlue,
                selectedTextColor = Color.White,
                indicatorColor = PaleSkyBlue,
                unselectedIconColor = Color.White.copy(alpha = 0.5f),
                unselectedTextColor = Color.White.copy(alpha = 0.5f)
            ),
            modifier = Modifier.testTag("nav_alerts")
        )
    }
}

// Custom theme thickness values helpers
object TValues {
    val BorderStrokeWidth = 1.dp
}


// --- HELPER COMPOSITIONS ---

fun getWeatherIconByCondition(condition: String): ImageVector {
    return when (condition.lowercase(Locale.ROOT)) {
        "sunny", "clear" -> Icons.Default.Place
        "partly cloudy" -> Icons.Default.Info
        "cloudy" -> Icons.Default.Info
        "rainy", "heavy rain", "tropical rain" -> Icons.Default.Refresh
        else -> Icons.Default.Info
    }
}

fun getWeatherIconColor(condition: String): Color {
    return when (condition.lowercase(Locale.ROOT)) {
        "sunny", "clear" -> Color(0xFFFFB300) // Gold
        "partly cloudy" -> Color(0xFF81D4FA) // Sky elements
        "cloudy" -> Color(0xFF90A4AE) // Slate Grey
        "rainy", "heavy rain", "tropical rain" -> Color(0xFF80DEEA) // Cyan water colors
        else -> Color(0xFFB0BEC5)
    }
}

fun getUvIndexDescription(uv: Int): String {
    return when {
        uv <= 2 -> "Low hazard risk"
        uv <= 5 -> "Moderate hazard"
        uv <= 7 -> "High solar impact"
        else -> "Extreme alert risk"
    }
}
