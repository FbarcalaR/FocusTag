package io.github.fbarcalar.focustag.ui.setup.blocklist

import com.google.common.truth.Truth.assertThat
import io.github.fbarcalar.focustag.blocker.InstalledApp
import org.junit.Test

class AppRowsTest {
    private val apps = listOf(
        InstalledApp("com.example.maps", "Maps"),
        InstalledApp("org.social.feed", "Feed"),
        InstalledApp("com.example.video", "VideoTube"),
    )

    private fun labels(query: String) = appRows(apps, emptySet(), query, locked = false).map { it.label }

    @Test
    fun `a blank query keeps every app in order`() {
        assertThat(labels("  ")).containsExactly("Maps", "Feed", "VideoTube").inOrder()
    }

    @Test
    fun `the label matches ignoring case and surrounding spaces`() {
        assertThat(labels(" vIdEo ")).containsExactly("VideoTube")
    }

    @Test
    fun `the package name matches too`() {
        assertThat(labels("org.social")).containsExactly("Feed")
    }

    @Test
    fun `no match gives an empty list`() {
        assertThat(labels("zzz")).isEmpty()
    }

    @Test
    fun `only blocked apps are locked in focus`() {
        val rows = appRows(apps, setOf("org.social.feed"), "", locked = true)

        assertThat(rows.filter { !it.canToggle }.map { it.label }).containsExactly("Feed")
    }
}
