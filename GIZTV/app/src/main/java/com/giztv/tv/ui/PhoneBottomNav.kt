package com.giztv.tv.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.giztv.tv.theme.GizMint
import com.giztv.tv.theme.DeepSpace
import com.giztv.tv.theme.MutedBlue
import com.giztv.tv.theme.NightSurface
import com.giztv.tv.theme.SoftWhite

/** Height of the interactive strip above system navigation insets. */
internal val PhoneBottomNavContentHeight = 64.dp

internal enum class PhoneBottomTab(
  val label: String,
  val icon: ImageVector,
) {
  // Search sits in a center slot (3rd of 6) for thumb reach; destinations fill either side.
  MOVIES("Movies", Icons.Filled.VideoLibrary),
  SPORTS("Sports", Icons.Filled.SportsBasketball),
  SEARCH("Search", Icons.Filled.Search),
  SHORTS("Shorts", Icons.Filled.Theaters),
  WEB("Web", Icons.Filled.Language),
  IPTV("IPTV", Icons.Filled.LiveTv),
}

/**
 * Phone-only footer for the main browse destinations.
 *
 * Kept out of leanback layouts; TV keeps the existing top-bar destination chips.
 */
@Composable
internal fun PhoneBottomNav(
  selected: PhoneBottomTab?,
  onSelect: (PhoneBottomTab) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .background(
          Brush.verticalGradient(
            colors =
              listOf(
                NightSurface.copy(alpha = .92f),
                DeepSpace.copy(alpha = .98f),
              )
          )
        )
        .border(width = 1.dp, color = SoftWhite.copy(alpha = .08f), shape = RoundedCornerShape(0.dp))
        .navigationBarsPadding(),
  ) {
    Box(
      modifier =
        Modifier
          .fillMaxWidth()
          .height(1.dp)
          .background(
            Brush.horizontalGradient(
              listOf(
                SoftWhite.copy(alpha = 0f),
                SoftWhite.copy(alpha = .12f),
                SoftWhite.copy(alpha = 0f),
              )
            )
          )
    )
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .height(PhoneBottomNavContentHeight)
          .padding(horizontal = 4.dp, vertical = 4.dp),
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      PhoneBottomTab.entries.forEach { tab ->
        PhoneBottomNavItem(
          tab = tab,
          selected = tab == selected,
          onClick = { onSelect(tab) },
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

@Composable
private fun PhoneBottomNavItem(
  tab: PhoneBottomTab,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val contentColor by
    animateColorAsState(
      targetValue = if (selected) GizMint else MutedBlue,
      label = "${tab.name} content",
    )
  val indicatorColor by
    animateColorAsState(
      targetValue = if (selected) GizMint.copy(alpha = .16f) else SoftWhite.copy(alpha = 0f),
      label = "${tab.name} indicator",
    )

  Column(
    modifier =
      modifier
        .widthIn(min = 48.dp)
        .height(PhoneBottomNavContentHeight - 8.dp)
        .clip(RoundedCornerShape(14.dp))
        .background(indicatorColor)
        .clickable(onClick = onClick)
        .semantics {
          role = Role.Tab
          this.selected = selected
          contentDescription = tab.label
        }
        .padding(horizontal = 2.dp, vertical = 4.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Icon(
      imageVector = tab.icon,
      contentDescription = null,
      tint = contentColor,
      modifier = Modifier.size(22.dp),
    )
    Spacer(modifier = Modifier.height(3.dp))
    Text(
      text = tab.label,
      color = contentColor,
      fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
      fontSize = 10.sp,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      textAlign = TextAlign.Center,
    )
  }
}
