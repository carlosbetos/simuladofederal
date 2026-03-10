package com.prova.simuladofederal

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import org.json.JSONArray
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

@Composable
fun AppNavegacao() {
    var telaAtual by rememberSaveable { mutableStateOf("home") }
    var materiaSelecionada by remember { mutableStateOf<Materia?>(null) }
    var simuladoSelecionadoArquivo by rememberSaveable { mutableStateOf<String?>(null) }

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
            onGlossarioClick = { telaAtual = "glossario" },
            onVideosClick = { telaAtual = "videos" }
        )
        "selecao_materia" -> TelaSelecaoMateria(materias, { 
            materiaSelecionada = it
            telaAtual = "selecao_data"
        }, { telaAtual = "home" })
        "selecao_data" -> TelaListaSimulados(materiaSelecionada!!, { arquivo ->
            simuladoSelecionadoArquivo = arquivo
            telaAtual = "simulado"
        }, { telaAtual = "selecao_materia" })
        "simulado" -> SimuladoApp(materiaSelecionada!!, simuladoSelecionadoArquivo!!) {
            simuladoSelecionadoArquivo = null
            telaAtual = "home"
        }
        "glossario" -> TelaGlossario { telaAtual = "home" }
        "videos" -> TelaVideos { telaAtual = "home" }
    }
}

@Composable
fun TelaPrincipal(onSimuladosClick: () -> Unit, onGlossarioClick: () -> Unit, onVideosClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Federal Simu", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1B5E20))
        Text("Preparação de Elite", fontSize = 14.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(48.dp))
        MenuButton("Simulados por Matéria", "Escolha disciplina e data", Icons.Default.PlayArrow, Color(0xFF1B5E20), onSimuladosClick)
        MenuButton("Glossário", "Termos importantes", Icons.Default.Info, Color(0xFF2E7D32), onGlossarioClick)
        MenuButton("Aulas em Vídeo", "Dicas do YouTube", Icons.Default.Search, Color(0xFF388E3C), onVideosClick)
    }
}

