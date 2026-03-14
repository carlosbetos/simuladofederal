package com.prova.simuladofederal

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// --- MODELOS DE DADOS ---
data class Materia(val nome: String, val pasta: String, val icone: String)
data class SimuladoInfo(val nomeArquivo: String, val dataFormatada: String)
data class Questao(val materia: String, val identificacao: String, val pergunta: String, val caminhoImagem: String?, val opcoes: List<String>, val respostaCorreta: Int, val explicacao: String)
data class ResultadoMateria(val materia: String, var total: Int = 0, var acertos: Int = 0)
data class GlossarioItem(val termo: String, val definicao: String, val exemplo: String)
data class VideoItem(val titulo: String, val url: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF1B5E20))) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNavegacao()
                }
            }
        }
    }
}

// --- UTILITÁRIOS ---
fun extrairDataDoNome(nome: String): String {
    return try {
        val raw = nome.substringBefore(".txt")
        
        // Trata simulado_geral_2024_1 -> 2024 - Edição 1
        if (raw.startsWith("simulado_geral_")) {
            val partes = raw.split("_")
            if (partes.size >= 4) {
                return "${partes[2]} - Edição ${partes[3]}"
            }
        }
        
        // Trata matematica-05-03-26 -> 05/03/26
        val dataPart = if (raw.contains("-")) {
            if (raw.first().isDigit()) raw else raw.substringAfter("-")
        } else raw
        
        dataPart.replace("-", "/")
    } catch (e: Exception) { "Simulado" }
}

