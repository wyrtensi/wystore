package dev.wystore.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SentenceJoinTest {
    @Test
    fun addsStopAndSpaceBetweenSentences() {
        assertEquals(
            "Восстановлено приложений: 1, репозиториев: 0. Оптимизация включена.",
            SentenceJoin.sentences("Восстановлено приложений: 1, репозиториев: 0", "Оптимизация включена.")
        )
    }

    @Test
    fun keepsAnExistingStop() {
        assertEquals("Done. Next", SentenceJoin.sentences("Done.", "Next"))
    }

    @Test
    fun chineseUsesIdeographicStopWithoutSpace() {
        assertEquals("已恢复：1。提示", SentenceJoin.sentences("已恢复：1", "提示"))
    }

    @Test
    fun emptySecondLeavesFirstAlone() {
        assertEquals("Done", SentenceJoin.sentences("Done", ""))
    }

    @Test
    fun valueAfterColonDoesNotBreak() {
        assertEquals("Под присмотром: 1 из 4", SentenceJoin.keepValueTogether("Под присмотром: 1 из 4"))
    }

    @Test
    fun noColonIsUntouched() {
        assertEquals("1 / 4", SentenceJoin.keepValueTogether("1 / 4"))
    }
}
