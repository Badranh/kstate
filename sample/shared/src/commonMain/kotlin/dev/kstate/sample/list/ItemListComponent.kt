package dev.kstate.sample.list

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import dev.kstate.adapter.decompose.inspect

data class ListItem(
    val id: Long,
    val title: String,
)

/** The Decompose screen of the sample: a filterable checklist. */
interface ItemListComponent {
    val model: Value<Model>

    fun onQueryChanged(query: String)

    fun onItemToggled(id: Long)

    data class Model(
        val query: String,
        val visibleItems: List<ListItem>,
        val doneIds: Set<Long>,
    )
}

class DefaultItemListComponent(
    componentContext: ComponentContext,
    private val items: List<ListItem>,
) : ItemListComponent,
    ComponentContext by componentContext {
    private val mutableModel =
        MutableValue(ItemListComponent.Model(query = "", visibleItems = items, doneIds = emptySet()))
    override val model: Value<ItemListComponent.Model> = mutableModel

    init {
        model.inspect(lifecycle, "List")
    }

    override fun onQueryChanged(query: String) {
        mutableModel.update { model ->
            model.copy(query = query, visibleItems = items.matching(query))
        }
    }

    override fun onItemToggled(id: Long) {
        mutableModel.update { it.copy(doneIds = it.doneIds.toggle(id)) }
    }

    private fun List<ListItem>.matching(query: String): List<ListItem> =
        filter { it.title.contains(query.trim(), ignoreCase = true) }

    private fun Set<Long>.toggle(id: Long): Set<Long> = if (id in this) this - id else this + id
}
