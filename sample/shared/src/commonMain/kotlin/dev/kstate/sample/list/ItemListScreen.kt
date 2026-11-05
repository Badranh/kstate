package dev.kstate.sample.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState

@Composable
fun ItemListScreen(
    component: ItemListComponent,
    modifier: Modifier = Modifier,
) {
    val model by component.model.subscribeAsState()
    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = model.query,
            onValueChange = component::onQueryChanged,
            label = { Text("Filter") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
        LazyColumn {
            items(model.visibleItems, key = { it.id }) { item ->
                ListItem(
                    headlineContent = { Text(item.title) },
                    trailingContent = {
                        Checkbox(checked = item.id in model.doneIds, onCheckedChange = null)
                    },
                    modifier = Modifier.clickable { component.onItemToggled(item.id) },
                )
            }
        }
    }
}
