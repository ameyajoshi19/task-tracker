package com.tasktracker.ui.signin

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tasktracker.R
import com.tasktracker.ui.theme.SortdColors

@Composable
fun SignInScreen(
    onSignedIn: () -> Unit,
    viewModel: SignInViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        viewModel.handleSignInResult(result.data)
    }

    LaunchedEffect(uiState.signedIn) {
        if (uiState.signedIn) onSignedIn()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    brush = Brush.linearGradient(
                        listOf(Color(0xFF1A1625), Color(0xFF2D2640)),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, size.height),
                    )
                )
            },
    ) {
        // Center-aligned branding content
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Logo container
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        color = SortdColors.accent.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(20.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_sortd_logo),
                    contentDescription = "Sortd logo",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(48.dp),
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Sortd",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF1F5F9),
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "Smart scheduling, sorted.",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFA78BFA),
            )
        }

        // Bottom-anchored sign-in section
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Google Sign-In button
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(20.dp),
            ) {
                Row(
                    modifier = Modifier
                        .clickable(enabled = !uiState.isSigningIn) {
                            viewModel.setSigningIn()
                            signInLauncher.launch(viewModel.getSignInIntent())
                        }
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (uiState.isSigningIn) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        GoogleGIcon(modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Sign in with Google",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF333333),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Privacy policy text
            val uriHandler = LocalUriHandler.current
            val privacyText = buildAnnotatedString {
                withStyle(SpanStyle(color = Color(0xFF555555), fontSize = 11.sp)) {
                    append("By signing in, you agree to our ")
                }
                pushStringAnnotation(tag = "privacy", annotation = "https://ameyajoshi19.github.io/task-tracker/privacy-policy.html")
                withStyle(
                    SpanStyle(
                        color = Color(0xFF555555),
                        fontSize = 11.sp,
                        textDecoration = TextDecoration.Underline,
                    ),
                ) {
                    append("Privacy Policy")
                }
                pop()
            }

            ClickableText(
                text = privacyText,
                onClick = { offset ->
                    privacyText.getStringAnnotations(tag = "privacy", start = offset, end = offset)
                        .firstOrNull()?.let { annotation ->
                            uriHandler.openUri(annotation.item)
                        }
                },
            )

            // Error text
            if (uiState.signInError != null) {
                Text(
                    text = uiState.signInError!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun GoogleGIcon(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val centerX = w / 2
        val centerY = h / 2
        val radius = w / 2

        // Blue (right arc)
        drawArc(
            color = Color(0xFF4285F4),
            startAngle = -45f,
            sweepAngle = 90f,
            useCenter = true,
            size = size,
        )
        // Green (bottom-right arc)
        drawArc(
            color = Color(0xFF34A853),
            startAngle = 45f,
            sweepAngle = 90f,
            useCenter = true,
            size = size,
        )
        // Yellow (bottom-left arc)
        drawArc(
            color = Color(0xFFFBBC05),
            startAngle = 135f,
            sweepAngle = 90f,
            useCenter = true,
            size = size,
        )
        // Red (top-left arc)
        drawArc(
            color = Color(0xFFEA4335),
            startAngle = 225f,
            sweepAngle = 90f,
            useCenter = true,
            size = size,
        )

        // White center circle to make it look like a "G"
        drawCircle(
            color = Color.White,
            radius = radius * 0.55f,
            center = androidx.compose.ui.geometry.Offset(centerX, centerY),
        )

        // Blue horizontal bar (the crossbar of the G)
        drawRect(
            color = Color(0xFF4285F4),
            topLeft = androidx.compose.ui.geometry.Offset(centerX - radius * 0.05f, centerY - radius * 0.15f),
            size = androidx.compose.ui.geometry.Size(radius * 0.55f, radius * 0.3f),
        )
    }
}