@Composable
fun QuestaoParser(rawText: String, modifier: Modifier = Modifier) {
    val regex = remember { Regex("(?<!R)\\${'$'}{1,2}(.*?)\\${'$'}{1,2}") }
    val parts = mutableListOf<Pair<String, Boolean>>()
    var lastIndex = 0
    regex.findAll(rawText).forEach { match ->
        if (match.range.first > lastIndex) parts.add(rawText.substring(lastIndex, match.range.first) to false)
        parts.add(match.groupValues[1] to true)
        lastIndex = match.range.last + 1
    }
    if (lastIndex < rawText.length) parts.add(rawText.substring(lastIndex) to false)

    Column(modifier = modifier.fillMaxWidth()) {
        parts.forEach { (content, isMath) ->
            if (isMath) KaTeXView(formula = content)
            else if (content.trim().isNotEmpty() || content.contains("R$")) {
                Text(text = content.trim(), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 24.sp), modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

@Composable
fun KaTeXView(formula: String) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val htmlData = """
        <!DOCTYPE html><html><head>
        <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.8/dist/katex.min.css">
        <script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.8/dist/katex.min.js"></script>
        <style>body { margin: 0; padding: 10px 0; background-color: transparent; color: rgb(${(textColor shr 16) and 0xFF}, ${(textColor shr 8) and 0xFF}, ${textColor and 0xFF}); font-size: 18px; } #math { width: 100%; overflow-x: auto; }</style>
        </head><body><div id="math"></div><script>document.addEventListener("DOMContentLoaded", function() { katex.render("${formula.replace("\\", "\\\\").replace("\"", "\\\"")}", document.getElementById("math"), { throwOnError: false, displayMode: true }); });</script></body></html>
    """.trimIndent()
    AndroidView(modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp), factory = { context -> WebView(context).apply { settings.javaScriptEnabled = true; setBackgroundColor(0); webViewClient = WebViewClient() } }, update = { it.loadDataWithBaseURL("https://cdn.jsdelivr.net", htmlData, "text/html", "UTF-8", null) })
}

@Composable
fun AppNavegacao() {
    var telaAtual by rememberSaveable { mutableStateOf("home") }
    var materiaSelecionada by remember { mutableStateOf<Materia?>(null) }
    var simuladoArquivo by rememberSaveable { mutableStateOf<String?>(null) }

    val materias = listOf(
        Materia("Português", "portugues", "📚"),
        Materia("Matemática", "matematica", "📐"),
        Materia("Química", "quimica", "🧪"),
        Materia("Biologia", "biologia", "🧬"),
        Materia("Geografia", "geografia", "🌎"),
        Materia("História", "historia", "⏳")
    )

    when (telaAtual) {
        "home" -> TelaPrincipal(
            onSimuladosClick = { telaAtual = "selecao_materia" },
            onSimuladoGeralClick = { telaAtual = "selecao_data_geral" },
            onGlossarioClick = { telaAtual = "glossario" },
            onVideosClick = { telaAtual = "videos" }
        )
        "selecao_materia" -> TelaSelecaoMateria(materias, { 
            materiaSelecionada = it
            telaAtual = "selecao_data"
        }, { telaAtual = "home" })
        "selecao_data" -> TelaListaSimulados(materiaSelecionada!!.pasta, materiaSelecionada!!.nome, { arquivo -> 
            simuladoArquivo = arquivo
            telaAtual = "simulado" 
        }, { telaAtual = "selecao_materia" })
        "selecao_data_geral" -> TelaListaSimulados("simuladogeral", "Simulado Geral", { arquivo ->
            simuladoArquivo = arquivo
            telaAtual = "simulado_geral_quizz"
        }, { telaAtual = "home" })
        "simulado" -> SimuladoApp(materiaSelecionada!!, simuladoArquivo!!) {
            simuladoArquivo = null
            telaAtual = "home"
        }
        "simulado_geral_quizz" -> SimuladoGeralApp(simuladoArquivo!!) {
            simuladoArquivo = null
            telaAtual = "home"
        }
        "glossario" -> TelaGlossario { telaAtual = "home" }
        "videos" -> TelaVideos { telaAtual = "home" }
    }
}

@Composable
fun TelaPrincipal(onSimuladosClick: () -> Unit, onSimuladoGeralClick: () -> Unit, onGlossarioClick: () -> Unit, onVideosClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Federal Simu", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1B5E20))
        Text("Preparação de Elite", fontSize = 14.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(48.dp))
        MenuButton("Simulados por Matéria", "Escolha disciplina e data", Icons.Default.PlayArrow, Color(0xFF1B5E20), onSimuladosClick)
        MenuButton("Simulado Geral", "Questões variadas por edição", Icons.Default.List, Color(0xFF2E7D32), onSimuladoGeralClick)
        MenuButton("Glossário", "Termos importantes", Icons.Default.Info, Color(0xFF388E3C), onGlossarioClick)
        MenuButton("Aulas em Vídeo", "Dicas do YouTube", Icons.Default.Search, Color(0xFF43A047), onVideosClick)
    }
}

@Composable
fun MenuButton(titulo: String, subtitulo: String, icone: ImageVector, cor: Color, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { onClick() }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = cor), elevation = CardDefaults.cardElevation(4.dp)) {
        Row(modifier = Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icone, null, tint = Color.White, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(20.dp))
            Column { Text(titulo, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text(subtitulo, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp) }
        }
    }
}

@Composable
fun SimuladoGeralApp(nomeArquivo: String, onVoltar: () -> Unit) {
    val context = LocalContext.current
    var lista by remember { mutableStateOf<List<Questao>>(emptyList()) }
    var carregando by remember { mutableStateOf(true) }
    var idx by rememberSaveable { mutableIntStateOf(0) }
    var finalizado by rememberSaveable { mutableStateOf(false) }
    val stats = remember { mutableStateMapOf<String, ResultadoMateria>() }
    val respUser = remember { mutableStateMapOf<Int, Int>() }

    LaunchedEffect(nomeArquivo) {
        val path = "simuladogeral/$nomeArquivo"
        val jsonStr = carregarUrlGit(path) ?: try { context.assets.open(path).bufferedReader().use { it.readText() } } catch (e: Exception) { null }
        if (jsonStr != null) {
            lista = parsearQuestoesJson(jsonStr)
        }
        carregando = false
    }

    if (carregando) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    else if (lista.isEmpty()) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Erro ao carregar simulado.") }
    else if (!finalizado) {
        TelaQuestao(lista[idx], idx + 1, lista.size, respUser[idx] ?: -1, if (idx > 0) { { idx -= 1 } } else onVoltar) { s ->
            if (!respUser.containsKey(idx)) {
                val res = stats.getOrPut(lista[idx].materia) { ResultadoMateria(lista[idx].materia) }
                res.total++; if (s == lista[idx].respostaCorreta) res.acertos++; respUser[idx] = s
            }
            if (idx < lista.size - 1) idx++ else finalizado = true
        }
    } else { TelaDesempenho("Simulado Geral", extrairDataDoNome(nomeArquivo), stats.values.toList(), onVoltar) }
}

