package tss.t.tsiptv.ui.screens.home.homeiptvlist.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.all_channels_title
import tss.t.tsiptv.ui.screens.home.HomeEvent
import tss.t.tsiptv.ui.screens.home.HomeUiState
import tss.t.tsiptv.ui.screens.home.widget.HomeCategoryItem

@Composable
fun CategoryRow(
    homeUiState: HomeUiState,
    modifier: Modifier = Modifier.Companion,
    listState: LazyListState = rememberLazyListState(),
    onHomeEvent: (HomeEvent) -> Unit,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        state = listState
    ) {
        item {
            Spacer(Modifier.Companion.width(8.dp))
        }

        items(homeUiState.categories.size) {
            if (it == 0) {
                HomeCategoryItem(
                    categoryName = stringResource(Res.string.all_channels_title),
                    isSelected = remember(homeUiState.selectedCategory) {
                        homeUiState.selectedCategory == null
                    },
                    onItemClick = {
                        onHomeEvent(HomeEvent.OnClearFilterCategory)
                    }
                )
                return@items
            }
            val item = remember(it) {
                homeUiState.categories[it - 1]
            }
            HomeCategoryItem(
                categoryName = item.name,
                isSelected = remember(homeUiState.selectedCategory) {
                    homeUiState.selectedCategory == item
                },
                onItemClick = {
                    onHomeEvent(HomeEvent.OnCategorySelected(item))
                }
            )
        }
    }
}