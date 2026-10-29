package ru.tsvetaeva.daily.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import ru.tsvetaeva.daily.R
import ru.tsvetaeva.daily.core.Author

/**
 * Оформление вкладки поэта: палитра (светлая и тёмная) и типографика.
 * Почему у кого какое — в [note] и в README («Оформление»).
 */
data class PoetStyle(
    val light: ColorScheme,
    val dark: ColorScheme,
    val text: FontFamily,
    val textWeight: FontWeight,
    val textSize: TextUnit,
    val lineHeight: TextUnit,
    val title: FontFamily,
    val titleWeight: FontWeight,
    val titleSize: TextUnit,
    val titleItalic: Boolean = false,
    val titleUppercase: Boolean = false,
    val titleLetterSpacing: TextUnit = 0.sp,
    /** Подписи (дата, «из сборника») курсивом; у Маяковского курсива нет — плакатный шрифт прямой. */
    val italicMeta: Boolean = true,
    val badgeShape: Shape = RoundedCornerShape(50),
    /** Одна строка: откуда взят стиль. Показывается в настройках. */
    val note: String,
)

private val Cormorant = FontFamily(
    Font(R.font.cormorant_medium, FontWeight.Medium),
    Font(R.font.cormorant_medium_italic, FontWeight.Medium, FontStyle.Italic),
    Font(R.font.cormorant_semibold, FontWeight.SemiBold),
)
private val Alegreya = FontFamily(
    Font(R.font.alegreya_regular, FontWeight.Normal),
    Font(R.font.alegreya_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.alegreya_semibold, FontWeight.SemiBold),
)
private val OldStandard = FontFamily(
    Font(R.font.old_standard_regular, FontWeight.Normal),
    Font(R.font.old_standard_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.old_standard_bold, FontWeight.Bold),
)
private val Oswald = FontFamily(
    Font(R.font.oswald_regular, FontWeight.Normal),
    Font(R.font.oswald_medium, FontWeight.Medium),
    Font(R.font.oswald_semibold, FontWeight.SemiBold),
)
private val Playfair = FontFamily(
    Font(R.font.playfair_regular, FontWeight.Normal),
    Font(R.font.playfair_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.playfair_semibold, FontWeight.SemiBold),
)

private fun light(
    primary: Long, primaryContainer: Long, onPrimaryContainer: Long, background: Long, ink: Long,
    variant: Long, onVariant: Long, outline: Long, container: Long, secondary: Long,
    onPrimary: Long = 0xFFFFFFFF,
) = lightColorScheme(
    primary = Color(primary), onPrimary = Color(onPrimary),
    primaryContainer = Color(primaryContainer), onPrimaryContainer = Color(onPrimaryContainer),
    secondary = Color(secondary),
    background = Color(background), onBackground = Color(ink),
    surface = Color(background), onSurface = Color(ink),
    surfaceVariant = Color(variant), onSurfaceVariant = Color(onVariant),
    surfaceContainerHigh = Color(container), outlineVariant = Color(outline),
)

private fun dark(
    primary: Long, onPrimary: Long, primaryContainer: Long, onPrimaryContainer: Long, background: Long, ink: Long,
    variant: Long, onVariant: Long, outline: Long, container: Long, secondary: Long,
) = darkColorScheme(
    primary = Color(primary), onPrimary = Color(onPrimary),
    primaryContainer = Color(primaryContainer), onPrimaryContainer = Color(onPrimaryContainer),
    secondary = Color(secondary),
    background = Color(background), onBackground = Color(ink),
    surface = Color(background), onSurface = Color(ink),
    surfaceVariant = Color(variant), onSurfaceVariant = Color(onVariant),
    surfaceContainerHigh = Color(container), outlineVariant = Color(outline),
)

