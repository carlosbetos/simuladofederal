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

    when (telaAtual) {
        "home" -> TelaPrincipal(
            onSimuladosClick = { telaAtual = "selecao_materia" },
            onGlossarioClick = { telaAtual = "glossario" },
            onVideosClick = { telaAtual = "videos" }
        )
        "selecao_materia" -> TelaSelecaoMateria(
            onMateriaEscolhida = { 
                materiaSelecionada = it
                telaAtual = "selecao_data"
            },
            onVoltar = { telaAtual = "home" }
        )
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
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Federal Simu", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1B5E20))
        Text("Preparação de Elite", fontSize = 14.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(48.dp))

        MenuButton("Iniciar Simulado", "Pratique por disciplina", Icons.Default.PlayArrow, Color(0xFF1B5E20), onSimuladosClick)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaSelecaoMateria(onMateriaEscolhida: (Materia) -> Unit, onVoltar: () -> Unit) {
    val materias = listOf(
        Materia("Português", "portugues", "📚"),
        Materia("Matemática", "matematica", "📐"),
        Materia("Química", "quimica", "🧪"),
        Materia("Biologia", "biologia", "🧬"),
        Materia("Geografia", "geografia", "🌎"),
        Materia("História", "historia", "⏳")
    )

    Scaffold(
        topBar = { TopAppBar(title = { Text("Escolha a Disciplina") }, navigationIcon = { IconButton(onClick = onVoltar) { Icon(Icons.Default.ArrowBack, null) } }) }
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

    Scaffold(
        topBar = { TopAppBar(title = { Text("Simulados: ${materia.nome}") }, navigationIcon = { IconButton(onClick = onVoltar) { Icon(Icons.Default.ArrowBack, null) } }) }
    ) { padding ->
        if (carregando) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (simulados.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Nenhum simulado disponível.") }
        } else {
            Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
                simulados.forEach { simulado ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { onSimuladoEscolhido(simulado.nomeArquivo) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DateRange, contentDescription = null, tint = Color(0xFF1B5E20))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Simulado de ${simulado.dataFormatada}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

suspend fun carregarListaSimuladosDoGit(pasta: String): List<SimuladoInfo> {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/repos/carlosbetos/simuladofederal/contents/app/src/main/assets/$pasta")
            val connection = url.openConnection() as HttpURLConnection
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            if (connection.responseCode == 200) {
                val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
                val array = JSONArray(jsonStr)
                val lista = mutableListOf<SimuladoInfo>()
                for (i in 0 until array.length()) {
                    val file = array.getJSONObject(i)
                    val name = file.getString("name")
                    if (name.endsWith(".txt")) lista.add(SimuladoInfo(name, extrairDataDoNome(name)))
                }
                lista.sortedByDescending { it.dataFormatada }
            } else emptyList()
        } catch (e: Exception) { emptyList() }
    }
}

fun carregarListaSimuladosLocal(context: Context, pasta: String): List<SimuladoInfo> {
    return try {
        context.assets.list(pasta)?.filter { it.endsWith(".txt") }?.map { SimuladoInfo(it, extrairDataDoNome(it)) } ?: emptyList()
    } catch (e: Exception) { emptyList() }
}

fun extrairDataDoNome(nome: String): String {
    val parteData = nome.substringAfter("-").substringBefore(".txt")
    return parteData.replace("-", "/")
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
        TelaDesempenho(materia.nome, extrairDataDoNome(nomeArquivo), estatisticas.values.toList(), onVoltarMenu)
    }
}

@Composable
fun TelaDesempenho(materiaNome: String, dataSimulado: String, resultados: List<ResultadoMateria>, onVoltar: () -> Unit) {
    val context = LocalContext.current
    val totalA = resultados.sumOf { it.acertos }
    val totalQ = resultados.sumOf { it.total }
    val pGeral = if (totalQ > 0) (totalA.toFloat() / totalQ * 100).toInt() else 0

    Column(modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Simulado Concluído", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
        Spacer(modifier = Modifier.height(24.dp))
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Desempenho: $pGeral%")
                Text("$totalA de $totalQ acertos", fontSize = 48.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2E7D32))
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
        Button(
            onClick = {
                enviarEmailRelatorio(context, materiaNome, dataSimulado, totalA, totalQ, resultados)
                onVoltar()
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Enviar Relatório e Sair", fontSize = 16.sp)
        }
    }
}

fun enviarEmailRelatorio(context: Context, materia: String, data: String, acertos: Int, total: Int, detalhes: List<ResultadoMateria>) {
    val porcentagem = if (total > 0) (acertos.toFloat() / total * 100).toInt() else 0
    val corpoEmail = StringBuilder()
    corpoEmail.append("Relatório de Desempenho - Federal Simu\n")
    corpoEmail.append("--------------------------------------\n")
    corpoEmail.append("Disciplina: $materia\n")
    corpoEmail.append("Data do Simulado: $data\n")
    corpoEmail.append("Resultado Geral: $porcentagem% ($acertos de $total acertos)\n\n")
    corpoEmail.append("Desempenho por Categoria:\n")
    detalhes.forEach {
        val p = (it.acertos.toFloat() / it.total * 100).toInt()
        corpoEmail.append("- ${it.materia}: $p% (${it.acertos}/${it.total})\n")
    }
    
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        this.data = Uri.parse("mailto:")
        putExtra(Intent.EXTRA_EMAIL, arrayOf("carlosbetos@gmail.com"))
        putExtra(Intent.EXTRA_SUBJECT, "📊 Simulado Concluído: $materia ($data)")
        putExtra(Intent.EXTRA_TEXT, corpoEmail.toString())
    }
    
    try {
        context.startActivity(Intent.createChooser(intent, "Enviar relatório via..."))
    } catch (e: Exception) {
        Log.e("EMAIL", "Erro ao abrir app de email")
    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaGlossario(onVoltar: () -> Unit) {
    var glossario by remember { mutableStateOf<List<GlossarioItem>>(emptyList()) }
    var filtro by remember { mutableStateOf("") }
    var carregando by remember { mutableStateOf(true) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val remoto = carregarGlossarioDoGit()
        glossario = if (remoto.isNotEmpty()) remoto else carregarGlossarioLocal(context)
        carregando = false
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Glossário") }, navigationIcon = { IconButton(onClick = onVoltar) { Icon(Icons.Default.ArrowBack, null) } }) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            OutlinedTextField(value = filtro, onValueChange = { filtro = it }, label = { Text("Buscar termo...") }, modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Search, null) }, shape = RoundedCornerShape(12.dp))
            Spacer(modifier = Modifier.height(16.dp))
            if (carregando) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                val listaExibicao = glossario.filter { it.termo.contains(filtro, ignoreCase = true) }.sortedBy { it.termo }
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    listaExibicao.forEach { item ->
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaVideos(onVoltar: () -> Unit) {
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var carregando by remember { mutableStateOf(true) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val remoto = carregarVideosDoGit()
        videos = if (remoto.isNotEmpty()) remoto else carregarVideosLocal(context)
        carregando = false
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Aulas e Dicas") }, navigationIcon = { IconButton(onClick = onVoltar) { Icon(Icons.Default.ArrowBack, null) } }) }
    ) { padding ->
        if (carregando) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            Column(modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
                videos.forEach { video ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { val intent = Intent(Intent.ACTION_VIEW, Uri.parse(video.url)); context.startActivity(intent) }, colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8E9))) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(50.dp).background(Color.Red, RoundedCornerShape(25.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White) }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(video.titulo, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

// --- FUNÇÕES DE CARREGAMENTO (GIT) ---
suspend fun carregarGlossarioDoGit(): List<GlossarioItem> {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL("https://raw.githubusercontent.com/carlosbetos/simuladofederal/main/app/src/main/assets/glossario.txt?t=${System.currentTimeMillis()}")
            val content = url.openConnection().getInputStream().bufferedReader().use { it.readText() }
            parsearGlossario(content)
        } catch (e: Exception) { emptyList() }
    }
}

suspend fun carregarVideosDoGit(): List<VideoItem> {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL("https://raw.githubusercontent.com/carlosbetos/simuladofederal/main/app/src/main/assets/videos.txt?t=${System.currentTimeMillis()}")
            val content = url.openConnection().getInputStream().bufferedReader().use { it.readText() }
            parsearVideos(content)
        } catch (e: Exception) { emptyList() }
    }
}

fun carregarGlossarioLocal(context: Context): List<GlossarioItem> {
    return try { val content = context.assets.open("glossario.txt").bufferedReader().use { it.readText() }; parsearGlossario(content) } catch (e: Exception) { emptyList() }
}

fun carregarVideosLocal(context: Context): List<VideoItem> {
    return try { val content = context.assets.open("videos.txt").bufferedReader().use { it.readText() }; parsearVideos(content) } catch (e: Exception) { emptyList() }
}

fun parsearGlossario(texto: String): List<GlossarioItem> {
    return texto.split("---").mapNotNull { bloco ->
        val linhas = bloco.trim().lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (linhas.isEmpty()) return@mapNotNull null
        var termo = ""; var oQueE = ""; var exemplo = ""
        for (i in linhas.indices) {
            val linha = linhas[i]
            when {
                linha.startsWith("Termo:", true) -> { termo = linha.substringAfter(":").trim(); if (termo.isEmpty() && i+1 < linhas.size) termo = linhas[i+1] }
                linha.startsWith("O que é:", true) -> { oQueE = linha.substringAfter(":").trim(); if (oQueE.isEmpty() && i+1 < linhas.size) oQueE = linhas[i+1] }
                linha.startsWith("Exemplo:", true) -> { exemplo = linha.substringAfter(":").trim(); if (exemplo.isEmpty() && i+1 < linhas.size) exemplo = linhas[i+1] }
            }
        }
        if (termo.isNotBlank()) GlossarioItem(termo, oQueE, exemplo) else null
    }
}

fun parsearVideos(texto: String): List<VideoItem> {
    return texto.split("---").mapNotNull { bloco ->
        val linhas = bloco.trim().lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (linhas.isEmpty()) return@mapNotNull null
        var tema = ""; var link = ""
        for (i in linhas.indices) {
            val linha = linhas[i]
            when {
                linha.startsWith("Tema:", true) -> { tema = linha.substringAfter(":").trim(); if (tema.isEmpty() && i+1 < linhas.size) tema = linhas[i+1] }
                linha.startsWith("Link:", true) -> { link = linha.substringAfter(":").trim(); if (link.isEmpty() && i+1 < linhas.size) link = linhas[i+1] }
            }
        }
        if (tema.isNotBlank() && link.isNotBlank()) VideoItem(tema, link) else null
    }
}

suspend fun carregarQuestoesDaInternet(path: String): List<Questao> {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL("https://raw.githubusercontent.com/carlosbetos/simuladofederal/main/app/src/main/assets/$path?t=${System.currentTimeMillis()}")
            val texto = url.openConnection().getInputStream().bufferedReader().use { it.readText() }
            parsearConteudoTxt(texto)
        } catch (e: Exception) { emptyList() }
    }
}

fun carregarQuestoesDoArquivo(context: Context, path: String): List<Questao> {
    return try {
        val stream = context.assets.open(path)
        val content = stream.bufferedReader().use { it.readText() }
        parsearConteudoTxt(content)
    } catch (e: Exception) { emptyList() }
}

fun parsearConteudoTxt(content: String): List<Questao> {
    val questoes = mutableListOf<Questao>()
    val blocos = content.split("---")
    for (bloco in blocos) {
        val linhas = bloco.trim().lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (linhas.size >= 5) {
            val mat = linhas[0]; val id = linhas[1]
            val idxA = linhas.indexOfFirst { it.startsWith("a)") }
            if (idxA == -1) continue
            val img = linhas.find { it.startsWith("IMAGEM:") }?.substringAfter(":")?.trim()
            val perg = linhas.subList(2, idxA).filter { !it.startsWith("IMAGEM:") }.joinToString("\n")
            val opc = listOf(linhas[idxA].substringAfter("a)").trim(), linhas[idxA+1].substringAfter("b)").trim(), linhas[idxA+2].substringAfter("c)").trim(), linhas[idxA+3].substringAfter("d)").trim())
            val resp = linhas.find { it.contains("RESPOSTA:") }?.substringAfter(":")?.trim()?.lowercase() ?: "a"
            val idxResp = when(resp) { "a"->0; "b"->1; "c"->2; "d"->3; else->0 }
            val expl = linhas.find { it.contains("EXPLICAÇÃO:") || it.contains("EXPLICACAO:") }?.substringAfter(":")?.trim() ?: ""
            questoes.add(Questao(mat, id, perg, img, opc, idxResp, expl))
        }
    }
    return questoes
}
