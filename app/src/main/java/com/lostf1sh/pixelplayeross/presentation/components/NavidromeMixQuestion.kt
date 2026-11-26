package com.lostf1sh.pixelplayeross.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.navidrome.NavidromeRepository
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import com.lostf1sh.pixelplayeross.presentation.viewmodel.DailyMixStateHolder
import com.lostf1sh.pixelplayeross.ui.theme.RoundedSans
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Mod: asks once, as soon as a Navidrome account is connected, whether the mixes are saved there. The setup has no
 * Navidrome step, so this comes right after logging in, or on the first start after the update for an account that
 * was already there. The answer can be changed under Play from › Navidrome.
 */
@HiltViewModel
class NavidromeMixQuestionViewModel @Inject constructor(
    navidromeRepository: NavidromeRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val dailyMixStateHolder: DailyMixStateHolder,
) : ViewModel() {
    val shouldAsk: StateFlow<Boolean> = combine(
        navidromeRepository.isLoggedInFlow,
        userPreferencesRepository.navidromeSaveMixesFlow
    ) { loggedIn, choice -> loggedIn && choice == null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun answer(save: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setNavidromeSaveMixes(save)
            if (save) dailyMixStateHolder.syncMixesNow()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavidromeMixQuestion(viewModel: NavidromeMixQuestionViewModel = hiltViewModel()) {
    val shouldAsk by viewModel.shouldAsk.collectAsStateWithLifecycle()
    if (!shouldAsk) return
    val sheetState = rememberModalSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // The first answer counts: hiding the sheet after a button can also report a dismiss, which must not undo it.
    var answered by remember { mutableStateOf(false) }
    val answer: (Boolean) -> Unit = { save ->
        if (!answered) {
            answered = true
            viewModel.answer(save)
        }
    }
    val close: (Boolean) -> Unit = { save ->
        if (!answered) {
            scope.launch { sheetState.hide() }.invokeOnCompletion { answer(save) }
        }
    }
    // Closing the sheet without a choice counts as not now, so it does not come back on every start.
    ModalBottomSheet(
        onDismissRequest = { answer(false) },
        sheetState = sheetState,
        modifier = Modifier.closeOnShortPull(sheetState) { answer(false) },
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.rounded_instant_mix_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(32.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.mix_question_title),
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = RoundedSans,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.mix_question_body),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.mix_question_where),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilledTonalButton(
                    onClick = { close(false) },
                    modifier = Modifier.weight(1f).height(56.dp),
                ) {
                    Text(stringResource(R.string.mix_question_no), style = MaterialTheme.typography.titleMedium)
                }
                Button(
                    onClick = { close(true) },
                    modifier = Modifier.weight(1f).height(56.dp),
                ) {
                    Text(stringResource(R.string.mix_question_yes), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
