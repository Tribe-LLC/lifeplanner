package az.tribe.lifeplanner.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuestionNavigationBar(
    pagerState: PagerState,
    allQuestions: List<QuestionWithType>,
    answers: Map<String, String>,
    coroutineScope: CoroutineScope,
    onComplete: () -> Unit
) {
    // Current question details
    val currentPage = pagerState.currentPage
    val currentQuestionTitle = allQuestions.getOrNull(currentPage)?.question?.title ?: ""
    val currentQuestionAnswered = answers.containsKey(currentQuestionTitle)
    
    // Navigation state
    val isFirstPage = currentPage == 0
    val isLastPage = currentPage == allQuestions.size - 1
    val allQuestionsAnswered = answers.size == allQuestions.size
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Previous button
        OutlinedButton(
            onClick = {
                if (!isFirstPage) {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(currentPage - 1)
                    }
                }
            },
            enabled = !isFirstPage,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Previous"
            )
            Text(
                text = "Previous",
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        
        // Next/Complete button
        Button(
            onClick = {
                if (isLastPage) {
                    onComplete()
                } else {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(currentPage + 1)
                    }
                }
            },
            enabled = currentQuestionAnswered || (isLastPage && allQuestionsAnswered),
            modifier = Modifier.weight(1f).padding(start = 8.dp)
        ) {
            Text(
                text = if (isLastPage) "Generate Goals" else "Next",
                modifier = Modifier.padding(end = if (isLastPage) 0.dp else 8.dp)
            )
            if (!isLastPage) {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "Next"
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Complete"
                )
            }
        }
    }
}