fun parsearQuestoesJson(jsonStr: String): List<Questao> {
    val qList = mutableListOf<Questao>()
    try {
        val array = JSONArray(jsonStr)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val alts = mutableListOf<String>()
            val altArr = obj.getJSONArray("alternativas")
            for (j in 0 until altArr.length()) alts.add(altArr.getString(j))
            qList.add(Questao(obj.optString("materia", "Geral"), "Questão ${obj.getInt("id")}", obj.getString("enunciado"), obj.optString("imagem", null).takeIf { it?.isNotEmpty() == true }, alts, obj.getInt("resposta"), obj.optString("explicacao", "")))
        }
    } catch (e: Exception) { e.printStackTrace() }
    return qList
}

@Composable
fun TelaQuestao(q: Questao, n: Int, total: Int, respDada: Int, onVoltar: (() -> Unit)?, onProxima: (Int) -> Unit) {
    var sel by rememberSaveable(q) { mutableIntStateOf(respDada) }
    var respondeu by rememberSaveable(q) { mutableStateOf(respDada != -1) }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(q.identificacao, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20)); Text("$n de $total", fontSize = 12.sp, color = Color.Gray) }
            LinearProgressIndicator(progress = { n.toFloat() / total }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            QuestaoParser(rawText = q.pergunta)
            q.caminhoImagem?.let { c ->
                Spacer(modifier = Modifier.height(16.dp))
                Card(elevation = CardDefaults.cardElevation(4.dp)) {
                    if (c.startsWith("http")) AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(c).crossfade(true).build(), null, modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp), contentScale = ContentScale.Fit)
                    else { val id = context.resources.getIdentifier(c, "drawable", context.packageName); if (id != 0) Image(painterResource(id), null, modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp), contentScale = ContentScale.Fit) }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            q.opcoes.forEachIndexed { i, t ->
                val letra = when(i) { 0->"a"; 1->"b"; 2->"c"; else->"d" }
                val cor = when { respondeu && i == q.respostaCorreta -> Color(0xFFC8E6C9); respondeu && i == sel && i != q.respostaCorreta -> Color(0xFFFFCDD2); sel == i -> Color(0xFFE3F2FD); else -> Color.White }
                OutlinedButton(onClick = { if (!respondeu) sel = i }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = cor, contentColor = Color.Black), shape = RoundedCornerShape(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("$letra) ", fontWeight = FontWeight.Bold)
                        Text(text = t.replace("$", "").replace("$$", ""))
                    }
                }
            }
            AnimatedVisibility(visible = respondeu) {
                Column(modifier = Modifier.padding(vertical = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.size(36.dp).background(Color(0xFF1B5E20), RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) { Text("👨‍🏫", fontSize = 18.sp) }; Spacer(modifier = Modifier.width(12.dp)); Text("Explicação do Professor", fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20)) }
                    Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FBE7))) { QuestaoParser(rawText = q.explicacao, modifier = Modifier.padding(16.dp)) }
                }
            }
        }
        Surface(tonalElevation = 8.dp, shadowElevation = 12.dp, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onVoltar != null) OutlinedButton(onClick = onVoltar, modifier = Modifier.weight(1f).height(52.dp)) { Text(if (n == 1) "Sair" else "Anterior") }
                if (!respondeu) Button(onClick = { respondeu = true }, enabled = sel != -1, modifier = Modifier.weight(2f).height(52.dp)) { Text("Confirmar") }
                else Button(onClick = { onProxima(sel) }, modifier = Modifier.weight(2f).height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))) { Text("Próxima") }
            }
        }
    }
}

