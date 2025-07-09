package az.tribe.lifeplanner.domain.model

import androidx.compose.ui.text.input.TextFieldValue

data class NewMilestone(
    val title: TextFieldValue,
    val dueDate: TextFieldValue
)