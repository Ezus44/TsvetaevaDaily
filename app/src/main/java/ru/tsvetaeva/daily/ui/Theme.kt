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

private val Spectral = FontFamily(
    Font(R.font.spectral_regular, FontWeight.Normal),
    Font(R.font.spectral_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.spectral_semibold, FontWeight.SemiBold),
)
private val Vollkorn = FontFamily(
    Font(R.font.vollkorn_regular, FontWeight.Normal),
    Font(R.font.vollkorn_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.vollkorn_semibold, FontWeight.SemiBold),
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
    // Акмеизм: ясность и сдержанность. «Белая стая» и рисунки Модильяни 1911 года — графитовая
    // линия на белом; акцент — зелень портрета Альтмана (1914). Spectral — ясная, сдержанная антиква.
    Author.AKHMATOVA -> PoetStyle(
        light = light(0xFF2F5D57, 0xFFDCE8E5, 0xFF123430, 0xFFF5F5F2, 0xFF1C1D1F,
            0xFFE7E8E5, 0xFF5B5E60, 0xFFD3D5D2, 0xFFECEDEA, 0xFF5B5E60),
        dark = dark(0xFF8CC2B8, 0xFF0F3A34, 0xFF1F4A44, 0xFFD2EDE8, 0xFF141516, 0xFFECEDEA,
            0xFF242628, 0xFFACB0B2, 0xFF34373A, 0xFF1C1E20, 0xFFACB0B2),
        text = Spectral, textWeight = FontWeight.Normal, textSize = 18.sp, lineHeight = 29.sp,
        title = Spectral, titleWeight = FontWeight.SemiBold, titleSize = 26.sp,
        note = "Акмеизм — ясность и сдержанность: графитовая линия на белом, как в «Белой стае» и на рисунках " +
            "Модильяни, и зелень портрета Альтмана. Spectral — ясная, сдержанная антиква.",
    )
    // «Сестра моя — жизнь»: сирень, ливень, сад после дождя — сиреневый на влажной зелени.
    // Vollkorn — плотная, «земная» антиква с живым рисунком — под его вещественную, густую образность.
    Author.PASTERNAK -> PoetStyle(
        light = light(0xFF6E4D99, 0xFFE6DCF2, 0xFF2E1A4A, 0xFFF2F5EF, 0xFF1E241F,
            0xFFE3E8DF, 0xFF58615A, 0xFFD2D9CE, 0xFFEAEFE6, 0xFF4F7A4A),
        dark = dark(0xFFC3A8E6, 0xFF36205A, 0xFF45306A, 0xFFEFE4FB, 0xFF121814, 0xFFE8EEE6,
            0xFF212A23, 0xFFAAB5A7, 0xFF313B33, 0xFF1A211C, 0xFF9CC596),
        text = Vollkorn, textWeight = FontWeight.Normal, textSize = 19.sp, lineHeight = 29.sp,
        title = Vollkorn, titleWeight = FontWeight.SemiBold, titleSize = 26.sp,
        note = "«Сестра моя — жизнь»: сирень и ливень, сад после дождя — сиреневый на влажной зелени. " +
            "Vollkorn — плотная, «земная» антиква под его вещественную, густую образность.",
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