@Composable
fun MenuButton(titulo: String, subtitulo: String, icone: ImageVector, cor: Color, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { onClick() }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = cor), elevation = CardDefaults.cardElevation(4.dp)) {
        Row(modifier = Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icone, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(20.dp))
            Column {
                Text(titulo, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(subtitulo, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaSelecaoMateria(materias: List<Materia>, onMateriaEscolhida: (Materia) -> Unit, onVoltar: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Escolha a Disciplina") }, navigationIcon = { IconButton(onClick = onVoltar) { Icon(Icons.Default.ArrowBack, null) } } ) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
            materias.forEach { materia ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { onMateriaEscolhida(materia) }, shape = RoundedCornerShape(16.dp)) {
                    Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(materia.icone, fontSize = 32.sp)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(materia.nome, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaListaSimulados(materia: Materia, onSimuladoEscolhido: (String) -> Unit, onVoltar: () -> Unit) {
    var simulados by remember { mutableStateOf<List<SimuladoInfo>>(emptyList()) }
    var carregando by remember { mutableStateOf(true) }
    val context = LocalContext.current
    LaunchedEffect(materia) {
        val listaRemota = carregarListaSimuladosDoGit(materia.pasta)
        simulados = if (listaRemota.isNotEmpty()) listaRemota else carregarListaSimuladosLocal(context, materia.pasta)
        carregando = false
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Simulados: ${materia.nome}") }, navigationIcon = { IconButton(onClick = onVoltar) { Icon(Icons.Default.ArrowBack, null) } } ) }
    ) { padding ->
        if (carregando) { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        else {
            Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
                simulados.forEach { s ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { onSimuladoEscolhido(s.nomeArquivo) }, shape = RoundedCornerShape(12.dp)) {
                        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DateRange, null, tint = Color(0xFF1B5E20))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Simulado de ${s.dataFormatada}", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SimuladoApp(materia: Materia, nomeArquivo: String, onVoltarMenu: () -> Unit) {
    val context = LocalContext.current
    var listaQuestoes by remember { mutableStateOf<List<Questao>>(emptyList()) }
    var carregando by remember { mutableStateOf(true) }
    var indiceQuestao by rememberSaveable { mutableIntStateOf(0) }
    var simuladoFinalizado by rememberSaveable { mutableStateOf(false) }
    val estatisticas = remember { mutableStateMapOf<String, ResultadoMateria>() }
    val respostasUsuario = remember { mutableStateMapOf<Int, Int>() }

    LaunchedEffect(nomeArquivo) {
        carregando = true
        val path = "${materia.pasta}/$nomeArquivo"
        var questoes = carregarQuestoesDaInternet(path)
        if (questoes.isEmpty()) { questoes = carregarQuestoesDoArquivo(context, path) }
        listaQuestoes = questoes
        carregando = false
    }

    if (carregando) { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
    else if (!simuladoFinalizado) {
        TelaQuestao(listaQuestoes[indiceQuestao], indiceQuestao + 1, listaQuestoes.size, respostasUsuario[indiceQuestao] ?: -1, if (indiceQuestao > 0) { { indiceQuestao -= 1 } } else onVoltarMenu) { selecionado ->
            if (!respostasUsuario.containsKey(indiceQuestao)) {
                val status = estatisticas.getOrPut(listaQuestoes[indiceQuestao].materia) { ResultadoMateria(listaQuestoes[indiceQuestao].materia) }
                status.total++; if (selecionado == listaQuestoes[indiceQuestao].respostaCorreta) status.acertos++
                respostasUsuario[indiceQuestao] = selecionado
            }
            if (indiceQuestao < listaQuestoes.size - 1) indiceQuestao++ else simuladoFinalizado = true
        }
    } else { TelaDesempenho(materia.nome, extrairDataDoNome(nomeArquivo), estatisticas.values.toList(), onVoltarMenu) }
}

@Composable
fun TelaDesempenho(materiaNome: String, dataSimulado: String, resultados: List<ResultadoMateria>, onVoltar: () -> Unit) {
    val context = LocalContext.current
    val totalA = resultados.sumOf { it.acertos }; val totalQ = resultados.sumOf { it.total }
    val p = if (totalQ > 0) (totalA.toFloat() / totalQ * 100).toInt() else 0
    Column(modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Simulado Concluído", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
        Spacer(modifier = Modifier.height(24.dp))
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Desempenho: $p%")
                Text("$totalA de $totalQ acertos", fontSize = 48.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2E7D32))
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        resultados.forEach { res ->
            val pM = (res.acertos.toFloat() / res.total * 100).toInt()
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text("${res.materia}: $pM%", fontWeight = FontWeight.Medium)
                LinearProgressIndicator(progress = { res.acertos.toFloat() / res.total }, modifier = Modifier.fillMaxWidth().height(8.dp).padding(top = 4.dp))
            }
        }
        Spacer(modifier = Modifier.height(40.dp))
        Button(onClick = { enviarEmailRelatorio(context, materiaNome, dataSimulado, totalA, totalQ, resultados); onVoltar() }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(12.dp)) { Text("Enviar Relatório e Sair") }
    }
}

fun enviarEmailRelatorio(context: Context, materia: String, data: String, acertos: Int, total: Int, detalhes: List<ResultadoMateria>) {
    val p = if (total > 0) (acertos.toFloat() / total * 100).toInt() else 0
    val corpo = StringBuilder("Relatório de Desempenho - Federal Simu\nDisciplina: $materia\nData: $data\nResultado: $p% ($acertos/$total)\n\nDetalhes:\n")
    detalhes.forEach { corpo.append("- ${it.materia}: ${(it.acertos.toFloat()/it.total*100).toInt()}% (${it.acertos}/${it.total})\n") }
    val intent = Intent(Intent.ACTION_SENDTO).apply { this.data = Uri.parse("mailto:"); putExtra(Intent.EXTRA_EMAIL, arrayOf("carlosbetos@gmail.com")); putExtra(Intent.EXTRA_SUBJECT, "📊 Simulado: $materia ($data)"); putExtra(Intent.EXTRA_TEXT, corpo.toString()) }
    try { context.startActivity(Intent.createChooser(intent, "Enviar via...")) } catch (e: Exception) { Log.e("EMAIL", "Erro") }
}

@Composable
fun TelaQuestao(q: Questao, n: Int, total: Int, respDada: Int, onVoltar: (() -> Unit)?, onProxima: (Int) -> Unit) {
    var sel by rememberSaveable(q) { mutableIntStateOf(respDada) }
    var resp by rememberSaveable(q) { mutableStateOf(respDada != -1) }
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(q.identificacao, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20)); Text("$n de $total", fontSize = 12.sp, color = Color.Gray) }
            LinearProgressIndicator(progress = { n.toFloat() / total }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8E9))) { Text(q.materia, modifier = Modifier.padding(12.dp), color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold) }
            Text(text = q.pergunta, fontSize = 17.sp, fontWeight = FontWeight.Medium, lineHeight = 24.sp)
            q.caminhoImagem?.let { c ->
                Spacer(modifier = Modifier.height(16.dp))
                Card(elevation = CardDefaults.cardElevation(4.dp)) {
                    if (c.startsWith("http")) { AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(c).crossfade(true).build(), contentDescription = null, modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp), contentScale = ContentScale.Fit) }
                    else { val id = context.resources.getIdentifier(c, "drawable", context.packageName); if (id != 0) Image(painter = painterResource(id = id), contentDescription = null, modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp), contentScale = ContentScale.Fit) }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            q.opcoes.forEachIndexed { i, t ->
                val letra = when(i) { 0->"a"; 1->"b"; 2->"c"; else->"d" }
                val cor = when { resp && i == q.respostaCorreta -> Color(0xFFC8E6C9); resp && i == sel && i != q.respostaCorreta -> Color(0xFFFFCDD2); sel == i -> Color(0xFFE3F2FD); else -> Color.White }
                OutlinedButton(onClick = { if (!resp) sel = i }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = cor, contentColor = Color.Black), shape = RoundedCornerShape(8.dp)) { Text(text = "$letra) $t", textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth()) }
            }
            AnimatedVisibility(visible = resp) {
                Column(modifier = Modifier.padding(vertical = 16.dp)) {
                    Divider(color = Color.LightGray); Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.size(36.dp).background(Color(0xFF1B5E20), RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) { Text("👨‍🏫", fontSize = 18.sp) }; Spacer(modifier = Modifier.width(12.dp)); Text("Explicação do Professor", fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20)) }
                    Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FBE7))) { Text(text = q.explicacao, modifier = Modifier.padding(16.dp), fontSize = 14.sp, fontStyle = FontStyle.Italic, lineHeight = 22.sp) }
                }
            }
        }
        Surface(tonalElevation = 8.dp, shadowElevation = 12.dp, modifier = Modifier.fillMaxWidth()) { Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onVoltar != null) OutlinedButton(onClick = onVoltar, modifier = Modifier.weight(1f).height(52.dp)) { Text(if (n == 1) "Sair" else "Anterior") }
            if (!resp) { Button(onClick = { resp = true }, enabled = sel != -1, modifier = Modifier.weight(2f).height(52.dp)) { Text("Confirmar") } }
            else { Button(onClick = { onProxima(sel) }, modifier = Modifier.weight(2f).height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))) { Text("Próxima") } }
        } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaGlossario(onVoltar: () -> Unit) {
    var glossario by remember { mutableStateOf<List<GlossarioItem>>(emptyList()) }
    var filtro by remember { mutableStateOf("") }
    var carregando by remember { mutableStateOf(true) }
    val context = LocalContext.current
    LaunchedEffect(Unit) { val remoto = carregarGlossarioDoGit(); glossario = if (remoto.isNotEmpty()) remoto else carregarGlossarioLocal(context); carregando = false }
    Scaffold(topBar = { TopAppBar(title = { Text("Glossário") }, navigationIcon = { IconButton(onClick = onVoltar) { Icon(Icons.Default.ArrowBack, null) } }) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            OutlinedTextField(value = filtro, onValueChange = { filtro = it }, label = { Text("Buscar termo...") }, modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Search, null) }, shape = RoundedCornerShape(12.dp))
            Spacer(modifier = Modifier.height(16.dp))
            if (carregando) { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            else {
                val lista = glossario.filter { it.termo.contains(filtro, ignoreCase = true) }.sortedBy { it.termo }
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) { lista.forEach { item ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), shape = RoundedCornerShape(12.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(item.termo, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF1B5E20))
                            Spacer(modifier = Modifier.height(6.dp)); Text(item.definicao, fontSize = 15.sp)
                            if (item.exemplo.isNotEmpty()) { Spacer(modifier = Modifier.height(10.dp)); Text("💡 Exemplo: ${item.exemplo}", fontSize = 14.sp, fontStyle = FontStyle.Italic, color = Color.Gray) }
                        }
                    }
                } }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaVideos(onVoltar: () -> Unit) {
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var carregando by remember { mutableStateOf(true) }
    val context = LocalContext.current
    LaunchedEffect(Unit) { val remoto = carregarVideosDoGit(); videos = if (remoto.isNotEmpty()) remoto else carregarVideosLocal(context); carregando = false }
    Scaffold(topBar = { TopAppBar(title = { Text("Aulas e Dicas") }, navigationIcon = { IconButton(onClick = onVoltar) { Icon(Icons.Default.ArrowBack, null) } }) }
    ) { padding ->
        if (carregando) { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        else {
            Column(modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) { videos.forEach { video ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { val intent = Intent(Intent.ACTION_VIEW, Uri.parse(video.url)); context.startActivity(intent) }, colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8E9))) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(50.dp).background(Color.Red, RoundedCornerShape(25.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.PlayArrow, null, tint = Color.White) }
                        Spacer(modifier = Modifier.width(16.dp)); Text(video.titulo, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            } }
        }
    }
}