val Author.style: PoetStyle get() = when (this) {
    // «Красною кистью рябина зажглась»: рябиновый красный на тёплой бумаге; острый контрастный
    // антиква Cormorant — под её тире, обрывы и напор.
    Author.TSVETAEVA -> PoetStyle(
        light = light(0xFFA33A2C, 0xFFF3DCD3, 0xFF5A1A12, 0xFFF7F2E8, 0xFF2A2420,
            0xFFEAE2D4, 0xFF6E6358, 0xFFDCD2C2, 0xFFF1EADD, 0xFF6B5E4F),
        dark = dark(0xFFE88A78, 0xFF4A120A, 0xFF5C2219, 0xFFF8DAD2, 0xFF1B1816, 0xFFEDE5D9,
            0xFF2E2925, 0xFFB5A99B, 0xFF3B3530, 0xFF2A2522, 0xFFC9BBA8),
        text = Cormorant, textWeight = FontWeight.Medium, textSize = 21.sp, lineHeight = 30.sp,
        title = Cormorant, titleWeight = FontWeight.SemiBold, titleSize = 29.sp,
        note = "Рябина — её знак («Красною кистью рябина зажглась…»): рябиновый красный на тёплой бумаге. " +
            "Cormorant — острая, контрастная антиква, под её тире и обрывы строки.",
    )
    // «Розы Пиерии», «Лоза», «Вполголоса»: винно-сливовая лоза на светлом камне, приглушённый
    // контраст; Alegreya с каллиграфическим, «рукописным» ритмом — интимно, без нажима.
    Author.PARNOK -> PoetStyle(
        light = light(0xFF7A3B5C, 0xFFEBDAE3, 0xFF3F1830, 0xFFF3F0EE, 0xFF2E282C,
            0xFFE6E0E2, 0xFF6C6268, 0xFFD9D0D4, 0xFFECE7E8, 0xFF6E6A55),
        dark = dark(0xFFD9A3BF, 0xFF4A1F36, 0xFF5A2E45, 0xFFF6DCEA, 0xFF1C1A1D, 0xFFECE4E9,
            0xFF2E2A2E, 0xFFB9AEB5, 0xFF3D373C, 0xFF29252A, 0xFFB8B392),
        text = Alegreya, textWeight = FontWeight.Normal, textSize = 19.sp, lineHeight = 29.sp,
        title = Alegreya, titleWeight = FontWeight.SemiBold, titleSize = 26.sp,
        note = "«Розы Пиерии», «Лоза», «Вполголоса»: винная лоза на светлом камне, тихий контраст. " +
            "Alegreya — шрифт с каллиграфическим ритмом, интимный, «вполголоса».",
    )
    // Рукописи пером: бурые орешковые чернила на пожелтевшей бумаге, золото «золотого века»;
    // Old Standard повторяет книжные шрифты русских изданий XIX — начала XX века.
    Author.PUSHKIN -> PoetStyle(
        light = light(0xFF7E5220, 0xFFEEDDB8, 0xFF4A2E0C, 0xFFF6EFDC, 0xFF2B2117,
            0xFFECE2C8, 0xFF6E5D45, 0xFFDCCFAE, 0xFFF0E7CF, 0xFF9C7A2E),
        dark = dark(0xFFD9B26A, 0xFF432C05, 0xFF5A4115, 0xFFF6E2B5, 0xFF1D1913, 0xFFEFE5CF,
            0xFF2F281D, 0xFFBFAF92, 0xFF40372A, 0xFF2A241B, 0xFFD9B26A),
        text = OldStandard, textWeight = FontWeight.Normal, textSize = 18.sp, lineHeight = 29.sp,
        title = OldStandard, titleWeight = FontWeight.Bold, titleSize = 25.sp,
        note = "Рукописи пером: бурые чернила на пожелтевшей бумаге, золото «золотого века». " +
            "Old Standard воспроизводит книжный шрифт русских изданий XIX века.",
    )
    // «Окна РОСТА» и плакаты с Родченко: красный и чёрный на газетной бумаге, рубленый узкий
    // гротеск, заглавия капителью, прямоугольные плашки — ни одного скругления.
    Author.MAYAKOVSKY -> PoetStyle(
        light = light(0xFFC62318, 0xFFC62318, 0xFFFFFFFF, 0xFFF2EDE4, 0xFF111111,
            0xFFE4DED3, 0xFF4A4540, 0xFF111111, 0xFFECE6DB, 0xFF111111),
        dark = dark(0xFFFF4A3D, 0xFFFFFFFF, 0xFFD52B1E, 0xFFFFFFFF, 0xFF121212, 0xFFF2EDE4,
            0xFF262422, 0xFFC9C1B6, 0xFFF2EDE4, 0xFF1E1E1E, 0xFFF2EDE4),
        text = Oswald, textWeight = FontWeight.Normal, textSize = 19.sp, lineHeight = 28.sp,
        title = Oswald, titleWeight = FontWeight.SemiBold, titleSize = 28.sp,
        titleUppercase = true, titleLetterSpacing = 0.5.sp, italicMeta = false, badgeShape = RectangleShape,
        note = "«Окна РОСТА» и плакаты с Родченко: красное и чёрное на газетной бумаге. " +
            "Oswald — узкий рубленый гротеск, как плакатные буквы; заглавия капителью, без скруглений.",
    )
    // Символизм: «Ты в синий плащ печально завернулась», «Ночь, улица, фонарь, аптека».
    // Днём — сумеречная синева и туман белой ночи, ночью — свет фонаря на тёмно-синем.
    // Playfair — высококонтрастная антиква с волосяными линиями, заглавия курсивом.
    Author.BLOK -> PoetStyle(
        light = light(0xFF33467A, 0xFFDCE1F0, 0xFF17224A, 0xFFEDEFF4, 0xFF1E2433,
            0xFFDFE2EA, 0xFF5A6276, 0xFFCDD2DE, 0xFFE5E8EF, 0xFF8A7A4A),
        dark = dark(0xFFE3C27A, 0xFF3A2C08, 0xFF26304F, 0xFFE8D9A8, 0xFF0F1424, 0xFFE3E6F0,
            0xFF1C2236, 0xFFA9B0C4, 0xFF2A3148, 0xFF171D30, 0xFFE3C27A),
        text = Playfair, textWeight = FontWeight.Normal, textSize = 18.sp, lineHeight = 30.sp,
        title = Playfair, titleWeight = FontWeight.Normal, titleSize = 27.sp, titleItalic = true,
        note = "Символизм: синий плащ, туман белой ночи, «Ночь, улица, фонарь, аптека» — синева днём, " +
            "свет фонаря ночью. Playfair — контрастная антиква с тонкими линиями, заглавия курсивом.",
    )
}

