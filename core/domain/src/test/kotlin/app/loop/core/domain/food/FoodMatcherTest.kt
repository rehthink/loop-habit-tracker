package app.loop.core.domain.food

import app.loop.core.model.Food
import app.loop.core.model.PortionUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodMatcherTest {
    private fun food(id: Long, name: String, vararg aliases: String) =
        Food(id, name, aliases.toList(), PortionUnit.KATORI, 150.0, 150.0, 8.0, 20.0, 4.0)

    private val foods = listOf(
        food(1, "Dal", "daal", "dhal", "dal tadka", "yellow dal"),
        food(2, "Paneer butter masala", "paneer makhani", "shahi paneer"),
        food(3, "Roti", "chapati", "phulka"),
        food(4, "Poha", "kanda poha", "aval"),
        food(5, "Chicken biryani", "biryani", "hyderabadi biryani"),
    )

    @Test
    fun `matches spelling variants and aliases`() {
        assertEquals(1L, FoodMatcher.best("Dal Tadka", foods)?.food?.id)
        assertEquals(1L, FoodMatcher.best("dhal", foods)?.food?.id)
        assertEquals(3L, FoodMatcher.best("Chapatti", foods)?.food?.id)
        assertEquals(2L, FoodMatcher.best("Paneer Makhani", foods)?.food?.id)
        assertEquals(5L, FoodMatcher.best("Hyderabadi Chicken Biryani", foods)?.food?.id)
    }

    @Test
    fun `unknown dishes don't match anything`() {
        assertNull(FoodMatcher.best("Sushi platter", foods))
    }

    @Test
    fun `search ranks the closest first and drops unrelated dishes`() {
        val ranked = FoodMatcher.rank("daal", foods).map { it.food.name }
        assertEquals("Dal", ranked.first())
        assertTrue("Poha shouldn't show for dal: $ranked", "Poha" !in ranked)
        assertEquals("Paneer butter masala", FoodMatcher.rank("panee", foods).first().food.name)
    }
}
