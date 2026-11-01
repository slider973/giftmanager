package ch.jonathanlemaine.giftmanager.ui.children

import ch.jonathanlemaine.giftmanager.core.Fixtures
import ch.jonathanlemaine.giftmanager.core.error.GiftError
import ch.jonathanlemaine.giftmanager.core.format.AvatarPalette
import ch.jonathanlemaine.giftmanager.core.model.Child
import ch.jonathanlemaine.giftmanager.core.model.DayDate
import ch.jonathanlemaine.giftmanager.core.session.SessionFixture
import ch.jonathanlemaine.giftmanager.core.session.testChild
import ch.jonathanlemaine.giftmanager.core.session.uuid
import ch.jonathanlemaine.giftmanager.data.auth.AuthState
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChildEditorViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private val today = LocalDate.of(2026, 10, 15)

    private suspend fun TestScope.fixture(setup: SessionFixture.() -> Unit = { seedReady() }): SessionFixture {
        val f = SessionFixture(backgroundScope, AuthState.SignedIn(uuid(1)))
        f.setup()
        f.session.start()
        runCurrent()
        return f
    }

    private fun vm(f: SessionFixture, mode: ChildEditorMode) =
        ChildEditorViewModel(mode, f.session, f.childRepo, Fixtures.clock) { uuid(900) }

    @Test fun createDefaults() = runTest {
        val s = vm(fixture(), ChildEditorMode.Create).uiState.value
        assertFalse(s.isAdult)
        assertFalse(s.isEditing)
        assertTrue(s.hasBirthdate)
        assertEquals(today.minusYears(6), s.birthdate)
        assertEquals(today, s.maxBirthdate)
        assertEquals(AvatarPalette.defaultEmoji, s.emoji)
        assertEquals("pastelMint", s.colorName)
        assertFalse(s.canSave)
        assertFalse(s.finished)
    }

    @Test fun createAdultHasNoBirthdateByDefault() = runTest {
        val s = vm(fixture(), ChildEditorMode.CreateAdult).uiState.value
        assertTrue(s.isAdult)
        assertFalse(s.hasBirthdate)
        assertEquals(today.minusYears(35), s.birthdate)
    }

    @Test fun firstNameIsLimitedTo40Characters() = runTest {
        val vm = vm(fixture(), ChildEditorMode.Create)
        vm.onFirstNameChange("a".repeat(60))
        assertEquals(40, vm.uiState.value.firstName.length)
    }

    @Test fun birthdateCannotBeInTheFuture() = runTest {
        val vm = vm(fixture(), ChildEditorMode.Create)
        vm.onBirthdateChange(today.plusDays(3))
        assertEquals(today, vm.uiState.value.birthdate)
        vm.onBirthdateChange(LocalDate.of(2020, 1, 2))
        assertEquals(LocalDate.of(2020, 1, 2), vm.uiState.value.birthdate)
    }

    @Test fun blankNameCannotBeSaved() = runTest {
        val f = fixture()
        val vm = vm(f, ChildEditorMode.Create)
        vm.onFirstNameChange("   ")
        vm.save()
        runCurrent()
        assertTrue(f.childRepo.saved.isEmpty())
    }

    @Test fun createSavesInMyHouseholdThenReloads() = runTest {
        val f = fixture()
        val vm = vm(f, ChildEditorMode.Create)
        vm.onFirstNameChange("  Léo ")
        vm.onEmojiChange("🚀")
        vm.onColorChange("pastelBlue")
        vm.onBirthdateChange(LocalDate.of(2019, 5, 4))
        vm.save()
        runCurrent()
        val saved = f.childRepo.saved.single()
        assertEquals(Child(uuid(900), uuid(201), "Léo", DayDate(LocalDate.of(2019, 5, 4)), "🚀", "pastelBlue", null, false), saved)
        assertTrue(vm.uiState.value.finished)
        assertFalse(vm.uiState.value.isSaving)
        // reloadGroup : le nouvel enfant est dans la session.
        assertEquals(listOf(uuid(900)), f.session.state.value.children.map { it.id })
    }

    @Test fun createAdultWithoutBirthdateSavesNull() = runTest {
        val f = fixture()
        val vm = vm(f, ChildEditorMode.CreateAdult)
        vm.onFirstNameChange("Mamie Jo")
        vm.save()
        runCurrent()
        val saved = f.childRepo.saved.single()
        assertTrue(saved.isAdult)
        assertNull(saved.birthdate)
    }

    @Test fun editLoadsAndKeepsIdHouseholdAndAvatarUrl() = runTest {
        val child = testChild(1, name = "Léa").copy(avatarEmoji = "🦄", avatarColor = "pastelPink", avatarUrl = "https://x/y.png")
        val f = fixture { seedReady(); childRepo.all += child }
        val vm = vm(f, ChildEditorMode.Edit(child.id))
        val s = vm.uiState.value
        assertTrue(s.isEditing)
        assertEquals("Léa", s.savedFirstName)
        assertEquals("Léa", s.firstName)
        assertEquals("🦄", s.emoji)
        assertEquals("pastelPink", s.colorName)
        assertEquals(LocalDate.of(2018, 3, 12), s.birthdate)
        assertFalse(s.finished)
        vm.onFirstNameChange("Léana")
        vm.onHasBirthdateChange(false)
        vm.save()
        runCurrent()
        val saved = f.childRepo.saved.single()
        assertEquals(child.copy(firstName = "Léana", birthdate = null), saved)
    }

    @Test fun editOfAnotherHouseholdKeepsItsHousehold() = runTest {
        val child = testChild(2, householdN = 2)
        val f = fixture { seedReady(); childRepo.all += child }
        val vm = vm(f, ChildEditorMode.Edit(child.id))
        vm.save()
        runCurrent()
        assertEquals(child.householdId, f.childRepo.saved.single().householdId)
    }

    @Test fun editOfUnknownChildFinishesImmediately() = runTest {
        assertTrue(vm(fixture(), ChildEditorMode.Edit(uuid(999))).uiState.value.finished)
    }

    @Test fun saveFailureShowsErrorAndStaysOpen() = runTest {
        val f = fixture()
        val vm = vm(f, ChildEditorMode.Create)
        vm.onFirstNameChange("Léo")
        f.childRepo.failure = IOException("réseau")
        vm.save()
        runCurrent()
        assertEquals(GiftError.Network, vm.uiState.value.error)
        assertFalse(vm.uiState.value.finished)
        assertFalse(vm.uiState.value.isSaving)
        vm.dismissError()
        assertNull(vm.uiState.value.error)
    }

    @Test fun withoutHouseholdSaveReportsNoHousehold() = runTest {
        val f = fixture { }
        val vm = vm(f, ChildEditorMode.Create)
        vm.onFirstNameChange("Léo")
        vm.save()
        runCurrent()
        assertEquals(GiftError.NoHousehold, vm.uiState.value.error)
        assertTrue(f.childRepo.saved.isEmpty())
    }

    @Test fun deleteNeedsConfirmationThenDeletesAndBumpsRevision() = runTest {
        val child = testChild(1)
        val f = fixture { seedReady(); childRepo.all += child }
        val vm = vm(f, ChildEditorMode.Edit(child.id))
        val revision = f.session.itemsRevision.value
        vm.requestDelete()
        assertTrue(vm.uiState.value.confirmDelete)
        vm.cancelDelete()
        assertFalse(vm.uiState.value.confirmDelete)
        assertTrue(f.childRepo.deleted.isEmpty())
        vm.requestDelete()
        vm.delete()
        runCurrent()
        assertEquals(listOf(child.id), f.childRepo.deleted)
        assertTrue(vm.uiState.value.finished)
        assertFalse(vm.uiState.value.confirmDelete)
        assertEquals(revision + 1, f.session.itemsRevision.value)
        assertTrue(f.session.state.value.children.isEmpty())
    }

    @Test fun cannotRequestDeleteWhenCreating() = runTest {
        val vm = vm(fixture(), ChildEditorMode.Create)
        vm.requestDelete()
        assertFalse(vm.uiState.value.confirmDelete)
    }

    @Test fun deleteFailureShowsError() = runTest {
        val child = testChild(1)
        val f = fixture { seedReady(); childRepo.all += child }
        val vm = vm(f, ChildEditorMode.Edit(child.id))
        f.childRepo.failure = IOException("x")
        vm.requestDelete()
        vm.delete()
        runCurrent()
        assertEquals(GiftError.Network, vm.uiState.value.error)
        assertFalse(vm.uiState.value.finished)
    }
}
