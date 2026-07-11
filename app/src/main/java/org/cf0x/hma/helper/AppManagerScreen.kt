package org.cf0x.hma.helper

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppManagerScreen(
    onBackClick: () -> Unit,
    onAppConfigClick: (List<String>) -> Unit = { _ -> },
    onExtraConfirm: ((List<String>) -> Unit)? = null,
    viewModel: AppManagerViewModel = viewModel()
) {
    val context = LocalContext.current
    val selectedTab by viewModel.selectedTab.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val apps by viewModel.filteredApps.collectAsState()
    val selectedCount by viewModel.selectedCount.collectAsState()
    val selectedPackages by viewModel.selectedPackages.collectAsState()
    val allApps by viewModel.allApps.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (onExtraConfirm != null) "Select Extra Apps"
                        else stringResource(R.string.nav_app_manager),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.clearSelection()
                        onBackClick()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (selectedCount > 0) {
                        if (onExtraConfirm != null) {
                            val allAppNames = remember(allApps) { allApps.map { it.packageName }.toSet() }
                            IconButton(onClick = {
                                val removed = selectedPackages.filter { it !in allAppNames }
                                if (removed.isNotEmpty()) {
                                    Toast.makeText(context,
                                        "Auto-removed: ${removed.joinToString(", ")}",
                                        Toast.LENGTH_LONG).show()
                                }
                                val valid = selectedPackages.filter { it in allAppNames }
                                viewModel.clearSelection()
                                onExtraConfirm(valid.toList())
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Confirm",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            IconButton(onClick = {
                                onAppConfigClick(selectedPackages.toList())
                            }) {
                                Icon(
                                    imageVector = Icons.Filled.Settings,
                                    contentDescription = "Config"
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ── Search Bar ──
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(stringResource(R.string.app_manager_search))
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null
                    )
                },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── Big Pill Toggle ──
            BigPillToggle(
                selectedIndex = selectedTab,
                options = listOf(
                    stringResource(R.string.app_manager_tab_user),
                    stringResource(R.string.app_manager_tab_system)
                ),
                onSelect = { viewModel.setSelectedTab(it) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── App List (with slide animation) ──
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    val direction = if (targetState > initialState) 1 else -1
                    (slideInHorizontally { width -> width * direction } + fadeIn()) togetherWith
                    (slideOutHorizontally { width -> -width * direction } + fadeOut())
                },
                label = "appList"
            ) { tab ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(apps, key = { it.packageName }) { appInfo ->
                        val isSel = appInfo.packageName in selectedPackages
                        AppListItem(
                            appInfo = appInfo,
                            isSelected = isSel,
                            onToggle = { viewModel.toggleSelection(appInfo.packageName) }
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────
//  Big Pill Toggle (User / System)
//  - Click to switch tabs with animated indicator
//  - Long-press + drag to slide, temporary enlargement
// ─────────────────────────────────────────────────
@Composable
private fun BigPillToggle(
    selectedIndex: Int,
    options: List<String>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var containerWidth by remember { mutableStateOf(0f) }
    val tabWidth = if (containerWidth > 0f) containerWidth / options.size else 0f

    // Drag state
    var isDragging by remember { mutableStateOf(false) }
    var dragAccumulated by remember { mutableStateOf(0f) }

    // Live indicator offset (tab position + drag), clamped to valid range
    val liveOffsetPx = (tabWidth * selectedIndex + dragAccumulated)
        .coerceIn(0f, (containerWidth - tabWidth).coerceAtLeast(0f))
    val animatedOffset by animateDpAsState(
        targetValue = with(density) { liveOffsetPx.toDp() },
        animationSpec = spring(dampingRatio = 0.7f, stiffness = if (isDragging) 1200f else 400f),
        label = "pillOffset"
    )

    // Indicator width = exact tab width
    val indicatorWidth by animateDpAsState(
        targetValue = if (containerWidth > 0f) {
            with(density) { (containerWidth / options.size).toDp() }
        } else 0.dp,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "indicatorWidth"
    )

    val targetScale = if (isDragging) 1.08f else 1f
    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "pillScale"
    )

    Box(
        modifier = modifier
            .height(56.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        isDragging = true
                        dragAccumulated = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragAccumulated += dragAmount.x
                    },
                    onDragEnd = {
                        isDragging = false
                        if (containerWidth > 0f) {
                            val cw = containerWidth
                            val finalPos = cw / options.size * selectedIndex + dragAccumulated
                            val newIndex = if (finalPos < cw / 2f) 0 else 1
                            if (newIndex != selectedIndex) {
                                onSelect(newIndex)
                            }
                        }
                        dragAccumulated = 0f
                    },
                    onDragCancel = {
                        isDragging = false
                        dragAccumulated = 0f
                    }
                )
            },
        contentAlignment = Alignment.TopStart
    ) {
        // Sliding indicator
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .offset(x = animatedOffset)
                .width(indicatorWidth)
                .scale(animatedScale)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.secondaryContainer)
        )

        // Labels row (also measures container width)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { containerWidth = it.width.toFloat() },
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEachIndexed { index, label ->
                val isSelected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onSelect(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 16.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected)
                            MaterialTheme.colorScheme.onSecondaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────
//  App List Item — click toggles selection with Y-axis flip
// ─────────────────────────────────────────────────
@Composable
private fun AppListItem(
    appInfo: AppInfo,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    val context = LocalContext.current
    var iconBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(appInfo.packageName) {
        val drawable = runCatching {
            context.packageManager.getApplicationIcon(appInfo.packageName)
        }.getOrNull()
        iconBitmap = drawable?.toBitmap(48, 48, null)
    }

    val rotation by animateFloatAsState(
        targetValue = if (isSelected) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "flipRotation"
    )

    // Accent border when selected
    val border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        shape = MaterialTheme.shapes.medium,
        border = border,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Rotatable icon area
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .graphicsLayer {
                        rotationY = rotation
                        cameraDistance = 12f * density
                    },
                contentAlignment = Alignment.Center
            ) {
                // Front face (visible when rotation < 90°)
                if (rotation < 90f) {
                    val icon = iconBitmap
                    if (icon != null) {
                        Image(
                            bitmap = icon.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(36.dp)
                        )
                    } else {
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
                // Back face (visible when rotation >= 90°)
                if (rotation >= 90f) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .graphicsLayer { rotationY = -rotation },
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = MaterialTheme.shapes.extraLarge,
                            color = Color(0xFF4CAF50)
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appInfo.appLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = appInfo.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
