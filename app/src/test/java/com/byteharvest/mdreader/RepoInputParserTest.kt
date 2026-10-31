package com.byteharvest.mdreader

import com.byteharvest.mdreader.core.ParseResult
import com.byteharvest.mdreader.core.RepoInputParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepoInputParserTest {
    private fun ok(s: String) = (RepoInputParser.parse(s) as ParseResult.Ok).input
    private fun isError(s: String) = RepoInputParser.parse(s) is ParseResult.Error

    @Test fun ownerSlashRepo() { val i = ok("torvalds/linux"); assertEquals("torvalds", i.owner); assertEquals("linux", i.name) }
    @Test fun httpsUrl() { assertEquals("linux", ok("https://github.com/torvalds/linux").name) }
    @Test fun noSchemeAndGitSuffix() { assertEquals("linux", ok("github.com/torvalds/linux.git").name) }
    @Test fun wwwHostAndQuery() { assertEquals("r", ok("https://www.github.com/o/r/?tab=readme").name) }
    @Test fun sshForm() { val i = ok("git@github.com:o/r.git"); assertEquals("o", i.owner); assertEquals("r", i.name) }
    @Test fun treeUrlGivesBranchAndPath() {
        val i = ok("https://github.com/o/r/tree/dev/docs/x")
        assertEquals("dev", i.branch); assertEquals("docs/x", i.path)
        assertFalse(i.isFile)
    }
    @Test fun blobUrlGivesFilePath() {
        val i = ok("https://github.com/o/r/blob/main/docs/README.md")
        assertEquals("main", i.branch); assertEquals("docs/README.md", i.path)
        assertTrue(i.isFile)
    }
    @Test fun plainRepoHasNoBranch() { assertNull(ok("o/r").branch) }
    @Test fun unsupportedHost() { assertTrue(isError("https://gitlab.com/o/r")) }
    @Test fun unsupportedScheme() { assertTrue(isError("ftp://github.com/o/r")) }
    @Test fun missingRepo() { assertTrue(isError("just-one")) }
    @Test fun empty() { assertTrue(isError("   ")) }
    @Test fun invalidChars() { assertTrue(isError("o w/r")) }
}