val LocalPoetStyle = staticCompositionLocalOf { Author.TSVETAEVA.style }

/** Тема одной вкладки: палитра и шрифты поэта. */
@Composable
fun PoetTheme(author: Author, content: @Composable () -> Unit) {
    val style = author.style
    CompositionLocalProvider(LocalPoetStyle provides style) {
        MaterialTheme(colorScheme = if (isSystemInDarkTheme()) style.dark else style.light, content = content)
    }
}

/** Тема общей рамки (дата, вкладки, настройки): цвета плавно перетекают к выбранному поэту. */
@Composable
fun AppTheme(author: Author, content: @Composable () -> Unit) {
    val style = author.style
    val target = if (isSystemInDarkTheme()) style.dark else style.light
    CompositionLocalProvider(LocalPoetStyle provides style) {
        MaterialTheme(colorScheme = animated(target), content = content)
    }
}

@Composable
private fun animated(t: ColorScheme): ColorScheme {
    @Composable fun a(c: Color) = animateColorAsState(c, tween(450), label = "theme").value
    return t.copy(
        primary = a(t.primary), onPrimary = a(t.onPrimary),
        primaryContainer = a(t.primaryContainer), onPrimaryContainer = a(t.onPrimaryContainer),
        secondary = a(t.secondary),
        background = a(t.background), onBackground = a(t.onBackground),
        surface = a(t.surface), onSurface = a(t.onSurface),
        surfaceVariant = a(t.surfaceVariant), onSurfaceVariant = a(t.onSurfaceVariant),
        surfaceContainerHigh = a(t.surfaceContainerHigh), outlineVariant = a(t.outlineVariant),
    )
}
