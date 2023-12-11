package cloud.keyspace.android

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt

@ColorInt
fun Context.attrToColor(
    @AttrRes
    attr: Int
): Int = with(TypedValue()) {
    theme.resolveAttribute(attr, this, true)
    data
}
