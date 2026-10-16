package app.loop.feature.food

import app.loop.core.domain.repository.PortionMemory
import app.loop.core.model.Food
import app.loop.core.model.PortionUnit
import app.loop.core.model.RecognizedItem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftBuilderTest {
    private val roti = Food(3, "Roti", listOf("chapati"), PortionUnit.PIECE, 104.0, 40.0, 3.1, 18.0, 2.4)
    private val dal = Food(1, "Dal", listOf("dal tadka"), PortionUnit.KATORI, 150.0, 150.0, 8.5, 20.0, 4.0)

    @Test
    fun `calories come from the table, not from Gemini`() = runTest {
        val drafts = DraftBuilder.build(
            listOf(RecognizedItem("Chapati", 2.0, PortionUnit.PIECE, 0.9, kcalGuess = 500.0)),
            listOf(roti, dal),
            { null },
        )
        assertEquals("Roti", drafts.single().name)
        assertEquals(208.0, drafts.single().nutrition!!.kcal, 1e-9)
    }

    @Test
    fun `gemini's guess is used only for unknown dishes`() = runTest {
        val drafts = DraftBuilder.build(listOf(RecognizedItem("Sushi", 2.0, PortionUnit.PIECE, 0.6, kcalGuess = 300.0)), listOf(roti), { null })
        assertNull(drafts.single().food)
        assertEquals(300.0, drafts.single().nutrition!!.kcal, 1e-9)
    }

    @Test
    fun `remembered portions pre-fill the card`() = runTest {
        val drafts = DraftBuilder.build(
            listOf(RecognizedItem("Dal tadka", 1.0, PortionUnit.KATORI, 0.8)),
            listOf(dal),
            { name -> if (name == "Dal") PortionMemory(1.5, PortionUnit.KATORI) else null },
        )
        assertEquals(1.5, drafts.single().quantity, 0.0)
        assertFalse(drafts.single().corrected)
        assertTrue(drafts.single().copy(quantity = 2.0).corrected)
    }

    @Test
    fun `grams convert through the table's grams per unit`() {
        val item = DraftItem(1, "Dal", dal, 300.0, PortionUnit.GRAM)
        assertEquals(300.0, item.nutrition!!.kcal, 1e-9) // 2 katori worth
    }
}
