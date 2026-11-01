package ch.jonathanlemaine.giftmanager.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ch.jonathanlemaine.giftmanager.R

/** Écran purement déclaratif : toute la logique vit dans [HomeViewModel]. */
@Composable
fun HomeScreen(state: HomeUiState, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Text(
                text = when (state) {
                    HomeUiState.Loading -> stringResource(R.string.home_session_loading)
                    HomeUiState.SignedOut -> stringResource(R.string.home_signed_out)
                    is HomeUiState.SignedIn -> stringResource(R.string.home_signed_in, state.displayName)
                    HomeUiState.Error -> stringResource(R.string.home_error)
                },
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