@Composable
fun TelaDesempenho(materiaNome: String, dataSimulado: String, resultados: List<ResultadoMateria>, onVoltar: () -> Unit) {
    val context = LocalContext.current
    val totalA = resultados.sumOf { it.acertos }; val totalQ = resultados.sumOf { it.total }
    val p = if (totalQ > 0) (totalA.toFloat() / totalQ * 100).toInt() else 0
    Column(modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Simulado Concluído", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
        Spacer(modifier = Modifier.height(24.dp)); Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))) { Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("Desempenho: $p%"); Text("$totalA de $totalQ acertos", fontSize = 48.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2E7D32)) } }
        Spacer(modifier = Modifier.height(32.dp)); resultados.forEach { res -> val pM = (res.acertos.toFloat() / res.total * 100).toInt(); Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) { Text("${res.materia}: $pM%", fontWeight = FontWeight.Medium); LinearProgressIndicator(progress = { res.acertos.toFloat() / res.total }, modifier = Modifier.fillMaxWidth().height(8.dp).padding(top = 4.dp)) } }
        Spacer(modifier = Modifier.height(40.dp)); Button(onClick = { enviarEmailRelatorio(context, materiaNome, dataSimulado, totalA, totalQ, resultados); onVoltar() }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(12.dp)) { Text("Enviar Relatório e Sair") }
    }
}

fun enviarEmailRelatorio(context: Context, m: String, d: String, a: Int, t: Int, det: List<ResultadoMateria>) {
    val corpo = "Simulado: $m ($d)\nAcertos: $a/$t\n\n" + det.joinToString("\n") { "- ${it.materia}: ${(it.acertos.toFloat()/it.total*100).toInt()}%" }
    val intent = Intent(Intent.ACTION_SENDTO).apply { data = Uri.parse("mailto:"); putExtra(Intent.EXTRA_EMAIL, arrayOf("carlosbetos@gmail.com")); putExtra(Intent.EXTRA_SUBJECT, "📊 Simulado: $m"); putExtra(Intent.EXTRA_TEXT, corpo) }
    try { context.startActivity(Intent.createChooser(intent, "Enviar via...")) } catch (e: Exception) { }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaSelecaoMateria(materias: List<Materia>, onMateriaEscolhida: (Materia) -> Unit, onVoltar: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Escolha a Disciplina") }, navigationIcon = { IconButton(onClick = onVoltar) { Icon(Icons.Default.ArrowBack, null) } }) }) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
            materias.forEach { m -> Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { onMateriaEscolhida(m) }, shape = RoundedCornerShape(16.dp)) { Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) { Text(m.icone, fontSize = 32.sp); Spacer(modifier = Modifier.width(16.dp)); Text(m.nome, fontSize = 18.sp, fontWeight = FontWeight.Medium) } } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaListaSimulados(pasta: String, titulo: String, onSimuladoEscolhido: (String) -> Unit, onVoltar: () -> Unit) {
    var simulados by remember { mutableStateOf<List<SimuladoInfo>>(emptyList()) }
    var carregando by remember { mutableStateOf(true) }
    val context = LocalContext.current
    LaunchedEffect(pasta) { 
        simulados = carregarUrlGitLista(pasta).ifEmpty { 
            try { context.assets.list(pasta)?.filter { it.endsWith(".txt") }?.map { SimuladoInfo(it, extrairDataDoNome(it)) } ?: emptyList() } catch (e: Exception) { emptyList() }
        }
        carregando = false 
    }
    Scaffold(topBar = { TopAppBar(title = { Text(titulo) }, navigationIcon = { IconButton(onClick = onVoltar) { Icon(Icons.Default.ArrowBack, null) } }) }) { padding ->
        if (carregando) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
            simulados.forEach { s -> Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { onSimuladoEscolhido(s.nomeArquivo) }, shape = RoundedCornerShape(12.dp)) { Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.DateRange, null, tint = Color(0xFF1B5E20)); Spacer(modifier = Modifier.width(16.dp)); Text(simuladoTituloDisplay(s), fontWeight = FontWeight.Bold) } } }
        }
    }
}

fun simuladoTituloDisplay(s: SimuladoInfo): String {
    return if (s.nomeArquivo.startsWith("simulado_geral")) s.dataFormatada else "Simulado de ${s.dataFormatada}"
}

