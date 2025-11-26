package com.example.besu.wear

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*

// RENAMED FUNCTION (File name is still BesuWearApp.kt)
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BesuWatchFace(
    isMotionEnabled: Boolean,
    activeProfile: MotionProfileType, // NEW
    customConfig: WatchConfig,
    onToggleMotion: (Boolean) -> Unit,
    onSwitchProfile: () -> Unit,      // NEW
    onSendCommand: (String) -> Unit
) {
    // PAGE ORDER:
    // 0: Controls
    // 1: Native Gestures
    // 2..N: Custom Pages
    // Last: Suggestions (Most Used)

    val staticBeforeCount = 2
    val customPageCount = customConfig.pages.size
    val totalPageCount = staticBeforeCount + customPageCount + 1

    val pagerState = rememberPagerState(pageCount = { totalPageCount })
    var subMenuCommand by remember { mutableStateOf<WearCommand?>(null) }
    val context = LocalContext.current

    val pageIndicatorState = remember {
        object : PageIndicatorState {
            override val pageOffset: Float get() = pagerState.currentPageOffsetFraction
            override val selectedPage: Int get() = pagerState.currentPage
            override val pageCount: Int get() = pagerState.pageCount
        }
    }

    Scaffold(
        timeText = { TimeText() },
        pageIndicator = { HorizontalPageIndicator(pageIndicatorState = pageIndicatorState) }
    ) {
        if (subMenuCommand != null) {
            SubMenuScreen(
                parent = subMenuCommand!!,
                onDismiss = { subMenuCommand = null },
                onSend = { path ->
                    onSendCommand(path)
                    subMenuCommand = null
                }
            )
        } else {
            HorizontalPager(state = pagerState) { page ->
                when {
                    // 0: CONTROLS
                    page == 0 -> ControlPage(
                        isEnabled = isMotionEnabled,
                        profileType = activeProfile,
                        onToggle = { enabled -> onToggleMotion(enabled) },
                        onDoubleTap = { if (isMotionEnabled) onSwitchProfile() },
                        onOpenSettings = {
                            context.startActivity(Intent(context, TrainingActivity::class.java))
                        }
                    )

                    // 1: NATIVE GESTURES
                    page == 1 -> GridPageNative(
                        commands = WearVocabulary.motionSet,
                        onSend = onSendCommand,
                        onLongPress = { subMenuCommand = it }
                    )

                    // CUSTOM PAGES
                    page >= staticBeforeCount && page < (staticBeforeCount + customPageCount) -> {
                        val customIndex = page - staticBeforeCount
                        val customPage = customConfig.pages[customIndex]
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(customPage.title, style = MaterialTheme.typography.caption2, color = Color.Gray)
                            GridPageDynamic(slots = customPage.slots, onSend = onSendCommand)
                        }
                    }

                    // LAST PAGE: SUGGESTIONS
                    else -> {
                        val slots = if (customConfig.topItems.isNotEmpty()) {
                            customConfig.topItems
                        } else {
                            getDefaultTopItems()
                        }
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Most Used", style = MaterialTheme.typography.caption2, color = Color.Gray)
                            GridPageDynamic(slots = slots, onSend = onSendCommand)
                        }
                    }
                }
            }
        }
    }
}

