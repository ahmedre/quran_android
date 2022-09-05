package com.quran.mobile.feature.downloadmanager.ui.sheikhdownload

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Text
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.quran.mobile.feature.downloadmanager.model.sheikhdownload.SuraOption
import com.quran.mobile.feature.downloadmanager.presenter.SearchTextUtil

@Composable
fun AutoCompleteDropdown(
  label: String,
  initialText: String,
  items: List<SuraOption>,
  onItemSelected: ((Int) -> Unit)
) {
  val textState = remember { mutableStateOf(TextFieldValue(initialText)) }
  val isExpanded = remember { mutableStateOf(false) }
  val restoreSelection = remember { mutableStateOf(false) }

  val filtered by remember(items) {
    derivedStateOf {
      val searchTerm = SearchTextUtil.asSearchableString(textState.value.text)
      items.filter { it.searchName.contains(searchTerm, ignoreCase = true) }
    }
  }

  ExposedDropdownMenuBox(
    expanded = isExpanded.value,
    onExpandedChange = { isExpanded.value = !isExpanded.value }
  ) {
    TextField(
      value = textState.value,
      onValueChange = {
        if (restoreSelection.value) {
          textState.value = it.copy(selection = TextRange(0, it.text.length))
          restoreSelection.value = false
        } else {
          textState.value = it
        }
      },
      label = { Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant) },
      trailingIcon = {
        ExposedDropdownMenuDefaults.TrailingIcon(
          expanded = isExpanded.value
        )
      },
      colors = ExposedDropdownMenuDefaults.textFieldColors(),
      modifier = Modifier.onFocusChanged { focusState ->
        if (focusState.isFocused) {
          val text = textState.value.text
          textState.value = textState.value.copy(selection = TextRange(0, text.length))
          restoreSelection.value = true
        }
      }
    )

    if (filtered.isNotEmpty()) {
      ExposedDropdownMenu(
        expanded = isExpanded.value,
        onDismissRequest = { isExpanded.value = false },
        modifier = Modifier.heightIn(max = 150.dp)
      ) {
        filtered.forEach {
          DropdownMenuItem(
            onClick = {
              textState.value = textState.value.copy(text = it.name)
              isExpanded.value = false
              onItemSelected(it.number)
            }
          ) {
            Text(it.name, color = MaterialTheme.colorScheme.onSecondaryContainer)
          }
        }
      }
    }
  }
}