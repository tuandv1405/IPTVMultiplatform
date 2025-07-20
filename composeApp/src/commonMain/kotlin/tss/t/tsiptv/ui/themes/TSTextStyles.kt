package tss.t.tsiptv.ui.themes

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object TSTextStyles {
    val bold21 = TextStyle.Default.copy(
        fontSize = 21.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 25.sp,
    )
    val bold19 = bold21.copy(
        fontSize = 19.sp,
        lineHeight = 23.sp
    )

    val bold17 = bold21.copy(
        fontSize = 17.sp,
        lineHeight = 21.sp
    )

    val bold15 = bold21.copy(
        fontSize = 15.sp,
        lineHeight = 19.sp
    )

    val bold13 = bold21.copy(
        fontSize = 13.sp,
        lineHeight = 16.sp
    )

    val bold11 = bold21.copy(
        fontSize = 11.sp,
        lineHeight = 14.sp
    )

    val semiBold21 = TextStyle.Default.copy(
        fontSize = 21.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 25.sp,
    )
    val semiBold19 = semiBold21.copy(
        fontSize = 19.sp,
        lineHeight = 23.sp
    )

    val semiBold17 = semiBold21.copy(
        fontSize = 17.sp,
        lineHeight = 21.sp
    )

    val semiBold15 = semiBold21.copy(
        fontSize = 15.sp,
        lineHeight = 19.sp
    )

    val semiBold13 = semiBold21.copy(
        fontSize = 13.sp,
        lineHeight = 16.sp
    )

    val semiBold11 = semiBold21.copy(
        fontSize = 11.sp,
        lineHeight = 14.sp
    )

    val normal21 = TextStyle.Default.copy(
        fontSize = 21.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 25.sp,
    )

    val normal19 = normal21.copy(
        fontSize = 19.sp,
        lineHeight = 23.sp
    )

    val normal17 = normal21.copy(
        fontSize = 17.sp,
        lineHeight = 21.sp
    )

    val normal15 = normal21.copy(
        fontSize = 15.sp,
        lineHeight = 19.sp
    )

    val normal13 = normal21.copy(
        fontSize = 13.sp,
        lineHeight = 16.sp
    )

    val normal11 = normal21.copy(
        fontSize = 11.sp,
        lineHeight = 14.sp
    )

    val primaryTitle = semiBold15.copy(TSColors.TextPrimary)
    val secondaryTitle = semiBold15.copy(TSColors.TextSecondary)
    val secondaryTitleLight = semiBold15.copy(TSColors.TextSecondaryLight)

    val primaryBody = normal15.copy(TSColors.TextPrimary)
    val secondaryBody = normal15.copy(TSColors.TextSecondary)
    val secondaryBodyLight = normal15.copy(TSColors.TextSecondaryLight)
}