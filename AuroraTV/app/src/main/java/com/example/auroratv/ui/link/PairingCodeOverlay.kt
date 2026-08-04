package com.example.auroratv.ui.link

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.example.auroratv.link.LinkHost
import com.example.auroratv.theme.AuroraMint
import com.example.auroratv.theme.DeepSpace
import com.example.auroratv.theme.MutedBlue
import com.example.auroratv.theme.NightSurface
import com.example.auroratv.theme.SoftWhite

/**
 * The number a phone has to be told before it can drive this television.
 *
 * Shown only while a phone is actually asking, and only ever on a television, since a phone is the
 * thing holding the remote. It takes no focus: the viewer is typing on the phone, and stealing the
 * television's focus would leave them unable to get back to what they were watching.
 */
@Composable
internal fun PairingCodeOverlay() {
  val code by LinkHost.pairingCode.collectAsState()
  val address by LinkHost.address.collectAsState()
  val showing = code ?: return

  Box(
    modifier = Modifier.fillMaxSize().padding(48.dp).testTag("pairing_overlay"),
    contentAlignment = Alignment.BottomEnd,
  ) {
    Column(
      modifier =
        Modifier.background(NightSurface, RoundedCornerShape(24.dp))
          .padding(horizontal = 36.dp, vertical = 28.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        text = "PAIR YOUR PHONE",
        color = AuroraMint,
        fontSize = 13.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 2.sp,
      )
      Spacer(Modifier.height(14.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // Digit by digit, because a six-figure number read across a room is easier to keep your
        // place in when it is not one long string.
        showing.forEach { digit ->
          Box(
            modifier = Modifier.size(54.dp, 68.dp).background(DeepSpace, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
          ) {
            Text(digit.toString(), color = SoftWhite, fontSize = 34.sp, fontWeight = FontWeight.Black)
          }
        }
      }
      Spacer(Modifier.height(14.dp))
      Text("Enter this in GIZTV on your phone", color = MutedBlue, fontSize = 14.sp)
      // Shown because a phone sharing its own connection with this television cannot hear it
      // announce itself, and then being able to type the address in is the only way through.
      address?.let {
        Spacer(Modifier.height(6.dp))
        Text("If your phone cannot find this TV, enter  $it", color = MutedBlue, fontSize = 12.sp)
      }
    }
  }
}
