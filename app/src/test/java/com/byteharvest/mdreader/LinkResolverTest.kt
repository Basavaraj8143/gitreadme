package com.byteharvest.mdreader

import com.byteharvest.mdreader.domain.LinkResolver
import com.byteharvest.mdreader.domain.LinkTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkResolverTest {
    @Test fun siblingFile() { assertEquals(LinkTarget.RepoFile("docs/setup.md", null), LinkResolver.resolve("docs/guide.md", "setup.md")) }
    @Test fun parentWithAnchor() { assertEquals(LinkTarget.RepoFile("README.md", "intro"), LinkResolver.resolve("docs/guide.md", "../README.md#Intro")) }
    @Test fun fromRoot() { assertEquals(LinkTarget.RepoFile("docs/a.md", null), LinkResolver.resolve("README.md", "docs/a.md")) }
    @Test fun absoluteRepoPath() { assertEquals(LinkTarget.RepoFile("LICENSE.md", null), LinkResolver.resolve("docs/guide.md", "/LICENSE.md")) }
    @Test fun escapingRootIsInvalid() { assertEquals(LinkTarget.Invalid, LinkResolver.resolve("README.md", "../x.md")) }
    @Test fun sameDocAnchor() { assertEquals(LinkTarget.Anchor("top-section"), LinkResolver.resolve("a.md", "#Top-Section")) }
    @Test fun external() {
        assertEquals(LinkTarget.External("https://x.org"), LinkResolver.resolve("a.md", "https://x.org"))
        assertEquals(LinkTarget.External("mailto:a@b.c"), LinkResolver.resolve("a.md", "mailto:a@b.c"))
    }
    @Test fun imagePathDecodesPercent() { assertEquals("docs/img/p q.png", LinkResolver.resolveRepoRef("docs/a.md", "./img/p%20q.png")) }
    @Test fun imageEscapingRoot() { assertNull(LinkResolver.resolveRepoRef("a.md", "../../x.png")) }
}