private fun getDefaultTopItems(): List<WatchSlot> {
    return listOf(
        WatchSlot("Yes", "✅", "/gesture/thumbsup"),
        WatchSlot("No", "🚫", "/gesture/no"),
        WatchSlot("Help", "🆘", "I need help."),
        WatchSlot("Toilet", "🚽", "I need to use the toilet."),
        WatchSlot("Hungry", "🍔", "I am hungry."),
        WatchSlot("Tired", "😴", "I am tired.")
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ControlPage(
    isEnabled: Boolean,
    profileType: MotionProfileType,
    onToggle: (Boolean) -> Unit,
    onDoubleTap: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background)
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Besu AI", style = MaterialTheme.typography.title2)
            Spacer(modifier = Modifier.height(8.dp))

            // CUSTOM TOGGLE BUTTON WITH DOUBLE TAP
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        color = if (isEnabled) Color(0xFF1B5E20) else Color.DarkGray,
                        shape = CircleShape
                    )
                    .combinedClickable(
                        onClick = { onToggle(!isEnabled) },
                        onDoubleClick = onDoubleTap
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isEnabled) "ON" else "OFF",
                        style = MaterialTheme.typography.title1,
                        color = Color.White
                    )
                    if (isEnabled) {
                        Text(
                            text = if (profileType == MotionProfileType.STANDARD) "STD" else "CUST",
                            style = MaterialTheme.typography.caption2,
                            color = Color(0xFF81C784)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isEnabled) {
                Text(
                    if (profileType == MotionProfileType.STANDARD) "Physics Engine" else "Custom Trained",
                    style = MaterialTheme.typography.caption1,
                    color = if (profileType == MotionProfileType.CUSTOM) Color.Cyan else Color.Gray
                )
            } else {
                Text("Touch Only", style = MaterialTheme.typography.caption1, color = Color.Gray)
            }
        }

        // Pull Up Drawer
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CompactChip(
                onClick = onOpenSettings,
                label = {
                    Text(
                        "TRAIN GESTURES",
                        style = MaterialTheme.typography.caption2,
                        color = Color.Black
                    )
                },
                icon = {
                    Text("⚙️", style = MaterialTheme.typography.body2)
                },
                colors = ChipDefaults.secondaryChipColors(backgroundColor = Color(0xFFEEEEEE))
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

// --- HELPER COMPOSABLES ---

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GridPageNative(
    commands: List<WearCommand>,
    onSend: (String) -> Unit,
    onLongPress: (WearCommand) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(14.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val chunked = commands.chunked(2)
        chunked.forEach { rowItems ->
            Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                rowItems.forEach { cmd ->
                    GridItem(
                        emoji = cmd.emoji,
                        label = cmd.label,
                        onClick = { onSend(cmd.path) },
                        onLongClick = { onLongPress(cmd) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GridPageDynamic(slots: List<WatchSlot>, onSend: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(14.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val displaySlots = slots.take(6)
        val chunked = displaySlots.chunked(2)
        chunked.forEach { rowItems ->
            Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                rowItems.forEach { slot ->
                    if (slot.path.isNotEmpty()) {
                        GridItem(
                            emoji = slot.emoji,
                            label = slot.label,
                            onClick = { onSend(slot.path) },
                            onLongClick = null
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                    }
                }
                if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RowScope.GridItem(emoji: String, label: String, onClick: () -> Unit, onLongClick: (() -> Unit)?) {
    Box(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1f)
            .padding(2.dp)
            .background(Color(0xFF222222), CircleShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, style = MaterialTheme.typography.title2)
            Text(label, style = MaterialTheme.typography.caption2, maxLines = 1, color = Color.LightGray)
        }
    }
}

@Composable
fun SubMenuScreen(parent: WearCommand, onDismiss: () -> Unit, onSend: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Variant", style = MaterialTheme.typography.caption1, color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))
        Chip(
            onClick = { onSend(parent.path) },
            label = { Text("Standard") },
            icon = { Text(parent.emoji) },
            colors = ChipDefaults.secondaryChipColors()
        )
        Spacer(modifier = Modifier.height(4.dp))
        if (parent.subCommands.isNotEmpty()) {
            val sub = parent.subCommands[0]
            Chip(
                onClick = { onSend(sub.path) },
                label = { Text("Alt: ${sub.label}") },
                icon = { Text(sub.emoji) },
                colors = ChipDefaults.primaryChipColors()
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onDismiss, modifier = Modifier.size(30.dp)) { Text("X") }
    }
}