// --- FUNÇÕES DE CARREGAMENTO E PARSE ---
suspend fun carregarGlossarioDoGit(): List<GlossarioItem> = withContext(Dispatchers.IO) {
    try {
        val content = URL("https://raw.githubusercontent.com/carlosbetos/simuladofederal/main/app/src/main/assets/glossario.txt?t=${System.currentTimeMillis()}").openConnection().getInputStream().bufferedReader().use { it.readText() }
        parsearGlossario(content)
    } catch (e: Exception) { emptyList() }
}

suspend fun carregarVideosDoGit(): List<VideoItem> = withContext(Dispatchers.IO) {
    try {
        val content = URL("https://raw.githubusercontent.com/carlosbetos/simuladofederal/main/app/src/main/assets/videos.txt?t=${System.currentTimeMillis()}").openConnection().getInputStream().bufferedReader().use { it.readText() }
        parsearVideos(content)
    } catch (e: Exception) { emptyList() }
}

fun carregarGlossarioLocal(c: Context): List<GlossarioItem> = try { parsearGlossario(c.assets.open("glossario.txt").bufferedReader().use { it.readText() }) } catch (e: Exception) { emptyList() }
fun carregarVideosLocal(c: Context): List<VideoItem> = try { parsearVideos(c.assets.open("videos.txt").bufferedReader().use { it.readText() }) } catch (e: Exception) { emptyList() }

