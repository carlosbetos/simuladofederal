package com.prova.simuladofederal

import android.content.Context
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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

data class Materia(
    val nome: String,
    val arquivo: String,
    val icone: String // Emojis para facilitar
)

data class Questao(
    val materia: String,
    val identificacao: String,
    val pergunta: String,
    val caminhoImagem: String?,
    val opcoes: List<String>,
    val respostaCorreta: Int,
    val explicacao: String
)

data class ResultadoMateria(
    val materia: String,
    var total: Int = 0,
    var acertos: Int = 0
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF1B5E20))) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SimuladoManager()
                }
            }
        }
    }
}

@Composable
fun SimuladoManager() {
    var materiaSelecionada by remember { mutableStateOf<Materia?>(null) }
    
    if (materiaSelecionada == null) {
        TelaSelecaoMateria { materiaSelecionada = it }
    } else {
        SimuladoApp(materiaSelecionada!!) { materiaSelecionada = null }
    }
}

@Composable
fun TelaSelecaoMateria(onMateriaEscolhida: (Materia) -> Unit) {
    val materias = listOf(
        Materia("Português", "questoesPortugues.txt", "📚"),
        Materia("Matemática", "questoesMatematica.txt", "📐"),
        Materia("Química", "questoesQuimica.txt", "🧪"),
        Materia("Biologia", "questoesBiologia.txt", "🧬"),
        Materia("Geografia", "questoesGeografia.txt", "🌎"),
        Materia("História", "questoesHistoria.txt", "⏳"),
        Materia("Simulado Geral", "questoes.txt", "📝")
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Text("O que vamos estudar hoje?", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
        Text("Selecione uma matéria para iniciar", fontSize = 14.sp, color = Color.Gray)
        
        Spacer(modifier = Modifier.height(32.dp))

        materias.forEach { materia ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { onMateriaEscolhida(materia) },
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(4.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
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

@Composable
fun SimuladoApp(materia: Materia, onVoltarMenu: () -> Unit) {
    val context = LocalContext.current
    var listaQuestoes by remember { mutableStateOf<List<Questao>>(emptyList()) }
    var carregando by remember { mutableStateOf(true) }
    var indiceQuestao by remember { mutableIntStateOf(0) }
    var simuladoFinalizado by remember { mutableStateOf(false) }
    val estatisticas = remember { mutableStateMapOf<String, ResultadoMateria>() }
    val respostasUsuario = remember { mutableStateMapOf<Int, Int>() }

    LaunchedEffect(materia) {
        carregando = true
        var questoes = carregarQuestoesDaInternet(materia.arquivo)
        if (questoes.isEmpty()) {
            questoes = carregarQuestoesDoArquivo(context, "questoes.txt") // Fallback
        }
        listaQuestoes = questoes
        carregando = false
    }

    if (carregando) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(12.dp))
                Text("Baixando questões de ${materia.nome}...", color = Color.Gray)
            }
        }
    } else if (listaQuestoes.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Erro ao carregar questões.")
                Button(onClick = onVoltarMenu) { Text("Voltar") }
            }
        }
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

                if (indiceQuestao < listaQuestoes.size - 1) {
                    indiceQuestao++
                } else {
                    simuladoFinalizado = true
                }
            }
        )
    } else {
        TelaDesempenho(estatisticas.values.toList(), onVoltarMenu)
    }
}

suspend fun carregarQuestoesDaInternet(nomeArquivo: String): List<Questao> {
    return withContext(Dispatchers.IO) {
        try {
            val timestamp = System.currentTimeMillis()
            val urlGit = "https://raw.githubusercontent.com/carlosbetos/simuladofederal/main/app/src/main/assets/$nomeArquivo?t=$timestamp"
            
            val url = URL(urlGit)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.useCaches = false
            
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.getInputStream()))
                val texto = reader.readText()
                parsearConteudoTxt(texto)
            } else emptyList()
        } catch (e: Exception) { emptyList() }
    }
}

fun carregarQuestoesDoArquivo(context: Context, fileName: String): List<Questao> {
    return try {
        val stream = context.assets.open(fileName)
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
            val materia = linhas[0]
            val id = linhas[1]
            val indexOpcaoA = linhas.indexOfFirst { it.startsWith("a)") }
            if (indexOpcaoA == -1) continue
            val linhaImg = linhas.find { it.startsWith("IMAGEM:") }
            val caminhoImagem = linhaImg?.substringAfter(":")?.trim()
            val pergunta = linhas.subList(2, indexOpcaoA).filter { !it.startsWith("IMAGEM:") }.joinToString("\n")
            val opcoes = listOf(
                linhas[indexOpcaoA].substringAfter("a)").trim(),
                linhas[indexOpcaoA+1].substringAfter("b)").trim(),
                linhas[indexOpcaoA+2].substringAfter("c)").trim(),
                linhas[indexOpcaoA+3].substringAfter("d)").trim()
            )
            val resp = linhas.find { it.contains("RESPOSTA:") }?.substringAfter(":")?.trim()?.lowercase() ?: "a"
            val indexResp = when(resp) { "a"->0; "b"->1; "c"->2; "d"->3; else->0 }
            val explicacao = linhas.find { it.contains("EXPLICAÇÃO:") || it.contains("EXPLICACAO:") }?.substringAfter(":")?.trim() ?: ""
            questoes.add(Questao(materia, id, pergunta, caminhoImagem, opcoes, indexResp, explicacao))
        }
    }
    return questoes
}

@Composable
fun TelaQuestao(questao: Questao, numeroAtual: Int, total: Int, respostaJaDada: Int, onVoltar: (() -> Unit)?, onProxima: (Int) -> Unit) {
    var selecionado by remember(questao) { mutableIntStateOf(respostaJaDada) }
    var respondeu by remember(questao) { mutableStateOf(respostaJaDada != -1) }
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
                        Box(modifier = Modifier.size(36.dp).background(Color(0xFF1B5E20), RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                            Text("👨‍🏫", fontSize = 18.sp)
                        }
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
