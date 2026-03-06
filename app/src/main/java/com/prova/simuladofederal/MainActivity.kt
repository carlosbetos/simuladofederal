package com.prova.simuladofederal

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
    val nomeImagem: String?, // Nome do arquivo em res/drawable
    val opcoes: List<String>,
    val respostaCorreta: Int,
    val explicacao: String
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
    var pontuacao by remember { mutableIntStateOf(0) }
    var simuladoFinalizado by remember { mutableStateOf(false) }

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
            Text("Nenhuma questão encontrada no arquivo.")
        }
    } else if (!simuladoFinalizado) {
        TelaQuestao(
            questao = listaQuestoes[indiceQuestao],
            numeroAtual = indiceQuestao + 1,
            total = listaQuestoes.size,
            onProxima = { acertou ->
                if (acertou) pontuacao++
                if (indiceQuestao < listaQuestoes.size - 1) {
                    indiceQuestao++
                } else {
                    simuladoFinalizado = true
                }
            }
        )
    } else {
        TelaResultado(pontuacao, listaQuestoes.size) {
            indiceQuestao = 0
            pontuacao = 0
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
                val identificacao = linhas[1].trim()
                
                val indexOpcaoA = linhas.indexOfFirst { it.trim().startsWith("a)") }
                if (indexOpcaoA == -1) continue

                // Detectar se existe a tag de imagem
                val linhaImagem = linhas.find { it.startsWith("IMAGEM:", ignoreCase = true) }
                val nomeImagem = linhaImagem?.substringAfter(":")?.trim()

                // O enunciado é tudo entre o ID e as Opções, removendo a linha da imagem se existir
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

                questoes.add(Questao(materia, identificacao, pergunta, nomeImagem, opcoes, indexResp, explicacao))
            }
        }
    } catch (e: Exception) { e.printStackTrace() }
    return questoes
}

@Composable
fun TelaQuestao(questao: Questao, numeroAtual: Int, total: Int, onProxima: (Boolean) -> Unit) {
    var selecionado by remember(questao) { mutableIntStateOf(-1) }
    var respondeu by remember(questao) { mutableStateOf(false) }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("${questao.identificacao} ($numeroAtual de $total)", color = Color.Gray, style = MaterialTheme.typography.labelMedium)
        LinearProgressIndicator(progress = { numeroAtual.toFloat() / total }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))

        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8E9))) {
            Text(questao.materia, modifier = Modifier.padding(8.dp), color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
        }

        Text(text = questao.pergunta, fontSize = 18.sp, fontWeight = FontWeight.Medium)

        // SEÇÃO DE IMAGEM
        questao.nomeImagem?.let { nome ->
            val resourceId = context.resources.getIdentifier(nome, "drawable", context.packageName)
            if (resourceId != 0) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(elevation = CardDefaults.cardElevation(4.dp), modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Image(
                        painter = painterResource(id = resourceId),
                        contentDescription = "Imagem da questão",
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
                respondeu && index == selecionado -> Color(0xFFFFCDD2)
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

        if (!respondeu) {
            Button(onClick = { respondeu = true }, enabled = selecionado != -1, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text("Confirmar Resposta")
            }
        }

        AnimatedVisibility(visible = respondeu) {
            Column {
                Card(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), colors = CardDefaults.cardColors(containerColor = if (selecionado == questao.respostaCorreta) Color(0xFFE8F5E9) else Color(0xFFFBE9E7))) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(if (selecionado == questao.respostaCorreta) "✅ Acertou!" else "❌ Errou!", fontWeight = FontWeight.Bold)
                        Text(text = questao.explicacao, fontSize = 14.sp)
                    }
                }
                Button(onClick = { onProxima(selecionado == questao.respostaCorreta) }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    Text("Próxima Questão")
                }
            }
        }
    }
}

@Composable
fun TelaResultado(pontos: Int, total: Int, onReiniciar: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Simulado Concluído!", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("$pontos de $total acertos", fontSize = 42.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1B5E20))
        Button(onClick = onReiniciar, modifier = Modifier.padding(top = 32.dp)) { Text("Refazer Simulado") }
    }
}
