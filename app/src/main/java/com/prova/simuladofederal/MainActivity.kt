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
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// --- MODELOS DE DADOS ---
data class Materia(val nome: String, val arquivo: String, val icone: String)
data class Questao(val materia: String, val identificacao: String, val pergunta: String, val caminhoImagem: String?, val opcoes: List<String>, val respostaCorreta: Int, val explicacao: String)
data class ResultadoMateria(val materia: String, var total: Int = 0, var acertos: Int = 0)
data class GlossarioItem(val termo: String, val definicao: String, val exemplo: String)
data class VideoItem(val titulo: String, val descricao: String, val url: String)

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
    var materiaSelecionadaNome by rememberSaveable { mutableStateOf<String?>(null) }

    val materias = listOf(
        Materia("Português", "questoesPortugues.txt", "📚"),
        Materia("Matemática", "questoesMatematica.txt", "📐"),
        Materia("Química", "questoesQuimica.txt", "🧪"),
        Materia("Biologia", "questoesBiologia.txt", "🧬"),
        Materia("Geografia", "questoesGeografia.txt", "🌎"),
        Materia("História", "questoesHistoria.txt", "⏳"),
        Materia("Simulado Geral", "questoes.txt", "📝")
    )

    val materiaSelecionada = materias.find { it.nome == materiaSelecionadaNome }

    when (telaAtual) {
        "home" -> TelaPrincipal(
            onSimuladosClick = { telaAtual = "selecao_materia" },
            onGlossarioClick = { telaAtual = "glossario" },
            onVideosClick = { telaAtual = "videos" }
        )
        "selecao_materia" -> TelaSelecaoMateria(materias, { 
            materiaSelecionadaNome = it.nome
            telaAtual = "simulado"
        }, { telaAtual = "home" })
        "simulado" -> SimuladoApp(materiaSelecionada!!) {
            materiaSelecionadaNome = null
            telaAtual = "home"
        }
        "glossario" -> TelaGlossario { telaAtual = "home" }
        "videos" -> TelaVideos { telaAtual = "home" }
    }
}

// --- TELA PRINCIPAL (MENU) ---
@Composable
fun TelaPrincipal(onSimuladosClick: () -> Unit, onGlossarioClick: () -> Unit, onVideosClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Federal Simu", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1B5E20))
        Text("Preparação para Escolas Federais", fontSize = 14.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(48.dp))

        MenuButton("Questões e Simulados", "Pratique por disciplina", Icons.Default.PlayArrow, Color(0xFF1B5E20), onSimuladosClick)
        MenuButton("Glossário", "Termos importantes", Icons.Default.Info, Color(0xFF2E7D32), onGlossarioClick)
        MenuButton("Aulas em Vídeo", "Dicas do YouTube", Icons.Default.Search, Color(0xFF388E3C), onVideosClick)
    }
}

@Composable
fun MenuButton(titulo: String, subtitulo: String, icone: ImageVector, cor: Color, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cor),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
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

// --- TELA GLOSSÁRIO ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaGlossario(onVoltar: () -> Unit) {
    var glossario by remember { mutableStateOf<List<GlossarioItem>>(emptyList()) }
    var filtro by remember { mutableStateOf("") }
    var carregando by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        glossario = carregarGlossarioDoGit()
        carregando = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Glossário") },
                navigationIcon = { IconButton(onClick = onVoltar) { Icon(Icons.Default.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = filtro,
                onValueChange = { filtro = it },
                label = { Text("Buscar termo...") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Search, null) },
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (carregando) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                val listaFiltrada = glossario.filter { it.termo.contains(filtro, ignoreCase = true) }
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    listaFiltrada.forEach { item ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), shape = RoundedCornerShape(12.dp)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(item.termo, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF1B5E20))
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(item.definicao, fontSize = 15.sp)
                                if (item.exemplo.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text("💡 Exemplo: ${item.exemplo}", fontSize = 14.sp, fontStyle = FontStyle.Italic, color = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- TELA VÍDEOS ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaVideos(onVoltar: () -> Unit) {
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var carregando by remember { mutableStateOf(true) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        videos = carregarVideosDoGit()
        carregando = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Aulas e Dicas") },
                navigationIcon = { IconButton(onClick = onVoltar) { Icon(Icons.Default.ArrowBack, null) } }
            )
        }
    ) { padding ->
        if (carregando) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            Column(modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
                videos.forEach { video ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(video.url))
                            context.startActivity(intent)
                        },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8E9))
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(50.dp).background(Color.Red, RoundedCornerShape(25.dp)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(video.titulo, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(video.descricao, fontSize = 13.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- LÓGICA DE CARREGAMENTO (GIT) ---
suspend fun carregarGlossarioDoGit(): List<GlossarioItem> {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL("https://raw.githubusercontent.com/carlosbetos/simuladofederal/main/app/src/main/assets/glossario.txt?t=${System.currentTimeMillis()}")
            val content = url.openConnection().getInputStream().bufferedReader().use { it.readText() }
            content.split("---").map { bloco ->
                val linhas = bloco.trim().lines().map { it.trim() }
                GlossarioItem(
                    termo = linhas.find { it.startsWith("Termo:") }?.substringAfter(":")?.trim() ?: "",
                    definicao = linhas.find { it.startsWith("O que é:") }?.substringAfter(":")?.trim() ?: "",
                    exemplo = linhas.find { it.startsWith("Exemplo:") }?.substringAfter(":")?.trim() ?: ""
                )
            }.filter { it.termo.isNotEmpty() }
        } catch (e: Exception) { emptyList() }
    }
}

suspend fun carregarVideosDoGit(): List<VideoItem> {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL("https://raw.githubusercontent.com/carlosbetos/simuladofederal/main/app/src/main/assets/videos.txt?t=${System.currentTimeMillis()}")
            val content = url.openConnection().getInputStream().bufferedReader().use { it.readText() }
            content.split("---").map { bloco ->
                val linhas = bloco.trim().lines().map { it.trim() }
                VideoItem(
                    titulo = linhas.find { it.startsWith("Título:") }?.substringAfter(":")?.trim() ?: "",
                    descricao = linhas.find { it.startsWith("Descrição:") }?.substringAfter(":")?.trim() ?: "",
                    url = linhas.find { it.startsWith("URL:") }?.substringAfter(":")?.trim() ?: ""
                )
            }.filter { it.titulo.isNotEmpty() }
        } catch (e: Exception) { emptyList() }
    }
}

