package com.prova.simuladofederal

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.BufferedReader
import java.io.InputStreamReader

data class Questao(
    val materia: String,
    val identificacao: String,
    val pergunta: String,
    val nomeImagem: String?,
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
                    SimuladoApp()
                }
            }
        }
    }
}

@Composable
fun SimuladoApp() {
    val context = LocalContext.current
    var listaQuestoes by remember { mutableStateOf<List<Questao>>(emptyList()) }
    var carregando by remember { mutableStateOf(true) }
    var indiceQuestao by remember { mutableIntStateOf(0) }
    var simuladoFinalizado by remember { mutableStateOf(false) }
    
    // Armazena as estatísticas por matéria
    val estatisticas = remember { mutableStateMapOf<String, ResultadoMateria>() }
    
    // Novo: Armazena as respostas dadas pelo usuário (índice da questão -> índice da resposta)
    val respostasUsuario = remember { mutableStateMapOf<Int, Int>() }

    LaunchedEffect(Unit) {
        listaQuestoes = carregarQuestoesDoArquivo(context, "questoes.txt")
        carregando = false
    }

    if (carregando) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (listaQuestoes.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Erro ao carregar simulado.")
        }
    } else if (!simuladoFinalizado) {
        TelaQuestao(
            questao = listaQuestoes[indiceQuestao],
            numeroAtual = indiceQuestao + 1,
            total = listaQuestoes.size,
            respostaJaDada = respostasUsuario[indiceQuestao] ?: -1,
            onVoltar = if (indiceQuestao > 0) { { indiceQuestao-- } } else null,
            onProxima = { selecionado ->
                val q = listaQuestoes[indiceQuestao]
                
                // Só computa estatística e salva a resposta se for a primeira vez respondendo esta questão
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
        TelaDesempenho(estatisticas.values.toList()) {
            indiceQuestao = 0
            estatisticas.clear()
            respostasUsuario.clear()
            simuladoFinalizado = false
        }
    }
}

fun carregarQuestoesDoArquivo(context: Context, fileName: String): List<Questao> {
    val questoes = mutableListOf<Questao>()
    try {
        val reader = BufferedReader(InputStreamReader(context.assets.open(fileName)))
        val content = reader.readText()
        val blocos = content.split("---")

        for (bloco in blocos) {
            val linhas = bloco.trim().lines().filter { it.isNotBlank() }
            if (linhas.size >= 5) {
                val materia = linhas[0].trim()
                val id = linhas[1].trim()
                val indexOpcaoA = linhas.indexOfFirst { it.trim().startsWith("a)") }
                if (indexOpcaoA == -1) continue

                val linhaImg = linhas.find { it.startsWith("IMAGEM:", ignoreCase = true) }
                val nomeImagem = linhaImg?.substringAfter(":")?.trim()

                val pergunta = linhas.subList(2, indexOpcaoA)
                    .filter { !it.startsWith("IMAGEM:", ignoreCase = true) }
                    .joinToString("\n").trim()
                
                val opcoes = listOf(
                    linhas[indexOpcaoA].substringAfter("a)").trim(),
                    linhas[indexOpcaoA+1].substringAfter("b)").trim(),
                    linhas[indexOpcaoA+2].substringAfter("c)").trim(),
                    linhas[indexOpcaoA+3].substringAfter("d)").trim()
                )

                val resp = linhas.find { it.contains("RESPOSTA:") }?.substringAfter(":")?.trim()?.lowercase()
                val indexResp = when(resp) { "a"->0; "b"->1; "c"->2; "d"->3; else->0 }
                
                val explicacao = linhas.find { it.contains("EXPLICAÇÃO:") || it.contains("EXPLICACAO:") }
                    ?.substringAfter(":")?.trim() ?: ""

                questoes.add(Questao(materia, id, pergunta, nomeImagem, opcoes, indexResp, explicacao))
            }
        }
    } catch (e: Exception) { e.printStackTrace() }
    return questoes
}

@Composable
fun TelaQuestao(
    questao: Questao, 
    numeroAtual: Int, 
    total: Int, 
    respostaJaDada: Int, 
    onVoltar: (() -> Unit)?, 
    onProxima: (Int) -> Unit
) {
    // Se já respondeu antes, recuperamos o estado. Caso contrário, inicia limpo.
    var selecionado by remember(questao) { mutableIntStateOf(respostaJaDada) }
    var respondeu by remember(questao) { mutableStateOf(respostaJaDada != -1) }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Text("${questao.identificacao} ($numeroAtual de $total)", color = Color.Gray, style = MaterialTheme.typography.labelMedium)
            LinearProgressIndicator(progress = { numeroAtual.toFloat() / total }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))

            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8E9))) {
                Text(questao.materia, modifier = Modifier.padding(8.dp), color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
            }

            Text(text = questao.pergunta, fontSize = 18.sp, fontWeight = FontWeight.Medium)

            questao.nomeImagem?.let { nome ->
                val id = context.resources.getIdentifier(nome, "drawable", context.packageName)
                if (id != 0) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(elevation = CardDefaults.cardElevation(4.dp), modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Image(
                            painter = painterResource(id = id),
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                            contentScale = ContentScale.Fit
                        )
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
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = cor, contentColor = Color.Black)
                ) {
                    Text(text = "$letra) $texto", textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth())
                }
            }

            AnimatedVisibility(visible = respondeu) {
                Column {
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), colors = CardDefaults.cardColors(containerColor = if (selecionado == questao.respostaCorreta) Color(0xFFE8F5E9) else Color(0xFFFBE9E7))) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(if (selecionado == questao.respostaCorreta) "✅ Acertou!" else "❌ Errou!", fontWeight = FontWeight.Bold)
                            Text(text = questao.explicacao, fontSize = 14.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        Surface(
            tonalElevation = 8.dp,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Botão de Voltar (Consulta)
                if (onVoltar != null) {
                    OutlinedButton(
                        onClick = onVoltar,
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Anterior")
                    }
                }
                
                val weightBotaoAcao = if (onVoltar != null) 2f else 1f
                
                if (!respondeu) {
                    Button(
                        onClick = { respondeu = true },
                        enabled = selecionado != -1,
                        modifier = Modifier.weight(weightBotaoAcao).height(56.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Confirmar", fontSize = 18.sp)
                    }
                } else {
                    Button(
                        onClick = { onProxima(selecionado) },
                        modifier = Modifier.weight(weightBotaoAcao).height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) {
                        Text("Próxima", fontSize = 18.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun TelaDesempenho(resultados: List<ResultadoMateria>, onReiniciar: () -> Unit) {
    val totalAcertos = resultados.sumOf { it.acertos }
    val totalQuestoes = resultados.sumOf { it.total }
    val porcentagemGeral = if (totalQuestoes > 0) (totalAcertos.toFloat() / totalQuestoes * 100).toInt() else 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Desempenho Final", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
        
        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Acerto Geral", fontWeight = FontWeight.Bold)
                Text("$porcentagemGeral%", fontSize = 48.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2E7D32))
                Text("Você acertou $totalAcertos de $totalQuestoes questões")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text("Por Matéria", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
        
        Spacer(modifier = Modifier.height(16.dp))

        resultados.forEach { res ->
            val porcentagem = (res.acertos.toFloat() / res.total * 100).toInt()
            val corBarra = when {
                porcentagem >= 70 -> Color(0xFF4CAF50)
                porcentagem >= 50 -> Color(0xFFFFC107)
                else -> Color(0xFFF44336)
            }

            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(res.materia, fontWeight = FontWeight.Medium)
                    Text("$porcentagem%", fontWeight = FontWeight.Bold, color = corBarra)
                }
                LinearProgressIndicator(
                    progress = { res.acertos.toFloat() / res.total },
                    modifier = Modifier.fillMaxWidth().height(10.dp).padding(top = 4.dp),
                    color = corBarra,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        val materiaRuim = resultados.filter { (it.acertos.toFloat() / it.total) < 0.6 }.map { it.materia }
        if (materiaRuim.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("💡 Onde focar agora:", fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                    Text(
                        "Recomendamos revisar: ${materiaRuim.joinToString(", ")}.",
                        fontSize = 14.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = onReiniciar,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Reiniciar Simulado", fontSize = 18.sp)
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}
