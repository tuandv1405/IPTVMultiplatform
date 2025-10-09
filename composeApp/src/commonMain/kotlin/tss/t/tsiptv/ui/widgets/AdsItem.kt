package tss.t.tsiptv.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.launch
import tss.t.tsiptv.core.model.ShopeeAffiliateAds
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.themes.TSShapes
import tss.t.tsiptv.ui.themes.TSTextStyles
import tss.t.tsiptv.utils.formatVietnamCurrencyWithSeparator
import tss.t.tsiptv.utils.getUrlOpener
import tss.t.tsiptv.utils.isValidUrl

@Composable
fun AdsItem(ads: ShopeeAffiliateAds) {
    val coroutineScope = rememberCoroutineScope()
    Row(
        modifier = Modifier.Companion
            .fillMaxWidth()
            .clip(TSShapes.roundedShape16)
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 16.dp)
            .background(TSColors.SecondaryBackgroundColor, TSShapes.roundedShape16)
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        AsyncImage(
            modifier = Modifier.Companion.size(70.dp)
                .clip(TSShapes.roundedShape8)
                .border(
                    1.dp, TSColors.baseGradient,
                    TSShapes.roundedShape8
                ),
            model = ImageRequest.Builder(LocalPlatformContext.current)
                .data(ads.imageUrl)
                .crossfade(200)
                .build(),
            contentDescription = ads.title
        )
        Spacer(Modifier.Companion.width(12.dp))
        Column(modifier = Modifier.Companion.weight(1f)) {
            Text(
                ads.title ?: "",
                style = TSTextStyles.semiBold15
            )
            Spacer(Modifier.Companion.height(4.dp))
            Text(
                text = ads.description ?: "",
                style = TSTextStyles.secondaryBody,
                maxLines = 3,
                overflow = TextOverflow.Companion.Ellipsis
            )
            Spacer(Modifier.Companion.height(12.dp))
            Row(
                modifier = Modifier.Companion.fillMaxWidth(),
                verticalAlignment = Alignment.Companion.CenterVertically
            ) {
                ads.price?.let { price ->
                    Text(
                        text = remember(price) {
                            price.formatVietnamCurrencyWithSeparator()
                        },
                        style = TSTextStyles.secondaryBody,
                        modifier = Modifier.drawWithContent {
                            drawContent()
                            drawLine(
                                color = TSColors.RedNotify,
                                start = Offset(0f, this.size.height / 2f),
                                end = Offset(this.size.width, this.size.height / 2f),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                    )
                }
                Spacer(Modifier.Companion.width(4.dp))
                ads.salePrice?.let { price ->
                    Text(
                        text = remember(price) {
                            price.formatVietnamCurrencyWithSeparator()
                        },
                        style = TSTextStyles.semiBold15.copy(
                            brush = TSColors.baseGradient
                        )
                    )
                }
            }
            Spacer(Modifier.Companion.height(10.dp))
            GradientButton1(
                ads.ctaAction ?: "",
                modifier = Modifier.Companion.fillMaxWidth()
            ) {
                coroutineScope.launch {
                    ads.ctaUrl?.let { url ->
                        if (url.isValidUrl()) {
                            val urlOpener = getUrlOpener()
                            if (urlOpener.canHandleUrl(url)) {
                                urlOpener.openUrl(url)
                            } else {
                                urlOpener.openUrl(url)
                            }
                        }
                    }
                }
            }
        }
    }
}
