package com.restify.rest

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.with
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue

data class OnboardingPage(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val iconTint: Color
)

val onboardingPages = listOf(
    OnboardingPage(
        title = "Вітаємо у Restify!",
        description = "Керуйте доставками вашого закладу легко та ефективно. Усі замовлення автоматично структуруються на активні та виконані, забезпечуючи миттєвий доступ до потрібної інформації.",
        icon = Icons.Outlined.LocalShipping,
        iconTint = Color(0xFF8A2BE2) // Глибокий фіолетовий
    ),
    OnboardingPage(
        title = "Оформлення доставки",
        description = "Для створення замовлення просто введіть точну адресу клієнта та вкажіть необхідні деталі. Після цього оберіть зручний формат розрахунку — звичайна передплата або ж викуп замовлення кур'єром на місці.",
        icon = Icons.Outlined.TouchApp,
        iconTint = Color(0xFF00C853) // Смарагдовий зелений
    ),
    OnboardingPage(
        title = "Трекінг та зв'язок",
        description = "Контролюйте кожен етап доставки. Відстежуйте актуальне місцезнаходження кур'єра на інтерактивній карті та підтримуйте з ним зв'язок через зручний внутрішній чат.",
        icon = Icons.Outlined.Map,
        iconTint = Color(0xFF2979FF) // Яскравий синій
    ),
    OnboardingPage(
        title = "Гнучке керування",
        description = "Сповіщайте кур'єра про готовність страв одним натисканням. У періоди високого навантаження використовуйте інструмент підняття ціни доставки для максимально швидкого призначення.",
        icon = Icons.Outlined.Chat,
        iconTint = Color(0xFFFF8F00) // Теплий помаранчевий
    )
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit // Колбек для переходу на екран авторизації/головний
) {
    val pagerState = rememberPagerState { onboardingPages.size }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        bottomBar = {
            BottomSection(
                size = onboardingPages.size,
                index = pagerState.currentPage,
                onNextClicked = {
                    if (pagerState.currentPage < onboardingPages.size - 1) {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    } else {
                        onFinish()
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) { position ->
                PagerScreen(onboardingPage = onboardingPages[position])
            }
        }
    }
}

@Composable
fun PagerScreen(onboardingPage: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Дизайнерський контейнер для іконки
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .background(onboardingPage.iconTint.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(onboardingPage.iconTint.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = onboardingPage.icon,
                    contentDescription = "Onboarding Icon",
                    modifier = Modifier.size(64.dp),
                    tint = onboardingPage.iconTint
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = onboardingPage.title,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = onboardingPage.description,
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun BottomSection(
    size: Int,
    index: Int,
    onNextClicked: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Кастомний індикатор сторінок
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(size) { indicatorIndex ->
                val isSelected = index == indicatorIndex
                val width by animateDpAsState(
                    targetValue = if (isSelected) 28.dp else 10.dp,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "indicator_width"
                )
                val color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)

                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .height(10.dp)
                        .width(width)
                        .clip(CircleShape)
                        .background(color)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Інтерактивна кнопка з анімацією тексту
        Button(
            onClick = onNextClicked,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            AnimatedContent(
                targetState = if (index == size - 1) "Почати роботу" else "Далі",
                transitionSpec = {
                    if (targetState == "Почати роботу") {
                        slideInVertically { height -> height } + fadeIn() with
                                slideOutVertically { height -> -height } + fadeOut()
                    } else {
                        slideInVertically { height -> -height } + fadeIn() with
                                slideOutVertically { height -> height } + fadeOut()
                    }.using(SizeTransform(clip = false))
                },
                label = "button_text_anim"
            ) { targetText ->
                Text(
                    text = targetText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}