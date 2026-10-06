package com.qimian233.ztool

import com.qimian233.ztool.data.home.ZToolSource
import com.qimian233.ztool.data.home.isVerifiedSourceInput
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the four accepted OOBE source-verification forms and the rejection of
 * anything pointing at a different repository.
 */
class ZToolSourceVerificationTest {

    @Test
    fun bareRepositoryName_passes() {
        assertTrue(isVerifiedSourceInput("ZUX-ZTool"))
        assertTrue(isVerifiedSourceInput("  zux-ztool  "))
    }

    @Test
    fun ownerSlashRepository_passes() {
        assertTrue(isVerifiedSourceInput("qwqawa64/ZUX-ZTool"))
        assertTrue(isVerifiedSourceInput("QWQAWA64/zux-ztool"))
    }

    @Test
    fun fullGitHubUrl_passes() {
        assertTrue(isVerifiedSourceInput("https://github.com/qwqawa64/ZUX-ZTool"))
        assertTrue(isVerifiedSourceInput("http://www.github.com/qwqawa64/ZUX-ZTool/"))
    }

    @Test
    fun gitHubUrlWithSuffix_passes() {
        assertTrue(isVerifiedSourceInput("https://github.com/qwqawa64/ZUX-ZTool/releases"))
        assertTrue(isVerifiedSourceInput("https://github.com/qwqawa64/ZUX-ZTool/issues/12"))
        assertTrue(isVerifiedSourceInput("github.com/qwqawa64/ZUX-ZTool/releases"))
    }

    @Test
    fun repositoryConstants_areConsistent() {
        assertTrue(isVerifiedSourceInput(ZToolSource.GITHUB_REPO))
        assertTrue(isVerifiedSourceInput("${ZToolSource.GITHUB_OWNER}/${ZToolSource.GITHUB_REPO}"))
        assertTrue(isVerifiedSourceInput(ZToolSource.GITHUB_URL))
    }

    @Test
    fun unrelatedOrEmptyInput_fails() {
        assertFalse(isVerifiedSourceInput(""))
        assertFalse(isVerifiedSourceInput("   "))
        assertFalse(isVerifiedSourceInput("ZUX-ZTool-fork"))
        assertFalse(isVerifiedSourceInput("someoneelse/ZUX-ZTool"))
        assertFalse(isVerifiedSourceInput("qwqawa64/AnotherTool"))
        assertFalse(isVerifiedSourceInput("https://github.com/someoneelse/ZUX-ZTool"))
    }
}
