package com.venkoi.terminal.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@Composable
fun MenuPreviewScreen(
    viewModel: MenuViewModel = hiltViewModel<MenuViewModel>()
) {
    val categories by viewModel.categories.collectAsState()
    val items by viewModel.items.collectAsState()
    
    var selectedCategoryId by remember(categories) { 
        mutableStateOf(categories.firstOrNull()?.id) 
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // Categories List (Sidebar)
        Surface(
            modifier = Modifier.width(200.dp).fillMaxHeight(),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            LazyColumn(modifier = Modifier.padding(8.dp)) {
                items(categories) { category ->
                    NavigationDrawerItem(
                        label = { Text(category.name) },
                        selected = selectedCategoryId == category.id,
                        onClick = { selectedCategoryId = category.id },
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }

        // Items Grid/List
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            val currentCategory = categories.find { it.id == selectedCategoryId }
            Text(
                text = currentCategory?.name ?: "Select a Category",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            val filteredItems = items.filter { it.categoryId == selectedCategoryId }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filteredItems) { item ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(item.name, style = MaterialTheme.typography.titleMedium)
                                if (!item.active) {
                                    Text("INACTIVE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                            Text(item.regularPrice.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
