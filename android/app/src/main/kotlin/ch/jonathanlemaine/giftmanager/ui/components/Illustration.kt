package ch.jonathanlemaine.giftmanager.ui.components

import androidx.annotation.DrawableRes
import ch.jonathanlemaine.giftmanager.R

/** Illustrations de `Illustrations.xcassets`, converties en WebP par scripts/android/convert-illustrations.sh. */
enum class Illustration(@DrawableRes val res: Int) {
    GIFT_RED(R.drawable.gift_red),
    MASCOT_CELEBRATE(R.drawable.mascot_celebrate),
    MASCOT_FAMILY(R.drawable.mascot_family),
    MASCOT_GIFT(R.drawable.mascot_gift),
    MASCOT_LOVE(R.drawable.mascot_love),
    MASCOT_SLEEPING(R.drawable.mascot_sleeping),
    MASCOT_SURPRISE(R.drawable.mascot_surprise),
    MASCOT_THINKING(R.drawable.mascot_thinking),
    ILLUSTRATION_TRAVEL(R.drawable.illustration_travel),
    EMPTY_BOX(R.drawable.empty_box),
}
