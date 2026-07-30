package com.dmap.map

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.dmap.R
import com.dmap.place.PlaceSummary
import com.dmap.place.SelectedPlace
import com.dmap.place.SelectedPlaceType
import com.dmap.place.SearchResult

@Composable
internal fun LayerToggleButton(
    baseLayer: MapBaseLayer,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ortofotoActive = baseLayer == MapBaseLayer.Ortofoto
    Surface(
        onClick = onClick,
        modifier = modifier.size(48.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
        tonalElevation = 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_layers),
                contentDescription = if (ortofotoActive) "Show map" else "Show Ortofoto",
                tint = if (ortofotoActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MapControlInactiveIconColor
                },
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
internal fun NorthArrowButton(
    rotationDegrees: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(48.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
        tonalElevation = 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_north_arrow),
                contentDescription = "Reset north",
                tint = Color.Unspecified,
                modifier = Modifier
                    .size(28.dp)
                    .rotate(rotationDegrees),
            )
        }
    }
}

@Composable
internal fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onSearchAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(14.dp, RoundedCornerShape(26.dp)),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        tonalElevation = 2.dp,
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState -> onFocusChanged(focusState.isFocused) },
            singleLine = true,
            placeholder = {
                Text("Search Denmark")
            },
            leadingIcon = {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_search),
                    contentDescription = null,
                )
            },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = onClear) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ic_close),
                            contentDescription = "Clear search",
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    onSearchAction()
                },
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

@Composable
internal fun SearchResultsPanel(
    searchUiState: SearchUiState,
    onSelect: (SearchResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        tonalElevation = 3.dp,
        shadowElevation = 12.dp,
    ) {
        when (searchUiState.status) {
            SearchStatus.Idle -> {
                Text(
                    text = "Type at least two characters to search for a place, address, or POI.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SearchStatus.Loading -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = "Searching…",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            SearchStatus.Empty -> {
                Text(
                    text = "No places matched that search.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SearchStatus.Error -> {
                Text(
                    text = searchUiState.errorMessage ?: "Search is temporarily unavailable.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            SearchStatus.Results -> {
                Column {
                    searchUiState.results.forEachIndexed { index, result ->
                        SearchResultRow(
                            result = result,
                            onClick = { onSelect(result) },
                        )
                        if (index < searchUiState.results.lastIndex) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                            ) {
                                Box(modifier = Modifier.size(width = 1.dp, height = 1.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    result: SearchResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val place = result.place
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlaceSummaryText(
            place = place,
            modifier = Modifier.weight(1f),
            titleStyle = MaterialTheme.typography.titleSmall,
            showCoordinates = false,
        )
    }
}

@Composable
internal fun SelectedPlaceCard(
    selectedPlace: SelectedPlace,
    isEnrichingPlace: Boolean,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val place = selectedPlace.place
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            PlaceSummaryText(
                place = place,
                modifier = Modifier.weight(1f),
                titleStyle = MaterialTheme.typography.titleMedium,
                showCoordinates = selectedPlace.type == SelectedPlaceType.CoordinatePin &&
                    place.title == "Dropped pin",
                isLoading = isEnrichingPlace,
            )
            IconButton(onClick = onClear) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_close),
                    contentDescription = "Clear selected place",
                )
            }
        }
    }
}

@Composable
private fun PlaceSummaryText(
    place: PlaceSummary,
    titleStyle: TextStyle,
    showCoordinates: Boolean,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        place.categoryHint?.let { hint ->
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = place.title,
            style = titleStyle,
            fontWeight = FontWeight.SemiBold,
        )
        if (isLoading && place.subtitle == null) {
            TextLinePlaceholder()
        } else if (place.subtitle != null) {
            Text(
                text = place.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (showCoordinates) {
            Text(
                text = place.coordinateLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TextLinePlaceholder() {
    val lineHeightDp = with(LocalDensity.current) {
        MaterialTheme.typography.bodySmall.lineHeight.toDp()
    }
    val infiniteTransition = rememberInfiniteTransition(label = "placeholder")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "placeholderAlpha",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth(0.6f)
            .height(lineHeightDp)
            .background(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                shape = RoundedCornerShape(4.dp),
            ),
    )
}

@Composable
internal fun LocateMeButton(
    enabled: Boolean,
    active: Boolean,
    isCenteredOnUser: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(48.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
        tonalElevation = 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_locate),
                contentDescription = "Locate me",
                tint = when {
                    isCenteredOnUser -> MapControlInactiveIconColor
                    active -> MaterialTheme.colorScheme.primary
                    else -> MapControlInactiveIconColor
                },
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
internal fun PermissionPrompt(
    onGrant: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Show your live location",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Enable location to see the blue dot and jump back to where you are.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onGrant) {
                Text("Enable")
            }
        }
    }
}

@Composable
internal fun OverlayChip(
    message: MapOverlayMessage,
    modifier: Modifier = Modifier,
) {
    val container = when (message.tone) {
        MapOverlayTone.Info -> MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
        MapOverlayTone.Error -> MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
    }
    val content = when (message.tone) {
        MapOverlayTone.Info -> MaterialTheme.colorScheme.onSurface
        MapOverlayTone.Error -> MaterialTheme.colorScheme.error
    }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = container,
        tonalElevation = 2.dp,
        shadowElevation = 8.dp,
    ) {
        Text(
            text = message.text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodySmall,
            color = content,
        )
    }
}

@Composable
internal fun FullscreenBackendError(
    backendUrl: String,
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.padding(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Self-hosted map unavailable",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Backend: $backendUrl",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

internal const val COMPASS_VISIBLE_BEARING_THRESHOLD_DEGREES = 1.0
private val MapControlInactiveIconColor = Color(0xFF9E9E9E)
