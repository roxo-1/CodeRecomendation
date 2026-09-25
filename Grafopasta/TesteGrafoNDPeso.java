/*
Carolina Lee 10440304
Pedro Casas Pequeno Junior 10437031
Pedro Gabriel Guimarães Fernandes 10437465

Esse arquivo é onde estruturamos a leitura do csv e a escrita da saida, no mesmo estilo dos exercicios
realizados da materia.
*/

package Grafopasta;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TesteGrafoNDPeso {

	// Nome do arquivo CSV com a lista de arestas
	static final String ARQUIVO_CSV = "Grafopasta/lista_arestas_teste.csv";

	// Nome do arquivo onde vamos salvar o resultado
	static final String ARQUIVO_SAIDA = "Grafopasta/grafo_resultado.txt";

	public static void main(String args[]) throws IOException {

		// Onde vamos guardar: nome do vertice -> numero do vertice (0, 1, 2...)
		// Usamos LinkedHashMap para manter a ordem em que os vertices apareceram
		Map<String, Integer> vertices = new LinkedHashMap<>();

		// Passo 1: ler o CSV e ja ir descobrindo os vertices (nomes -> numeros)
		List<Aresta> arestas = lerArestasDoCsv(ARQUIVO_CSV, vertices);

		// Passo 2: agora que sabemos quantos vertices existem, criamos o grafo
		TGrafoNDPeso grafo = new TGrafoNDPeso(vertices.size());

		// Passo 3: inserir cada aresta lida dentro do grafo
		for (Aresta aresta : arestas) {
			int indiceA = vertices.get(aresta.getNomeA());
			int indiceB = vertices.get(aresta.getNomeB());

			grafo.insereAND(indiceA, indiceB, aresta.getPeso());
		}

		System.out.println("Vertices lidos: " + vertices.size());
		System.out.println("Arestas lidas: " + arestas.size());

		// Passo 4: mostrar o resultado normalmente na tela
		mostraVertices(vertices);
		grafo.showND();

		// Passo 5: mostrar o resultado de novo, so que desta vez salvando em um .txt
		salvaResultadoEmArquivo(vertices, grafo);

		System.out.println("\nResultado salvo em: " + ARQUIVO_SAIDA);
	}

	// Le o arquivo CSV linha por linha e devolve uma lista de arestas.
	// Enquanto le, tambem vai preenchendo o mapa "vertices" com cada nome novo.
	public static List<Aresta> lerArestasDoCsv(String nomeArquivo, Map<String, Integer> vertices)
			throws IOException {

		List<Aresta> arestas = new ArrayList<>();

		BufferedReader br = new BufferedReader(new FileReader(nomeArquivo));

		br.readLine(); // primeira linha e o cabecalho (no_a,no_b,peso) -> nao usamos

		String linha = br.readLine();
		while (linha != null) {

			if (!linha.trim().isEmpty()) {
				String[] campos = separaCamposDaLinha(linha);

				String nomeA = campos[0].trim();
				String nomeB = campos[1].trim();
				float peso = Float.parseFloat(campos[2].trim());

				adicionaVerticeSeForNovo(nomeA, vertices);
				adicionaVerticeSeForNovo(nomeB, vertices);

				arestas.add(new Aresta(nomeA, nomeB, peso));
			}

			linha = br.readLine();
		}

		br.close();

		return arestas;
	}

	// Se o nome do vertice ainda nao esta no mapa, adiciona ele com um novo numero.
	// Esse numero e sempre o proximo disponivel (0, 1, 2, 3...).
	public static void adicionaVerticeSeForNovo(String nome, Map<String, Integer> vertices) {
		if (!vertices.containsKey(nome)) {
			int novoIndice = vertices.size();
			vertices.put(nome, novoIndice);
		}
	}

	// Separa uma linha do CSV em campos, respeitando aspas.
	// Isso e necessario porque alguns nomes tem virgula dentro deles,
	// como "PL/SQL," -> nesse caso a virgula NAO separa colunas.
	public static String[] separaCamposDaLinha(String linha) {
		List<String> campos = new ArrayList<>();
		StringBuilder campoAtual = new StringBuilder();
		boolean dentroDeAspas = false;

		for (int i = 0; i < linha.length(); i++) {
			char c = linha.charAt(i);

			if (c == '"') {
				dentroDeAspas = !dentroDeAspas;
			} else if (c == ',' && !dentroDeAspas) {
				campos.add(campoAtual.toString());
				campoAtual = new StringBuilder();
			} else {
				campoAtual.append(c);
			}
		}
		campos.add(campoAtual.toString());

		return campos.toArray(new String[0]);
	}

	// Imprime a lista de vertices com seus numeros, por exemplo:
	// 0 = VBA
	// 1 = Visual Basic .NET
	public static void mostraVertices(Map<String, Integer> vertices) {
		System.out.println("\nMapeamento de vertices:");
		for (String nome : vertices.keySet()) {
			int indice = vertices.get(nome);
			System.out.println(indice + " = " + nome);
		}
	}

	// Faz a mesma impressao de sempre (mostraVertices + showND),
	// mas trocando o destino do System.out para um arquivo .txt.
	// No final, devolve o System.out ao normal (a tela).
	public static void salvaResultadoEmArquivo(Map<String, Integer> vertices, TGrafoNDPeso grafo)
			throws IOException {

		// guarda uma referencia para a "tela" (console) de verdade
		PrintStream telaOriginal = System.out;

		// cria uma nova saida apontando para o arquivo .txt
		PrintStream arquivo = new PrintStream(new FileOutputStream(ARQUIVO_SAIDA));

		// a partir daqui, todo "System.out.println" vai para o arquivo, nao para a tela
		System.setOut(arquivo);

		mostraVertices(vertices);
		grafo.showND();

		arquivo.close();

		// volta o System.out para a tela normalmente
		System.setOut(telaOriginal);
	}

}