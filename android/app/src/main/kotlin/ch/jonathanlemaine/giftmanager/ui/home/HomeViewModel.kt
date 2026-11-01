package ch.jonathanlemaine.giftmanager.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.jonathanlemaine.giftmanager.data.GiftRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(private val repository: GiftRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = try {
                repository.currentProfile()?.let { HomeUiState.SignedIn(it.displayName) } ?: HomeUiState.SignedOut
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                HomeUiState.Error
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            _uiState.value = try {
                repository.signOut()
                HomeUiState.SignedOut
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                HomeUiState.Error
            }
        }
    }
}