fun parsearGlossario(texto: String): List<GlossarioItem> = texto.split("---").mapNotNull { b ->
    val l = b.trim().lines().map { it.trim() }.filter { it.isNotEmpty() }
    var t = ""; var o = ""; var e = ""
    for (i in l.indices) {
        val line = l[i]
        when {
            line.startsWith("Termo:", true) -> { t = line.substringAfter(":").trim(); if (t.isEmpty() && i+1 < l.size) t = l[i+1] }
            line.startsWith("O que é:", true) -> { o = line.substringAfter(":").trim(); if (o.isEmpty() && i+1 < l.size) o = l[i+1] }
            line.startsWith("Exemplo:", true) -> { e = line.substringAfter(":").trim(); if (e.isEmpty() && i+1 < l.size) e = l[i+1] }
        }
    }
    if (t.isNotBlank()) GlossarioItem(t, o, e) else null
}

fun parsearVideos(texto: String): List<VideoItem> = texto.split("---").mapNotNull { b ->
    val l = b.trim().lines().map { it.trim() }.filter { it.isNotEmpty() }
    var t = ""; var u = ""
    for (i in l.indices) {
        val line = l[i]
        when {
            line.startsWith("Tema:", true) -> { t = line.substringAfter(":").trim(); if (t.isEmpty() && i+1 < l.size) t = l[i+1] }
            line.startsWith("Link:", true) -> { u = line.substringAfter(":").trim(); if (u.isEmpty() && i+1 < l.size) u = l[i+1] }
        }
    }
    if (t.isNotBlank() && u.isNotBlank()) VideoItem(t, u) else null
}

suspend fun carregarListaSimuladosDoGit(pasta: String): List<SimuladoInfo> = withContext(Dispatchers.IO) {
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

fun carregarListaSimuladosLocal(c: Context, p: String): List<SimuladoInfo> = try { c.assets.list(p)?.filter { it.endsWith(".txt") }?.map { SimuladoInfo(it, extrairDataDoNome(it)) } ?: emptyList() } catch (e: Exception) { emptyList() }

suspend fun carregarQuestoesDaInternet(path: String): List<Questao> = withContext(Dispatchers.IO) {
    try { parsearConteudoTxt(URL("https://raw.githubusercontent.com/carlosbetos/simuladofederal/main/app/src/main/assets/$path?t=${System.currentTimeMillis()}").openConnection().getInputStream().bufferedReader().use { it.readText() }) } catch (e: Exception) { emptyList() }
}

fun carregarQuestoesDoArquivo(c: Context, path: String): List<Questao> = try { parsearConteudoTxt(c.assets.open(path).bufferedReader().use { it.readText() }) } catch (e: Exception) { emptyList() }

fun parsearConteudoTxt(c: String): List<Questao> = c.split("---").mapNotNull { b ->
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

fun extrairDataDoNome(nome: String): String {
    val parteData = nome.substringAfter("-").substringBefore(".txt")
    return parteData.replace("-", "/")
}
