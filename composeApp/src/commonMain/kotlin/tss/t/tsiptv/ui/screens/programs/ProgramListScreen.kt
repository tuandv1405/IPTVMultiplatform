package tss.t.tsiptv.ui.screens.programs

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.home_nav_programs
import tss.t.tsiptv.core.parser.IPTVChannel
import tss.t.tsiptv.ui.widgets.TSAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramListScreen(
    program: List<IPTVChannel> = emptyList(),
) {
    Scaffold(
        topBar = {
            TSAppBar(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(Res.string.home_nav_programs)
            )
        }
    ) {
        LazyColumn(
            modifier = Modifier.padding(it)
                .fillMaxSize()
        ) {

        }

    }
}
