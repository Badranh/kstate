package dev.kstate.sample.list

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultItemListComponentTest {
    private val items =
        listOf(
            ListItem(id = 1, title = "Apple"),
            ListItem(id = 2, title = "Banana"),
            ListItem(id = 3, title = "Pineapple"),
        )

    private val component = DefaultItemListComponent(DefaultComponentContext(LifecycleRegistry()), items)

    @Test
    fun showsEveryItemWithoutAQuery() {
        assertEquals(items, component.model.value.visibleItems)
    }

    @Test
    fun filtersByQueryIgnoringCase() {
        component.onQueryChanged("APPLE")

        assertEquals(
            listOf("Apple", "Pineapple"),
            component.model.value.visibleItems
                .map { it.title },
        )
    }

    @Test
    fun togglingMarksAnItemDoneAndBack() {
        component.onItemToggled(2)
        assertEquals(setOf(2L), component.model.value.doneIds)

        component.onItemToggled(2)
        assertEquals(emptySet(), component.model.value.doneIds)
    }

    @Test
    fun doneItemsStayDoneWhileFiltered() {
        component.onItemToggled(3)
        component.onQueryChanged("pine")

        assertEquals(setOf(3L), component.model.value.doneIds)
    }
}
