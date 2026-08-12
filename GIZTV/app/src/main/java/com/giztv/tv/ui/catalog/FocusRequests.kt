package com.giztv.tv.ui.catalog

import androidx.compose.ui.focus.FocusRequester

/**
 * Asks for focus, and says whether it was actually taken.
 *
 * Two different things stop a request landing, and every retry loop in this app exists to ride out
 * both. A requester whose node is not attached yet throws: a rail below the fold is not composed
 * until it has been scrolled to, and a screen one frame old has not placed anything. A requester
 * whose node *is* attached but cannot hold focus yet answers false instead. Both mean "not yet, ask
 * again", so both are false here — which is what makes `repeat(attempts)` around a call to this
 * actually retry rather than stop on the first pass.
 *
 * The answer is read as [Any] rather than [Boolean] on purpose. The two builds sharing these
 * sources disagree about the return type: the Compose the Android app is built against hands back
 * a Boolean, and the Compose Multiplatform the desktop module uses hands back Unit. Widening
 * accepts either, and Unit lands on true because "it did not throw" is the only answer that build
 * is able to give.
 */
internal fun FocusRequester.requestFocusIfReady(): Boolean {
  val outcome: Result<Any?> = runCatching { requestFocus() }
  val taken: Any? = outcome.getOrElse { return false }
  return taken != false
}
