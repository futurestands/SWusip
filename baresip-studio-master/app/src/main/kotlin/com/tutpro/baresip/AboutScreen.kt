package com.tutpro.baresip

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

fun NavGraphBuilder.aboutScreenRoute(navController: NavController) {
    composable("about") { AboutScreen(onBack = { navController.navigateUp() }, onLicences = { navController.navigate("licences") }) }
    composable("licences") { AboutScreen(onBack = { navController.navigateUp() }, licences = true) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit, onLicences: () -> Unit = {}, licences: Boolean = false) {
    val version = BuildConfig.VERSION_NAME
    val aboutText = stringResource(if (licences) R.string.third_party_text else R.string.about_text, version)
    val context = LocalContext.current
    val fullNotices = remember(licences) {
        if (licences) context.assets.open("third-party-notices.txt").bufferedReader().use { it.readText() } else ""
    }
    val errorColor = MaterialTheme.colorScheme.error
    val onBackground = MaterialTheme.colorScheme.onBackground
    val annotatedText = remember(aboutText, errorColor, fullNotices) {
        AnnotatedString.fromHtml(
            htmlString = aboutText,
            linkStyles = TextLinkStyles(SpanStyle(color = errorColor))
        ) + AnnotatedString("\n\n" + fullNotices)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (licences) "Third-Party Licences" else "About SWUSIP",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                    navigationIconContentColor = MaterialTheme.colorScheme.secondary,
                    actionIconContentColor = MaterialTheme.colorScheme.secondary
                )
            )
        }
    ) { contentPadding ->
        androidx.compose.foundation.layout.Column(Modifier.padding(contentPadding)) {
        if (!licences) androidx.compose.material3.TextButton(onClick = onLicences) { Text("Third-Party Licences") }
        Text(
            text = annotatedText,
            color = onBackground,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState())
                .fillMaxSize()
        )
        }
    }
}