suspend fun carregarUrlGitLista(pasta: String): List<SimuladoInfo> = withContext(Dispatchers.IO) {
    try {
        val conn = URL("https://api.github.com/repos/carlosbetos/simuladofederal/contents/app/src/main/assets/$pasta").openConnection() as HttpURLConnection
        if (conn.responseCode == 200) {
            val array = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
            val lista = mutableListOf<SimuladoInfo>()
            for (i in 0 until array.length()) { val name = array.getJSONObject(i).getString("name"); if (name.endsWith(".txt")) lista.add(SimuladoInfo(name, extrairDataDoNome(name))) }
            lista.sortedByDescending { it.dataFormatada }
        } else emptyList()
    } catch (e: Exception) { emptyList() }
}

@Composable
fun SimuladoApp(materia: Materia, nomeArquivo: String, onVoltarMenu: () -> Unit) {
    val context = LocalContext.current
    var lista by remember { mutableStateOf<List<Questao>>(emptyList()) }
    var carregando by remember { mutableStateOf(true) }
    var idx by rememberSaveable { mutableIntStateOf(0) }
    var finalizado by rememberSaveable { mutableStateOf(false) }
    val stats = remember { mutableStateMapOf<String, ResultadoMateria>() }
    val respUser = remember { mutableStateMapOf<Int, Int>() }
    LaunchedEffect(nomeArquivo) { val path = "${materia.pasta}/$nomeArquivo"; val text = carregarUrlGit(path) ?: try { context.assets.open(path).bufferedReader().use { it.readText() } } catch (e: Exception) { null }; if (text != null) lista = parsearQuestoesTxt(text); carregando = false }
    if (carregando) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    else if (!finalizado) TelaQuestao(lista[idx], idx + 1, lista.size, respUser[idx] ?: -1, if (idx > 0) { { idx -= 1 } } else onVoltarMenu) { s ->
        if (!respUser.containsKey(idx)) { val res = stats.getOrPut(lista[idx].materia) { ResultadoMateria(lista[idx].materia) }; res.total++; if (s == lista[idx].respostaCorreta) res.acertos++; respUser[idx] = s }
        if (idx < lista.size - 1) idx++ else finalizado = true
    } else TelaDesempenho(materia.nome, extrairDataDoNome(nomeArquivo), stats.values.toList(), onVoltarMenu)
}

