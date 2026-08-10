package com.example.auroratv.ui.catalog

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.example.auroratv.BuildConfig
import com.example.auroratv.theme.AuroraMint
import com.example.auroratv.theme.MutedBlue
import com.example.auroratv.theme.SoftWhite

@Composable
internal fun SectionHeading(
  heading: String,
  itemCount: Int,
  narrow: Boolean,
  attribution: Boolean = true,
  modifier: Modifier = Modifier,
) {
  Column(modifier) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(heading, color = SoftWhite, fontWeight = FontWeight.Black, fontSize = if (narrow) 17.sp else 19.sp)
      Spacer(Modifier.width(10.dp))
      if (itemCount > 0) {
        Text("$itemCount titles", color = AuroraMint, fontWeight = FontWeight.Bold, fontSize = 11.sp)
      }
      Spacer(Modifier.weight(1f))
      if (!narrow && attribution) {
        Text(
          "TMDB API · not endorsed or certified by TMDB",
          color = MutedBlue.copy(alpha = .6f),
          fontSize = 9.sp,
        )
      }
    }
    Spacer(Modifier.height(8.dp))
  }
}

@Composable
internal fun CatalogTopBar(
  modifier: Modifier = Modifier,
  narrow: Boolean,
  compact: Boolean,
  phoneDense: Boolean = false,
  labelDestinationActions: Boolean,
  showDestinationActions: Boolean,
  onOpenWeb: () -> Unit,
  onOpenShortDramas: () -> Unit,
  onOpenSports: () -> Unit,
  onOpenDlhdSoccer: () -> Unit,
  onOpenIptv: () -> Unit,
  onOpenRemote: (() -> Unit)?,
  onOpenSearch: (() -> Unit)? = null,
  onCloseSearch: (() -> Unit)? = null,
  openWebModifier: Modifier,
  shortDramasModifier: Modifier,
  sportsModifier: Modifier,
  soccerModifier: Modifier,
  iptvModifier: Modifier,
  tabs: @Composable () -> Unit,
  search: (@Composable () -> Unit)?,
) {
  val labelled = !narrow && labelDestinationActions
  val actions: @Composable () -> Unit = {
    Row(
      horizontalArrangement = Arrangement.spacedBy(if (phoneDense) 4.dp else if (labelled) 8.dp else 6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (showDestinationActions) {
        CatalogActionButton(
          label = "Sports",
          icon = Icons.Filled.SportsBasketball,
          showLabel = labelled,
          onClick = onOpenSports,
          modifier = sportsModifier,
        )
        CatalogActionButton(
          label = "Soccer",
          icon = Icons.Filled.SportsSoccer,
          showLabel = labelled,
          onClick = onOpenDlhdSoccer,
          modifier = soccerModifier,
        )
        CatalogActionButton(
          label = "IPTV",
          icon = Icons.Filled.LiveTv,
          showLabel = labelled,
          onClick = onOpenIptv,
          modifier = iptvModifier,
        )
        CatalogActionButton(
          label = "Short dramas",
          icon = Icons.Filled.Theaters,
          showLabel = labelled,
          onClick = onOpenShortDramas,
          modifier = shortDramasModifier,
        )
        CatalogActionButton(
          label = "Open web",
          icon = Icons.Filled.Language,
          showLabel = labelled,
          onClick = onOpenWeb,
          modifier = openWebModifier,
        )
      }
      onOpenSearch?.let { open ->
        CatalogIconButton("Search", Icons.Filled.Search, open)
      }
      onCloseSearch?.let { close ->
        CatalogIconButton("Close search", Icons.Filled.Close, close)
      }
      onOpenRemote?.let { open ->
        CatalogActionButton(
          label = "Remote",
          icon = Icons.Filled.SettingsRemote,
          showLabel = labelled,
          onClick = open,
        )
      }
    }
  }
  Column(
    modifier =
      modifier.fillMaxWidth()
        .padding(
          horizontal = if (phoneDense) 2.dp else 4.dp,
          vertical = if (phoneDense) 2.dp else 4.dp,
        ),
    verticalArrangement = Arrangement.spacedBy(if (phoneDense) 6.dp else if (compact) 8.dp else 10.dp),
  ) {
    if (narrow) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CatalogWordmark(compact = true, dense = phoneDense)
        Spacer(modifier.weight(1f))
        actions()
      }
      Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        tabs()
      }
    } else {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CatalogWordmark(compact = false)
        Spacer(modifier.width(24.dp))
        tabs()
        Spacer(modifier.weight(1f))
        actions()
      }
    }
    search?.let { searchContent ->
      Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Box(modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()) { searchContent() }
      }
    }
  }
}

@Composable
internal fun CatalogWordmark(compact: Boolean, dense: Boolean = false) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    GizTvMark(
      modifier = Modifier.size(if (dense) 26.dp else if (compact) 30.dp else 34.dp),
      cornerRadius = if (dense) 8.dp else 10.dp,
    )
    Spacer(Modifier.width(if (dense) 8.dp else 10.dp))
    Column {
      Text(
        "GIZTV",
        color = SoftWhite,
        fontWeight = FontWeight.Black,
        letterSpacing = if (dense) 2.sp else 2.5.sp,
        fontSize = if (dense) 15.sp else if (compact) 16.sp else 18.sp,
      )
      if (!dense) {
        Text(
          "v${BuildConfig.VERSION_NAME}",
          color = AuroraMint.copy(alpha = .75f),
          fontWeight = FontWeight.Bold,
          letterSpacing = 1.sp,
          fontSize = 9.sp,
        )
      }
    }
  }
}

@Composable
internal fun CatalogSearchRow(
  narrow: Boolean,
  query: String,
  placeholder: String,
  onQueryChanged: (String) -> Unit,
  onSearch: () -> Unit,
  searchFieldFocusRequester: FocusRequester,
  searchButtonFocusRequester: FocusRequester,
  tabFocusRequester: FocusRequester,
  bodyFocusRequester: FocusRequester?,
  onMoveDown: (() -> Unit)? = null,
) {
  val fieldModifier =
    Modifier.focusRequester(searchFieldFocusRequester).focusProperties {
      up = tabFocusRequester
      down = bodyFocusRequester ?: FocusRequester.Default
      right = searchButtonFocusRequester
    }.remoteFocusNavigation(up = tabFocusRequester, down = bodyFocusRequester)
  val buttonModifier =
    Modifier.focusRequester(searchButtonFocusRequester).focusProperties {
      up = tabFocusRequester
      left = searchFieldFocusRequester
      down = bodyFocusRequester ?: FocusRequester.Default
    }.remoteFocusNavigation(
      up = tabFocusRequester,
      left = searchFieldFocusRequester,
      down = bodyFocusRequester,
    )
  Row(
    modifier =
      Modifier.focusGroup().fillMaxWidth().onPreviewKeyEvent { event ->
        if (
          event.type == KeyEventType.KeyDown &&
            event.key == Key.DirectionDown &&
            onMoveDown != null
        ) {
          onMoveDown()
          true
        } else {
          false
        }
      },
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    val width = if (narrow) Modifier.weight(1f) else Modifier.width(560.dp)
    CatalogSearchField(query, placeholder, onQueryChanged, onSearch, fieldModifier.then(width))
    CatalogIconButton("Search", Icons.Filled.Search, onSearch, buttonModifier)
  }
}