// --- TELA SELEÇÃO MATÉRIA ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaSelecaoMateria(materias: List<Materia>, onMateriaEscolhida: (Materia) -> Unit, onVoltar: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Escolha a Matéria") },
                navigationIcon = { IconButton(onClick = onVoltar) { Icon(Icons.Default.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
            materias.forEach { materia ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { onMateriaEscolhida(materia) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
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

@Composable
fun SimuladoApp(materia: Materia, onVoltarMenu: () -> Unit) {
    val context = LocalContext.current
    var listaQuestoes by remember { mutableStateOf<List<Questao>>(emptyList()) }
    var carregando by remember { mutableStateOf(true) }
    var indiceQuestao by rememberSaveable { mutableIntStateOf(0) }
    var simuladoFinalizado by rememberSaveable { mutableStateOf(false) }
    val estatisticas = remember { mutableStateMapOf<String, ResultadoMateria>() }
    val respostasUsuario = remember { mutableStateMapOf<Int, Int>() }

    LaunchedEffect(materia) {
        carregando = true
        var questoes = carregarQuestoesDaInternet(materia.arquivo)
        if (questoes.isEmpty()) {
            questoes = carregarQuestoesDoArquivo(context, "questoes.txt")
        }
        listaQuestoes = questoes
        carregando = false
    }

    if (carregando) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else if (!simuladoFinalizado) {
        TelaQuestao(
            questao = listaQuestoes[indiceQuestao],
            numeroAtual = indiceQuestao + 1,
            total = listaQuestoes.size,
            respostaJaDada = respostasUsuario[indiceQuestao] ?: -1,
            onVoltar = if (indiceQuestao > 0) { { indiceQuestao -= 1 } } else onVoltarMenu,
            onProxima = { selecionado ->
                val q = listaQuestoes[indiceQuestao]
                if (!respostasUsuario.containsKey(indiceQuestao)) {
                    val status = estatisticas.getOrPut(q.materia) { ResultadoMateria(q.materia) }
                    status.total++
                    if (selecionado == q.respostaCorreta) status.acertos++
                    respostasUsuario[indiceQuestao] = selecionado
                }
                if (indiceQuestao < listaQuestoes.size - 1) indiceQuestao++ else simuladoFinalizado = true
            }
        )
    } else {
        TelaDesempenho(estatisticas.values.toList(), onVoltarMenu)
    }
}

suspend fun carregarQuestoesDaInternet(nomeArquivo: String): List<Questao> {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL("https://raw.githubusercontent.com/carlosbetos/simuladofederal/main/app/src/main/assets/$nomeArquivo?t=${System.currentTimeMillis()}")
            val texto = url.openConnection().getInputStream().bufferedReader().use { it.readText() }
            parsearConteudoTxt(texto)
        } catch (e: Exception) { emptyList() }
    }
}

fun carregarQuestoesDoArquivo(context: Context, fileName: String): List<Questao> {
    return try {
        val content = context.assets.open(fileName).bufferedReader().use { it.readText() }
        parsearConteudoTxt(content)
    } catch (e: Exception) { emptyList() }
}

fun parsearConteudoTxt(content: String): List<Questao> {
    val questoes = mutableListOf<Questao>()
    val blocos = content.split("---")
    for (bloco in blocos) {
        val linhas = bloco.trim().lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (linhas.size >= 5) {
            val materia = linhas[0]; val id = linhas[1]
            val idxA = linhas.indexOfFirst { it.startsWith("a)") }
            if (idxA == -1) continue
            val img = linhas.find { it.startsWith("IMAGEM:") }?.substringAfter(":")?.trim()
            val perg = linhas.subList(2, idxA).filter { !it.startsWith("IMAGEM:") }.joinToString("\n")
            val opc = listOf(linhas[idxA].substringAfter("a)").trim(), linhas[idxA+1].substringAfter("b)").trim(), linhas[idxA+2].substringAfter("c)").trim(), linhas[idxA+3].substringAfter("d)").trim())
            val resp = linhas.find { it.contains("RESPOSTA:") }?.substringAfter(":")?.trim()?.lowercase() ?: "a"
            val idxResp = when(resp) { "a"->0; "b"->1; "c"->2; "d"->3; else->0 }
            val expl = linhas.find { it.contains("EXPLICAÇÃO:") || it.contains("EXPLICACAO:") }?.substringAfter(":")?.trim() ?: ""
            questoes.add(Questao(materia, id, perg, img, opc, idxResp, expl))
        }
    }
    return questoes
}

@Composable
fun TelaQuestao(questao: Questao, numeroAtual: Int, total: Int, respostaJaDada: Int, onVoltar: (() -> Unit)?, onProxima: (Int) -> Unit) {
    var selecionado by rememberSaveable(questao) { mutableIntStateOf(respostaJaDada) }
    var respondeu by rememberSaveable(questao) { mutableStateOf(respostaJaDada != -1) }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(questao.identificacao, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
                Text("$numeroAtual de $total", fontSize = 12.sp, color = Color.Gray)
            }
            LinearProgressIndicator(progress = { numeroAtual.toFloat() / total }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8E9))) {
                Text(questao.materia, modifier = Modifier.padding(12.dp), color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
            }
            Text(text = questao.pergunta, fontSize = 17.sp, fontWeight = FontWeight.Medium, lineHeight = 24.sp)
            questao.caminhoImagem?.let { caminho ->
                Spacer(modifier = Modifier.height(16.dp))
                Card(elevation = CardDefaults.cardElevation(4.dp)) {
                    if (caminho.startsWith("http")) {
                        AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(caminho).crossfade(true).build(), contentDescription = null, modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp), contentScale = ContentScale.Fit)
                    } else {
                        val id = context.resources.getIdentifier(caminho, "drawable", context.packageName)
                        if (id != 0) Image(painter = painterResource(id = id), contentDescription = null, modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp), contentScale = ContentScale.Fit)
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            questao.opcoes.forEachIndexed { index, texto ->
                val letra = when(index) { 0->"a"; 1->"b"; 2->"c"; else->"d" }
                val cor = when {
                    respondeu && index == questao.respostaCorreta -> Color(0xFFC8E6C9)
                    respondeu && index == selecionado && index != questao.respostaCorreta -> Color(0xFFFFCDD2)
                    selecionado == index -> Color(0xFFE3F2FD)
                    else -> Color.White
                }
                OutlinedButton(
                    onClick = { if (!respondeu) selecionado = index },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = cor, contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = "$letra) $texto", textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth())
                }
            }
            AnimatedVisibility(visible = respondeu) {
                Column(modifier = Modifier.padding(vertical = 16.dp)) {
                    Divider(color = Color.LightGray)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(36.dp).background(Color(0xFF1B5E20), RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) { Text("👨‍🏫", fontSize = 18.sp) }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Explicação do Professor", fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
                    }
                    Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FBE7))) {
                        Text(text = questao.explicacao, modifier = Modifier.padding(16.dp), fontSize = 14.sp, fontStyle = FontStyle.Italic, lineHeight = 22.sp)
                    }
                }
            }
        }
        Surface(tonalElevation = 8.dp, shadowElevation = 12.dp, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onVoltar != null) OutlinedButton(onClick = onVoltar, modifier = Modifier.weight(1f).height(52.dp)) { Text(if (numeroAtual == 1) "Sair" else "Anterior") }
                if (!respondeu) {
                    Button(onClick = { respondeu = true }, enabled = selecionado != -1, modifier = Modifier.weight(2f).height(52.dp)) { Text("Confirmar") }
                } else {
                    Button(onClick = { onProxima(selecionado) }, modifier = Modifier.weight(2f).height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))) { Text("Próxima") }
                }
            }
        }
    }
}

@Composable
fun TelaDesempenho(resultados: List<ResultadoMateria>, onVoltar: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Simulado Concluído", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
        Spacer(modifier = Modifier.height(24.dp))
        val totalA = resultados.sumOf { it.acertos }
        val totalQ = resultados.sumOf { it.total }
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$totalA de $totalQ", fontSize = 48.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2E7D32))
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        resultados.forEach { res ->
            val p = (res.acertos.toFloat() / res.total * 100).toInt()
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text("${res.materia}: $p%", fontWeight = FontWeight.Medium)
                LinearProgressIndicator(progress = { res.acertos.toFloat() / res.total }, modifier = Modifier.fillMaxWidth().height(8.dp).padding(top = 4.dp))
            }
        }
        Spacer(modifier = Modifier.height(40.dp))
        Button(onClick = onVoltar, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Voltar ao Menu") }
    }
}
