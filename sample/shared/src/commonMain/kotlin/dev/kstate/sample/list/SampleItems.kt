package dev.kstate.sample.list

object SampleItems {
    val groceries: List<ListItem> =
        listOf("Apples", "Bread", "Cheese", "Dates", "Eggs", "Flour", "Grapes", "Honey", "Ice cream", "Jam")
            .mapIndexed { index, title -> ListItem(id = index.toLong() + 1, title = title) }
}