fun parsearQuestoesTxt(c: String): List<Questao> = c.split("---").mapNotNull { b ->
    val l = b.trim().lines().map { it.trim() }.filter { it.isNotEmpty() }
    if (l.size >= 5) {
        val idxA = l.indexOfFirst { it.startsWith("a)") }; if (idxA == -1) return@mapNotNull null
        val img = l.find { it.startsWith("IMAGEM:") }?.substringAfter(":")?.trim()
        val perg = l.subList(2, idxA).filter { !it.startsWith("IMAGEM:") }.joinToString("\n")
        val opc = listOf(l[idxA].substringAfter("a)").trim(), l[idxA+1].substringAfter("b)").trim(), l[idxA+2].substringAfter("c)").trim(), l[idxA+3].substringAfter("d)").trim())
        val resp = l.find { it.contains("RESPOSTA:") }?.substringAfter(":")?.trim()?.lowercase() ?: "a"
        Questao(l[0], l[1], perg, img, opc, when(resp) { "a"->0; "b"->1; "c"->2; "d"->3; else->0 }, l.find { it.contains("EXPLICAÇÃO:") || it.contains("EXPLICACAO:") }?.substringAfter(":")?.trim() ?: "")
    } else null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaGlossario(onVoltar: () -> Unit) {
    var glossario by remember { mutableStateOf<List<GlossarioItem>>(emptyList()) }
    var filtro by remember { mutableStateOf("") }
    var carregando by remember { mutableStateOf(true) }
    val context = LocalContext.current
    LaunchedEffect(Unit) { val text = carregarUrlGit("glossario.txt") ?: try { context.assets.open("glossario.txt").bufferedReader().use { it.readText() } } catch (e: Exception) { null }; if (text != null) glossario = parsearGlossario(text); carregando = false }
    Scaffold(topBar = { TopAppBar(title = { Text("Glossário") }, navigationIcon = { IconButton(onClick = onVoltar) { Icon(Icons.Default.ArrowBack, null) } }) }) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            OutlinedTextField(value = filtro, onValueChange = { filtro = it }, label = { Text("Buscar termo...") }, modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Search, null) }, shape = RoundedCornerShape(12.dp))
            Spacer(modifier = Modifier.height(16.dp))
            if (carregando) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else { val lista = glossario.filter { it.termo.contains(filtro, ignoreCase = true) }.sortedBy { it.termo }
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) { lista.forEach { item -> Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), shape = RoundedCornerShape(12.dp)) { Column(modifier = Modifier.padding(16.dp)) { Text(item.termo, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF1B5E20)); Spacer(modifier = Modifier.height(6.dp)); Text(item.definicao, fontSize = 15.sp); if (item.exemplo.isNotEmpty()) { Spacer(modifier = Modifier.height(10.dp)); Text("💡 Exemplo: ${item.exemplo}", fontSize = 14.sp, fontStyle = FontStyle.Italic, color = Color.Gray) } } } } } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaVideos(onVoltar: () -> Unit) {
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var carregando by remember { mutableStateOf(true) }
    val context = LocalContext.current
    LaunchedEffect(Unit) { val text = carregarUrlGit("videos.txt") ?: try { context.assets.open("videos.txt").bufferedReader().use { it.readText() } } catch (e: Exception) { null }; if (text != null) videos = parsearVideos(text); carregando = false }
    Scaffold(topBar = { TopAppBar(title = { Text("Aulas e Dicas") }, navigationIcon = { IconButton(onClick = onVoltar) { Icon(Icons.Default.ArrowBack, null) } }) }) { padding ->
        if (carregando) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else Column(modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) { videos.forEach { video -> Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { val intent = Intent(Intent.ACTION_VIEW, Uri.parse(video.url)); context.startActivity(intent) }, colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8E9))) { Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.size(50.dp).background(Color.Red, RoundedCornerShape(25.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.PlayArrow, null, tint = Color.White) }; Spacer(modifier = Modifier.width(16.dp)); Text(video.titulo, fontWeight = FontWeight.Bold, fontSize = 16.sp) } } } }
    }
}

suspend fun carregarUrlGit(path: String): String? = withContext(Dispatchers.IO) {
    try { URL("https://raw.githubusercontent.com/carlosbetos/simuladofederal/main/app/src/main/assets/$path?t=${System.currentTimeMillis()}").openConnection().getInputStream().bufferedReader().use { it.readText() } } catch (e: Exception) { null }
}

fun parsearGlossario(texto: String): List<GlossarioItem> = texto.split("---").mapNotNull { b ->
    val l = b.trim().lines().map { it.trim() }.filter { it.isNotEmpty() }
    var t = ""; var o = ""; var e = ""
    for (i in l.indices) {
        when {
            l[i].startsWith("Termo:", true) -> { t = l[i].substringAfter(":").trim(); if (t.isEmpty() && i+1 < l.size) t = l[i+1] }
            l[i].startsWith("O que é:", true) -> { o = l[i].substringAfter(":").trim(); if (o.isEmpty() && i+1 < l.size) o = l[i+1] }
            l[i].startsWith("Exemplo:", true) -> { e = l[i].substringAfter(":").trim(); if (e.isEmpty() && i+1 < l.size) e = l[i+1] }
        }
    }
    if (t.isNotBlank()) GlossarioItem(t, o, e) else null
}

fun parsearVideos(texto: String): List<VideoItem> = texto.split("---").mapNotNull { b ->
    val l = b.trim().lines().map { it.trim() }.filter { it.isNotEmpty() }
    var t = ""; var u = ""
    for (i in l.indices) {
        when {
            l[i].startsWith("Tema:", true) -> { t = l[i].substringAfter(":").trim(); if (t.isEmpty() && i+1 < l.size) t = l[i+1] }
            l[i].startsWith("Link:", true) -> { u = l[i].substringAfter(":").trim(); if (u.isEmpty() && i+1 < l.size) u = l[i+1] }
        }
    }
    if (t.isNotBlank() && u.isNotBlank()) VideoItem(t, u) else null
}
