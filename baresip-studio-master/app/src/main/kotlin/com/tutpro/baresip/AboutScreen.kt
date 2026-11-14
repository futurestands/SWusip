package com.tutpro.baresip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val InstitutionBlue = Color(0xFF124C7C)
private val InstitutionInk = Color(0xFF20364B)
private val SecondaryInk = Color(0xFF536579)
private val PaleBlue = Color(0xFFEAF2F9)
private val RuleBlue = Color(0xFFD8E4EF)

/** Public information routes: independent of agent/admin authorisation. */
fun NavGraphBuilder.aboutScreenRoute(navController: NavController) {
    composable("about") {
        AboutScreen(onBack = { navController.navigateUp() }, onLegal = { navController.navigate("legal-information") })
    }
    composable("legal-information") {
        LegalInformationScreen(onBack = { navController.navigateUp() }, onLicences = { navController.navigate("licences") })
    }
    composable("licences") { OpenSourceLicencesScreen(onBack = { navController.navigateUp() }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InformationPage(title: String, onBack: () -> Unit, content: @Composable (PaddingValues) -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = InstitutionBlue, onPrimary = Color.White,
        background = Color.White, surface = Color.White, onSurface = InstitutionInk,
        onSurfaceVariant = SecondaryInk, outline = RuleBlue, surfaceVariant = PaleBlue)) {
        Scaffold(modifier = Modifier.fillMaxSize().imePadding(), containerColor = Color.White,
            topBar = {
                TopAppBar(title = { Text(title, fontWeight = FontWeight.SemiBold) },
                    navigationIcon = { IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White,
                        titleContentColor = InstitutionBlue, navigationIconContentColor = InstitutionBlue))
            }, content = content)
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit, onLegal: () -> Unit = {}) {
    InformationPage("About SWUSIP",onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp)) {
            Text("SWUSIP",fontSize = 36.sp,lineHeight = 44.sp,fontWeight = FontWeight.Bold,color = InstitutionBlue)
            Spacer(Modifier.height(8.dp))
            Text("South Western Umbrella Call Centre",style = MaterialTheme.typography.titleLarge,color = InstitutionInk)
            Spacer(Modifier.height(28.dp))
            HorizontalDivider(color = RuleBlue)
            Spacer(Modifier.height(24.dp))
            Text("South Western Umbrella of Water and Sanitation",fontSize = 20.sp,lineHeight = 28.sp,
                fontWeight = FontWeight.SemiBold,color = InstitutionBlue)
            Spacer(Modifier.height(16.dp))
            Text("Ministry of Water and Environment",style = MaterialTheme.typography.titleMedium,color = InstitutionInk)
            Spacer(Modifier.height(4.dp))
            Text("Uganda",style = MaterialTheme.typography.bodyMedium,color = SecondaryInk)
            Spacer(Modifier.height(28.dp))
            Column(Modifier.fillMaxWidth().background(PaleBlue,MaterialTheme.shapes.medium).padding(20.dp)) {
                Text("Institutional calling and customer support",style = MaterialTheme.typography.titleMedium,color = InstitutionBlue)
                Spacer(Modifier.height(8.dp))
                Text("For help with your extension or connection, contact your SWUSIP administrator.",
                    style = MaterialTheme.typography.bodyMedium,color = InstitutionInk)
            }
            Spacer(Modifier.height(24.dp))
            Text("Version ${BuildConfig.VERSION_NAME}",style = MaterialTheme.typography.bodySmall,color = SecondaryInk)
            Spacer(Modifier.height(32.dp))
            HorizontalDivider(color = RuleBlue)
            TextButton(onClick = onLegal, contentPadding = PaddingValues(vertical = 12.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = SecondaryInk)) {
                Text("Legal information",style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun LegalInformationScreen(onBack: () -> Unit, onLicences: () -> Unit) {
    InformationPage("Legal information",onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp)) {
            Text("Software information",style = MaterialTheme.typography.titleLarge,color = InstitutionBlue)
            Spacer(Modifier.height(12.dp))
            Text("SWUSIP includes open-source software. Copyright notices, licence conditions and disclaimers are available below.",
                style = MaterialTheme.typography.bodyLarge,color = InstitutionInk)
            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = RuleBlue)
            TextButton(onClick = onLicences,contentPadding = PaddingValues(vertical = 16.dp)) {
                Text("Open-source licences",style = MaterialTheme.typography.titleMedium)
            }
            HorizontalDivider(color = RuleBlue)
        }
    }
}

@Composable
private fun OpenSourceLicencesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val notices by produceState(initialValue = "",context) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.assets.open("third-party-notices.txt").bufferedReader().use { it.readText() } }
                .getOrElse { "Open-source notices could not be loaded. Contact your SWUSIP administrator." }
        }
    }
    val attribution = stringResource(R.string.third_party_text)
    val base = remember(attribution,notices) {
        AnnotatedString.fromHtml(attribution,linkStyles = TextLinkStyles(SpanStyle(color = InstitutionBlue))) +
            AnnotatedString("\n\n" + notices)
    }
    var query by remember { mutableStateOf("") }
    val term = query.trim()
    val matches = remember(base,term) {
        if(term.length < 2) emptyList() else generateSequence(base.text.indexOf(term,ignoreCase = true)) {
            base.text.indexOf(term,it + term.length,ignoreCase = true)
        }.takeWhile { it >= 0 }.toList()
    }
    val text = remember(base,term,matches) { buildAnnotatedString {
        append(base)
        matches.take(1000).forEach { addStyle(SpanStyle(background = PaleBlue,color = InstitutionBlue),it,it+term.length) }
    } }
    val scroll = rememberScrollState()
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    LaunchedEffect(term) {
        matches.firstOrNull()?.let { offset ->
            layout?.let { scroll.animateScrollTo(it.getLineTop(it.getLineForOffset(offset)).toInt()) }
        }
    }
    InformationPage("Open-source licences",onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            OutlinedTextField(query,{ query = it },label = { Text("Find in licences") },singleLine = true,
                modifier = Modifier.fillMaxWidth())
            Text(when { notices.isEmpty() -> "Loading notices…"; term.length < 2 -> "Enter two or more characters to find text.";
                matches.isEmpty() -> "No matches"; else -> "${matches.size} matches" },
                style = MaterialTheme.typography.bodySmall,color = SecondaryInk,modifier = Modifier.padding(vertical = 8.dp))
            SelectionContainer(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)) {
                Text(text,style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),color = InstitutionInk,
                    onTextLayout = { layout = it },modifier = Modifier.padding(bottom = 24.dp))
            }
        }
    }
}
