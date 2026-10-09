package io.github.fbarcalar.focustag.nfc

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import io.github.fbarcalar.focustag.focus.TagRole
import io.github.fbarcalar.focustag.testing.TestDataStores
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class IdOnlyPairingStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(Job() + Dispatchers.IO)
    private val dataStore by lazy { TestDataStores.preferences(folder.root, scope) }
    private val store by lazy { TagPairingStore(dataStore) }
    private val sticker = TagPairing.written(TagRole.ACTIVATE, "6f1d2c3b-4a59-4e8f-9a01-0b2c3d4e5f60", "04A1B2C3D4E5F6")
    private val card = TagPairing.idOnly(TagRole.ACTIVATE, "5A3B9C21")

    @After
    fun closeStore() = runBlocking<Unit> { scope.coroutineContext.job.cancelAndJoin() }

    @Test
    fun `a card paired by id is stored and read back as id-only`() = runBlocking<Unit> {
        store.save(card)

        assertThat(store.pairings.first()).containsExactly(TagRole.ACTIVATE, card)
    }

    @Test
    fun `re-pairing a role with a card drops the old sticker's tag id`() = runBlocking<Unit> {
        store.save(sticker)
        store.save(card)

        assertThat(store.pairings.first()).containsExactly(TagRole.ACTIVATE, card)
        assertThat(dataStore.data.first()[stringPreferencesKey("activate_tag_id")]).isNull()
    }

    @Test
    fun `re-pairing a role with a sticker drops the id-only marker`() = runBlocking<Unit> {
        store.save(card)
        store.save(sticker)

        assertThat(store.pairings.first()).containsExactly(TagRole.ACTIVATE, sticker)
    }

    @Test
    fun `a pairing saved before id-only existed still reads as a written tag`() = runBlocking<Unit> {
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("activate_tag_id")] = "6f1d2c3b-4a59-4e8f-9a01-0b2c3d4e5f60"
            prefs[stringPreferencesKey("activate_uid")] = "04A1B2C3D4E5F6"
        }

        assertThat(store.pairings.first()).containsExactly(TagRole.ACTIVATE, sticker)
    }

    @Test
    fun `a card cannot be paired as both tags`() = runBlocking<Unit> {
        store.save(card)

        val result = store.save(TagPairing.idOnly(TagRole.DEACTIVATE, card.uidHex))

        assertThat(result).isEqualTo(PairingResult.UidUsedByOtherRole)
    }
}
