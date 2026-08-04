package com.example.auroratv.link

/**
 * The parts of the television's own screen that a phone can reach.
 *
 * Key events dispatched by an app to itself never take it out of touch mode, so nothing on screen
 * holds focus and there is nothing for a pad press to move. Rather than fight that, the screen
 * lends the remote the two things it actually needs: a way to move focus, and a way to put text
 * into the search box. Both are set while the screen that owns them is up and cleared when it goes.
 */
internal object RemoteUiBridge {
  /** Moves focus one step. Returns whether anything moved. */
  @Volatile var moveFocus: ((LinkKey) -> Boolean)? = null

  /** Puts a phrase into the catalog's search box and runs it. */
  @Volatile var typeIntoSearch: ((String) -> Unit)? = null

  /** Whether the player is up, in which case there is nothing to search or navigate. */
  @Volatile var playerOpen: Boolean = false
}
